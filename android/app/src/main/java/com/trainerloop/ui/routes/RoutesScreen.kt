package com.trainerloop.ui.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.repository.RouteSummary

@Composable
fun RoutesScreen(
  onRouteClick: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: RoutesViewModel = viewModel()
) {
  val routes by viewModel.routes.collectAsStateWithLifecycle()
  val importError by viewModel.importError.collectAsStateWithLifecycle()

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri -> uri?.let { viewModel.importGpx(it) } }

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Text("GPX Routes", style = MaterialTheme.typography.headlineMedium)
    }

    Button(
      onClick = {
        viewModel.clearError()
        // Many file managers don't register the GPX MIME type — accept anything
        // and let the parser reject non-GPX content.
        picker.launch(arrayOf("application/gpx+xml", "application/octet-stream", "*/*"))
      },
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(Icons.Default.Add, contentDescription = null)
      Text("Import GPX")
    }

    importError?.let {
      Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    if (routes.isEmpty()) {
      Text(
        "No routes yet — import a GPX file to ride it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(routes, key = { it.id }) { route ->
        RouteRow(
          route = route,
          onClick = { onRouteClick(route.id) },
          onDelete = { viewModel.deleteRoute(route.id) }
        )
      }
    }
  }
}

@Composable
private fun RouteRow(route: RouteSummary, onClick: () -> Unit, onDelete: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          "%.1f km · %d m ↑".format(route.distanceM / 1000.0, route.ascentM),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.Delete, contentDescription = "Delete ${route.name}")
      }
    }
  }
}
