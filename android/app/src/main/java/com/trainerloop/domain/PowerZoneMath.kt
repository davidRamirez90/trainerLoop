package com.trainerloop.domain

object PowerZoneMath {

  /** 1..6 from %FTP using the app's <55, <75, <90, <105, <120, else bands. */
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
}
