package com.trainerloop.ui.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutImporter

data class ImportedWorkout(
  val fileName: String,
  val workout: Workout
)

object WorkoutImportHelper {

  val SUPPORTED_MIME_TYPES = arrayOf(
    "*/*"
  )

  suspend fun importWorkout(context: Context, uri: Uri, ftp: Int = 250): ImportedWorkout? {
    return try {
      val inputStream = context.contentResolver.openInputStream(uri) ?: return null
      val content = inputStream.bufferedReader().use { it.readText() }
      val fileName = getFileName(context, uri) ?: "imported"
      val workout = WorkoutImporter.import(fileName, content, ftp)
      ImportedWorkout(fileName = fileName, workout = workout)
    } catch (e: Exception) {
      null
    }
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
