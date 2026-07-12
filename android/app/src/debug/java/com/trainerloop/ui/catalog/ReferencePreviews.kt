package com.trainerloop.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainerloop.ui.theme.TrainerLoopTheme
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.trainerLoopColors

@Composable
private fun ReferenceSurface(content: @Composable () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
    content = content
  )
}

@Composable
private fun HomeReadyReference() {
  val colors = MaterialTheme.trainerLoopColors
  ReferenceSurface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      PageHeading("Good morning", "Ready when you are")
      Card(
        colors = CardDefaults.cardColors(
          containerColor = colors.ready,
          contentColor = colors.onReady
        ),
        shape = RoundedCornerShape(24.dp)
      ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR TRAINER IS READY", style = MaterialTheme.typography.labelMedium)
            Text("Turn today into a good ride.", style = MaterialTheme.typography.headlineLarge)
            Text("No workout needed. Settle in and ride by feel.", style = MaterialTheme.typography.bodyMedium)
          }
          Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(
              containerColor = colors.heroAction,
              contentColor = colors.onHeroAction
            )
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start Free Ride")
          }
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectionTile("Trainer", "Connected", Icons.AutoMirrored.Filled.DirectionsBike, Modifier.weight(1f))
        ConnectionTile("Heart rate", "Connected", Icons.Default.Favorite, Modifier.weight(1f))
      }
      Text("Explore", style = MaterialTheme.typography.titleLarge)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UtilityCard("Workout library", "Find your next session", Modifier.weight(1f))
        UtilityCard("Routes", "Ride somewhere new", Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun HomeDisconnectedReference() {
  val colors = MaterialTheme.trainerLoopColors
  ReferenceSurface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      PageHeading("Good morning", "Let’s get set up")
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(24.dp)
      ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.warning)
            Text("Trainer not connected", style = MaterialTheme.typography.headlineMedium)
          }
          Text(
            "Connect a smart trainer before starting a ride.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
          )
          Button(onClick = {}) { Text("Find trainer") }
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DisconnectedTile("Trainer", "Unavailable", Modifier.weight(1f))
        ConnectionTile("Heart rate", "Connected", Icons.Default.Favorite, Modifier.weight(1f))
      }
      Text("While you wait", style = MaterialTheme.typography.titleLarge)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UtilityCard("Workout library", "Plan the next session", Modifier.weight(1f))
        UtilityCard("Ride history", "Review your progress", Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun ConnectionTile(title: String, status: String, icon: ImageVector, modifier: Modifier) {
  val colors = MaterialTheme.trainerLoopColors
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(icon, contentDescription = null, tint = colors.connected)
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(status, color = colors.connected, style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
private fun DisconnectedTile(title: String, status: String, modifier: Modifier) {
  val colors = MaterialTheme.trainerLoopColors
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = colors.stale)
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(status, color = colors.stale, style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
private fun UtilityCard(title: String, subtitle: String, modifier: Modifier) {
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun LibraryReference() {
  val colors = MaterialTheme.trainerLoopColors
  ReferenceSurface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      PageHeading("Workout library", "Choose a session for today")
      OutlinedTextField(
        value = "",
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search workouts") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = true, onClick = {}, label = { Text("All") })
        FilterChip(selected = false, onClick = {}, label = { Text("Endurance") })
        FilterChip(selected = false, onClick = {}, label = { Text("Intervals") })
      }
      Card(
        colors = CardDefaults.cardColors(containerColor = colors.coach, contentColor = colors.onCoach),
        shape = RoundedCornerShape(18.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ASSESSMENT", style = MaterialTheme.typography.labelSmall)
            Text("FTP Ramp Test", style = MaterialTheme.typography.titleLarge)
            Text("Find your training baseline · 25 min", style = MaterialTheme.typography.bodySmall)
          }
          Icon(Icons.Default.PlayArrow, contentDescription = null)
        }
      }
      WorkoutCard("Aerobic Foundation", "60 min · Endurance", listOf(1, 2, 2, 3, 2, 2))
      WorkoutCard("Over / Unders", "45 min · Threshold", listOf(2, 4, 3, 4, 3, 2))
    }
  }
}

@Composable
private fun WorkoutCard(title: String, details: String, zones: List<Int>) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Text(title, style = MaterialTheme.typography.titleMedium)
          Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
      }
      ZoneStrip(zones, 32.dp)
    }
  }
}

@Composable
private fun ZoneStrip(zones: List<Int>, height: androidx.compose.ui.unit.Dp) {
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  Row(
    modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)),
    horizontalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    zones.forEach { zone ->
      Box(Modifier.weight(1f).fillMaxHeight().background(ZoneColors.forZone(zone, dark).fill))
    }
  }
}

@Composable
private fun PlayerPortraitReference() {
  ReferenceSurface {
    Column(
      modifier = Modifier.fillMaxSize().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      PlayerHeader()
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "248",
          color = MaterialTheme.trainerLoopColors.chartPower,
          style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 64.sp,
            lineHeight = 68.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
          )
        )
        Text("Target 260 W", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerMetric("Heart rate", "147", "bpm", MaterialTheme.trainerLoopColors.chartHeartRate, Modifier.weight(1f))
        PlayerMetric("Cadence", "92", "rpm", MaterialTheme.trainerLoopColors.chartCadence, Modifier.weight(1f))
      }
      ZoneStrip(listOf(2, 2, 3, 3, 4, 4, 3, 3), 84.dp)
      Spacer(Modifier.weight(1f))
      PlayerControls()
    }
  }
}

@Composable
private fun PlayerLandscapeReference() {
  ReferenceSurface {
    Row(
      modifier = Modifier.fillMaxSize().padding(20.dp),
      horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      Column(
        modifier = Modifier.weight(0.8f).fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        PlayerHeader()
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            "248",
            color = MaterialTheme.trainerLoopColors.chartPower,
            style = MaterialTheme.typography.displayLarge.copy(
              fontSize = 64.sp,
              lineHeight = 68.sp,
              fontWeight = FontWeight.Bold,
              fontFeatureSettings = "tnum"
            )
          )
          Text(" W", modifier = Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
        }
        Text("Target 260 W", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
      }
      Column(
        modifier = Modifier.weight(1.2f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          PlayerMetric("Heart rate", "147", "bpm", MaterialTheme.trainerLoopColors.chartHeartRate, Modifier.weight(1f))
          PlayerMetric("Cadence", "92", "rpm", MaterialTheme.trainerLoopColors.chartCadence, Modifier.weight(1f))
        }
        ZoneStrip(listOf(2, 2, 3, 3, 4, 4, 3, 3), 120.dp)
        Spacer(Modifier.weight(1f))
        PlayerControls()
      }
    }
  }
}

@Composable
private fun PlayerHeader() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Column {
      Text("Threshold repeats", style = MaterialTheme.typography.headlineMedium)
      Text("3 / 8 · 4:12", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
    Box(
      modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.trainerLoopColors.connected)
    )
  }
}

@Composable
private fun PlayerMetric(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
      Text(
        value,
        color = color,
        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum")
      )
      Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun PlayerControls() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = {}) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
      Button(onClick = {}) {
        Icon(Icons.Default.Pause, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Pause")
      }
      IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, contentDescription = "Skip interval") }
    }
  }
}

@Preview(name = "Home ready — Light", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun HomeReadyLightPreview() = TrainerLoopTheme(darkTheme = false) { HomeReadyReference() }

@Preview(name = "Home ready — Dark", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun HomeReadyDarkPreview() = TrainerLoopTheme(darkTheme = true) { HomeReadyReference() }

@Preview(name = "Home disconnected — Light", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun HomeDisconnectedLightPreview() = TrainerLoopTheme(darkTheme = false) { HomeDisconnectedReference() }

@Preview(name = "Home disconnected — Dark", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun HomeDisconnectedDarkPreview() = TrainerLoopTheme(darkTheme = true) { HomeDisconnectedReference() }

@Preview(name = "Library — Light", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun LibraryLightPreview() = TrainerLoopTheme(darkTheme = false) { LibraryReference() }

@Preview(name = "Library — Dark", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun LibraryDarkPreview() = TrainerLoopTheme(darkTheme = true) { LibraryReference() }

@Preview(name = "Player portrait — Light", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun PlayerPortraitLightPreview() = TrainerLoopTheme(darkTheme = false) { PlayerPortraitReference() }

@Preview(name = "Player portrait — Dark", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun PlayerPortraitDarkPreview() = TrainerLoopTheme(darkTheme = true) { PlayerPortraitReference() }

@Preview(name = "Player landscape — Light", showBackground = true, widthDp = 891, heightDp = 411)
@Composable
private fun PlayerLandscapeLightPreview() = TrainerLoopTheme(darkTheme = false) { PlayerLandscapeReference() }

@Preview(name = "Player landscape — Dark", showBackground = true, widthDp = 891, heightDp = 411)
@Composable
private fun PlayerLandscapeDarkPreview() = TrainerLoopTheme(darkTheme = true) { PlayerLandscapeReference() }
