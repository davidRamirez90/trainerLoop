package com.trainerloop.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ui.components.WorkoutMiniChart
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBuilderScreen(
  onSaved: () -> Unit,
  onBack: () -> Unit,
  viewModel: WorkoutBuilderViewModel = viewModel()
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val ftp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }
  val draft = uiState
  val previewWorkout = remember(draft) { draft.toWorkout(id = "builder_preview") }
  val previewMotionSpec = reducedMotionAware(MotionSpec.default)

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text("Workout Builder") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(Spacing.lg)
    ) {
      OutlinedTextField(
        value = uiState.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Workout name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(Spacing.md))

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
      ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Live preview", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "${previewWorkout.segments.sumOf { it.durationSec } / 60} min",
              style = NumericSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          AnimatedContent(
            targetState = previewWorkout,
            transitionSpec = {
              fadeIn(animationSpec = previewMotionSpec) togetherWith
                fadeOut(animationSpec = previewMotionSpec)
            },
            label = "workout builder preview"
          ) { workout ->
            WorkoutMiniChart(
              workout = workout,
              ftp = ftp,
              modifier = Modifier.padding(top = Spacing.sm)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.md))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
      ) {
        itemsIndexed(uiState.steps) { index, step ->
          StepCard(
            index = index,
            step = step,
            canMoveUp = index > 0,
            canMoveDown = index < uiState.steps.lastIndex,
            onMoveUp = { viewModel.moveStep(index, index - 1) },
            onMoveDown = { viewModel.moveStep(index, index + 1) },
            onDelete = { viewModel.deleteStep(index) },
            onMinutesChange = { viewModel.onMinutesChange(index, it) },
            onLowWChange = { viewModel.onLowWChange(index, it) },
            onHighWChange = { viewModel.onHighWChange(index, it) }
          )
        }
        item {
          OutlinedButton(
            onClick = viewModel::addStep,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text("Add interval")
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.md))

      Button(
        onClick = {
          val workout = uiState.toWorkout(id = "custom_${System.currentTimeMillis()}")
          ImportedWorkoutStore.add(context, workout)
          onSaved()
        },
        enabled = uiState.isValid,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Save Workout")
      }
      uiState.saveReason?.let { reason ->
        Text(
          text = reason,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = Spacing.xs)
        )
      }
    }
  }
}

@Composable
private fun StepCard(
  index: Int,
  step: BuilderStepDraft,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onDelete: () -> Unit,
  onMinutesChange: (String) -> Unit,
  onLowWChange: (String) -> Unit,
  onHighWChange: (String) -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(Spacing.md)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Interval ${index + 1}",
          style = MaterialTheme.typography.titleSmall
        )
        Row {
          IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
              Icons.Default.KeyboardArrowUp,
              contentDescription = "Move interval ${index + 1} up"
            )
          }
          IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
              Icons.Default.KeyboardArrowDown,
              contentDescription = "Move interval ${index + 1} down"
            )
          }
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Default.Delete,
              contentDescription = "Delete interval ${index + 1}",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NumberField(step.minutes, "Minutes", Modifier.weight(1f), onMinutesChange)
        NumberField(step.lowW, "Low W", Modifier.weight(1f), onLowWChange)
        NumberField(step.highW, "High W", Modifier.weight(1f), onHighWChange)
      }
    }
  }
}

@Composable
private fun NumberField(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  onValueChange: (String) -> Unit
) {
  OutlinedTextField(
    value = value,
    onValueChange = { onValueChange(it.filter { c -> c.isDigit() }) },
    label = { Text(label) },
    singleLine = true,
    textStyle = NumericSmall,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier
  )
}
