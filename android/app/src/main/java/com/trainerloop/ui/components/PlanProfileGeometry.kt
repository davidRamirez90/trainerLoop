package com.trainerloop.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Alpha applied by renderers to [com.trainerloop.ui.theme.TrainerLoopColors.chartPlanFill]. */
internal const val PLAN_FILL_ALPHA = 0.08f
internal const val PLAN_RANGE_FILL_ALPHA = 0.12f
internal const val PLAN_SPLINE_ALPHA = 0.65f
internal const val PLAN_REFERENCE_ALPHA = 0.24f

internal data class PlanProfilePoint(val timeSec: Float, val watts: Int)

/**
 * Groups [bands] into contiguous runs and emits stepped-polyline vertices per
 * run. A run breaks wherever bands are not contiguous (free-ride stretches
 * emit no band), so the outline gaps instead of bridging a plan that does not
 * exist.
 */
internal fun planProfileRuns(bands: List<ZoneBand>): List<List<PlanProfilePoint>> {
  val runs = mutableListOf<MutableList<PlanProfilePoint>>()
  var prevEndSec = Float.NaN
  bands.forEach { band ->
    val run = if (runs.isEmpty() || band.startSec != prevEndSec) {
      mutableListOf<PlanProfilePoint>().also { runs.add(it) }
    } else {
      runs.last()
    }
    if (run.lastOrNull()?.watts != band.targetWatts) {
      run.add(PlanProfilePoint(band.startSec, band.targetWatts))
    }
    run.add(PlanProfilePoint(band.endSec, band.targetWatts))
    prevEndSec = band.endSec
  }
  return runs
}

internal fun planPeakWatts(bands: List<ZoneBand>): Int =
  bands.maxOfOrNull { it.highWatts } ?: 0

/** Draws the light interior shadow for constant-range steps. */
internal fun DrawScope.drawPlanRangeFills(
  bands: List<ZoneBand>,
  xForTime: (Float) -> Float,
  yForPower: (Float) -> Float,
  color: Color
) {
  bands.forEach { band ->
    if (band.lowWatts >= band.highWatts) return@forEach
    val left = xForTime(band.startSec)
    val right = xForTime(band.endSec)
    val top = yForPower(band.highWatts.toFloat())
    val bottom = yForPower(band.lowWatts.toFloat())
    drawRect(
      color = color,
      topLeft = Offset(left, top),
      size = Size(right - left, bottom - top)
    )
  }
}

/** Draws the target-band boxes with the same outline used by the plan spline. */
internal fun DrawScope.drawPlanRangeOutlines(
  bands: List<ZoneBand>,
  xForTime: (Float) -> Float,
  yForPower: (Float) -> Float,
  color: Color,
  strokeWidth: Float
) {
  bands.forEach { band ->
    if (band.lowWatts >= band.highWatts) return@forEach
    val left = xForTime(band.startSec)
    val right = xForTime(band.endSec)
    val top = yForPower(band.highWatts.toFloat())
    val bottom = yForPower(band.lowWatts.toFloat())
    drawRect(
      color = color,
      topLeft = Offset(left, top),
      size = Size(right - left, bottom - top),
      style = Stroke(width = strokeWidth)
    )
  }
}

/** Draws the lighter midpoint indication inside each constant target range. */
internal fun DrawScope.drawPlanRangeReferences(
  bands: List<ZoneBand>,
  xForTime: (Float) -> Float,
  yForPower: (Float) -> Float,
  color: Color,
  strokeWidth: Float
) {
  bands.forEach { band ->
    if (band.lowWatts >= band.highWatts) return@forEach
    drawLine(
      color = color,
      start = Offset(xForTime(band.startSec), yForPower(band.targetWatts.toFloat())),
      end = Offset(xForTime(band.endSec), yForPower(band.targetWatts.toFloat())),
      strokeWidth = strokeWidth
    )
  }
}

/**
 * Rewinds [outline] and [fill] and rebuilds them from [runs]. The outline
 * traces each run's staircase; the fill closes each run down to [baselineY].
 * Callers own the Path instances so per-frame renderers can reuse scratch
 * paths.
 */
internal fun buildPlanProfilePaths(
  runs: List<List<PlanProfilePoint>>,
  outline: Path,
  fill: Path,
  xForTime: (Float) -> Float,
  yForPower: (Float) -> Float,
  baselineY: Float
) {
  outline.rewind()
  fill.rewind()
  runs.forEach { run ->
    if (run.size < 2) return@forEach
    val firstX = xForTime(run.first().timeSec)
    val firstY = yForPower(run.first().watts.toFloat())
    outline.moveTo(firstX, firstY)
    fill.moveTo(firstX, baselineY)
    fill.lineTo(firstX, firstY)
    run.drop(1).forEach { point ->
      val x = xForTime(point.timeSec)
      val y = yForPower(point.watts.toFloat())
      outline.lineTo(x, y)
      fill.lineTo(x, y)
    }
    fill.lineTo(xForTime(run.last().timeSec), baselineY)
    fill.close()
  }
}
