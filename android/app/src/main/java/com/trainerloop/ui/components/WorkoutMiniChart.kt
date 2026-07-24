package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.theme.trainerLoopColors

@Composable
fun WorkoutMiniChart(
  workout: Workout,
  ftp: Int,
  modifier: Modifier = Modifier,
  chartHeight: Dp = 60.dp,
  maxPowerAxis: Int = 400,
  lineColor: Color = MaterialTheme.trainerLoopColors.chartPlanOutline
) {
  val totalDuration = remember(workout) {
    WorkoutMath.totalDurationSec(workout.segments)
  }
  val isFreeRideOnly = remember(workout) {
    WorkoutSummaryMath.isFreeRideOnly(workout)
  }
  // Plan geometry depends only on the workout and FTP, not on canvas size.
  val planBands = remember(workout, ftp) {
    if (totalDuration == 0) emptyList() else zoneBands(
      segments = workout.segments,
      ftp = ftp,
      winStartSec = 0f,
      winEndSec = totalDuration.toFloat(),
      stepSec = (totalDuration / 120f).coerceAtLeast(1f)
    )
  }
  val runs = remember(planBands) { planProfileRuns(planBands) }
  val axisMax = remember(planBands, maxPowerAxis) {
    maxOf(maxPowerAxis, planPeakWatts(planBands))
  }
  val placeholderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
  val rangeFillColor = MaterialTheme.trainerLoopColors.chartPlanFill.copy(alpha = PLAN_RANGE_FILL_ALPHA)

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(chartHeight)
      .semantics { contentDescription = workoutProfileSummary(workout.segments) }
  ) {
    if (isFreeRideOnly) {
      val bandHeight = size.height * 0.4f
      val bandWidth = size.width - 4.dp.toPx()
      val bandTop = (size.height - bandHeight) / 2f
      val dash = PathEffect.dashPathEffect(
        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
        0f
      )
      drawRoundRect(
        color = placeholderColor,
        topLeft = Offset(2.dp.toPx(), bandTop),
        size = Size(bandWidth, bandHeight),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx(), pathEffect = dash)
      )
      return@Canvas
    }

    if (totalDuration == 0 || runs.isEmpty()) return@Canvas

    val width = size.width
    val heightPx = size.height
    val padding = 2.dp.toPx()
    val drawHeight = heightPx - padding * 2
    val chartBottom = heightPx - padding

    val outline = Path()
    val fill = Path()
    drawPlanRangeFills(
      bands = planBands,
      xForTime = { sec -> (sec / totalDuration.toFloat()) * width },
      yForPower = { watts ->
        chartBottom - (watts / axisMax.toFloat()).coerceIn(0f, 1f) * drawHeight
      },
      color = rangeFillColor
    )
    buildPlanProfilePaths(
      runs = runs,
      outline = outline,
      fill = fill,
      xForTime = { sec -> (sec / totalDuration.toFloat()) * width },
      yForPower = { watts ->
        chartBottom - (watts / axisMax.toFloat()).coerceIn(0f, 1f) * drawHeight
      },
      baselineY = chartBottom
    )
    drawPath(fill, color = lineColor.copy(alpha = PLAN_FILL_ALPHA))
    drawPlanRangeOutlines(
      bands = planBands,
      xForTime = { sec -> (sec / totalDuration.toFloat()) * width },
      yForPower = { watts ->
        chartBottom - (watts / axisMax.toFloat()).coerceIn(0f, 1f) * drawHeight
      },
      color = lineColor.copy(alpha = PLAN_SPLINE_ALPHA),
      strokeWidth = 2.dp.toPx()
    )
    drawPath(
      outline,
      color = lineColor.copy(alpha = PLAN_SPLINE_ALPHA),
      style = Stroke(width = 2.dp.toPx())
    )
    drawPlanRangeReferences(
      bands = planBands,
      xForTime = { sec -> (sec / totalDuration.toFloat()) * width },
      yForPower = { watts ->
        chartBottom - (watts / axisMax.toFloat()).coerceIn(0f, 1f) * drawHeight
      },
      color = lineColor.copy(alpha = PLAN_REFERENCE_ALPHA),
      strokeWidth = 2.dp.toPx()
    )
  }
}
