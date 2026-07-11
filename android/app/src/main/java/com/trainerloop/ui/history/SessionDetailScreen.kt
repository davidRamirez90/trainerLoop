package com.trainerloop.ui.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.app.Application
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.components.SampleChart
import com.trainerloop.ui.theme.NumericMedium
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.zoneColorSet
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
  sessionId: String,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val viewModel: SessionDetailViewModel = viewModel(
    factory = SessionDetailViewModelFactory(
      context.applicationContext as Application,
      sessionId
    )
  )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val session = state.session

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text(session?.workoutName ?: "Session") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { padding ->
    val s = session
    if (s == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    val samples: List<TelemetrySample> = try {
      Json.decodeFromString(ListSerializer(TelemetrySample.serializer()), s.samplesJson)
    } catch (e: Exception) {
      emptyList()
    }
    val np = WorkoutSummaryMath.normalizedPower(samples)
    val ftp = context.trainerLoopApp.profileRepository.getProfileSync().ftp
    val ifactor = WorkoutSummaryMath.intensityFactor(np, ftp)
    val tss = WorkoutSummaryMath.tss(np, ftp, s.durationSec)
    val zoneSeconds = WorkoutSummaryMath.zoneTimeSec(samples, ftp)
    val powerAccent = zoneColorSet(s.avgPower, ftp).line
    val npAccent = zoneColorSet(np, ftp).line
    val neutralAccent = MaterialTheme.colorScheme.outlineVariant

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = Spacing.lg)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
    ) {
      Text(
        text = formatDate(s.startedAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(Spacing.xl))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
      ) {
        SummaryCard("TSS", tss.toString(), neutralAccent, Modifier.weight(1f))
        SummaryCard("IF", "%.2f".format(ifactor), neutralAccent, Modifier.weight(1f))
        SummaryCard("NP", "$np W", npAccent, Modifier.weight(1f))
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      if (samples.isNotEmpty()) {
        ZoneTimeDistribution(zoneSeconds = zoneSeconds)
        Spacer(modifier = Modifier.height(Spacing.xl))
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
      ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
          StatRow("Duration", formatDuration(s.durationSec), neutralAccent)
          StatRow("Avg Power", "${s.avgPower} W", powerAccent)
          StatRow("Max Power", "${s.maxPower} W", zoneColorSet(s.maxPower, ftp).line)
          StatRow("Avg Heart Rate", "${s.avgHr} bpm", neutralAccent)
          StatRow("Avg Cadence", "${s.avgCadence} rpm", neutralAccent)
          val distanceKm = WorkoutSummaryMath.totalDistanceKm(samples)
          if (distanceKm > 0) {
            StatRow("Distance", "%.1f km".format(distanceKm), neutralAccent)
            StatRow("Elevation Gain", "${WorkoutSummaryMath.totalAscentM(samples)} m", neutralAccent)
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      if (s.completed && state.icuConfigured) {
        s.icuSyncedAt?.let { syncedAt ->
          Text(
            text = "Synced to intervals.icu · ${formatDate(syncedAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
          )
        } ?: run {
          Button(
            onClick = { viewModel.uploadToIcu() },
            enabled = !state.isUploading,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(if (state.isUploading) "Uploading…" else "Upload to intervals.icu")
          }
        }
        state.uploadStatus?.let {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (samples.isNotEmpty()) {
        Spacer(modifier = Modifier.height(Spacing.xl))
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
          )
        ) {
          Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
              text = "Ride Chart",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SampleChart(samples = samples)
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xl))
    }
  }
}

@Composable
private fun SummaryCard(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(Spacing.md)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp)
          .background(accent)
      )
      Text(
        text = value,
        style = NumericMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.md)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.lg)
      )
    }
  }
}

@Composable
private fun StatRow(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.xs),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .width(3.dp)
          .height(24.dp)
          .clip(RoundedCornerShape(percent = 50))
          .background(accent)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.sm)
      )
    }
    Text(
      text = value,
      style = NumericSmall.copy(fontWeight = FontWeight.Bold)
    )
  }
}

@Composable
private fun ZoneTimeDistribution(zoneSeconds: IntArray) {
  val dark = androidx.compose.foundation.isSystemInDarkTheme()
  val nonZeroZones = zoneSeconds.indices.filter { zoneSeconds[it] > 0 }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(Spacing.md)
  ) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
      Text(
        text = "Time in zones",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(Spacing.md))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(12.dp)
          .clip(RoundedCornerShape(percent = 50))
      ) {
        for (index in zoneSeconds.indices) {
          val seconds by animateFloatAsState(
            targetValue = zoneSeconds[index].toFloat(),
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
            label = "Zone ${index + 1} duration"
          )
          if (seconds > 0f) {
            Box(
              modifier = Modifier
                .weight(seconds)
                .fillMaxWidth()
                .height(12.dp)
                .background(ZoneColors.forZone(index + 1, dark).fill)
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(Spacing.sm))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
      ) {
        nonZeroZones.forEach { index ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(ZoneColors.forZone(index + 1, dark).fill)
            )
            Text(
              text = "Z${index + 1} ${zoneSeconds[index] / 60}m",
              style = NumericSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = Spacing.xs)
            )
          }
        }
      }
    }
  }
}

private fun formatDate(startedAt: String): String {
  return try {
    val instant = Instant.parse(startedAt)
    DateTimeFormatter.ofPattern("EEEE, MMM d yyyy · HH:mm")
      .withZone(ZoneId.systemDefault())
      .format(instant)
  } catch (_: Exception) {
    startedAt
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
