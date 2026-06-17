package com.trainerloop.ble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object BlePermissions {

  val REQUIRED: Array<String>
    get() = when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
      )
      else -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }

  fun hasPermissions(context: Context): Boolean {
    return REQUIRED.all { permission ->
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun shouldShowRationale(activity: Activity): Boolean {
    return REQUIRED.any { permission ->
      ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
  }

  fun request(activity: Activity, requestCode: Int = REQUEST_CODE) {
    ActivityCompat.requestPermissions(activity, REQUIRED, requestCode)
  }

  fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return false
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
      locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
  }

  fun canScan(context: Context): Boolean {
    if (!hasPermissions(context)) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled(context)) return false
    return true
  }

  private const val REQUEST_CODE = 1001
}
