package com.trainerloop.ui.library

import android.content.Context
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** Prefs-backed set of favorite workout ids (built-in or imported). */
object FavoriteStore {
  private const val PREFS = "trainer_loop_favorites"
  private const val KEY = "ids"

  fun ids(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

  fun toggle(context: Context, id: String) {
    val next = ids(context).toMutableSet()
    if (!next.add(id)) next.remove(id)
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, next).apply()
  }
}

/** File-backed store for user-imported and user-built workouts. */
object ImportedWorkoutStore {

  private fun file(context: Context) = File(context.filesDir, "imported_workouts.json")

  fun add(context: Context, workout: Workout) = add(file(context), workout)

  internal fun add(file: File, workout: Workout) {
    val existing = load(file).filter { it.id != workout.id } + workout
    writeAtomically(file, existing)
  }

  fun remove(context: Context, id: String) = remove(file(context), id)

  internal fun remove(file: File, id: String) {
    writeAtomically(file, load(file).filter { it.id != id })
  }

  fun load(context: Context): List<Workout> = load(file(context))

  internal fun load(file: File): List<Workout> {
    if (!file.exists()) return emptyList()
    return try {
      val json = Json.parseToJsonElement(file.readText()).jsonArray
      (0 until json.size).mapNotNull { i ->
        try {
          jsonToWorkout(json[i].jsonObject)
        } catch (e: Exception) {
          null
        }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun writeAtomically(file: File, workouts: List<Workout>) {
    val contents = buildJsonArray {
      workouts.forEach { add(workoutToJson(it)) }
    }.toString()
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(contents)
    if (!tmp.renameTo(file)) {
      file.writeText(contents)
      tmp.delete()
    }
  }

  private fun workoutToJson(w: Workout): JsonObject = buildJsonObject {
      put("id", w.id)
      put("name", w.name)
      put("description", w.description ?: "")
      put("source", w.source.name)
      put("segments", buildJsonArray {
        w.segments.forEach { seg ->
          add(buildJsonObject {
            put("id", seg.id)
            put("durationSec", seg.durationSec)
            put("label", seg.label ?: "")
            put("phase", seg.phase.name)
            put("isWork", seg.isWork)
            when (seg) {
              is WorkoutSegment.Step -> {
                put("type", "step")
                put("targetLow", seg.targetRange.low)
                put("targetHigh", seg.targetRange.high)
                seg.targetCadence?.let {
                  put("cadenceLow", it.first)
                  put("cadenceHigh", it.last)
                }
              }
              is WorkoutSegment.Ramp -> {
                put("type", "ramp")
                put("startPower", seg.startPower)
                put("endPower", seg.endPower)
              }
              is WorkoutSegment.FreeRide -> put("type", "freeride")
            }
          })
        }
      })
  }

  private fun jsonToWorkout(obj: JsonObject): Workout {
    val segs = mutableListOf<WorkoutSegment>()
    val segArr = obj.required("segments").jsonArray
    (0 until segArr.size).forEach { i ->
      val s = segArr[i].jsonObject
      val type = s.requiredString("type")
      val segment = when (type) {
        "step" -> WorkoutSegment.Step(
          id = s.requiredString("id"),
          durationSec = s.requiredInt("durationSec"),
          label = s.optionalString("label"),
          phase = SegmentPhase.valueOf(s.requiredString("phase")),
          isWork = s.requiredBoolean("isWork"),
          targetRange = TargetRange(s.requiredInt("targetLow"), s.requiredInt("targetHigh")),
          targetCadence = if (s.containsKey("cadenceLow") && s.containsKey("cadenceHigh")) {
            IntRange(s.requiredInt("cadenceLow"), s.requiredInt("cadenceHigh"))
          } else null
        )
        "ramp" -> WorkoutSegment.Ramp(
          id = s.requiredString("id"),
          durationSec = s.requiredInt("durationSec"),
          label = s.optionalString("label"),
          phase = SegmentPhase.valueOf(s.requiredString("phase")),
          isWork = s.requiredBoolean("isWork"),
          startPower = s.requiredInt("startPower"),
          endPower = s.requiredInt("endPower")
        )
        else -> WorkoutSegment.FreeRide(
          id = s.requiredString("id"),
          durationSec = s.requiredInt("durationSec"),
          label = s.optionalString("label"),
          phase = SegmentPhase.valueOf(s.requiredString("phase")),
          isWork = s.requiredBoolean("isWork")
        )
      }
      segs.add(segment)
    }
    return Workout(
      id = obj.requiredString("id"),
      name = obj.requiredString("name"),
      description = obj.optionalString("description"),
      source = WorkoutSource.valueOf(obj.requiredString("source")),
      segments = segs
    )
  }

  private fun JsonObject.required(key: String) = get(key) ?: error("Missing $key")

  private fun JsonObject.requiredString(key: String) = required(key).jsonPrimitive.content

  private fun JsonObject.requiredInt(key: String) = required(key).jsonPrimitive.content.toInt()

  private fun JsonObject.requiredBoolean(key: String) = required(key).jsonPrimitive.content.toBooleanStrict()

  private fun JsonObject.optionalString(key: String) = get(key)?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
}
