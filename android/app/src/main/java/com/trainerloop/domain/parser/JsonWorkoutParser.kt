package com.trainerloop.domain.parser

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object JsonWorkoutParser {

  private val json = Json { ignoreUnknownKeys = true }

  fun parse(name: String, content: String, @Suppress("UNUSED_PARAMETER") ftpWatts: Int = 250): Workout {
    val dto = json.decodeFromString(WorkoutJson.serializer(), content)

    if (dto.segments.isEmpty()) {
      throw IllegalArgumentException("Workout must include a non-empty segments array.")
    }

    val fallbackName = name.substringBeforeLast('.').trim()
    val fallbackSubtitle = "Imported workout"

    return Workout(
      id = dto.id?.takeIf { it.isNotBlank() } ?: "import-${System.currentTimeMillis()}",
      name = dto.name?.takeIf { it.isNotBlank() } ?: fallbackName,
      description = dto.subtitle?.takeIf { it.isNotBlank() } ?: fallbackSubtitle,
      source = WorkoutSource.IMPORTED,
      segments = dto.segments.mapIndexed { index, segment ->
        val phase = segment.phase?.let { parsePhase(it) }
          ?: throw IllegalArgumentException("segments[$index].phase must be warmup, work, recovery, or cooldown.")
        val targetRange = segment.targetRange
          ?: throw IllegalArgumentException("segments[$index].targetRange must be an object.")

        val rampToRange = segment.rampToRange
        val cadenceRange = segment.cadenceRange?.let { IntRange(it.low, it.high) }

        if (rampToRange != null) {
          WorkoutSegment.Ramp(
            id = segment.id?.takeIf { it.isNotBlank() } ?: "segment-${index + 1}",
            durationSec = segment.durationSec,
            label = segment.label?.takeIf { it.isNotBlank() } ?: "Segment ${index + 1}",
            phase = phase,
            isWork = segment.isWork ?: (phase == SegmentPhase.WORK),
            startPower = targetRange.low,
            endPower = rampToRange.high,
            targetCadence = cadenceRange
          )
        } else {
          WorkoutSegment.Step(
            id = segment.id?.takeIf { it.isNotBlank() } ?: "segment-${index + 1}",
            durationSec = segment.durationSec,
            label = segment.label?.takeIf { it.isNotBlank() } ?: "Segment ${index + 1}",
            phase = phase,
            isWork = segment.isWork ?: (phase == SegmentPhase.WORK),
            targetRange = TargetRange(targetRange.low, targetRange.high),
            targetCadence = cadenceRange
          )
        }
      }
    )
  }

  private fun parsePhase(value: String): SegmentPhase {
    return when (value.lowercase()) {
      "warmup" -> SegmentPhase.WARMUP
      "work" -> SegmentPhase.WORK
      "recovery" -> SegmentPhase.RECOVERY
      "cooldown" -> SegmentPhase.COOLDOWN
      else -> throw IllegalArgumentException("phase must be warmup, work, recovery, or cooldown.")
    }
  }

  @Serializable
  private data class WorkoutJson(
    val id: String? = null,
    val name: String? = null,
    val subtitle: String? = null,
    val ftpWatts: Int = 250,
    val segments: List<SegmentJson> = emptyList()
  )

  @Serializable
  private data class SegmentJson(
    val id: String? = null,
    val label: String? = null,
    val durationSec: Int,
    val targetRange: RangeJson? = null,
    val cadenceRange: RangeJson? = null,
    val rampToRange: RangeJson? = null,
    val phase: String? = null,
    val isWork: Boolean? = null
  )

  @Serializable
  private data class RangeJson(val low: Int, val high: Int)
}
