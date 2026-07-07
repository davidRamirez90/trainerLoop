package com.trainerloop.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Route
import com.trainerloop.data.repository.RouteRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.ui.components.MetricBadge
import com.trainerloop.ui.components.RouteProfileChart

@Composable
fun RouteDetailScreen(
  routeId: String,
  onStartRide: (String) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  var route by remember { mutableStateOf<Route?>(null) }
  LaunchedEffect(routeId) {
    route = RouteRepository.create(AppDatabase.getInstance(context)).getById(routeId)
  }

  val r = route ?: return
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Text(r.name ?: "Route", style = MaterialTheme.typography.headlineMedium)
    }

    RouteProfileChart(points = r.points, positionM = null)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      MetricBadge(label = "Distance", value = "%.1f km".format(r.totalDistanceM / 1000.0))
      MetricBadge(label = "Ascent", value = "${r.totalAscentM} m")
    }

    Button(onClick = { onStartRide(routeId) }, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Default.PlayArrow, contentDescription = null)
      Text("Start Ride")
    }
  }
}
