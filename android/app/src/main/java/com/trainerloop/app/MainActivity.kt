package com.trainerloop.app

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import com.trainerloop.ble.BlePermissions
import com.trainerloop.ui.TrainerLoopApp
import com.trainerloop.ui.theme.TrainerLoopTheme

class MainActivity : ComponentActivity() {

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { }

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    val allGranted = results.all { it.value }
    if (allGranted && !BlePermissions.isLocationEnabled(this)) {
      // Location services are still disabled on Android 11; the connect screen
      // will surface this to the user before scanning.
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    )

    if (!BlePermissions.hasPermissions(this)) {
      permissionLauncher.launch(BlePermissions.REQUIRED)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    setContent {
      TrainerLoopTheme {
        TrainerLoopApp()
      }
    }
  }
}
