package com.trainerloop.ui.complete

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.trainerloop.ui.components.SampleChart
import com.trainerloop.ui.theme.NumericMedium
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCompleteScreen(
  viewModel: WorkoutCompleteViewModel,
  onDiscard: () -> Unit,
  onDone: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)

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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
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

      Spacer(modifier = Modifier.height(16.dp))

      if (uiState.isRampTest) {
        RampTestResultCard(
          uiState = uiState,
          onAccept = viewModel::acceptFtp,
          onDiscard = viewModel::discardFtp,
          onPush = viewModel::pushFtpToIcu,
          onDeclinePush = viewModel::declineIcuFtpPush
        )
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Summary grid: TSS, IF, NP
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SummaryCard(
          label = "TSS",
          value = uiState.tss.toString(),
          modifier = Modifier.weight(1f)
        )
        SummaryCard(
          label = "IF",
          value = "%.2f".format(uiState.intensityFactor),
          modifier = Modifier.weight(1f)
        )
        SummaryCard(
          label = "NP",
          value = "${uiState.normalizedPower} W",
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Stats list
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          StatRow("Avg Power", "${uiState.avgPower} W")
          StatRow("Max Power", "${uiState.maxPower} W")
          StatRow("Avg Heart Rate", "${uiState.avgHr} bpm")
          StatRow("Avg Cadence", "${uiState.avgCadence} rpm")
          StatRow("Calories", "${uiState.calories} kcal")
          StatRow("Total Work", "${uiState.totalWorkKj} kJ")
          if (uiState.distanceKm > 0) {
            StatRow("Distance", "%.1f km".format(uiState.distanceKm))
            StatRow("Elevation Gain", "${uiState.ascentM} m")
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Post-ride chart with tabs
      if (viewModel.samples.isNotEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Ride Chart",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SampleChart(samples = viewModel.samples)
          }
        }
      }

      uiState.coachData?.let { coach ->
        CoachSummaryCard(coach)
        Spacer(modifier = Modifier.height(16.dp))
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedButton(
          onClick = { viewModel.onDiscard() },
          enabled = !uiState.isSaving,
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          )
        ) {
          Text("Discard")
        }
        Button(
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
          ) { saved -> Text(if (saved) "Saved" else "Save") }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
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
        uiState.uploadStatus?.let { status ->
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedButton(
        onClick = onDone,
        enabled = uiState.isSaved || uiState.isDiscarded || viewModel.samples.isEmpty(),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Done")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    // Error snackbar
    AnimatedVisibility(
      visible = uiState.error != null,
      enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
      exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
    ) {
      uiState.error?.let { error ->
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
  onAccept: () -> Unit,
  onDiscard: () -> Unit,
  onPush: () -> Unit,
  onDeclinePush: () -> Unit
) {
  val defaultMotionSpec = reducedMotionAware(MotionSpec.default)
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "FTP Test Result",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(8.dp))

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
            color = MaterialTheme.colorScheme.onSecondaryContainer
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                  Text("Discard FTP")
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                  Text("Accept FTP")
                }
              }
              actionState.second -> {
                Text(
                  text = "FTP saved. Set FTP on intervals.icu now?",
                  style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  OutlinedButton(onClick = onDeclinePush, modifier = Modifier.weight(1f)) {
                    Text("Later")
                  }
                  Button(onClick = onPush, modifier = Modifier.weight(1f)) {
                    Text("Yes")
                  }
                }
              }
              else -> Text(
                text = if (actionState.third) "New FTP saved to your profile" else "FTP discarded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
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
        uiState.ftpPushStatus?.let { status ->
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
          )
        }
      }
    }
  }
}

@Composable
private fun SummaryCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
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

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s)
  else "%d:%02d".format(m, s)
}
