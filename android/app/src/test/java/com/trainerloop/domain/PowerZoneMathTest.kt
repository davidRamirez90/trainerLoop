package com.trainerloop.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerZoneMathTest {
  @Test
  fun `displayed watt boundaries match live and recorded zone classification`() {
    listOf(200, 250, 257).forEach { ftp ->
      val bounds = PowerZoneMath.lowerBoundsWatts(ftp)
      assertEquals(6, bounds.size)
      bounds.forEachIndexed { index, watts ->
        assertEquals(index + 1, PowerZoneMath.zoneIndex(watts, ftp))
        if (index > 0) assertEquals(index, PowerZoneMath.zoneIndex(watts - 1, ftp))
      }
    }
  }
}
