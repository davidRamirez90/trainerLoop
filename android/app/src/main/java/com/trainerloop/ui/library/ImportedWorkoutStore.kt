package com.trainerloop.ui.library

import android.content.Context
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import org.json.JSONArray
import org.json.JSONObject
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

  fun add(context: Context, workout: Workout) {
    val existing = load(context).toMutableList()
    existing.add(workout)
    val json = JSONArray()
    existing.forEach { json.put(workoutToJson(it)) }
    file(context).writeText(json.toString())
  }

  fun remove(context: Context, id: String) {
    val remaining = load(context).filter { it.id != id }
    val json = JSONArray()
    remaining.forEach { json.put(workoutToJson(it)) }
    file(context).writeText(json.toString())
  }

  fun load(context: Context): List<Workout> {
    val f = file(context)
    if (!f.exists()) return emptyList()
    return try {
      val json = JSONArray(f.readText())
      (0 until json.length()).map { i -> jsonToWorkout(json.getJSONObject(i)) }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun workoutToJson(w: Workout): JSONObject {
    return JSONObject().apply {
      put("id", w.id)
      put("name", w.name)
      put("description", w.description ?: "")
      put("source", w.source.name)
      val segs = JSONArray()
      w.segments.forEach { seg ->
        val obj = JSONObject()
        obj.put("id", seg.id)
        obj.put("durationSec", seg.durationSec)
        obj.put("label", seg.label ?: "")
        obj.put("phase", seg.phase.name)
        obj.put("isWork", seg.isWork)
        when (seg) {
          is WorkoutSegment.Step -> {
            obj.put("type", "step")
            obj.put("targetLow", seg.targetRange.low)
            obj.put("targetHigh", seg.targetRange.high)
            seg.targetCadence?.let { obj.put("cadenceLow", it.first); obj.put("cadenceHigh", it.last) }
          }
          is WorkoutSegment.Ramp -> {
            obj.put("type", "ramp")
            obj.put("startPower", seg.startPower)
            obj.put("endPower", seg.endPower)
          }
          is WorkoutSegment.FreeRide -> {
            obj.put("type", "freeride")
          }
        }
        segs.put(obj)
      }
      put("segments", segs)
    }
  }

  private fun jsonToWorkout(obj: JSONObject): Workout {
    val segs = mutableListOf<WorkoutSegment>()
    val segArr = obj.getJSONArray("segments")
    (0 until segArr.length()).forEach { i ->
      val s = segArr.getJSONObject(i)
      val type = s.getString("type")
      val segment = when (type) {
        "step" -> WorkoutSegment.Step(
          id = s.getString("id"),
          durationSec = s.getInt("durationSec"),
          label = s.optString("label", null)?.takeIf { it.isNotEmpty() },
          phase = SegmentPhase.valueOf(s.getString("phase")),
          isWork = s.getBoolean("isWork"),
          targetRange = TargetRange(s.getInt("targetLow"), s.getInt("targetHigh")),
          targetCadence = if (s.has("cadenceLow") && s.has("cadenceHigh")) {
            IntRange(s.getInt("cadenceLow"), s.getInt("cadenceHigh"))
          } else null
        )
        "ramp" -> WorkoutSegment.Ramp(
          id = s.getString("id"),
          durationSec = s.getInt("durationSec"),
          label = s.optString("label", null)?.takeIf { it.isNotEmpty() },
          phase = SegmentPhase.valueOf(s.getString("phase")),
          isWork = s.getBoolean("isWork"),
          startPower = s.getInt("startPower"),
          endPower = s.getInt("endPower")
        )
        else -> WorkoutSegment.FreeRide(
          id = s.getString("id"),
          durationSec = s.getInt("durationSec"),
          label = s.optString("label", null)?.takeIf { it.isNotEmpty() },
          phase = SegmentPhase.valueOf(s.getString("phase")),
          isWork = s.getBoolean("isWork")
        )
      }
      segs.add(segment)
    }
    return Workout(
      id = obj.getString("id"),
      name = obj.getString("name"),
      description = obj.optString("description", null)?.takeIf { it.isNotEmpty() },
      source = WorkoutSource.valueOf(obj.getString("source")),
      segments = segs
    )
  }
}
