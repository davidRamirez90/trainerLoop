package com.trainerloop.ui.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutImporter
import java.io.InputStream

data class ImportedWorkout(
  val fileName: String,
  val workout: Workout
)

object WorkoutImportHelper {

  const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

  val SUPPORTED_MIME_TYPES = arrayOf(
    "*/*"
  )

  suspend fun importWorkout(context: Context, uri: Uri, ftp: Int): ImportedWorkout? {
    return try {
      val inputStream = context.contentResolver.openInputStream(uri) ?: return null
      val content = inputStream.buffered().use { stream ->
        val bytes = readCapped(stream)
        if (bytes.size > MAX_IMPORT_BYTES) return null
        bytes.decodeToString()
      }
      val fileName = getFileName(context, uri) ?: "imported"
      val workout = WorkoutImporter.import(fileName, content, ftp)
      ImportedWorkout(fileName = fileName, workout = workout)
    } catch (e: Exception) {
      null
    }
  }

  private fun readCapped(inputStream: InputStream): ByteArray {
    val bytes = ByteArray(MAX_IMPORT_BYTES + 1)
    var size = 0
    while (size < bytes.size) {
      val read = inputStream.read(bytes, size, bytes.size - size)
      if (read < 0) break
      size += read
    }
    return bytes.copyOf(size)
  }

  private fun getFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
      if (it.moveToFirst()) {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0) it.getString(nameIndex) else null
      } else null
    }
  }
}
