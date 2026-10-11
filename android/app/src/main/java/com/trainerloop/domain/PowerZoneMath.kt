package com.trainerloop.domain

object PowerZoneMath {

  private val thresholdsPercent = listOf(55, 75, 90, 105, 120)

  /** First integer wattage in each displayed zone, using the same boundaries as classification. */
  fun lowerBoundsWatts(ftp: Int): List<Int> =
    listOf(0) + thresholdsPercent.map { percent -> (ftp * percent + 99) / 100 }

  /** 1..6 from %FTP using the app's <55, <75, <90, <105, <120, else bands. */
  fun zoneIndex(targetWatts: Int, ftp: Int): Int {
    if (ftp <= 0) return 1
    val percentFtp = targetWatts.toFloat() * 100f / ftp
    val belowThreshold = thresholdsPercent.indexOfFirst { percentFtp < it }
    return if (belowThreshold < 0) 6 else belowThreshold + 1
  }
}
