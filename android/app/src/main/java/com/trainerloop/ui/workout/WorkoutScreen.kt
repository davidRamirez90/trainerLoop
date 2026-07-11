package com.trainerloop.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.app.WorkoutForegroundService
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.components.WorkoutChart
import com.trainerloop.ui.components.workoutProfileSummary
import com.trainerloop.ui.haptics.Haptics
import com.trainerloop.ui.components.AnimatedMetricValue
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.Green40
import com.trainerloop.ui.theme.NumericDisplay
import com.trainerloop.ui.theme.NumericLarge
import com.trainerloop.ui.theme.NumericMedium
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.zoneColorSet
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.workout.WorkoutStatsPager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
  workout: Workout,
  viewModel: WorkoutViewModel,
  onSessionFinished: (WorkoutFinishData) -> Unit,
  onExit: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val finishData by viewModel.finishEvent.collectAsStateWithLifecycle()
  val view = LocalView.current
  val context = LocalContext.current
  val ftp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }
  val completionSoundEnabled = remember {
    context.trainerLoopApp.profileRepository.getProfileSync().completionSoundEnabled
  }
  val toneGen = remember {
    android.media.ToneGenerator(
      android.media.AudioManager.STREAM_MUSIC,
      android.media.ToneGenerator.MAX_VOLUME
    )
  }
  val darkTheme = isSystemInDarkTheme()
  val currentPowerColor = zoneColorSet(uiState.currentPowerWatts, ftp).line
  var showStopConfirm by remember { mutableStateOf(false) }

  fun requestStop() {
    if (uiState.elapsedSec == 0) onExit() else showStopConfirm = true
  }

  BackHandler(enabled = uiState.elapsedSec > 0) { showStopConfirm = true }

  LaunchedEffect(finishData) {
    finishData?.let {
      viewModel.consumeFinishEvent()
      onSessionFinished(it)
    }
  }

  LaunchedEffect(uiState.isComplete) {
    if (uiState.isComplete) {
      Haptics.workoutComplete(view)
      if (completionSoundEnabled) {
        toneGen.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 250)
      }
    }
  }

  DisposableEffect(uiState.isRunning) {
    view.keepScreenOn = uiState.isRunning
    onDispose { view.keepScreenOn = false }
  }

  // Audible cue when the interval changes, so you don't need to watch the
  // screen. Skips the very first segment (index 0) so it doesn't fire on open.
  val tts = remember {
    var engine: android.speech.tts.TextToSpeech? = null
    engine = android.speech.tts.TextToSpeech(context) { /* status ignored; speak() no-ops if not ready */ }
    engine
  }
  DisposableEffect(Unit) {
    onDispose {
      toneGen.release()
      tts.stop()
      tts.shutdown()
    }
  }
  LaunchedEffect(uiState.segmentIndex) {
    if (uiState.isRunning && uiState.segmentIndex > 0) {
      Haptics.intervalChange(view)
      toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
      val seg = workout.segments.getOrNull(uiState.segmentIndex)
      val label = seg?.label ?: seg?.phase?.name?.lowercase() ?: "next interval"
      val target = uiState.targetRange
      val spoken = if (target.low > 0) "$label, ${(target.low + target.high) / 2} watts" else label
      tts.speak(spoken, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "seg-${uiState.segmentIndex}")
    }
  }

  LaunchedEffect(uiState.isRunning, uiState.segmentIndex, uiState.elapsedSec) {
    if (!uiState.isRunning) return@LaunchedEffect
    val remainingSec = uiState.segmentEndSec - uiState.elapsedSec
    if (remainingSec in 1..5) Haptics.countdownTick(view)
  }

  // Speak urgent coach feedback (safety / fatigue / sensor tiers) so it lands
  // eyes-free mid-interval; lower tiers stay visual-only.
  LaunchedEffect(uiState.liveFeedback?.id) {
    val feedback = uiState.liveFeedback ?: return@LaunchedEffect
    val spokenCategories = setOf(
      com.trainerloop.domain.coach.FeedbackCategory.SAFETY,
      com.trainerloop.domain.coach.FeedbackCategory.DATA_QUALITY,
      com.trainerloop.domain.coach.FeedbackCategory.FATIGUE_MANAGEMENT
    )
    if (feedback.category in spokenCategories) {
      tts.speak(
        feedback.message,
        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
        null,
        "coach-${feedback.id}"
      )
    }
  }

  // Service lives while a session exists (running or paused); paused rides keep
  // process protection but the service holds no wake lock (isRunning = false).
  val sessionActive = uiState.elapsedSec > 0 || uiState.isRunning
  LaunchedEffect(sessionActive, uiState.isRunning) {
    if (sessionActive) {
      WorkoutForegroundService.start(
        context,
        uiState.currentPowerWatts,
        formatDuration(uiState.elapsedSec)
      )
      if (!uiState.isRunning) {
        WorkoutForegroundService.update(
          context,
          uiState.currentPowerWatts,
          formatDuration(uiState.elapsedSec),
          false
        )
      }
    } else {
      WorkoutForegroundService.stop(context)
    }
  }

  DisposableEffect(Unit) {
    onDispose { WorkoutForegroundService.stop(context) }
  }

  LaunchedEffect(Unit) {
    context.trainerLoopApp.stopRequests.collect { viewModel.stop() }
  }

  // Throttle FGS notification re-posts: each update() is a startService IPC +
  // Notification rebuild. Keying on a 3 s time bucket (not raw elapsedSec) cuts
  // that per-second churn to ~once per 3 s while keeping the "W • m:ss" fresh.
  LaunchedEffect(uiState.currentPowerWatts, uiState.elapsedSec / 3) {
    if (uiState.isRunning) {
      val timeStr = formatDuration(uiState.elapsedSec)
      WorkoutForegroundService.update(context, uiState.currentPowerWatts, timeStr, true)
    }
  }

  val isLandscape =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  if (isLandscape) {
    LandscapeWorkout(
      uiState = uiState,
      ftp = ftp,
      powerColor = currentPowerColor,
      dark = darkTheme,
      onPlayPause = {
        when {
          uiState.isRunning -> viewModel.pause()
          uiState.isComplete -> viewModel.maybeEmitFinish()
          else -> if (uiState.elapsedSec == 0) viewModel.start() else viewModel.resume()
        }
      },
      onStop = { requestStop() }
    )
    if (showStopConfirm) {
      StopConfirmDialog(
        onDismiss = { showStopConfirm = false },
        onConfirm = { showStopConfirm = false; viewModel.stop() }
      )
    }
    return
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text(workout.name) },
        navigationIcon = {
          IconButton(onClick = { requestStop() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {}
      )
    }
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = Spacing.lg)
          .padding(bottom = PlayerControlsSheetPeekHeight)
      ) {
      // Reading hierarchy: interval context, dominant power, then secondary metrics.
      val currentSegment = uiState.segments.getOrNull(uiState.segmentIndex)
      IntervalContextLine(
        label = currentSegment?.label ?: currentSegment?.phase?.name ?: "Interval",
        position = "${uiState.segmentIndex + 1}/${uiState.segments.size}",
        remaining = formatDuration(
          (uiState.segmentEndSec - uiState.elapsedSec).coerceAtLeast(0)
        )
      )

      Spacer(modifier = Modifier.height(Spacing.lg))

      val sessionHasStarted = uiState.isRunning || uiState.elapsedSec > 0
      PowerHero(
        powerWatts = uiState.currentPowerWatts,
        powerColor = currentPowerColor,
        sessionHasStarted = sessionHasStarted,
        targetRange = uiState.targetRange,
        inZoneSec = uiState.inZoneSec,
        segmentElapsedSec = uiState.elapsedInSegmentSec.coerceAtLeast(1),
        progressColor = zoneColorSet(
          targetWatts = (uiState.targetRange.low + uiState.targetRange.high) / 2,
          ftp = ftp
        ).fill
      )

      Spacer(modifier = Modifier.height(Spacing.lg))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
      ) {
        SecondaryMetricTile(
          label = "HR",
          unit = "bpm",
          value = uiState.currentHrBpm,
          modifier = Modifier.weight(1f)
        )
        SecondaryMetricTile(
          label = "Cadence",
          unit = "rpm",
          value = uiState.currentCadenceRpm,
          modifier = Modifier.weight(1f)
        )
      }

      uiState.currentVirtualSpeedKph?.let { speedKph ->
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          BigMetric(
            label = "Speed",
            value = "%.1f".format(speedKph),
            unit = "km/h",
            modifier = Modifier.weight(1f)
          )
          BigMetric(
            label = "Grade",
            value = "%.1f".format(uiState.currentGradePercent ?: 0.0),
            unit = "%",
            modifier = Modifier.weight(1f)
          )
          BigMetric(
            label = "Distance",
            value = "%.1f".format((uiState.virtualDistanceM ?: 0.0) / 1000.0),
            unit = "km",
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      // Chart section with tabs
      WorkoutStatsPager(
        powerSamples = uiState.samples,
        modifier = Modifier.fillMaxWidth()
      ) {
        val currentSegment = uiState.segments.getOrNull(uiState.segmentIndex)
        Text(
          text = currentSegment?.label ?: currentSegment?.phase?.name ?: "",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "Segment ${uiState.segmentIndex + 1}/${uiState.segments.size}",
          style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        WorkoutChart(
          segments = uiState.segments,
          samples = uiState.samples,
          elapsedSec = uiState.elapsedSec,
          ftp = ftp,
          modifier = Modifier.fillMaxWidth(),
          elevationProfile = uiState.elevationProfile
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          FooterStat("Elapsed", formatDuration(uiState.elapsedSec))
          FooterStat(
            "Remaining",
            formatDuration((WorkoutMath.totalDurationSec(uiState.segments) - uiState.elapsedSec).coerceAtLeast(0))
          )
          FooterStat("Total", formatDuration(WorkoutMath.totalDurationSec(uiState.segments)))
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      // Live coach feedback (auto-dismisses after 12 s)
      AnimatedVisibility(
        visible = uiState.liveFeedback != null,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
        uiState.liveFeedback?.let { feedback ->
          com.trainerloop.ui.coach.LiveFeedbackCard(item = feedback)
          Spacer(modifier = Modifier.height(8.dp))
        }
      }

        Spacer(modifier = Modifier.height(8.dp))
      }

      PlayerControlsSheet(
        modifier = Modifier.align(Alignment.BottomCenter),
        isRunning = uiState.isRunning,
        isComplete = uiState.isComplete,
        elapsedSec = uiState.elapsedSec,
        segmentEndSec = uiState.segmentEndSec,
        totalDurationSec = WorkoutMath.totalDurationSec(uiState.segments),
        intensityOffsetPct = uiState.intensityOffsetPct,
        isErgEnabled = uiState.isErgEnabled,
        showRecovery = uiState.segments.getOrNull(uiState.segmentIndex)?.phase ==
          com.trainerloop.data.model.SegmentPhase.RECOVERY,
        onPlayPause = {
          if (uiState.isRunning) {
            viewModel.pause()
          } else {
            val resumable = uiState.elapsedSec > 0 && !uiState.isComplete
            if (resumable) viewModel.resume() else viewModel.start()
          }
        },
        onSkip = { viewModel.skipSegment() },
        onStop = { requestStop() },
        onBiasDown = { viewModel.fineIntensityDown() },
        onBiasUp = { viewModel.fineIntensityUp() },
        onBiasReset = {
          repeat(kotlin.math.abs(uiState.intensityOffsetPct)) {
            if (uiState.intensityOffsetPct > 0) {
              viewModel.fineIntensityDown()
            } else {
              viewModel.fineIntensityUp()
            }
          }
        },
        onToggleErg = { viewModel.toggleErg() },
        onExtendRecovery = { viewModel.extendCurrentRecovery() },
        onFinish = { viewModel.maybeEmitFinish() }
      )
    }
  }

  if (showStopConfirm) {
    StopConfirmDialog(
      onDismiss = { showStopConfirm = false },
      onConfirm = { showStopConfirm = false; viewModel.stop() }
    )
  }
}

@Composable
private fun StopConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("End workout?") },
    text = { Text("Your ride will be saved.") },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
      ) {
        Text("End")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

@Composable
private fun InZoneProgressIndicator(
  inZoneSec: Int,
  segmentElapsedSec: Int,
  fillColor: Color
) {
  val progress by animateFloatAsState(
    targetValue = (inZoneSec.toFloat() / segmentElapsedSec).coerceIn(0f, 1f),
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
    label = "In-zone progress"
  )
  val animatedFillColor by animateColorAsState(
    targetValue = fillColor,
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
    label = "Interval zone color"
  )
  androidx.compose.material3.LinearProgressIndicator(
    progress = { progress },
    modifier = Modifier
      .fillMaxWidth()
      .height(4.dp)
      .clip(RoundedCornerShape(2.dp)),
    color = animatedFillColor,
    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
  )
}

@Composable
private fun IntervalContextLine(
  label: String,
  position: String,
  remaining: String,
  stacked: Boolean = false
) {
  if (stacked) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = label,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.weight(1f)
        )
        Text(
          text = position,
          style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
      Text(
        text = "$remaining left",
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
  } else {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = " · $position · ",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
      Text(
        text = "$remaining left",
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun PowerHero(
  powerWatts: Int,
  powerColor: Color,
  sessionHasStarted: Boolean,
  targetRange: com.trainerloop.data.model.TargetRange,
  inZoneSec: Int,
  segmentElapsedSec: Int,
  progressColor: Color,
  powerStyle: TextStyle = NumericDisplay
) {
  val animatedPowerColor by animateColorAsState(
    targetValue = powerColor,
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
    label = "Power zone color"
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clearAndSetSemantics {
        contentDescription = "Power " +
          if (sessionHasStarted) "$powerWatts watts" else "not available"
      },
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(verticalAlignment = Alignment.Bottom) {
      AnimatedMetricValue(
        value = powerWatts,
        showDashWhenZero = !sessionHasStarted,
        style = powerStyle,
        color = animatedPowerColor
      )
      Spacer(modifier = Modifier.width(Spacing.xs))
      Text(
        text = "W",
        style = NumericSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.sm)
      )
    }

    Text(
      text = "target ${targetRange.low}–${targetRange.high} W",
      style = NumericSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(Spacing.sm))

    if (targetRange.low > 0) {
      InZoneProgressIndicator(
        inZoneSec = inZoneSec,
        segmentElapsedSec = segmentElapsedSec,
        fillColor = progressColor
      )
    }
  }
}

@Composable
private fun SecondaryMetricTile(
  label: String,
  unit: String,
  value: Int,
  modifier: Modifier = Modifier,
  compact: Boolean = false
) {
  Card(
    modifier = modifier.clearAndSetSemantics {
      contentDescription = liveMetricDescription(label, value, unit, zeroIsUnavailable = true)
    },
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(if (compact) Spacing.sm else Spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Row(verticalAlignment = Alignment.Bottom) {
        AnimatedMetricValue(
          value = value,
          showDashWhenZero = true,
          style = if (compact) NumericSmall else NumericMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
          text = unit,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

/**
 * Landscape mode: the chart is the hero. Big scrollable/zoomable interval graph
 * with a compact metric rail. Editing controls (skip / intensity / extend) are
 * intentionally absent — rotate back to portrait to adjust the ride.
 */
@Composable
private fun LandscapeWorkout(
  uiState: WorkoutUiState,
  ftp: Int,
  powerColor: Color,
  dark: Boolean,
  onPlayPause: () -> Unit,
  onStop: () -> Unit
) {
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  Row(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Metric rail
    Column(
      modifier = Modifier
        .width(160.dp)
        .fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      val currentSegment = uiState.segments.getOrNull(uiState.segmentIndex)
      IntervalContextLine(
        label = currentSegment?.label ?: currentSegment?.phase?.name ?: "Interval",
        position = "${uiState.segmentIndex + 1}/${uiState.segments.size}",
        remaining = formatDuration(
          (uiState.segmentEndSec - uiState.elapsedSec).coerceAtLeast(0)
        ),
        stacked = true
      )

      PowerHero(
        powerWatts = uiState.currentPowerWatts,
        powerColor = powerColor,
        sessionHasStarted = uiState.isRunning || uiState.elapsedSec > 0,
        targetRange = uiState.targetRange,
        inZoneSec = uiState.inZoneSec,
        segmentElapsedSec = uiState.elapsedInSegmentSec.coerceAtLeast(1),
        progressColor = zoneColorSet(
          targetWatts = (uiState.targetRange.low + uiState.targetRange.high) / 2,
          ftp = ftp
        ).fill,
        powerStyle = NumericLarge
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
      ) {
        SecondaryMetricTile(
          label = "HR",
          unit = "bpm",
          value = uiState.currentHrBpm,
          modifier = Modifier.weight(1f),
          compact = true
        )
        SecondaryMetricTile(
          label = "Cadence",
          unit = "rpm",
          value = uiState.currentCadenceRpm,
          modifier = Modifier.weight(1f),
          compact = true
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Elapsed",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = formatDuration(uiState.elapsedSec),
          style = NumericSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Spacer(Modifier.weight(1f))
      val playPauseInteractionSource = remember { MutableInteractionSource() }
      val stopInteractionSource = remember { MutableInteractionSource() }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledIconButton(
          onClick = onPlayPause,
          modifier = Modifier
            .pressable(playPauseInteractionSource)
            .weight(1f),
          interactionSource = playPauseInteractionSource
        ) {
          AnimatedContent(
            targetState = uiState.isRunning,
            transitionSpec = {
              fadeIn(animationSpec = fastMotionSpec) togetherWith
                fadeOut(animationSpec = fastMotionSpec)
            },
            label = "landscape-transport-icon"
          ) { running ->
            Icon(
              if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (running) "Pause" else "Play"
            )
          }
        }
        FilledIconButton(
          onClick = onStop,
          modifier = Modifier
            .pressable(stopInteractionSource)
            .weight(1f),
          interactionSource = stopInteractionSource,
          colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          )
        ) {
          Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
      }
    }

    ImmersiveWorkoutChart(
      uiState = uiState,
      ftp = ftp,
      dark = dark,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    )
  }
}

private val ZOOM_LEVELS = listOf(1f, 2f, 4f, 8f)

@Composable
private fun ImmersiveWorkoutChart(
  uiState: WorkoutUiState,
  ftp: Int,
  dark: Boolean,
  modifier: Modifier = Modifier
) {
  val segments = uiState.segments
  val samples = uiState.samples
  val elapsedSec = uiState.elapsedSec
  val totalDuration = remember(segments) { WorkoutMath.totalDurationSec(segments) }

  var zoomIdx by remember { mutableStateOf(0) }
  val zoom = ZOOM_LEVELS[zoomIdx]
  val scrollState = rememberScrollState()
  val density = LocalDensity.current
  // Touching the chart pauses auto-follow so you can inspect earlier intervals;
  // the "Live" button re-engages it.
  var followEnabled by remember { mutableStateOf(true) }

  val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
  val hrLineColor = MaterialTheme.colorScheme.error
  val powerLineColor = MaterialTheme.colorScheme.secondary
  val gridColor = cursorColor.copy(alpha = 0.15f)

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surface)
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val viewportWidth = maxWidth
      val contentWidth = viewportWidth * zoom

      // Auto-follow the cursor while running, keeping it ~40% from the left.
      LaunchedEffect(elapsedSec, zoom, totalDuration, followEnabled) {
        if (followEnabled && uiState.isRunning && totalDuration > 0) {
          val contentPx = with(density) { contentWidth.toPx() }
          val viewportPx = with(density) { viewportWidth.toPx() }
          val cursorPx = contentPx * (elapsedSec.toFloat() / totalDuration)
          val target = (cursorPx - viewportPx * 0.4f)
            .coerceIn(0f, (contentPx - viewportPx).coerceAtLeast(0f))
          scrollState.animateScrollTo(target.toInt())
        }
      }

      Canvas(
        modifier = Modifier
          .horizontalScroll(scrollState)
          .semantics { contentDescription = workoutProfileSummary(segments) }
          .pointerInput(Unit) {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false)
              followEnabled = false
            }
          }
          .width(contentWidth)
          .fillMaxHeight()
      ) {
        if (totalDuration == 0) return@Canvas
        val width = size.width
        val heightPx = size.height
        val pad = 10.dp.toPx()
        val chartHeight = heightPx - pad * 2
        val chartBottom = heightPx - pad

        val peakTarget = (0..totalDuration step (totalDuration / 100).coerceAtLeast(1))
          .maxOf { WorkoutMath.targetRangeAt(segments, it).high }
        val peakSample = samples.maxOfOrNull { it.powerWatts } ?: 0
        val maxPowerAxis = (maxOf(peakTarget, peakSample, 1) * 1.1f)

        fun xForTime(sec: Int): Float = (sec / totalDuration.toFloat()) * width
        fun yForPower(power: Int): Float =
          chartBottom - (power / maxPowerAxis).coerceIn(0f, 1f) * chartHeight
        fun yForHr(bpm: Int): Float =
          chartBottom - ((bpm - 40f) / 160f).coerceIn(0f, 1f) * chartHeight

        if (ftp > 0) {
          listOf(ftp, ftp / 2).forEach { watts ->
            val y = yForPower(watts)
            drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
          }
        }

        // Zone blocks from zero.
        val step = (totalDuration / 400).coerceAtLeast(1)
        var sec = 0
        while (sec <= totalDuration) {
          val range = WorkoutMath.targetRangeAt(segments, sec)
          val target = (range.low + range.high) / 2
          val x = xForTime(sec)
          val xEnd = xForTime((sec + step).coerceAtMost(totalDuration))
          val yTop = yForPower(target)
          drawRect(
            color = ZoneColors.forTarget(target, ftp, dark).fill,
            topLeft = Offset(x, yTop),
            size = Size((xEnd - x).coerceAtLeast(1f), chartBottom - yTop)
          )
          sec += step
        }

        if (samples.size >= 2) {
          var hrPath: Path? = null
          samples.forEach { s ->
            if (s.hrBpm <= 0) hrPath = null
            else {
              val p = Offset(xForTime(s.timeSec), yForHr(s.hrBpm))
              val cur = hrPath
              if (cur == null) hrPath = Path().apply { moveTo(p.x, p.y) }
              else cur.lineTo(p.x, p.y)
            }
          }
          hrPath?.let { drawPath(it, hrLineColor, style = Stroke(width = 2.dp.toPx())) }

          val power = Path()
          samples.firstOrNull()?.let { power.moveTo(xForTime(it.timeSec), yForPower(it.powerWatts)) }
          samples.drop(1).forEach { power.lineTo(xForTime(it.timeSec), yForPower(it.powerWatts)) }
          drawPath(power, powerLineColor, style = Stroke(width = 2.5.dp.toPx()))
        }

        val cursorX = xForTime(elapsedSec)
        drawLine(cursorColor, Offset(cursorX, 0f), Offset(cursorX, heightPx), strokeWidth = 2.dp.toPx())
      }
    }

    // Zoom controls, floating top-end.
    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      AnimatedVisibility(
        visible = !followEnabled,
        enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.fast)),
        exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.fast))
      ) {
        FilledTonalButton(
          onClick = { followEnabled = true },
          modifier = Modifier.heightIn(min = 48.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(androidx.compose.foundation.shape.CircleShape)
              .background(Green40)
          )
          Spacer(Modifier.width(6.dp))
          Text("Live", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
      }
      FilledIconButton(
        onClick = { if (zoomIdx > 0) zoomIdx-- },
        enabled = zoomIdx > 0,
        modifier = Modifier.size(48.dp)
      ) { Icon(Icons.Default.Remove, contentDescription = "Zoom out") }
      Text(
        text = "${zoom.toInt()}×",
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold
      )
      FilledIconButton(
        onClick = { if (zoomIdx < ZOOM_LEVELS.lastIndex) zoomIdx++ },
        enabled = zoomIdx < ZOOM_LEVELS.lastIndex,
        modifier = Modifier.size(48.dp)
      ) { Icon(Icons.Default.Add, contentDescription = "Zoom in") }
    }
  }
}

@Composable
private fun BigMetric(
  label: String,
  value: String,
  unit: String,
  modifier: Modifier = Modifier,
  highlight: Boolean = false,
  valueColor: androidx.compose.ui.graphics.Color? = null,
  animatedValue: Int? = null,
  animatedShowDashWhenZero: Boolean = false
) {
  val defaultValueColor = if (highlight) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val resolvedValueColor = if (valueColor != null) {
    val animatedColor by animateColorAsState(
      targetValue = valueColor,
      animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
      label = "Power zone color"
    )
    animatedColor
  } else {
    defaultValueColor
  }

  Card(
    modifier = modifier.clearAndSetSemantics {
      contentDescription = liveMetricDescription(
        label,
        value,
        unit,
        zeroIsUnavailable = animatedShowDashWhenZero
      )
    },
    colors = CardDefaults.cardColors(
      containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Row(verticalAlignment = Alignment.Bottom) {
        val valueStyle = MaterialTheme.typography.displaySmall.copy(
          fontWeight = FontWeight.Bold,
          fontFeatureSettings = "tnum"
        )
        if (animatedValue != null) {
          AnimatedMetricValue(
            value = animatedValue,
            showDashWhenZero = animatedShowDashWhenZero,
            style = valueStyle,
            color = resolvedValueColor
          )
        } else {
          Text(text = value, style = valueStyle, color = resolvedValueColor)
        }
        Spacer(modifier = Modifier.width(2.dp))
        Text(
          text = unit,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun liveMetricDescription(
  label: String,
  value: Any,
  unit: String,
  zeroIsUnavailable: Boolean
): String {
  val valueText = if (zeroIsUnavailable && value.toString() == "0") {
    "not available"
  } else {
    value.toString()
  }
  return listOf(label, valueText, unit).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun FooterStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
      fontWeight = FontWeight.SemiBold
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private fun formatDuration(totalSec: Int): String {
  val min = totalSec / 60
  val sec = totalSec % 60
  return "%d:%02d".format(min, sec)
}

private fun formatShortDuration(totalSec: Int): String {
  val min = totalSec / 60
  val sec = totalSec % 60
  return if (min > 0) "${min}m" else "${sec}s"
}
