package com.trainerloop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource

// ponytail: steps only (duration + watt range); ramps/free-ride via file import if needed
private class BuilderStep {
  var minutes by mutableStateOf("5")
  var lowW by mutableStateOf("150")
  var highW by mutableStateOf("160")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBuilderScreen(
  onSaved: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  var name by remember { mutableStateOf("") }
  val steps = remember { mutableStateListOf(BuilderStep()) }

  val valid = name.isNotBlank() && steps.isNotEmpty() && steps.all {
    (it.minutes.toIntOrNull() ?: 0) > 0 &&
      (it.lowW.toIntOrNull() ?: 0) > 0 &&
      (it.highW.toIntOrNull() ?: 0) >= (it.lowW.toIntOrNull() ?: 0)
  }

  Scaffold(
    topBar = {
      TopAppBar(
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
        .padding(16.dp)
    ) {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Workout name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        itemsIndexed(steps) { index, step ->
          StepCard(
            index = index,
            step = step,
            canDelete = steps.size > 1,
            onDelete = { steps.removeAt(index) }
          )
        }
        item {
          OutlinedButton(
            onClick = { steps.add(BuilderStep()) },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Add interval")
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = {
          val workout = Workout(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            description = "Custom workout",
            source = WorkoutSource.MANUAL,
            segments = steps.mapIndexed { i, s ->
              WorkoutSegment.Step(
                id = "step$i",
                durationSec = s.minutes.toInt() * 60,
                label = "Interval ${i + 1}",
                phase = SegmentPhase.WORK,
                isWork = true,
                targetRange = TargetRange(s.lowW.toInt(), s.highW.toInt())
              )
            }
          )
          ImportedWorkoutStore.add(context, workout)
          onSaved()
        },
        enabled = valid,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Save Workout")
      }
    }
  }
}

@Composable
private fun StepCard(
  index: Int,
  step: BuilderStep,
  canDelete: Boolean,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Interval ${index + 1}",
          style = MaterialTheme.typography.titleSmall
        )
        if (canDelete) {
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Default.Delete,
              contentDescription = "Delete interval ${index + 1}",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(step.minutes, "Minutes", Modifier.weight(1f)) { step.minutes = it }
        NumberField(step.lowW, "Low W", Modifier.weight(1f)) { step.lowW = it }
        NumberField(step.highW, "High W", Modifier.weight(1f)) { step.highW = it }
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
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier
  )
}
