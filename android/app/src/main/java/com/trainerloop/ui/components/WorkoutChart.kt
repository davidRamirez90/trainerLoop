package com.trainerloop.ui.components

import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.zoneColorSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HR_AXIS_MIN = 40f
private const val HR_AXIS_MAX = 200f
private const val ZOOM_PAD_SEC = 20
private const val PAN_FLING_THRESHOLD_PX_PER_SEC = 350f
private const val PAN_RUBBER_BAND_DP = 24f

@Composable
fun WorkoutChart(
  segments: List<WorkoutSegment>,
  samples: List<TelemetrySample>,
  elapsedSec: Int,
  ftp: Int,
  modifier: Modifier = Modifier,
  elevationProfile: DoubleArray? = null
) {
  val darkTheme = isSystemInDarkTheme()
  val totalDuration = remember(segments) { WorkoutMath.totalDurationSec(segments) }
  // Segment bounds (startSec, endSec) for tap hit-testing.
  val bounds = remember(segments) {
    var acc = 0
    segments.map { seg -> Triple(acc, acc + seg.durationSec, seg).also { acc += seg.durationSec } }
  }

  var zoomToCurrent by remember { mutableStateOf(false) }
  var selectedIndex by remember { mutableStateOf<Int?>(null) }
  var tooltipIndex by remember { mutableStateOf<Int?>(null) }
  var selectionInteraction by remember { mutableStateOf(0) }
  var tooltipX by remember { mutableStateOf(0f) }
  var tooltipGrabOffset by remember { mutableStateOf(0f) }
  var tooltipWidthPx by remember { mutableStateOf(0) }
  var chartWidthPx by remember { mutableStateOf(0) }

  // Visible time window: whole session, or the current interval padded.
  val targetWindow = computeWorkoutChartWindow(
    zoomToCurrent = zoomToCurrent,
    totalDurationSec = totalDuration,
    elapsedSec = elapsedSec,
    segments = segments,
    bounds = bounds
  )
  val baseWinStart by animateFloatAsState(
    targetValue = targetWindow.startSec,
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
    label = "Chart window start"
  )
  val baseWinEnd by animateFloatAsState(
    targetValue = targetWindow.endSec,
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
    label = "Chart window end"
  )
  val animatedElapsedSec by animateFloatAsState(
    targetValue = elapsedSec.toFloat(),
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
    label = "Chart cursor"
  )
  val selectedHighlightAlpha by animateFloatAsState(
    targetValue = if (selectedIndex != null) 0.18f else 0f,
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
    label = "Chart selected interval highlight"
  )
  LaunchedEffect(selectedIndex, selectionInteraction) {
    if (selectedIndex != null) {
      delay(4_000)
      selectedIndex = null
    }
  }
  val panOffset = remember { Animatable(0f) }
  val panScope = rememberCoroutineScope()
  val reducedMotion = com.trainerloop.ui.theme.LocalReducedMotion.current
  val decaySpec = rememberSplineBasedDecay<Float>()
  val panSettleSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>())
  val panBounds = computeWorkoutChartPanBounds(
    windowStartSec = baseWinStart,
    windowEndSec = baseWinEnd,
    totalDurationSec = totalDuration
  )
  val maxRubberBandPx = with(LocalDensity.current) { PAN_RUBBER_BAND_DP.dp.toPx() }
  val maxRubberBandSec = if (chartWidthPx > 0) {
    maxRubberBandPx / chartWidthPx * (baseWinEnd - baseWinStart).coerceAtLeast(1f)
  } else {
    0f
  }
  val renderedPanOffset = if (reducedMotion) {
    clampWorkoutChartPanOffset(panOffset.value, panBounds)
  } else {
    rubberBandWorkoutChartPanOffset(panOffset.value, panBounds, maxRubberBandSec)
  }
  val winStart = baseWinStart + renderedPanOffset
  val winEnd = baseWinEnd + renderedPanOffset
  val winSpan = (winEnd - winStart).coerceAtLeast(1f)

  androidx.compose.runtime.LaunchedEffect(zoomToCurrent) {
    panOffset.stop()
    panOffset.snapTo(0f)
  }

  // Keep gesture hit-testing attached to the pointer input coroutine while the
  // animated window changes on every frame.
  val currentWinStart = rememberUpdatedState(winStart)
  val currentWinEnd = rememberUpdatedState(winEnd)
  val currentZoomToCurrent = rememberUpdatedState(zoomToCurrent)
  val currentReducedMotion = rememberUpdatedState(reducedMotion)
  val currentPanBounds = rememberUpdatedState(panBounds)

  // Static across the ride (depends only on the plan), so don't rescan the plan
  // on every 1 Hz redraw.
  val peakTarget = remember(segments, totalDuration) {
    if (totalDuration == 0) 0
    else (0..totalDuration step (totalDuration / 100).coerceAtLeast(1))
      .maxOf { WorkoutMath.targetRangeAt(segments, it).high }
  }
  val peakSample = remember(samples.size) { samples.maxOfOrNull { it.powerWatts } ?: 0 }
  val cachedHrPath = remember(samples.size) { buildHrPath(samples) }
  val cachedPowerPath = remember(samples.size) { buildPowerPath(samples) }
  val hrScratchPath = remember { Path() }
  val powerScratchPath = remember { Path() }
  val elevationScratchPath = remember { Path() }
  val elevationLineScratchPath = remember { Path() }
  val zoneScratchPaths = remember { Array(6) { Path() } }
  val hrMatrix = remember { Matrix() }
  val powerMatrix = remember { Matrix() }

  val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
  val hrLineColor = MaterialTheme.colorScheme.error
  val powerLineColor = MaterialTheme.colorScheme.secondary
  // Drawn OVER the opaque zone fills, so it needs a scrim + edge line to read.
  val elevationFillColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
  val elevationLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      val focusChipColor by animateColorAsState(
        targetValue = if (zoomToCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
        label = "chart-focus-chip-color"
      )
      FilterChip(
        selected = zoomToCurrent,
        onClick = { zoomToCurrent = !zoomToCurrent },
        label = { Text(if (zoomToCurrent) "Focus" else "Full") },
        colors = FilterChipDefaults.filterChipColors(
          containerColor = focusChipColor,
          selectedContainerColor = focusChipColor
        )
      )
    }

    Box {
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
          .semantics { contentDescription = workoutProfileSummary(segments) }
          .onSizeChanged { chartWidthPx = it.width }
          .pointerInput(segments, reducedMotion) {
            // Scrub owns press+drag. Pan only wins after release when the
            // horizontal scrub velocity is a Focus-mode fling.
            val config = viewConfiguration
            var previousTapTime = Long.MIN_VALUE
            var previousTapPosition = Offset.Zero
            var panJob: Job? = null

            fun selectAt(x: Float, initialPress: Boolean) {
              val chartWidth = size.width.toFloat()
              if (chartWidth <= 0f || totalDuration <= 0) return
              val currentStart = currentWinStart.value
              val currentEnd = currentWinEnd.value
              val currentSpan = (currentEnd - currentStart).coerceAtLeast(1f)
              val clampedX = x.coerceIn(0f, chartWidth)
              val sec = (currentStart + clampedX / chartWidth * currentSpan)
                .coerceAtMost(totalDuration.toFloat() - 0.001f)
              val idx = bounds.indexOfFirst { sec >= it.first && sec < it.second }
              if (idx < 0) {
                selectedIndex = null
                return
              }
              selectionInteraction++
              if (initialPress && selectedIndex == idx) {
                selectedIndex = null
                return
              }
              tooltipIndex = idx
              selectedIndex = idx
              if (initialPress) {
                val intervalX = (bounds[idx].first - currentStart) / currentSpan * chartWidth
                tooltipGrabOffset = clampedX - intervalX
                tooltipX = intervalX
              } else {
                tooltipX = clampedX - tooltipGrabOffset
              }
            }

            fun settlePan() {
              val boundsNow = currentPanBounds.value
              val target = clampWorkoutChartPanOffset(panOffset.value, boundsNow)
              if (abs(target - panOffset.value) < 0.01f) return
              panJob?.cancel()
              panJob = panScope.launch {
                if (currentReducedMotion.value) {
                  panOffset.snapTo(target)
                } else {
                  panOffset.animateTo(
                    targetValue = target,
                    animationSpec = panSettleSpec
                  )
                }
              }
            }

            fun flingPan(velocityPxPerSec: Float) {
              val boundsNow = currentPanBounds.value
              if (!currentZoomToCurrent.value || !boundsNow.canPan || size.width <= 0) {
                settlePan()
                return
              }
              val currentSpan = (currentWinEnd.value - currentWinStart.value).coerceAtLeast(1f)
              val velocitySecPerSec = -velocityPxPerSec / size.width * currentSpan
              val overshootSec = maxRubberBandPx / size.width * currentSpan
              panJob?.cancel()
              panJob = panScope.launch {
                if (currentReducedMotion.value) {
                  panOffset.snapTo(clampWorkoutChartPanOffset(panOffset.value, boundsNow))
                  return@launch
                }
                panOffset.updateBounds(
                  lowerBound = boundsNow.minOffsetSec - overshootSec,
                  upperBound = boundsNow.maxOffsetSec + overshootSec
                )
                panOffset.animateDecay(
                  initialVelocity = velocitySecPerSec,
                  animationSpec = decaySpec
                )
                panOffset.animateTo(
                  targetValue = clampWorkoutChartPanOffset(panOffset.value, boundsNow),
                  animationSpec = panSettleSpec
                )
              }
            }

            awaitEachGesture {
              panJob?.cancel()

              val down = awaitFirstDown(requireUnconsumed = false)
              down.consume()
              selectAt(down.position.x, initialPress = true)

              val tracker = VelocityTracker()
              tracker.addPosition(down.uptimeMillis, down.position)
              var movedHorizontally = false
              var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
              var cancelled = false

              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                  cancelled = true
                  break
                }
                if (change.changedToUp()) {
                  tracker.addPosition(change.uptimeMillis, change.position)
                  up = change
                  break
                }
                if (change.position != change.previousPosition) {
                  tracker.addPosition(change.uptimeMillis, change.position)
                  val delta = change.position - down.position
                  if (abs(delta.x) > config.touchSlop && abs(delta.x) >= abs(delta.y)) {
                    movedHorizontally = true
                  }
                  if (movedHorizontally) selectAt(change.position.x, initialPress = false)
                  change.consume()
                }
              }

              if (cancelled || up == null) {
                previousTapTime = Long.MIN_VALUE
                return@awaitEachGesture
              }

              val release = up
              val velocityX = tracker.calculateVelocity().x
              val isDoubleTap = !movedHorizontally &&
                previousTapTime != Long.MIN_VALUE &&
                release.uptimeMillis - previousTapTime <= config.doubleTapTimeoutMillis &&
                (release.position - previousTapPosition).getDistance() <= config.touchSlop * 2f

              if (isDoubleTap) {
                zoomToCurrent = !currentZoomToCurrent.value
                previousTapTime = Long.MIN_VALUE
              } else if (!movedHorizontally) {
                previousTapTime = release.uptimeMillis
                previousTapPosition = release.position
              } else {
                previousTapTime = Long.MIN_VALUE
              }

              if (!isDoubleTap && movedHorizontally && abs(velocityX) > PAN_FLING_THRESHOLD_PX_PER_SEC) {
                flingPan(velocityX)
              } else {
                settlePan()
              }
            }
          }
      ) {
        if (totalDuration == 0) return@Canvas

        val width = size.width
        val heightPx = size.height
        val padding = 8.dp.toPx()
        val chartHeight = heightPx - padding * 2
        val chartBottom = heightPx - padding

        val maxPowerAxis = (maxOf(peakTarget, peakSample, 1) * 1.1f)

        fun xForTime(sec: Float): Float = ((sec - winStart) / winSpan) * width

        fun yForPower(power: Float): Float {
          val ratio = (power / maxPowerAxis).coerceIn(0f, 1f)
          return chartBottom - ratio * chartHeight
        }

        // Gridlines at FTP and FTP/2.
        if (ftp > 0) {
          val gridColor = cursorColor.copy(alpha = 0.15f)
          val gridStrokeWidth = 1.dp.toPx()
          val ftpY = yForPower(ftp.toFloat())
          drawLine(color = gridColor, start = Offset(0f, ftpY), end = Offset(width, ftpY), strokeWidth = gridStrokeWidth)
          val halfFtpY = yForPower((ftp / 2).toFloat())
          drawLine(color = gridColor, start = Offset(0f, halfFtpY), end = Offset(width, halfFtpY), strokeWidth = gridStrokeWidth)
        }

        // Full-height-from-zero interval blocks over the visible window.
        // All bands of one zone are filled as a single Path: abutting rects
        // inside one path cancel their shared AA edges, so no hairline seams.
        zoneScratchPaths.forEach { it.rewind() }
        zoneBands(
          segments = segments,
          ftp = ftp,
          winStartSec = winStart,
          winEndSec = winEnd,
          stepSec = (winSpan / 200f).coerceAtLeast(1f)
        ).forEach { band ->
          val yTop = yForPower(band.targetWatts.toFloat())
          zoneScratchPaths[band.zone - 1].addRect(
            androidx.compose.ui.geometry.Rect(
              left = xForTime(band.startSec),
              top = yTop,
              right = xForTime(band.endSec),
              bottom = chartBottom
            )
          )
        }
        zoneScratchPaths.forEachIndexed { index, path ->
          if (!path.isEmpty) {
            drawPath(path, color = ZoneColors.forZone(index + 1, darkTheme).fill)
          }
        }

        // Soft terrain silhouette across the bottom 30%, drawn over the zone
        // blocks (they are opaque now) as a translucent scrim + edge line.
        if (elevationProfile != null && elevationProfile.isNotEmpty()) {
          // Local val: smart casts don't reliably reach into local functions.
          val profile = elevationProfile
          val minAlt = profile.min()
          val altSpan = (profile.max() - minAlt).coerceAtLeast(1.0)
          val bandHeight = chartHeight * 0.3f
          fun yForAlt(sec: Float): Float {
            val alt = profile[sec.toInt().coerceIn(0, profile.lastIndex)]
            return chartBottom - ((alt - minAlt) / altSpan).toFloat() * bandHeight
          }
          val elevStep = (winSpan / 200f).coerceAtLeast(1f)
          val linePath = elevationLineScratchPath
          linePath.rewind()
          linePath.moveTo(xForTime(winStart), yForAlt(winStart))
          var t = winStart + elevStep
          while (t <= winEnd) {
            linePath.lineTo(xForTime(t), yForAlt(t))
            t += elevStep
          }
          linePath.lineTo(xForTime(winEnd), yForAlt(winEnd))

          val fillPath = elevationScratchPath
          fillPath.rewind()
          fillPath.addPath(linePath)
          fillPath.lineTo(xForTime(winEnd), chartBottom)
          fillPath.lineTo(xForTime(winStart), chartBottom)
          fillPath.close()

          drawPath(fillPath, color = elevationFillColor)
          drawPath(linePath, color = elevationLineColor, style = Stroke(width = 1.5.dp.toPx()))
        }

        // Highlight the tapped interval.
        selectedIndex?.let { idx ->
          bounds.getOrNull(idx)?.let { (s, e, _) ->
            val xs = xForTime(s.toFloat()).coerceIn(0f, width)
            val xe = xForTime(e.toFloat()).coerceIn(0f, width)
            drawRect(
              color = cursorColor.copy(alpha = selectedHighlightAlpha),
              topLeft = Offset(xs, 0f),
              size = Size(xe - xs, heightPx)
            )
          }
        }

        // Cached paths are stored in time/value coordinates. Transform their
        // geometry into screen coordinates so the stroke is not scaled.
        if (samples.size >= 2) {
          val xScale = width / winSpan
          val hrRange = HR_AXIS_MAX - HR_AXIS_MIN
          val hrScaleY = -chartHeight / hrRange
          hrMatrix.setScale(xScale, hrScaleY)
          hrMatrix.postTranslate(
            -winStart * xScale,
            chartBottom - HR_AXIS_MIN * hrScaleY
          )
          val hrScratchAndroidPath = hrScratchPath.asAndroidPath()
          hrScratchAndroidPath.rewind()
          hrScratchAndroidPath.addPath(cachedHrPath.asAndroidPath(), hrMatrix)
          drawPath(hrScratchPath, color = hrLineColor, style = Stroke(width = 2.dp.toPx()))
        }

        // Power line, drawn last so it stays on top of the zone blocks. The
        // newest segment is intentionally omitted from the cached path: it is
        // drawn below from the animated elapsed value to make each live sample
        // extend into place.
        if (samples.size >= 2) {
          val xScale = width / winSpan
          val powerScaleY = -chartHeight / maxPowerAxis
          powerMatrix.setScale(xScale, powerScaleY)
          powerMatrix.postTranslate(-winStart * xScale, chartBottom)
          val powerScratchAndroidPath = powerScratchPath.asAndroidPath()
          powerScratchAndroidPath.rewind()
          powerScratchAndroidPath.addPath(cachedPowerPath.asAndroidPath(), powerMatrix)
          drawPath(powerScratchPath, color = powerLineColor, style = Stroke(width = 2.5.dp.toPx()))

          val previous = samples[samples.lastIndex - 1]
          val newest = samples.last()
          val tailSpan = newest.timeSec - previous.timeSec
          if (tailSpan > 0) {
            val tailProgress = ((animatedElapsedSec - previous.timeSec) / tailSpan).coerceIn(0f, 1f)
            val tailTime = previous.timeSec + tailSpan * tailProgress
            val tailPower = previous.powerWatts +
              (newest.powerWatts - previous.powerWatts) * tailProgress
            drawLine(
              color = powerLineColor,
              start = Offset(xForTime(previous.timeSec.toFloat()), yForPower(previous.powerWatts.toFloat())),
              end = Offset(xForTime(tailTime), yForPower(tailPower)),
              strokeWidth = 2.5.dp.toPx()
            )
          }
        }

        if (animatedElapsedSec in winStart..winEnd) {
          val currentX = xForTime(animatedElapsedSec)
          drawLine(
            color = cursorColor,
            start = Offset(currentX, 0f),
            end = Offset(currentX, heightPx),
            strokeWidth = 1.5.dp.toPx()
          )
        }
      }

      androidx.compose.animation.AnimatedVisibility(
        visible = selectedIndex != null,
        enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
        tooltipIndex?.let { idx ->
          bounds.getOrNull(idx)?.let { (start, _, seg) ->
            IntervalTooltip(
              index = idx,
              count = segments.size,
              start = start,
              segment = seg,
              ftp = ftp,
              modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                  val maxX = (chartWidthPx - tooltipWidthPx).coerceAtLeast(0)
                  IntOffset(tooltipX.roundToInt().coerceIn(0, maxX), 0)
                }
                .onSizeChanged { tooltipWidthPx = it.width }
                .padding(8.dp)
            )
          }
        }
      }
    }
  }
}

internal data class WorkoutChartWindow(
  val startSec: Float,
  val endSec: Float
)

internal fun computeWorkoutChartWindow(
  zoomToCurrent: Boolean,
  totalDurationSec: Int,
  elapsedSec: Int,
  segments: List<WorkoutSegment>,
  bounds: List<Triple<Int, Int, WorkoutSegment>>
): WorkoutChartWindow {
  if (!zoomToCurrent || totalDurationSec <= 0) {
    return WorkoutChartWindow(startSec = 0f, endSec = totalDurationSec.toFloat())
  }

  val currentIndex = WorkoutMath.segmentIndexAt(segments, elapsedSec)
  val (start, end) = bounds.getOrNull(currentIndex)?.let { it.first to it.second }
    ?: (0 to totalDurationSec)
  return WorkoutChartWindow(
    startSec = (start - ZOOM_PAD_SEC).coerceAtLeast(0).toFloat(),
    endSec = (end + ZOOM_PAD_SEC).coerceAtMost(totalDurationSec).toFloat()
  )
}

internal data class WorkoutChartPanBounds(
  val minOffsetSec: Float,
  val maxOffsetSec: Float
) {
  val canPan: Boolean
    get() = maxOffsetSec - minOffsetSec > 0.001f
}

internal fun computeWorkoutChartPanBounds(
  windowStartSec: Float,
  windowEndSec: Float,
  totalDurationSec: Int
): WorkoutChartPanBounds {
  val minOffset = -windowStartSec
  val maxOffset = totalDurationSec.toFloat() - windowEndSec
  return if (maxOffset >= minOffset) {
    WorkoutChartPanBounds(minOffset, maxOffset)
  } else {
    val centered = (minOffset + maxOffset) / 2f
    WorkoutChartPanBounds(centered, centered)
  }
}

internal fun clampWorkoutChartPanOffset(
  offsetSec: Float,
  bounds: WorkoutChartPanBounds
): Float = offsetSec.coerceIn(bounds.minOffsetSec, bounds.maxOffsetSec)

internal fun rubberBandWorkoutChartPanOffset(
  offsetSec: Float,
  bounds: WorkoutChartPanBounds,
  maxOvershootSec: Float
): Float {
  if (maxOvershootSec <= 0f) return clampWorkoutChartPanOffset(offsetSec, bounds)
  return when {
    offsetSec < bounds.minOffsetSec -> {
      val distance = bounds.minOffsetSec - offsetSec
      bounds.minOffsetSec - (distance * maxOvershootSec / (distance + maxOvershootSec))
    }
    offsetSec > bounds.maxOffsetSec -> {
      val distance = offsetSec - bounds.maxOffsetSec
      bounds.maxOffsetSec + (distance * maxOvershootSec / (distance + maxOvershootSec))
    }
    else -> offsetSec
  }
}

private fun buildHrPath(samples: List<TelemetrySample>): Path = Path().apply {
  var hasPoint = false
  samples.forEach { sample ->
    if (sample.hrBpm <= 0) {
      hasPoint = false
    } else if (hasPoint) {
      lineTo(sample.timeSec.toFloat(), sample.hrBpm.toFloat())
    } else {
      moveTo(sample.timeSec.toFloat(), sample.hrBpm.toFloat())
      hasPoint = true
    }
  }
}

private fun buildPowerPath(samples: List<TelemetrySample>): Path = Path().apply {
  if (samples.size < 2) return@apply
  moveTo(samples.first().timeSec.toFloat(), samples.first().powerWatts.toFloat())
  for (index in 1 until samples.lastIndex) {
    val sample = samples[index]
    lineTo(sample.timeSec.toFloat(), sample.powerWatts.toFloat())
  }
}

@Composable
private fun IntervalTooltip(
  index: Int,
  count: Int,
  start: Int,
  segment: WorkoutSegment,
  ftp: Int,
  modifier: Modifier = Modifier
) {
  val range = WorkoutMath.targetRangeAt(listOf(segment), 0)
  val mid = (range.low + range.high) / 2
  val colors = zoneColorSet(mid, ftp)
  val title = segment.label ?: segment.phase.name.lowercase().replaceFirstChar { it.uppercase() }
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(colors.line)
        )
        Text(
          text = "  $title",
          style = MaterialTheme.typography.titleSmall
        )
      }
      Text(
        text = "Interval ${index + 1}/$count · starts ${fmt(start)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = if (range.low > 0) "${range.low}–${range.high} W · ${fmt(segment.durationSec)}"
        else "Free ride · ${fmt(segment.durationSec)}",
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}

private fun fmt(totalSec: Int): String {
  val m = totalSec / 60
  val s = totalSec % 60
  return "%d:%02d".format(m, s)
}

internal fun workoutProfileSummary(segments: List<WorkoutSegment>): String {
  val totalSec = WorkoutMath.totalDurationSec(segments)
  val duration = if (totalSec < 60) "$totalSec seconds" else "${totalSec / 60} minutes"
  val targetValues = segments.flatMap { segment ->
    when (segment) {
      is WorkoutSegment.Step -> listOf(segment.targetRange.low, segment.targetRange.high)
      is WorkoutSegment.Ramp -> listOf(segment.startPower, segment.endPower)
      is WorkoutSegment.FreeRide -> emptyList()
    }
  }
  val targetSummary = if (targetValues.isEmpty()) {
    "free ride"
  } else {
    "targets ${targetValues.minOrNull()} to ${targetValues.maxOrNull()} watts"
  }
  return "Workout profile: $duration, ${segments.size} intervals, $targetSummary."
}

internal data class ZoneBand(
  val zone: Int,
  val startSec: Float,
  val endSec: Float,
  val targetWatts: Int
)

/**
 * Samples the plan across [winStartSec, winEndSec] and merges equal-target
 * runs into bands. Rendering fills all bands of a zone as ONE Path so
 * abutting edges cannot leave anti-aliasing seams (the thin-vertical-bars
 * regression). Free-ride stretches (target 0) produce no band.
 */
internal fun zoneBands(
  segments: List<WorkoutSegment>,
  ftp: Int,
  winStartSec: Float,
  winEndSec: Float,
  stepSec: Float
): List<ZoneBand> {
  if (winEndSec <= winStartSec || stepSec <= 0f) return emptyList()
  val bands = mutableListOf<ZoneBand>()
  var sec = winStartSec
  while (sec < winEndSec) {
    val nextSec = (sec + stepSec).coerceAtMost(winEndSec)
    val range = WorkoutMath.targetRangeAt(segments, sec.toInt())
    val target = (range.low + range.high) / 2
    if (target > 0) {
      val zone = ZoneColors.zoneIndex(target, ftp)
      val last = bands.lastOrNull()
      if (last != null && last.zone == zone && last.targetWatts == target && last.endSec == sec) {
        bands[bands.lastIndex] = last.copy(endSec = nextSec)
      } else {
        bands.add(ZoneBand(zone, sec, nextSec, target))
      }
    }
    sec = nextSec
  }
  return bands
}
