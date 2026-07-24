package com.trainerloop.domain.parser

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.domain.WorkoutImporter
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
  fun `headerless MRC uses percent units even when first point is above two`() {
    val workout = MrcParser.parse("headerless.mrc", "0 50\n5 50", ftpWatts = 250)
    val first = workout.segments.first() as WorkoutSegment.Step
    assertEquals(125, first.targetRange.low)
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
  fun `ZWO steadyState preserves unequal power bounds as a target range`() {
    val content = """
      <workout_file>
        <name>Threshold range</name>
        <workout>
          <SteadyState Duration="600" PowerLow="0.88" PowerHigh="0.95" />
        </workout>
      </workout_file>
    """.trimIndent()

    val segment = ZwoParser.parse("threshold-range.zwo", content, ftpWatts = 250).segments.single()

    assertTrue(segment is WorkoutSegment.Step)
    segment as WorkoutSegment.Step
    assertEquals(220, segment.targetRange.low)
    assertEquals(238, segment.targetRange.high)
  }

  @Test
  fun `ZWO explicit ramp remains a ramp`() {
    val content = """
      <workout_file>
        <name>Ramp</name>
        <workout>
          <Ramp Duration="600" PowerLow="0.88" PowerHigh="0.95" />
        </workout>
      </workout_file>
    """.trimIndent()

    val segment = ZwoParser.parse("ramp.zwo", content, ftpWatts = 250).segments.single()

    assertTrue(segment is WorkoutSegment.Ramp)
  }

  @Test
  fun `ZWO parser rejects doctype declarations on all XML platforms`() {
    val content = """
      <!DOCTYPE workout_file [<!ENTITY injected "bad">]>
      <workout_file>
        <name>Unsafe workout</name>
        <workout><SteadyState Duration="60" Power="1" /></workout>
      </workout_file>
    """.trimIndent()

    assertThrows(ZwoParseException::class.java) {
      ZwoParser.parse("unsafe.zwo", content)
    }
  }

  @Test
  fun `ZWO parser caps repeats and segment durations`() {
    val content = """
      <?xml version="1.0" encoding="UTF-8"?>
      <workout_file>
        <name>Large intervals</name>
        <workout>
          <IntervalsT Repeat="100000" OnDuration="10000000" OffDuration="0" OnPower="1" OffPower="0.5" />
        </workout>
      </workout_file>
    """.trimIndent()

    val workout = ZwoParser.parse("large.zwo", content)

    assertEquals(400, workout.segments.size)
    assertEquals(86_400, workout.segments.first().durationSec)
    assertEquals(1, workout.segments[1].durationSec)
  }

  @Test
  fun `ZWO parser rejects more than 2000 segments`() {
    val content = buildString {
      append("<workout_file><workout>")
      repeat(2_001) {
        append("<SteadyState Duration=\"60\" Power=\"1\" />")
      }
      append("</workout></workout_file>")
    }

    assertThrows(IllegalArgumentException::class.java) {
      ZwoParser.parse("too-many.zwo", content)
    }
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
  fun `JSON parser caps segment durations`() {
    val content = """
      {
        "name": "Clamped workout",
        "segments": [
          {"durationSec": 10000000, "targetRange": {"low": 100, "high": 100}, "phase": "work"},
          {"durationSec": -10, "targetRange": {"low": 80, "high": 80}, "phase": "recovery"}
        ]
      }
    """.trimIndent()

    val workout = JsonWorkoutParser.parse("clamped.json", content)

    assertEquals(86_400, workout.segments.first().durationSec)
    assertEquals(1, workout.segments[1].durationSec)
  }

  @Test
  fun `JSON parser rejects more than 2000 segments`() {
    val content = buildString {
      append("{\"segments\":[")
      repeat(2_001) { index ->
        if (index > 0) append(',')
        append("{\"durationSec\":60,\"targetRange\":{\"low\":100,\"high\":100},\"phase\":\"work\"}")
      }
      append("]}")
    }

    assertThrows(IllegalArgumentException::class.java) {
      JsonWorkoutParser.parse("too-many.json", content)
    }
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
