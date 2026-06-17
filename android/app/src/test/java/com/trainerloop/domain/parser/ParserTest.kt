package com.trainerloop.domain.parser

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.domain.WorkoutImporter
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

  @Test
  fun `ERG parser imports minutes power file`() {
    val content = loadResource("sample.erg")
    val workout = ErgParser.parse("sample.erg", content)
    assertEquals("Sample ERG Workout", workout.name)
    assertEquals(WorkoutSource.IMPORTED, workout.source)
    assertTrue(workout.segments.isNotEmpty())
    assertEquals(SegmentPhase.WARMUP, workout.segments.first().phase)
  }

  @Test
  fun `MRC parser converts percent to watts`() {
    val content = loadResource("sample.mrc")
    val workout = MrcParser.parse("sample.mrc", content)
    assertTrue(workout.segments.isNotEmpty())
    val first = workout.segments.first() as WorkoutSegment.Step
    assertEquals(100, first.targetRange.low)
    assertEquals(100, first.targetRange.high)
  }

  @Test
  fun `ZWO parser parses warmup steadyState and cooldown`() {
    val content = loadResource("sample.zwo")
    val workout = ZwoParser.parse("sample.zwo", content, ftpWatts = 250)
    assertEquals("Sample ZWO Workout", workout.name)
    assertEquals(3, workout.segments.size)
    assertTrue(workout.segments[0] is WorkoutSegment.Ramp)
    assertTrue(workout.segments[1] is WorkoutSegment.Step)
    assertTrue(workout.segments[2] is WorkoutSegment.Ramp)
  }

  @Test
  fun `JSON parser parses workout with segments`() {
    val content = loadResource("sample.json")
    val workout = JsonWorkoutParser.parse("sample.json", content)
    assertEquals("Sample JSON Workout", workout.name)
    assertEquals(2, workout.segments.size)
    assertEquals(SegmentPhase.WARMUP, workout.segments[0].phase)
    assertEquals(SegmentPhase.WORK, workout.segments[1].phase)
  }

  @Test
  fun `importer dispatches by extension`() {
    val erg = loadResource("sample.erg")
    val zwo = loadResource("sample.zwo")
    val json = loadResource("sample.json")

    val ergWorkout = WorkoutImporter.import("sample.erg", erg)
    assertEquals("Sample ERG Workout", ergWorkout.name)

    val zwoWorkout = WorkoutImporter.import("sample.zwo", zwo)
    assertEquals("Sample ZWO Workout", zwoWorkout.name)

    val jsonWorkout = WorkoutImporter.import("sample.json", json)
    assertEquals("Sample JSON Workout", jsonWorkout.name)
  }

  private fun loadResource(name: String): String {
    return javaClass.classLoader?.getResource(name)?.readText()
      ?: throw IllegalArgumentException("Resource $name not found")
  }
}
