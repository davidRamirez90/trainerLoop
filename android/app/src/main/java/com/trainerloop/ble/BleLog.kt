package com.trainerloop.ble

import android.util.Log

/**
 * Thin wrapper around android.util.Log so the whole BLE pipeline logs under
 * a single tag. Filter on the device with:
 *
 *   adb logcat -s TrainerLoopBle:V
 *
 * The wrapper is defensive: if `android.util.Log` ever throws (e.g. when
 * invoked from a JVM unit test that didn't enable
 * `testOptions.unitTests.isReturnDefaultValues`), the call is swallowed
 * rather than crashing the BLE pipeline.
 */
object BleLog {
  const val TAG = "TrainerLoopBle"

  fun d(msg: String) { safe { Log.d(TAG, msg) } }
  fun i(msg: String) { safe { Log.i(TAG, msg) } }
  fun w(msg: String, t: Throwable? = null) { safe { Log.w(TAG, msg, t) } }
  fun e(msg: String, t: Throwable? = null) { safe { Log.e(TAG, msg, t) } }

  private inline fun safe(block: () -> Unit) {
    try { block() } catch (_: Throwable) { /* swallowed */ }
  }
}
