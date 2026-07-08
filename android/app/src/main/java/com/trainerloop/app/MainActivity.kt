package com.trainerloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.trainerloop.ble.BlePermissions
import com.trainerloop.ui.TrainerLoopApp
import com.trainerloop.ui.theme.TrainerLoopTheme

class MainActivity : ComponentActivity() {

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    val allGranted = results.all { it.value }
    if (allGranted && !BlePermissions.isLocationEnabled(this)) {
      // Location services are still disabled on Android 11; the connect screen
      // will surface this to the user before scanning.
    }
  }

  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
    val handler = trainerLoopApp.volumeShiftHandler
    if (handler != null &&
      (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
    ) {
      handler(keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (!BlePermissions.hasPermissions(this)) {
      permissionLauncher.launch(BlePermissions.REQUIRED)
    }

    setContent {
      TrainerLoopTheme {
        TrainerLoopApp()
      }
    }
  }
}
