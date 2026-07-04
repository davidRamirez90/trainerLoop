package com.trainerloop.domain

import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.model.IndoorBikeData
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
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
  data class DataProvider(
    val data: StateFlow<IndoorBikeData?>,
    val heartRate: StateFlow<Int?>
  )

  constructor(
    clock: WorkoutClock,
    ftms: FtmsManager,
    hr: HrManager? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
  ) : this(
    clock,
    DataProvider(
      data = ftms.data,
      heartRate = hr?.heartRate ?: MutableStateFlow<Int?>(null).asStateFlow()
    ),
    dispatcher
  )

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  private val _latest = MutableStateFlow(
    TelemetrySample(timeSec = 0, powerWatts = 0, cadenceRpm = 0, hrBpm = 0, dropout = true)
  )
  val latest: StateFlow<TelemetrySample> = _latest.asStateFlow()

  private val _samples = MutableStateFlow<List<TelemetrySample>>(emptyList())
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
        val dropout = ftmsData == null

        if (ftmsData != null) {
          lastDataReceivedAtSec = elapsedSec
          ftmsData.powerWatts?.let { lastPowerWatts = it }
          ftmsData.cadenceRpm?.let { lastCadenceRpm = it.toInt() }
        }
        hrBpm?.let { lastHrBpm = it }

        val sample = TelemetrySample(
          timeSec = elapsedSec,
          powerWatts = lastPowerWatts,
          cadenceRpm = lastCadenceRpm,
          hrBpm = lastHrBpm,
          dropout = dropout,
          lagCompensated = false
        )

        _latest.value = sample

        // Skip time 0 (initial flow values) and dedup by timeSec
        if (elapsedSec > 0) {
          val existing = _samples.value
          if (existing.isEmpty() || existing.last().timeSec < elapsedSec) {
            _samples.value = existing + sample
          }
        }
        com.trainerloop.ble.BleLog.d(
          "tick t=${elapsedSec}s p=${sample.powerWatts} c=${sample.cadenceRpm} " +
            "hr=${sample.hrBpm} dropout=${sample.dropout}"
        )
      }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  fun reset(sessionId: Int) {
    com.trainerloop.ble.BleLog.d("TelemetryRecorder.reset sessionId=$sessionId")
    _samples.value = emptyList()
    lastDataReceivedAtSec = null
    lastPowerWatts = 0
    lastCadenceRpm = 0
    lastHrBpm = 0
    _latest.value = TelemetrySample(
      timeSec = 0, powerWatts = 0, cadenceRpm = 0, hrBpm = 0, dropout = true
    )
  }

  fun stop() {
    collecting = false
    scope.cancel()
  }
}
