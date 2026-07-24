package com.trainerloop.domain.telemetry

import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.Stamped
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.TargetRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class TelemetryScenario(val label: String, val description: String) {
  GOOD_ADHERENCE(
    "Good adherence + stable HR",
    "Tracks target power with steady cadence and heart rate."
  ),
  LOW_CADENCE_HR_DRIFT(
    "Low cadence + HR drift",
    "Holds power while cadence falls and heart rate rises."
  ),
  HIGH_RECOVERY_HR(
    "Recovery HR remains high",
    "Keeps heart rate elevated through recovery segments."
  ),
  REPEATED_FATIGUE(
    "Repeated fatigue",
    "Repeats low-power, low-cadence, high-HR fatigue conditions."
  ),
  SENSOR_DROPOUT(
    "Sensor dropouts",
    "Drops power and cadence notifications for five seconds at a time."
  )
}

object TelemetryScenarioGenerator {
  fun sample(
    scenario: TelemetryScenario,
    elapsedSec: Int,
    targetRange: TargetRange,
    phase: SegmentPhase
  ): TelemetrySample {
    val target = ((targetRange.low + targetRange.high) / 2).coerceAtLeast(100)
    val deterministicNoise = ((elapsedSec % 5) - 2) * 2

    return when (scenario) {
      TelemetryScenario.GOOD_ADHERENCE -> TelemetrySample(
        timeSec = elapsedSec,
        powerWatts = (target + deterministicNoise).coerceAtLeast(0),
        cadenceRpm = 88 + (elapsedSec % 3),
        hrBpm = 132 + (elapsedSec / 45).coerceAtMost(10)
      )

      TelemetryScenario.LOW_CADENCE_HR_DRIFT -> {
        val fatigueSec = (elapsedSec - 10).coerceAtLeast(0)
        TelemetrySample(
          timeSec = elapsedSec,
          powerWatts = (target + deterministicNoise).coerceAtLeast(0),
          cadenceRpm = (88 - fatigueSec).coerceAtLeast(52),
          hrBpm = 138 + (fatigueSec / 3).coerceAtMost(32)
        )
      }

      TelemetryScenario.HIGH_RECOVERY_HR -> TelemetrySample(
        timeSec = elapsedSec,
        powerWatts = (target + deterministicNoise).coerceAtLeast(0),
        cadenceRpm = if (phase == SegmentPhase.RECOVERY) 76 else 88,
        hrBpm = if (phase == SegmentPhase.RECOVERY) 168 else 154
      )

      TelemetryScenario.REPEATED_FATIGUE -> {
        val cycleSec = elapsedSec % 45
        val fatigued = cycleSec >= 10
        TelemetrySample(
          timeSec = elapsedSec,
          powerWatts = if (fatigued) (target * 0.68).roundToInt() else target,
          cadenceRpm = if (fatigued) 54 else 86,
          hrBpm = if (fatigued) 176 else 148
        )
      }

      TelemetryScenario.SENSOR_DROPOUT -> {
        val dropout = elapsedSec > 0 && elapsedSec % 20 in 15..19
        TelemetrySample(
          timeSec = elapsedSec,
          powerWatts = if (dropout) 0 else target + deterministicNoise,
          cadenceRpm = if (dropout) 0 else 87,
          hrBpm = 145,
          dropout = dropout
        )
      }
    }
  }
}

class ScenarioTelemetryProvider(
  val scenario: TelemetryScenario,
  private val now: () -> Long = android.os.SystemClock::elapsedRealtime
) : TelemetryInputProvider {
  override val replacesCurrentTick: Boolean = true

  private val _data = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
  override val data: StateFlow<Stamped<IndoorBikeData>?> = _data.asStateFlow()

  private val _heartRate = MutableStateFlow<Stamped<Int>?>(null)
  override val heartRate: StateFlow<Stamped<Int>?> = _heartRate.asStateFlow()

  override fun update(context: TelemetryInputContext) {
    val sample = TelemetryScenarioGenerator.sample(
      scenario = scenario,
      elapsedSec = context.elapsedSec,
      targetRange = context.targetRange,
      phase = context.phase
    )
    val atMs = now()
    _data.value = if (sample.dropout) {
      null
    } else {
      Stamped(
        IndoorBikeData(
          powerWatts = sample.powerWatts,
          cadenceRpm = sample.cadenceRpm.toDouble(),
          speedKph = null,
          resistanceLevel = null,
          averagePower = null,
          averageSpeed = null,
          totalDistanceMeters = null,
          heartRateBpm = sample.hrBpm,
          elapsedTimeSec = sample.timeSec,
          remainingTimeSec = null
        ),
        atMs
      )
    }
    _heartRate.value = Stamped(sample.hrBpm, atMs)
  }

  override fun reset() {
    _data.value = null
    _heartRate.value = null
  }
}
