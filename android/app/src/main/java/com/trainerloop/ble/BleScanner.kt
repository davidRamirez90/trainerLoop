package com.trainerloop.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.trainerloop.ble.model.BleDevice
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleScanner(context: Context) {

  private val scanner: BluetoothLeScanner? by lazy {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    bluetoothManager?.adapter?.bluetoothLeScanner
  }

  private var activeCallback: ScanCallback? = null

  fun scan(services: List<UUID>, durationMs: Long = 10_000): Flow<List<BleDevice>> {
    val scannerRef = scanner ?: return emptyFlow()
    return callbackFlow {
      val devices = mutableMapOf<String, BleDevice>()

      val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          addResult(devices, result)
          trySend(devices.values.toList())
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
          results.forEach { addResult(devices, it) }
          trySend(devices.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
          cancel("BLE scan failed with error code $errorCode")
        }
      }

      activeCallback = callback

      val filters = if (services.isEmpty()) {
        emptyList()
      } else {
        services.map { uuid ->
          ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(uuid))
            .build()
        }
      }

      val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

      scannerRef.startScan(filters, settings, callback)

      val timeoutJob = launch {
        kotlinx.coroutines.delay(durationMs)
        close()
      }

      awaitClose {
        timeoutJob.cancel()
        scannerRef.stopScan(callback)
        activeCallback = null
      }
    }
  }

  fun stopScan() {
    activeCallback?.let { callback ->
      scanner?.stopScan(callback)
      activeCallback = null
    }
  }

  private fun addResult(devices: MutableMap<String, BleDevice>, result: ScanResult) {
    val device = result.device
    val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
    devices[device.address] = BleDevice(
      address = device.address,
      name = device.name ?: result.scanRecord?.deviceName,
      services = uuids,
      rssi = result.rssi
    )
  }
}
