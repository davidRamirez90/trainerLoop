package com.trainerloop.ui.workout

import android.view.View
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.haptics.Haptics
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.NumericMedium
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal val PlayerControlsSheetPeekHeight = 72.dp

private enum class PlayerSheetAnchor {
  Peek,
  Expanded
}

private const val PlayerSheetRubberBandFactor = 0.3f

/** Resolves the latest spec when an anchored settle starts without replacing the state. */
private class CurrentAnimationSpec<T>(
  private val currentSpec: () -> AnimationSpec<T>
) : AnimationSpec<T> {
  override fun <V : AnimationVector> vectorize(
    converter: TwoWayConverter<T, V>
  ): VectorizedAnimationSpec<V> = currentSpec().vectorize(converter)
}

/**
 * Consumes anchored-draggable's clamped remainder and exposes a damped visual offset.
 * The anchored state remains clamped and is still responsible for every settle.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PlayerSheetRubberBandEffect(
  private val currentSettleSpec: () -> AnimationSpec<Float>
) : OverscrollEffect {
  private var rawOverdrag by mutableFloatStateOf(0f)

  val offset: Float
    get() = rawOverdrag * PlayerSheetRubberBandFactor

  override val effectModifier: Modifier = Modifier

  override fun applyToScroll(
    delta: Offset,
    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    performScroll: (Offset) -> Offset
  ): Offset {
    val adjustedDeltaY = if (rawOverdrag == 0f || delta.y == 0f) {
      delta.y
    } else {
      val rawAfterDelta = rawOverdrag + delta.y
      val clampedRawOverdrag = if (rawOverdrag > 0f) {
        rawAfterDelta.coerceAtLeast(0f)
      } else {
        rawAfterDelta.coerceAtMost(0f)
      }
      rawOverdrag = clampedRawOverdrag
      delta.y - (clampedRawOverdrag - (rawAfterDelta - delta.y))
    }

    val consumedByScroll = if (adjustedDeltaY == 0f) {
      Offset.Zero
    } else {
      performScroll(Offset(delta.x, adjustedDeltaY))
    }
    val unconsumedY = adjustedDeltaY - consumedByScroll.y
    if (unconsumedY != 0f) rawOverdrag += unconsumedY

    return Offset(consumedByScroll.x, delta.y - unconsumedY)
  }

  override suspend fun applyToFling(
    velocity: Velocity,
    performFling: suspend (Velocity) -> Velocity
  ) {
    if (rawOverdrag != 0f) {
      animate(
        initialValue = rawOverdrag,
        targetValue = 0f,
        animationSpec = currentSettleSpec()
      ) { value, _ -> rawOverdrag = value }
    }
    performFling(velocity)
  }

  override val isInProgress: Boolean
    get() = rawOverdrag != 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerControlsSheet(
  modifier: Modifier = Modifier,
  isRunning: Boolean,
  isComplete: Boolean,
  elapsedSec: Int,
  segmentEndSec: Int,
  totalDurationSec: Int,
  intensityOffsetPct: Int,
  isErgEnabled: Boolean,
  showRecovery: Boolean,
  onPlayPause: () -> Unit,
  onSkip: () -> Unit,
  onStop: () -> Unit,
  onBiasDown: () -> Unit,
  onBiasUp: () -> Unit,
  onBiasReset: () -> Unit,
  onToggleErg: () -> Unit,
  onExtendRecovery: () -> Unit,
  onFinish: () -> Unit
) {
  val density = LocalDensity.current
  val view = LocalView.current
  val peekHeightPx = with(density) { PlayerControlsSheetPeekHeight.toPx() }
  val settleSpec = reducedMotionAware(MotionSpec.momentum)
  val currentDensity by rememberUpdatedState(density)
  val currentSettleSpec by rememberUpdatedState(settleSpec)
  val defaultSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>())
  val ergLabelAlpha by animateFloatAsState(
    targetValue = if (isErgEnabled) 1f else 0.82f,
    animationSpec = reducedMotionAware(MotionSpec.default),
    label = "ERG label alpha"
  )
  var sheetHeightPx by remember { mutableFloatStateOf(0f) }
  val decaySpec = remember { exponentialDecay<Float>() }

  // Keep this state stable so a pointer-down can cancel and take over from an
  // in-flight settle. Its thresholds and settle spec resolve current values.
  val state: AnchoredDraggableState<PlayerSheetAnchor> = remember {
    AnchoredDraggableState<PlayerSheetAnchor>(
      initialValue = PlayerSheetAnchor.Peek,
      positionalThreshold = { distance: Float -> distance * 0.5f },
      velocityThreshold = { with(currentDensity) { 125.dp.toPx() } },
      snapAnimationSpec = CurrentAnimationSpec { currentSettleSpec },
      decayAnimationSpec = decaySpec
    )
  }
  val rubberBandEffect = remember {
    PlayerSheetRubberBandEffect { currentSettleSpec }
  }
  val anchors = remember(sheetHeightPx, peekHeightPx) {
    DraggableAnchors<PlayerSheetAnchor> {
      PlayerSheetAnchor.Expanded at 0f
      PlayerSheetAnchor.Peek at (sheetHeightPx - peekHeightPx).coerceAtLeast(0f)
    }
  }
  SideEffect { state.updateAnchors(anchors, state.targetValue) }

  // Workout complete: surface the Finish action instead of leaving it hidden
  // below the peek line.
  LaunchedEffect(isComplete) {
    if (isComplete) state.animateTo(PlayerSheetAnchor.Expanded)
  }

  val sheetOffset = state.offset
  val safeOffset = if (sheetOffset.isNaN()) {
    (sheetHeightPx - peekHeightPx).coerceAtLeast(0f)
  } else {
    sheetOffset
  }
  val visualOffset = safeOffset + rubberBandEffect.offset

  Column(
    modifier = modifier
      .fillMaxWidth()
      .onSizeChanged { sheetHeightPx = it.height.toFloat() }
      .offset { IntOffset(0, visualOffset.roundToInt()) }
      .anchoredDraggable(
        state = state,
        orientation = Orientation.Vertical,
        overscrollEffect = rubberBandEffect
      )
      .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .navigationBarsPadding()
      .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .width(32.dp)
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
    Spacer(modifier = Modifier.height(Spacing.xs))

    TransportRow(
      isRunning = isRunning,
      isComplete = isComplete,
      elapsedSec = elapsedSec,
      onPlayPause = onPlayPause,
      onSkip = onSkip,
      onStop = onStop,
      skipEnabled = segmentEndSec < totalDurationSec
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      BiasStepButton(
        icon = { Icon(Icons.Default.Remove, contentDescription = null) },
        description = "Decrease bias",
        isActive = isRunning && !isComplete,
        view = view,
        onStep = onBiasDown
      )
      TextButton(onClick = onBiasReset, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
        Text(
          text = formatBias(intensityOffsetPct),
          style = NumericMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      BiasStepButton(
        icon = { Text("+", style = NumericMedium.copy(fontSize = 22.sp)) },
        description = "Increase bias",
        isActive = isRunning && !isComplete,
        view = view,
        onStep = onBiasUp
      )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val ergLabelColor by animateColorAsState(
        targetValue = if (isErgEnabled) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = defaultSpec,
        label = "ERG label color"
      )
      Text(
        text = "ERG mode",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge,
        color = ergLabelColor.copy(alpha = ergLabelAlpha)
      )
      Switch(
        checked = isErgEnabled,
        onCheckedChange = {
          if (isRunning && !isComplete) Haptics.ergToggle(view)
          onToggleErg()
        }
      )
    }

    if (showRecovery) {
      Spacer(modifier = Modifier.height(Spacing.md))
      FilledTonalButton(
        onClick = onExtendRecovery,
        modifier = Modifier.fillMaxWidth().pressable()
      ) {
        Text("+30s recovery")
      }
    }

    if (isComplete) {
      Spacer(modifier = Modifier.height(Spacing.md))
      Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().pressable()) {
        Text("Finish Workout")
      }
    }
  }
}

@Composable
private fun TransportRow(
  isRunning: Boolean,
  isComplete: Boolean,
  elapsedSec: Int,
  onPlayPause: () -> Unit,
  onSkip: () -> Unit,
  onStop: () -> Unit,
  skipEnabled: Boolean
) {
  val playPauseInteractionSource = remember { MutableInteractionSource() }
  val skipInteractionSource = remember { MutableInteractionSource() }
  val stopInteractionSource = remember { MutableInteractionSource() }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    Button(
      onClick = onPlayPause,
      modifier = Modifier
        .pressable(playPauseInteractionSource)
        .weight(1f),
      interactionSource = playPauseInteractionSource
    ) {
      Icon(
        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isRunning) "Pause" else "Start"
      )
      Spacer(modifier = Modifier.width(Spacing.xs))
      Text(
        if (isRunning) "Pause"
        else if (elapsedSec > 0 && !isComplete) "Resume"
        else "Start"
      )
    }

    FilledTonalButton(
      onClick = onSkip,
      enabled = skipEnabled,
      modifier = Modifier.pressable(skipInteractionSource),
      interactionSource = skipInteractionSource
    ) {
      Icon(Icons.Default.SkipNext, contentDescription = "Skip")
      Spacer(modifier = Modifier.width(Spacing.xs))
      Text("Skip")
    }

    // Destructive red is reserved for the confirm step (plan: Agency/forgiveness);
    // the always-visible transport Stop stays tonal.
    FilledTonalButton(
      onClick = onStop,
      modifier = Modifier.pressable(stopInteractionSource),
      interactionSource = stopInteractionSource
    ) {
      Icon(Icons.Default.Stop, contentDescription = "Stop")
    }
  }
}

@Composable
private fun BiasStepButton(
  icon: @Composable () -> Unit,
  description: String,
  isActive: Boolean,
  view: View,
  onStep: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val latestOnStep by rememberUpdatedState(onStep)

  fun step() {
    if (isActive) Haptics.biasDetent(view)
    latestOnStep()
  }

  LaunchedEffect(isPressed) {
    if (!isPressed) return@LaunchedEffect
    delay(400)
    while (true) {
      step()
      delay(200)
    }
  }

  FilledTonalButton(
    onClick = ::step,
    modifier = Modifier
      .size(52.dp)
      .clearAndSetSemantics { contentDescription = description }
      .pressable(interactionSource),
    interactionSource = interactionSource,
    contentPadding = PaddingValues(0.dp)
  ) {
    icon()
  }
}

private fun formatBias(offset: Int): String =
  "${if (offset >= 0) "+" else ""}${offset}%"
