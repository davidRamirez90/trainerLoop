package com.trainerloop.ui.library

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ImportedWorkoutStoreTest {

  private lateinit var file: File

  @Before
  fun setUp() {
    file = File.createTempFile("workouts", ".json").apply { delete() }
  }

  @After
  fun tearDown() {
    file.delete()
    File(file.parentFile, "${file.name}.tmp").delete()
  }

  @Test
  fun `add with an existing id replaces instead of duplicating`() {
    val w = workout(id = "a", name = "One")
    ImportedWorkoutStore.add(file, w)
    ImportedWorkoutStore.add(file, w.copy(name = "Two"))

    val loaded = ImportedWorkoutStore.load(file)

    assertEquals(1, loaded.size)
    assertEquals("Two", loaded[0].name)
  }

  @Test
  fun `one malformed entry does not wipe the library`() {
    ImportedWorkoutStore.add(file, workout(id = "good", name = "Good"))
    val arr = Json.parseToJsonElement(file.readText()).jsonArray
    file.writeText(JsonArray(arr + buildJsonObject { put("id", "broken") }).toString())

    assertEquals(listOf("good"), ImportedWorkoutStore.load(file).map { it.id })
  }

  private fun workout(id: String, name: String) = Workout(
    id = id,
    name = name,
    description = null,
    source = WorkoutSource.IMPORTED,
    segments = listOf(
      WorkoutSegment.Step(
        id = "segment-1",
        durationSec = 60,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(200, 200)
      )
    )
  )
}
