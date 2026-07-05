package com.trainerloop.ble

import android.util.Log
import com.trainerloop.app.BuildConfig

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

  // Debug logs are per-BLE-packet on the hot path. Gate on DEBUG and take a
  // lambda so the message (e.g. toHex() over every byte) is never built in
  // release. `d(String)` kept for convenience but also DEBUG-gated.
  inline fun d(msg: () -> String) { if (BuildConfig.DEBUG) safe { Log.d(TAG, msg()) } }
  fun d(msg: String) { if (BuildConfig.DEBUG) safe { Log.d(TAG, msg) } }
  fun i(msg: String) { safe { Log.i(TAG, msg) } }
  fun w(msg: String, t: Throwable? = null) { safe { Log.w(TAG, msg, t) } }
  fun e(msg: String, t: Throwable? = null) { safe { Log.e(TAG, msg, t) } }

  @PublishedApi
  internal inline fun safe(block: () -> Unit) {
    try { block() } catch (_: Throwable) { /* swallowed */ }
  }
}
