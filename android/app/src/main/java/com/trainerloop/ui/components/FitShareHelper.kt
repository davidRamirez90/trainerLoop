package com.trainerloop.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.fit.FitEncoder
import java.io.File

object FitShareHelper {

  private const val FILENAME = "trainer_loop_workout.fit"

  fun createFitFile(
    context: Context,
    startTimeMs: Long,
    elapsedSec: Int,
    samples: List<TelemetrySample>
  ): File {
    val dir = File(context.cacheDir, "fit_exports")
    dir.mkdirs()
    val file = File(dir, FILENAME)
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
    context.startActivity(Intent.createChooser(intent, "Export Workout"))
  }
}
