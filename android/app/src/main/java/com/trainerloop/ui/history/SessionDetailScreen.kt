package com.trainerloop.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.trainerloop.ui.theme.Spacing
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = Spacing.lg)
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SummaryCard("TSS", tss.toString(), Modifier.weight(1f))
        SummaryCard("IF", "%.2f".format(ifactor), Modifier.weight(1f))
        SummaryCard("NP", "$np W", Modifier.weight(1f))
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
      ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
          StatRow("Duration", formatDuration(s.durationSec))
          StatRow("Avg Power", "${s.avgPower} W")
          StatRow("Max Power", "${s.maxPower} W")
          StatRow("Avg Heart Rate", "${s.avgHr} bpm")
          StatRow("Avg Cadence", "${s.avgCadence} rpm")
          val distanceKm = WorkoutSummaryMath.totalDistanceKm(samples)
          if (distanceKm > 0) {
            StatRow("Distance", "%.1f km".format(distanceKm))
            StatRow("Elevation Gain", "${WorkoutSummaryMath.totalAscentM(samples)} m")
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      if (s.completed && state.icuConfigured) {
        if (s.icuSyncedAt != null) {
          Text(
            text = "Synced to intervals.icu · ${formatDate(s.icuSyncedAt!!)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
          )
        } else {
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
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.lg),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        style = NumericMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = NumericSmall.copy(fontWeight = FontWeight.Bold)
    )
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
