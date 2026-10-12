package com.trainerloop.ui.designproposals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Review-only concepts. No production route, BLE, network, or persistence effects.
private val Paper = Color(0xFFF5F4ED)
private val Forest = Color(0xFF163E32)
private val Ink = Color(0xFF172B25)
private val Night = Color(0xFF101719)
private val Mint = Color(0xFFCCFA75)
private val Blue = Color(0xFF243CB5)
private val Peach = Color(0xFFF6E7DB)

@Composable
fun HomeProposal(concept: Int) {
  MaterialTheme {
    when (concept) {
      1 -> FocusHome()
      2 -> ConsoleHome()
      else -> JournalHome()
    }
  }
}

@Composable
private fun Label(value: String, color: Color, size: Int = 14, bold: Boolean = false,
  family: FontFamily = FontFamily.SansSerif) {
  Text(value, color = color, fontSize = size.sp, lineHeight = (size * 1.2).sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = family)
}

@Composable
private fun Gap(height: Int = 16) { Spacer(Modifier.height(height.dp)) }

@Composable
private fun Action(text: String, background: Color, foreground: Color, radius: Int = 16) {
  Box(Modifier.fillMaxWidth().height(56.dp).background(background, RoundedCornerShape(radius.dp)),
    contentAlignment = Alignment.Center) { Label(text, foreground, 16, true) }
}

@Composable
private fun Nav(labels: List<String>, bg: Color, fg: Color, accent: Color) {
  Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceEvenly) {
    labels.forEachIndexed { index, label ->
      Column(Modifier.weight(1f).height(52.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(width = 28.dp, height = 4.dp)
          .background(if (index == 0) accent else Color.Transparent, RoundedCornerShape(2.dp)))
        Gap(8)
        Label(label, if (index == 0) accent else fg, 13, index == 0)
      }
    }
  }
}

@Composable
private fun Profile(color: Color) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically) {
    Label("TRAINERLOOP", color, 12, true)
    Box(Modifier.size(48.dp).border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(24.dp)),
      contentAlignment = Alignment.Center) { Label("AM", color, 14, true) }
  }
}

@Composable
private fun WorkoutChart(accent: Color, recovery: Color, grid: Color, height: Int = 104) {
  Canvas(Modifier.fillMaxWidth().height(height.dp)) {
    repeat(4) { i ->
      val y = size.height * (i + 1) / 4f
      drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
    val durations = listOf(600, 600, 180, 600, 180, 600, 300)
    val levels = listOf(.53f, .92f, .40f, .92f, .40f, .92f, .38f)
    var x = 0f
    durations.forEachIndexed { i, seconds ->
      val width = size.width * seconds / 3060f
      val h = size.height * levels[i]
      drawRect(if (i in listOf(1, 3, 5)) accent else recovery,
        Offset(x + 1.dp.toPx(), size.height - h), Size(width - 2.dp.toPx(), h))
      x += width
    }
  }
}

@Composable
private fun FocusHome() {
  Column(Modifier.fillMaxSize().background(Paper)) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
      Profile(Ink)
      Gap(20)
      Label("SUNDAY, 11 OCTOBER", Forest, 12, true)
      Gap(8)
      Label("One good ride.", Ink, 38, family = FontFamily.Serif)
      Gap(8)
      Label("Your time. Your training.", Forest, 16)
      Gap(28)
      Column(Modifier.fillMaxWidth().background(Forest, RoundedCornerShape(24.dp)).padding(24.dp)) {
        Label("TODAY’S PLAN  ·  INTERVALS.ICU", Color(0xFFD4E6DB), 11, true)
        Gap(16)
        Label("Threshold\n3 × 10", Color.White, 32, family = FontFamily.Serif)
        Gap(12)
        Label("51 min  ·  3 efforts at 235–250 W", Color(0xFFD4E6DB), 14)
        Gap(20)
        WorkoutChart(Color(0xFFCCE4A5), Color(0xFF6E9A83), Color(0xFF52705E), 80)
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Label("Warm up", Color(0xFFD4E6DB), 11)
          Label("Cool down", Color(0xFFD4E6DB), 11)
        }
        Gap(24)
        Action("Open workout  →", Color(0xFFDFEDC8), Forest, 28)
      }
      Gap(16)
      Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column { Label("Equipment connected", Ink, 14, true); Label("KICKR CORE  ·  Heart rate", Forest, 12) }
        Label("Manage →", Forest, 13, true)
      }
      Gap(8)
      Action("Choose a different workout", Color.White, Forest, 28)
      Gap(20)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Label("Last ride", Ink, 14, true); Label("View history →", Forest, 13)
      }
      Gap(8)
      Label("Endurance  ·  Fri 9 Oct  ·  30 min", Forest, 14)
    }
    Nav(listOf("Today", "Workouts", "History"), Paper, Forest, Forest)
  }
}

@Composable
private fun ConsoleHome() {
  val white = Color(0xFFF1F5F3)
  val muted = Color(0xFFADBEB7)
  val panel = Color(0xFF1C282A)
  Column(Modifier.fillMaxSize().background(Night)) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
      Profile(white)
      Gap(16)
      Label("RIDE CONSOLE", Mint, 12, true, FontFamily.Monospace)
      Gap(8)
      Label("Your next effort.", white, 30, true)
      Gap(16)
      Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(12.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Label("TRAINER", muted, 10, true); Gap(6); Label("KICKR CORE", white, 13, true); Gap(6); Label("Connected", Mint, 12) }
        Column { Label("HEART RATE", muted, 10, true); Gap(6); Label("124 bpm", white, 18, true, FontFamily.Monospace); Gap(6); Label("Connected", Mint, 12) }
        Column { Label("FTP", muted, 10, true); Gap(6); Label("250 W", white, 18, true, FontFamily.Monospace); Gap(6); Label("Profile →", muted, 12) }
      }
      Gap(20)
      Label("01 / TODAY’S PLAN  ·  INTERVALS.ICU", muted, 11, true, FontFamily.Monospace)
      Gap(12)
      Label("Threshold 3 × 10", white, 27, true)
      Gap(20)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Label("51", white, 48, true, FontFamily.Monospace); Label("MINUTES", muted, 11, true) }
        Column { Label("3 × 10", white, 28, true, FontFamily.Monospace); Gap(8); Label("MIN AT THRESHOLD", muted, 11, true) }
      }
      Gap(16)
      WorkoutChart(Mint, Color(0xFF517269), Color(0xFF31453E), 104)
      Gap(10)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Label("0 min", muted, 11, family = FontFamily.Monospace)
        Label("51 min", muted, 11, family = FontFamily.Monospace)
      }
      Gap(16)
      Action("LOAD WORKOUT  →", Mint, Night, 8)
      Gap(12)
      Label("Press Play in the player to begin.", muted, 12)
      Gap(16)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).background(panel, RoundedCornerShape(8.dp)).padding(16.dp)) { Label("Library →", white, 15, true) }
        Box(Modifier.weight(1f).background(panel, RoundedCornerShape(8.dp)).padding(16.dp)) { Label("FTP test →", white, 15, true) }
      }
      Gap(20)
      Label("LAST RIDE  ·  9 OCT", muted, 11, true, FontFamily.Monospace)
      Gap(8)
      Label("Endurance  /  30 min  /  View →", white, 14)
    }
    Nav(listOf("Ride", "Library", "History", "Setup"), Night, muted, Mint)
  }
}

@Composable
private fun JournalHome() {
  val ink = Color(0xFF25243A)
  val muted = Color(0xFF655B66)
  Column(Modifier.fillMaxSize().background(Peach)) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
      Profile(ink)
      Gap(16)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom) {
        Column { Label("Your training", ink, 32, true); Gap(4); Label("A little work. Lasting progress.", muted, 14) }
        Label("11\nOCT", Blue, 24, true, FontFamily.Monospace)
      }
      Gap(24)
      Label("TODAY  /  SUNDAY", Blue, 12, true)
      Gap(12)
      Column(Modifier.fillMaxWidth().background(Blue, RoundedCornerShape(6.dp)).padding(22.dp)) {
        Label("PLANNED · INTERVALS.ICU", Color(0xFFDFE4FF), 11, true)
        Gap(12)
        Label("Threshold\n3 × 10", Color.White, 32, true)
        Gap(16)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Label("51 min", Color.White, 23, true)
          Label("235–250 W\nEffort target", Color(0xFFDFE4FF), 13)
        }
        Gap(16)
        WorkoutChart(Color(0xFFF6BA8C), Color(0xFF8191D6), Color(0xFF6173C8), 72)
        Gap(20)
        Action("Open today’s workout  →", Color.White, Blue, 6)
      }
      Gap(20)
      Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Label("KICKR CORE + HR connected", ink, 13, true)
        Label("Devices →", Blue, 13, true)
      }
      Gap(16)
      Label("PREVIOUS ENTRY  /  FRIDAY 9 OCT", muted, 11, true)
      Gap(12)
      Row(Modifier.fillMaxWidth().background(Color(0xFFFFF9F3), RoundedCornerShape(6.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Label("Endurance", ink, 19, true); Gap(6); Label("30 min  ·  Saved ride", muted, 13) }
        Label("Review →", Blue, 14, true)
      }
      Gap(20)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Label("Something else today?", ink, 14); Label("Browse workouts →", Blue, 13, true)
      }
    }
    Nav(listOf("Journal", "Workouts", "History"), Peach, muted, Blue)
  }
}
