package com.trainerloop.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.fit.FitEncoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FitShareHelper {

  fun createFitFile(
    context: Context,
    startTimeMs: Long,
    elapsedSec: Int,
    samples: List<TelemetrySample>
  ): File {
    val dir = File(context.filesDir, "fit_exports")
    dir.mkdirs()
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTimeMs))
    val file = File(dir, "trainer_loop_$timestamp.fit")
    val bytes = FitEncoder.encode(
      startTimeMs = startTimeMs,
      elapsedSec = elapsedSec,
      samples = samples
    )
    file.writeBytes(bytes)
    return file
  }

  fun shareFitFile(context: Context, file: File) {
    val authority = "${context.packageName}.fileprovider"
    val uri: Uri = FileProvider.getUriForFile(context, authority, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "application/fit"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val pm = context.packageManager
    val activities = pm.queryIntentActivities(intent, 0)
    if (activities.isEmpty()) {
      intent.type = "application/octet-stream"
    }
    context.startActivity(Intent.createChooser(intent, "Export Workout"))
  }
}
