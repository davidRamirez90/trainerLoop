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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.Route
import com.trainerloop.data.repository.RouteRepository
import com.trainerloop.data.repository.RouteSummary
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.ui.components.EmptyState
import com.trainerloop.ui.components.MessageSeverity
import com.trainerloop.ui.components.InlineMessage
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.RouteProfileChart
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.components.TrainerLoopTopBar
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware

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
      TrainerLoopTopBar(
        title = "GPX Routes",
        windowInsets = WindowInsets(0),
        onBack = onBack,
        firstAction = {
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
      contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.screenMargin),
      verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)
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
          EmptyState(
            icon = Icons.Default.Map,
            title = "No routes yet",
            body = "Import a GPX file to ride it.",
            action = {
              PrimaryActionButton(
                onClick = importGpx,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.controlGap)
              ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Import GPX")
              }
            }
          )
        }
      }

      item {
        AnimatedVisibility(
          visible = importError != null,
          enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
          exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
        ) {
          displayedImportError?.let {
            InlineMessage(severity = MessageSeverity.Error, text = it)
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
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
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
  val context = LocalContext.current
  var fullRoute by remember(route.id) { mutableStateOf<Route?>(null) }
  LaunchedEffect(route.id) {
    fullRoute = RouteRepository.create(AppDatabase.getInstance(context)).getById(route.id)
  }

  TrainerLoopCard(
    modifier = Modifier.fillMaxWidth(),
    onClick = onClick
  ) {
    val loadedRoute = fullRoute
    if (loadedRoute != null && loadedRoute.points.size >= 2) {
      RouteProfileChart(points = loadedRoute.points, positionM = null)
      Spacer(modifier = Modifier.height(Spacing.controlGap))
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
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
