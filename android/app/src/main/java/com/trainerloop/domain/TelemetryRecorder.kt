package com.trainerloop.domain

import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.Stamped
import com.trainerloop.data.model.TelemetrySample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TelemetryRecorder(
  private val clock: WorkoutClock,
  private val dataProvider: DataProvider,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val stamper: com.trainerloop.domain.sim.SampleStamper? = null,
  private val now: () -> Long = android.os.SystemClock::elapsedRealtime,
  initialSamples: List<TelemetrySample> = emptyList()
) {
  data class DataProvider(
    val data: StateFlow<Stamped<IndoorBikeData>?>,
    val heartRate: StateFlow<Stamped<Int>?>
  )

  constructor(
    clock: WorkoutClock,
    ftms: FtmsManager,
    hr: HrManager? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    stamper: com.trainerloop.domain.sim.SampleStamper? = null,
    now: () -> Long = android.os.SystemClock::elapsedRealtime,
    initialSamples: List<TelemetrySample> = emptyList()
  ) : this(
    clock,
    DataProvider(
      data = ftms.data,
      heartRate = hr?.heartRate
        ?: MutableStateFlow<Stamped<Int>?>(null).asStateFlow()
    ),
    dispatcher,
    stamper,
    now,
    initialSamples
  )

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  private val _latest = MutableStateFlow(
    TelemetrySample(timeSec = 0, powerWatts = 0, cadenceRpm = 0, hrBpm = 0, dropout = true)
  )
  val latest: StateFlow<TelemetrySample> = _latest.asStateFlow()

  private val buffer = ArrayList(initialSamples)
  private val _samples = MutableStateFlow(initialSamples)
  val samples: StateFlow<List<TelemetrySample>> = _samples.asStateFlow()

  private var lastDataReceivedAtSec: Int? = null
  private var lastPowerWatts: Int = 0
  private var lastCadenceRpm: Int = 0
  private var lastHrBpm: Int = 0
  private var collecting = false

  fun startCollecting() {
    if (collecting) return
    collecting = true
    com.trainerloop.ble.BleLog.d("TelemetryRecorder.startCollecting")

    scope.launch {
      combine(
        clock.elapsedSec,
        dataProvider.data,
        dataProvider.heartRate
      ) { elapsedSec, ftmsData, hrBpm ->
        Triple(elapsedSec, ftmsData, hrBpm)
      }.collect { (elapsedSec, ftmsData, hrBpm) ->
        val nowMs = now()
        val ftmsFresh = ftmsData != null && nowMs - ftmsData.atMs <= STALE_AFTER_MS
        val hrFresh = hrBpm != null && nowMs - hrBpm.atMs <= HR_STALE_AFTER_MS
        val dropout = !ftmsFresh

        if (ftmsFresh) {
          lastDataReceivedAtSec = elapsedSec
          ftmsData!!.value.powerWatts?.let { lastPowerWatts = it }
          ftmsData.value.cadenceRpm?.let { lastCadenceRpm = it.toInt() }
        }
        if (hrFresh) lastHrBpm = hrBpm!!.value

        val virtual = stamper?.stamp(elapsedSec, lastPowerWatts, lastCadenceRpm, dropout)
        val sample = TelemetrySample(
          timeSec = elapsedSec,
          powerWatts = lastPowerWatts,
          cadenceRpm = lastCadenceRpm,
          hrBpm = if (hrFresh) lastHrBpm else 0,
          dropout = dropout,
          lagCompensated = false,
          virtualSpeedKph = virtual?.speedKph,
          virtualDistanceM = virtual?.distanceM,
          virtualAltitudeM = virtual?.altitudeM,
          gradePercent = virtual?.gradePercent,
          positionLat = virtual?.lat,
          positionLon = virtual?.lon
        )

        _latest.value = sample

        // Skip time 0 (initial flow values) and dedup by timeSec
        if (elapsedSec > 0) {
          if (buffer.isEmpty() || buffer.last().timeSec < elapsedSec) {
            buffer.add(sample)
            if (elapsedSec % SNAPSHOT_EVERY_SEC == 0) {
              _samples.value = buffer.toList()
            }
          }
        }
        com.trainerloop.ble.BleLog.d {
          "tick t=${elapsedSec}s p=${sample.powerWatts} c=${sample.cadenceRpm} " +
            "hr=${sample.hrBpm} dropout=${sample.dropout}"
        }
      }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  fun reset(sessionId: Int) {
    com.trainerloop.ble.BleLog.d("TelemetryRecorder.reset sessionId=$sessionId")
    buffer.clear()
    _samples.value = emptyList()
    lastDataReceivedAtSec = null
    lastPowerWatts = 0
    lastCadenceRpm = 0
    lastHrBpm = 0
    _latest.value = TelemetrySample(
      timeSec = 0, powerWatts = 0, cadenceRpm = 0, hrBpm = 0, dropout = true
    )
  }

  fun flush() {
    _samples.value = buffer.toList()
  }

  fun stop() {
    collecting = false
    scope.cancel()
  }

  companion object {
    const val STALE_AFTER_MS = 3_000L
    const val HR_STALE_AFTER_MS = 5_000L
    private const val SNAPSHOT_EVERY_SEC = 5
  }
}
