package com.trainerloop.ui.components

import androidx.compose.ui.graphics.Path

/** Alpha applied by renderers to [com.trainerloop.ui.theme.TrainerLoopColors.chartPlanFill]. */
internal const val PLAN_FILL_ALPHA = 0.08f

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
  bands.maxOfOrNull { it.targetWatts } ?: 0

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
