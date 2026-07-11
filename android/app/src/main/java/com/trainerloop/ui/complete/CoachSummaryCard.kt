package com.trainerloop.ui.complete

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trainerloop.domain.coach.CoachSessionData
import com.trainerloop.domain.coach.FeedbackItem
import com.trainerloop.domain.coach.executionScore
import kotlin.math.roundToInt

/** Post-ride coach summary (§11): execution score, fatigue curve, feedback timeline. */
@Composable
fun CoachSummaryCard(data: CoachSessionData) {
  val intervalScores = data.intervals.map { it.executionScore }
  val workoutScore = intervalScores.takeIf { it.isNotEmpty() }?.average()

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Coach Summary",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(8.dp))

      Row(modifier = Modifier.fillMaxWidth()) {
        SummaryStat(
          label = "Execution",
          value = workoutScore?.let { "${it.roundToInt()}" } ?: "—",
          modifier = Modifier.weight(1f)
        )
        SummaryStat(
          label = "Final fatigue",
          value = "${data.finalFatigueScore.roundToInt()}",
          modifier = Modifier.weight(1f)
        )
        SummaryStat(
          label = "Intervals",
          value = "${data.intervals.size}",
          modifier = Modifier.weight(1f)
        )
      }

      if (data.fatigueCurve.size >= 2) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Fatigue over the ride",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        FatigueSparkline(data.fatigueCurve)
      }

      data.intervals.forEachIndexed { i, rec ->
        if (i == 0) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Work intervals",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
        ) {
          Text(
            text = "#${i + 1} ${rec.segmentClass.name.lowercase().replace('_', ' ')}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = "${rec.avgPower.roundToInt()} W · ${rec.adherencePct.roundToInt()}% · score ${rec.executionScore.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (data.feedback.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Coach feedback",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        data.feedback.forEach { item -> FeedbackRow(item) }
      }
    }
  }
}

/** Tap a feedback row to see why the coach said it (§13.6): rule + metric snapshot. */
@Composable
private fun FeedbackRow(item: FeedbackItem) {
  var expanded by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { expanded = !expanded }
      .padding(vertical = 2.dp)
  ) {
    Row {
      Text(
        text = formatTimestamp(item.timestampSec),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 8.dp)
      )
      Text(text = item.message, style = MaterialTheme.typography.bodySmall)
    }
    if (expanded) {
      Column(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp)) {
        Text(
          text = "rule ${item.ruleId} · ${item.category.name.lowercase()} · sev ${item.severity}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        item.snapshot.forEach { (k, v) ->
          Text(
            text = "$k: $v",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun FatigueSparkline(curve: List<Double>) {
  val color = MaterialTheme.colorScheme.primary
  val bandColor = Color(0xFFE57373) // elevated-fatigue reference line at 60
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp)
      .semantics {
        contentDescription = "Fatigue over the ride: scores from " +
          "${curve.minOrNull()?.roundToInt() ?: 0} to ${curve.maxOrNull()?.roundToInt() ?: 0}, " +
          "ending at ${curve.lastOrNull()?.roundToInt() ?: 0}."
      }
  ) {
    val maxY = 100.0
    val stepX = size.width / (curve.size - 1)
    fun y(v: Double) = (size.height * (1 - v / maxY)).toFloat()
    val path = Path()
    curve.forEachIndexed { i, v ->
      val x = i * stepX
      if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
    }
    drawLine(
      color = bandColor.copy(alpha = 0.5f),
      start = Offset(0f, y(60.0)),
      end = Offset(size.width, y(60.0)),
      strokeWidth = 1.dp.toPx()
    )
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
  }
}

private fun formatTimestamp(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
