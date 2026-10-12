package com.trainerloop.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationPresetsTest {
  @Test fun defaultsMatchEverydayRoadRide() {
    assertEquals("Road", bikePresets[nearestPreset("8.0", bikePresets)].name)
    assertEquals("Regular road", surfacePresets[nearestPreset("0.005", surfacePresets)].name)
    assertEquals("On the hoods", positionPresets[nearestPreset("0.32", positionPresets)].name)
  }
  @Test fun intermediateLevelsAndInvalidValuesMapCorrectly() {
    assertEquals("Endurance", bikePresets[nearestPreset("9.0", bikePresets)].name)
    assertEquals("Worn pavement", surfacePresets[nearestPreset("0.006", surfacePresets)].name)
    assertEquals("In the drops", positionPresets[nearestPreset("0.27", positionPresets)].name)
    assertEquals("Road", bikePresets[nearestPreset("", bikePresets)].name)
    assertEquals("Regular road", surfacePresets[nearestPreset("", surfacePresets)].name)
    assertEquals("On the hoods", positionPresets[nearestPreset("", positionPresets)].name)
  }
  @Test fun customValuesAreMappedWithoutChangingThem() {
    assertEquals(0, nearestPreset("5.5", bikePresets))
    assertEquals(6, nearestPreset("0.0085", surfacePresets))
    assertEquals(6, nearestPreset("0.55", positionPresets))
  }
}
