package com.trainerloop.ui.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.repository.RouteSummary
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
  onRouteClick: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: RoutesViewModel = viewModel()
) {
  val routes by viewModel.routes.collectAsStateWithLifecycle()
  val importError by viewModel.importError.collectAsStateWithLifecycle()
  var displayedImportError by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(importError) {
    importError?.let { displayedImportError = it }
  }
  val hasRoutes = routes.isNotEmpty()

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri -> uri?.let { viewModel.importGpx(it) } }
  val importGpx = {
    viewModel.clearError()
    // Many file managers don't register the GPX MIME type — accept anything
    // and let the parser reject non-GPX content.
    picker.launch(arrayOf("application/gpx+xml", "application/octet-stream", "*/*"))
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text("GPX Routes") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.pressable()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          AnimatedVisibility(
            visible = hasRoutes,
            enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
            exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
          ) {
            IconButton(onClick = importGpx, modifier = Modifier.pressable()) {
              Icon(Icons.Default.Add, contentDescription = "Import GPX")
            }
          }
        }
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
      verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      item(key = "route-empty-state") {
        AnimatedVisibility(
          visible = !hasRoutes,
          enter = expandVertically(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
          ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
          exit = shrinkVertically(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
          ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = importGpx, modifier = Modifier.fillMaxWidth().pressable()) {
              Icon(Icons.Default.Add, contentDescription = null)
              Text("Import GPX")
            }
            Text(
              "No routes yet — import a GPX file to ride it.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      item {
        AnimatedVisibility(
          visible = importError != null,
          enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
          exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
        ) {
          displayedImportError?.let {
            Text(
              text = it,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }

      item(key = "route-list") {
        AnimatedVisibility(
          visible = hasRoutes,
          enter = expandVertically(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
          ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
          exit = shrinkVertically(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
          ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            routes.forEach { route ->
              RouteRow(
                route = route,
                onClick = { onRouteClick(route.id) },
                onDelete = { viewModel.deleteRoute(route.id) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RouteRow(route: RouteSummary, onClick: () -> Unit, onDelete: () -> Unit) {
  Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().pressable()
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          route.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          "%.1f km · %d m ↑".format(route.distanceM / 1000.0, route.ascentM),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      IconButton(onClick = onDelete, modifier = Modifier.pressable()) {
        Icon(Icons.Default.Delete, contentDescription = "Delete ${route.name}")
      }
    }
  }
}
