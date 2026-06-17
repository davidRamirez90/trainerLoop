package com.trainerloop.domain.parser

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlin.math.roundToInt

internal object ErgMrcShared {

  private const val DEFAULT_FTP_WATTS = 250
  private const val WORK_THRESHOLD = 0.85

  fun parse(
    text: String,
    fileName: String,
    ftpWatts: Int = DEFAULT_FTP_WATTS
  ): Workout {
    val lines = text.lines()
    val header = mutableMapOf<String, String>()
    var timeUnit: TimeUnit = TimeUnit.MINUTES
    var powerUnit: PowerUnit? = null

    val dataPoints = mutableListOf<DataPoint>()

    for (rawLine in lines) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue
      if (line.startsWith('[') && line.endsWith(']')) continue

      if (line.contains('=')) {
        val parts = line.split('=', limit = 2)
        header[parts[0].trim().uppercase()] = parts[1].trim()
        continue
      }

      val tokens = line.split(Regex("\\s+"))
      val token0 = tokens.getOrNull(0)?.uppercase()
      val token1 = tokens.getOrNull(1)?.uppercase()
      if (tokens.size == 2 && (token0 == "MINUTES" || token0 == "SECONDS")) {
        timeUnit = if (token0 == "SECONDS") TimeUnit.SECONDS else TimeUnit.MINUTES
        powerUnit = if (token1 == "PERCENT") PowerUnit.PERCENT else PowerUnit.WATTS
        continue
      }

      if (!line.matches(Regex("^[+-]?\\d.*"))) continue

      val timeValue = tokens.getOrNull(0)?.toDoubleOrNull() ?: continue
      val powerValue = tokens.getOrNull(1)?.toDoubleOrNull() ?: continue
      val timeSec = if (timeUnit == TimeUnit.MINUTES) timeValue * 60 else timeValue
      dataPoints.add(DataPoint(timeSec, powerValue))
    }

    if (dataPoints.size < 2) {
      throw IllegalArgumentException("ERG/MRC file must include at least two data points.")
    }

    val fileFtpWatts = header["FTP"] ?: header["FTP WATTS"] ?: header["FTP_WATTS"] ?: header["FTPWATTS"]
    val resolvedFtpWatts = if (fileFtpWatts != null && fileFtpWatts.toDoubleOrNull() != null) {
      fileFtpWatts.toDouble().roundToInt()
    } else ftpWatts

    val resolvedPowerUnit = powerUnit
      ?: if (dataPoints.first().power <= 2) PowerUnit.PERCENT else PowerUnit.WATTS

    val points = dataPoints
      .sortedBy { it.timeSec }
      .map {
        val powerWatts = if (resolvedPowerUnit == PowerUnit.PERCENT) {
          toWattsFromPercent(it.power, resolvedFtpWatts)
        } else {
          it.power.roundToInt()
        }
        DataPoint(it.timeSec, powerWatts.toDouble())
      }
      .toMutableList()

    if (points.first().timeSec > 0) {
      points.add(0, points.first().copy(timeSec = 0.0))
    }

    val segments = mutableListOf<WorkoutSegment>()
    var workCount = 0
    var recoveryCount = 0

    for (index in 0 until points.size - 1) {
      val start = points[index]
      val end = points[index + 1]
      val duration = (end.timeSec - start.timeSec).roundToInt()
      if (duration <= 0) continue

      val phase = classifyPhase(start.power, resolvedFtpWatts, index, points.size - 2)
      val isWork = phase == SegmentPhase.WORK
      val label = when (phase) {
        SegmentPhase.WARMUP -> "Warmup"
        SegmentPhase.COOLDOWN -> "Cooldown"
        SegmentPhase.WORK -> {
          workCount += 1
          "Interval $workCount"
        }
        SegmentPhase.RECOVERY -> {
          recoveryCount += 1
          "Recovery $recoveryCount"
        }
      }

      val segment = buildSegment(index, label, duration, start.power, end.power, phase, isWork)
      if (segment != null) {
        segments.add(segment)
      }
    }

    if (segments.isEmpty()) {
      throw IllegalArgumentException("No segments could be parsed from this workout.")
    }

    val name = header["FILE NAME"] ?: header["DESCRIPTION"] ?: slugFromFileName(fileName)
    val description = header["DESCRIPTION"] ?: "Imported ERG/MRC workout"

    return Workout(
      id = slugFromFileName(fileName),
      name = name,
      description = description,
      source = WorkoutSource.IMPORTED,
      segments = segments
    )
  }

  private fun toWattsFromPercent(value: Double, ftpWatts: Int): Int {
    val percent = if (value <= 1) value * 100 else value
    return ((percent / 100) * ftpWatts).roundToInt()
  }

  private fun classifyPhase(
    watts: Double,
    ftpWatts: Int,
    index: Int,
    lastIndex: Int
  ): SegmentPhase {
    if (index == 0) return SegmentPhase.WARMUP
    if (index >= lastIndex) return SegmentPhase.COOLDOWN
    return if (watts >= ftpWatts * WORK_THRESHOLD) SegmentPhase.WORK else SegmentPhase.RECOVERY
  }

  private fun buildSegment(
    index: Int,
    label: String,
    durationSec: Int,
    startWatts: Double,
    endWatts: Double,
    phase: SegmentPhase,
    isWork: Boolean
  ): WorkoutSegment? {
    if (durationSec <= 0) return null

    return if (startWatts != endWatts) {
      WorkoutSegment.Ramp(
        id = "segment-${index + 1}",
        durationSec = durationSec,
        label = label,
        phase = phase,
        isWork = isWork,
        startPower = startWatts.roundToInt(),
        endPower = endWatts.roundToInt()
      )
    } else {
      WorkoutSegment.Step(
        id = "segment-${index + 1}",
        durationSec = durationSec,
        label = label,
        phase = phase,
        isWork = isWork,
        targetRange = TargetRange(startWatts.roundToInt(), startWatts.roundToInt())
      )
    }
  }

  private fun slugFromFileName(fileName: String): String {
    return fileName.substringBeforeLast('.').trim()
  }

  private enum class TimeUnit { MINUTES, SECONDS }
  private enum class PowerUnit { WATTS, PERCENT }
  private data class DataPoint(val timeSec: Double, val power: Double)
}
