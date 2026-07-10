package com.trainerloop.domain.parser

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

class ZwoParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ZwoParser {

  private const val WORK_THRESHOLD = 0.85

  fun parse(name: String, content: String, ftpWatts: Int = 250): Workout {
    val document = try {
      val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      }
      factory.newDocumentBuilder().parse(content.byteInputStream())
    } catch (e: Exception) {
      throw ZwoParseException("Not a valid ZWO file", e)
    }

    val workoutNode = document.getElementsByTagName("workout").item(0)
      ?: throw IllegalArgumentException("ZWO file missing <workout> definition.")

    val nameNode = document.getElementsByTagName("name").item(0)
    val descriptionNode = document.getElementsByTagName("description").item(0)
    val workoutName = nameNode?.textContent?.trim()?.takeIf { it.isNotEmpty() }
      ?: name.substringBeforeLast('.').trim()
    val description = descriptionNode?.textContent?.trim()?.takeIf { it.isNotEmpty() }
      ?: "Imported ZWO workout"

    val segments = mutableListOf<WorkoutSegment>()
    var segmentIndex = 0
    var intervalCount = 0
    var recoveryCount = 0

    val children = workoutNode.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType != Node.ELEMENT_NODE) continue
      val element = child as Element
      val tag = element.tagName.lowercase()

      when (tag) {
        "warmup" -> {
          val (low, high) = resolveWarmupCooldownPower(element, ftpWatts)
          val cadenceRange = getCadenceRange(element)
          val duration = element.getAttrNumber("Duration").roundToInt()
          if (low != high) {
            segments.addRamp(
              id = segmentIndex,
              label = "Warmup",
              durationSec = duration,
              startPower = low,
              endPower = high,
              phase = SegmentPhase.WARMUP,
              isWork = false,
              cadenceRange = cadenceRange
            )
          } else {
            segments.addStep(
              id = segmentIndex,
              label = "Warmup",
              durationSec = duration,
              targetRange = TargetRange(low, high),
              phase = SegmentPhase.WARMUP,
              isWork = false,
              cadenceRange = cadenceRange
            )
          }
          segmentIndex += 1
        }

        "cooldown" -> {
          val (low, high) = resolveWarmupCooldownPower(element, ftpWatts)
          val cadenceRange = getCadenceRange(element)
          val duration = element.getAttrNumber("Duration").roundToInt()
          if (low != high) {
            segments.addRamp(
              id = segmentIndex,
              label = "Cooldown",
              durationSec = duration,
              startPower = low,
              endPower = high,
              phase = SegmentPhase.COOLDOWN,
              isWork = false,
              cadenceRange = cadenceRange
            )
          } else {
            segments.addStep(
              id = segmentIndex,
              label = "Cooldown",
              durationSec = duration,
              targetRange = TargetRange(low, high),
              phase = SegmentPhase.COOLDOWN,
              isWork = false,
              cadenceRange = cadenceRange
            )
          }
          segmentIndex += 1
        }

        "ramp" -> {
          val duration = element.getAttrNumber("Duration").roundToInt()
          val low = toWatts(element.getAttrNumber("PowerLow"), ftpWatts)
          val high = toWatts(element.getAttrNumber("PowerHigh"), ftpWatts)
          val isWork = high >= ftpWatts * WORK_THRESHOLD
          val phase = if (isWork) SegmentPhase.WORK else SegmentPhase.RECOVERY
          val cadenceRange = getCadenceRange(element)
          segments.addRamp(
            id = segmentIndex,
            label = "Ramp",
            durationSec = duration,
            startPower = low,
            endPower = high,
            phase = phase,
            isWork = isWork,
            cadenceRange = cadenceRange
          )
          segmentIndex += 1
        }

        "steadystate" -> {
          val duration = element.getAttrNumber("Duration").roundToInt()
          val (resolvedLow, resolvedHigh) = resolveSteadyStatePower(element)
          val lowWatts = toWatts(resolvedLow, ftpWatts)
          val highWatts = toWatts(resolvedHigh, ftpWatts)
          val rangeLow = minOf(lowWatts, highWatts)
          val rangeHigh = maxOf(lowWatts, highWatts)
          val targetWatts = ((rangeLow + rangeHigh) / 2.0).roundToInt()
          val isWork = targetWatts >= ftpWatts * WORK_THRESHOLD
          val phase = if (isWork) SegmentPhase.WORK else SegmentPhase.RECOVERY
          val label = if (isWork) {
            intervalCount += 1
            "Interval $intervalCount"
          } else {
            recoveryCount += 1
            "Steady $recoveryCount"
          }
          val cadenceRange = getCadenceRange(element)
          if (rangeLow != rangeHigh) {
            segments.addRamp(
              id = segmentIndex,
              label = label,
              durationSec = duration,
              startPower = rangeLow,
              endPower = rangeHigh,
              phase = phase,
              isWork = isWork,
              cadenceRange = cadenceRange
            )
          } else {
            segments.addStep(
              id = segmentIndex,
              label = label,
              durationSec = duration,
              targetRange = TargetRange(rangeLow, rangeHigh),
              phase = phase,
              isWork = isWork,
              cadenceRange = cadenceRange
            )
          }
          segmentIndex += 1
        }

        "freeride" -> {
          val duration = element.getAttrNumber("Duration").roundToInt()
          val powerLow = element.getOptionalAttrNumber("PowerLow")
          val power = element.getOptionalAttrNumber("Power")
          val powerHigh = element.getOptionalAttrNumber("PowerHigh")
          val low = when {
            powerLow != null -> toWatts(powerLow, ftpWatts)
            power != null -> toWatts(power, ftpWatts)
            else -> toWatts(0.55, ftpWatts)
          }
          val high = if (powerHigh != null) toWatts(powerHigh, ftpWatts) else low
          val rangeLow = minOf(low, high)
          val rangeHigh = maxOf(low, high)
          val cadenceRange = getCadenceRange(element)
          if (rangeLow != rangeHigh) {
            segments.addRamp(
              id = segmentIndex,
              label = "Free Ride",
              durationSec = duration,
              startPower = rangeLow,
              endPower = rangeHigh,
              phase = SegmentPhase.RECOVERY,
              isWork = false,
              cadenceRange = cadenceRange
            )
          } else {
            segments.addStep(
              id = segmentIndex,
              label = "Free Ride",
              durationSec = duration,
              targetRange = TargetRange(rangeLow, rangeHigh),
              phase = SegmentPhase.RECOVERY,
              isWork = false,
              cadenceRange = cadenceRange
            )
          }
          segmentIndex += 1
        }

        "intervalst" -> {
          val repeat = maxOf(1, element.getAttrNumber("Repeat", 1.0).roundToInt())
          val onDuration = element.getAttrNumber("OnDuration").roundToInt()
          val offDuration = element.getAttrNumber("OffDuration").roundToInt()
          val onPower = toWatts(element.getAttrNumber("OnPower"), ftpWatts)
          val offPower = toWatts(element.getAttrNumber("OffPower"), ftpWatts)
          val cadenceRange = getCadenceRange(element)
          repeat(repeat) {
            intervalCount += 1
            segments.addStep(
              id = segmentIndex,
              label = "Interval $intervalCount",
              durationSec = onDuration,
              targetRange = TargetRange(onPower, onPower),
              phase = SegmentPhase.WORK,
              isWork = true,
              cadenceRange = cadenceRange
            )
            segmentIndex += 1
            recoveryCount += 1
            segments.addStep(
              id = segmentIndex,
              label = "Recovery $recoveryCount",
              durationSec = offDuration,
              targetRange = TargetRange(offPower, offPower),
              phase = SegmentPhase.RECOVERY,
              isWork = false,
              cadenceRange = cadenceRange
            )
            segmentIndex += 1
          }
        }
      }
    }

    if (segments.isEmpty()) {
      throw IllegalArgumentException("No workout steps found in this ZWO file.")
    }

    return Workout(
      id = name.substringBeforeLast('.').trim(),
      name = workoutName,
      description = description,
      source = WorkoutSource.IMPORTED,
      segments = segments
    )
  }

  private fun resolveWarmupCooldownPower(element: Element, ftpWatts: Int): Pair<Int, Int> {
    val powerLow = element.getOptionalAttrNumber("PowerLow")
    val powerHigh = element.getOptionalAttrNumber("PowerHigh")
    val power = element.getOptionalAttrNumber("Power")
    return when {
      powerLow != null && powerHigh != null -> {
        toWatts(powerLow, ftpWatts) to toWatts(powerHigh, ftpWatts)
      }
      power != null -> {
        val watts = toWatts(power, ftpWatts)
        watts to watts
      }
      powerLow != null -> {
        val watts = toWatts(powerLow, ftpWatts)
        watts to watts
      }
      powerHigh != null -> {
        val watts = toWatts(powerHigh, ftpWatts)
        watts to watts
      }
      else -> throw IllegalArgumentException(
        "Missing Power, PowerLow, or PowerHigh attribute in ${element.tagName}."
      )
    }
  }

  private fun resolveSteadyStatePower(element: Element): Pair<Double, Double> {
    val power = element.getOptionalAttrNumber("Power")
    val powerLow = element.getOptionalAttrNumber("PowerLow")
    val powerHigh = element.getOptionalAttrNumber("PowerHigh")
    return when {
      power != null -> power to power
      powerLow != null && powerHigh != null -> powerLow to powerHigh
      powerLow != null -> powerLow to powerLow
      powerHigh != null -> powerHigh to powerHigh
      else -> throw IllegalArgumentException(
        "Missing Power, PowerLow, or PowerHigh attribute in SteadyState."
      )
    }
  }

  private fun getCadenceRange(element: Element): IntRange? {
    val cadenceLow = element.getOptionalAttrNumber("CadenceLow")
    val cadenceHigh = element.getOptionalAttrNumber("CadenceHigh")
    if (cadenceLow != null || cadenceHigh != null) {
      val low = cadenceLow ?: cadenceHigh!!
      val high = cadenceHigh ?: cadenceLow!!
      return IntRange(minOf(low.roundToInt(), high.roundToInt()), maxOf(low.roundToInt(), high.roundToInt()))
    }
    val cadence = element.getOptionalAttrNumber("Cadence")
    if (cadence != null) {
      val value = cadence.roundToInt()
      return IntRange(value, value)
    }
    return null
  }

  private fun toWatts(value: Double, ftpWatts: Int): Int {
    return if (value > 10) value.roundToInt() else (value * ftpWatts).roundToInt()
  }

  private fun MutableList<WorkoutSegment>.addStep(
    id: Int,
    label: String,
    durationSec: Int,
    targetRange: TargetRange,
    phase: SegmentPhase,
    isWork: Boolean,
    cadenceRange: IntRange?
  ) {
    add(
      WorkoutSegment.Step(
        id = "segment-${id + 1}",
        durationSec = durationSec,
        label = label,
        phase = phase,
        isWork = isWork,
        targetRange = targetRange,
        targetCadence = cadenceRange
      )
    )
  }

  private fun MutableList<WorkoutSegment>.addRamp(
    id: Int,
    label: String,
    durationSec: Int,
    startPower: Int,
    endPower: Int,
    phase: SegmentPhase,
    isWork: Boolean,
    cadenceRange: IntRange?
  ) {
    add(
      WorkoutSegment.Ramp(
        id = "segment-${id + 1}",
        durationSec = durationSec,
        label = label,
        phase = phase,
        isWork = isWork,
        startPower = startPower,
        endPower = endPower,
        targetCadence = cadenceRange
      )
    )
  }

  private fun Element.getAttrNumber(name: String, fallback: Double? = null): Double {
    val value = getAttribute(name)
    if (value.isNullOrEmpty()) {
      if (fallback != null) return fallback
      throw IllegalArgumentException("Missing $name attribute in $tagName.")
    }
    return value.toDoubleOrNull()
      ?: throw IllegalArgumentException("Invalid $name attribute in $tagName.")
  }

  private fun Element.getOptionalAttrNumber(name: String): Double? {
    val value = getAttribute(name)
    if (value.isNullOrEmpty()) return null
    return value.toDoubleOrNull()
      ?: throw IllegalArgumentException("Invalid $name attribute in $tagName.")
  }
}
