package com.trainerloop.ui.complete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.trainerloop.ui.components.MetricTile
import com.trainerloop.ui.components.MetricTileState
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.SampleChart
import com.trainerloop.ui.components.SecondaryActionButton
import com.trainerloop.ui.components.SecondaryActionStyle
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.trainerLoopColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCompleteScreen(
  viewModel: WorkoutCompleteViewModel,
  onDiscard: () -> Unit,
  onDone: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  var displayedUploadStatus by remember { mutableStateOf<String?>(null) }
  var displayedError by remember { mutableStateOf<String?>(null) }
  var displayedFtpPushStatus by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(uiState.uploadStatus) {
    uiState.uploadStatus?.let { displayedUploadStatus = it }
  }
  LaunchedEffect(uiState.error) {
    uiState.error?.let { displayedError = it }
  }
  LaunchedEffect(uiState.ftpPushStatus) {
    uiState.ftpPushStatus?.let { displayedFtpPushStatus = it }
  }

  LaunchedEffect(uiState.isDiscarded) {
    if (uiState.isDiscarded) onDiscard()
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text("Workout Complete") },
        navigationIcon = {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
        }
      )
    }
  ) { padding ->
    val semantic = MaterialTheme.trainerLoopColors

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = Spacing.screenMargin)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
    ) {
      Text(
        text = uiState.workoutName,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "${formatDuration(uiState.durationSec)} · ${uiState.avgPower} W avg",
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(Spacing.sectionGap))

      if (uiState.isRampTest) {
        RampTestResultCard(
          uiState = uiState,
          displayedFtpPushStatus = displayedFtpPushStatus,
          onAccept = viewModel::acceptFtp,
          onDiscard = viewModel::discardFtp,
          onPush = viewModel::pushFtpToIcu,
          onDeclinePush = viewModel::declineIcuFtpPush
        )
        Spacer(modifier = Modifier.height(Spacing.sectionGap))
      }

      // Summary grid: TSS, IF, NP
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
      ) {
        MetricTile(
          label = "TSS",
          value = uiState.tss.toString(),
          unit = "",
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          label = "IF",
          value = "%.2f".format(uiState.intensityFactor),
          unit = "",
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          label = "NP",
          value = "${uiState.normalizedPower}",
          unit = "W",
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(Spacing.sectionGap))

      // Stats grid
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
        ) {
          MetricTile(
            label = "Avg Power",
            value = "${uiState.avgPower}",
            unit = "W",
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            label = "Max Power",
            value = "${uiState.maxPower}",
            unit = "W",
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
        ) {
          MetricTile(
            label = "Avg Heart Rate",
            value = if (uiState.avgHr > 0) "${uiState.avgHr}" else "",
            unit = "bpm",
            state = if (uiState.avgHr > 0) MetricTileState.Available else MetricTileState.Unavailable,
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            label = "Avg Cadence",
            value = if (uiState.avgCadence > 0) "${uiState.avgCadence}" else "",
            unit = "rpm",
            state = if (uiState.avgCadence > 0) MetricTileState.Available else MetricTileState.Unavailable,
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
        ) {
          MetricTile(
            label = "Calories",
            value = "${uiState.calories}",
            unit = "kcal",
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            label = "Total Work",
            value = "${uiState.totalWorkKj}",
            unit = "kJ",
            modifier = Modifier.weight(1f)
          )
        }
        if (uiState.distanceKm > 0) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
          ) {
            MetricTile(
              label = "Distance",
              value = "%.1f".format(uiState.distanceKm),
              unit = "km",
              modifier = Modifier.weight(1f)
            )
            MetricTile(
              label = "Elevation Gain",
              value = "${uiState.ascentM}",
              unit = "m",
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.sectionGap))

      // Post-ride chart with tabs
      if (viewModel.samples.isNotEmpty()) {
        TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Ride Chart",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(Spacing.sm))
          SampleChart(samples = viewModel.samples)
        }
        Spacer(modifier = Modifier.height(Spacing.sectionGap))
      }

      uiState.coachData?.let { coach ->
        CoachSummaryCard(coach)
        Spacer(modifier = Modifier.height(Spacing.sectionGap))
      }

      // Actions: Save (primary) / Discard (destructive)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
      ) {
        androidx.compose.material3.OutlinedButton(
          onClick = { viewModel.onDiscard() },
          enabled = !uiState.isSaving,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          )
        ) {
          Text("Discard")
        }
        PrimaryActionButton(
          onClick = { viewModel.onSave() },
          enabled = !uiState.isSaved && !uiState.isSaving,
          modifier = Modifier.weight(1f)
        ) {
          AnimatedContent(
            targetState = uiState.isSaved,
            transitionSpec = {
              fadeIn(animationSpec = fastMotionSpec) togetherWith
                fadeOut(animationSpec = fastMotionSpec)
            },
            label = "complete-save-label"
          ) { saved ->
            if (saved) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Saved")
              }
            } else {
              Text("Save")
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.controlGap))

      // Share/Upload: secondary tier
      SecondaryActionButton(
        onClick = { viewModel.onShare() },
        enabled = uiState.fitFile != null,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Share FIT")
      }

      AnimatedVisibility(
        visible = uiState.uploadStatus != null,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
        displayedUploadStatus?.let { status ->
          Column {
            Spacer(modifier = Modifier.height(Spacing.controlGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = semantic.connected,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(Spacing.xs))
              Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.connected
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.controlGap))

      SecondaryActionButton(
        onClick = onDone,
        enabled = uiState.isSaved || uiState.isDiscarded || viewModel.samples.isEmpty(),
        modifier = Modifier.fillMaxWidth(),
        style = SecondaryActionStyle.Outlined
      ) {
        Text("Done")
      }

      Spacer(modifier = Modifier.height(Spacing.sectionGap))
    }

    // Error snackbar
    AnimatedVisibility(
      visible = uiState.error != null,
      enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
      exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
    ) {
      displayedError?.let { error ->
        Snackbar(
          modifier = Modifier.padding(16.dp),
          action = {
            TextButton(onClick = { viewModel.clearError() }) {
              Text("Dismiss")
            }
          }
        ) {
          Text(error)
        }
      }
    }
  }
}

@Composable
private fun RampTestResultCard(
  uiState: WorkoutCompleteUiState,
  displayedFtpPushStatus: String?,
  onAccept: () -> Unit,
  onDiscard: () -> Unit,
  onPush: () -> Unit,
  onDeclinePush: () -> Unit
) {
  val defaultMotionSpec = reducedMotionAware(MotionSpec.default)
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  TrainerLoopCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
    Column {
      Text(
        text = "FTP Test Result",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(Spacing.sm))

      val newFtp = uiState.rampTestNewFtp
      AnimatedContent(
        targetState = newFtp != null,
        transitionSpec = {
          fadeIn(animationSpec = defaultMotionSpec) togetherWith
            fadeOut(animationSpec = defaultMotionSpec)
        },
        label = "ramp-test-result"
      ) { hasResult ->
        if (!hasResult) {
          Text(
            text = "Test too short — no FTP calculated",
            style = MaterialTheme.typography.bodyLarge
          )
        } else {
          val resultFtp = requireNotNull(newFtp)
          val prev = uiState.rampTestPreviousFtp
          val delta = resultFtp - prev
          val pct = if (prev > 0) delta * 100 / prev else 0
          Text(
            text = "$resultFtp W",
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${if (delta >= 0) "up" else "down"} ${kotlin.math.abs(delta)} W " +
              "(${if (delta >= 0) "+" else ""}$pct%) from $prev W",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(12.dp))

          AnimatedContent(
            targetState = Triple(uiState.ftpDecided, uiState.showIcuFtpPrompt, uiState.ftpAccepted),
            transitionSpec = {
              fadeIn(animationSpec = fastMotionSpec) togetherWith
                fadeOut(animationSpec = fastMotionSpec)
            },
            label = "ramp-test-actions"
          ) { actionState ->
            when {
              !actionState.first -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
              ) {
                androidx.compose.material3.OutlinedButton(
                  onClick = onDiscard,
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                  )
                ) {
                  Text("Discard FTP")
                }
                PrimaryActionButton(onClick = onAccept, modifier = Modifier.weight(1f)) {
                  Text("Accept FTP")
                }
              }
              actionState.second -> {
                Text(
                  text = "FTP saved. Set FTP on intervals.icu now?",
                  style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
                ) {
                  SecondaryActionButton(
                    onClick = onDeclinePush,
                    modifier = Modifier.weight(1f),
                    style = SecondaryActionStyle.Outlined
                  ) {
                    Text("Later")
                  }
                  PrimaryActionButton(onClick = onPush, modifier = Modifier.weight(1f)) {
                    Text("Yes")
                  }
                }
              }
              else -> Text(
                text = if (actionState.third) "New FTP saved to your profile" else "FTP discarded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      AnimatedVisibility(
        visible = uiState.ftpPushStatus != null,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
        displayedFtpPushStatus?.let { status ->
          Column {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = status,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s)
  else "%d:%02d".format(m, s)
}
