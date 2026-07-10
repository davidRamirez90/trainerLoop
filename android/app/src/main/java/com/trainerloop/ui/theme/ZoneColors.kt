package com.trainerloop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ZoneColorSet(
  val fill: Color,
  val onFill: Color,
  val line: Color
)

object ZoneColors {

  /** 1..6 from %FTP using the existing bands: <55, <75, <90, <105, <120, else. Returns 1 when ftp <= 0. */
  fun zoneIndex(targetWatts: Int, ftp: Int): Int {
    if (ftp <= 0) return 1
    val percentFtp = targetWatts.toFloat() * 100f / ftp
    return when {
      percentFtp < 55f -> 1
      percentFtp < 75f -> 2
      percentFtp < 90f -> 3
      percentFtp < 105f -> 4
      percentFtp < 120f -> 5
      else -> 6
    }
  }

  fun forZone(zone: Int, dark: Boolean): ZoneColorSet {
    val colors = if (dark) Dark else Light
    return colors[(zone - 1).coerceIn(0, colors.lastIndex)]
  }

  fun forTarget(targetWatts: Int, ftp: Int, dark: Boolean): ZoneColorSet =
    forZone(zoneIndex(targetWatts, ftp), dark)

  private val Dark = arrayOf(
    ZoneColorSet(Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFF94A3B8)),
    ZoneColorSet(Color(0xFF3B82F6), Color(0xFF0B1210), Color(0xFF60A5FA)),
    ZoneColorSet(Color(0xFF22C55E), Color(0xFF0B1210), Color(0xFF4ADE80)),
    ZoneColorSet(Color(0xFFF59E0B), Color(0xFF0B1210), Color(0xFFFCD34D)),
    ZoneColorSet(Color(0xFFF97316), Color(0xFF0B1210), Color(0xFFFB923C)),
    ZoneColorSet(Color(0xFFEF4444), Color(0xFF0B1210), Color(0xFFF87171))
  )

  private val Light = arrayOf(
    ZoneColorSet(Color(0xFF475569), Color(0xFFF8FAFC), Color(0xFF475569)),
    ZoneColorSet(Color(0xFF2563EB), Color(0xFFF8FAFC), Color(0xFF2563EB)),
    ZoneColorSet(Color(0xFF15803D), Color(0xFFF8FAFC), Color(0xFF15803D)),
    ZoneColorSet(Color(0xFFB45309), Color(0xFFF8FAFC), Color(0xFFB45309)),
    ZoneColorSet(Color(0xFFC2410C), Color(0xFFF8FAFC), Color(0xFFC2410C)),
    ZoneColorSet(Color(0xFFB91C1C), Color(0xFFF8FAFC), Color(0xFFB91C1C))
  )
}

/** Theme-aware helper for composables. */
@Composable
fun zoneColorSet(targetWatts: Int, ftp: Int): ZoneColorSet =
  ZoneColors.forTarget(targetWatts, ftp, dark = isSystemInDarkTheme())
