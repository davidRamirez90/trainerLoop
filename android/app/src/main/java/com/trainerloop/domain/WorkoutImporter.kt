package com.trainerloop.domain

import com.trainerloop.data.model.Workout
import com.trainerloop.domain.parser.ErgParser
import com.trainerloop.domain.parser.JsonWorkoutParser
import com.trainerloop.domain.parser.MrcParser
import com.trainerloop.domain.parser.ZwoParser

object WorkoutImporter {

  fun import(fileName: String, content: String, ftpWatts: Int = 250): Workout {
    val trimmed = content.trim()
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when {
      extension == "json" || trimmed.startsWith("{") -> {
        JsonWorkoutParser.parse(fileName, content, ftpWatts)
      }
      extension == "zwo" || trimmed.startsWith("<") -> {
        ZwoParser.parse(fileName, content, ftpWatts)
      }
      extension == "erg" -> ErgParser.parse(fileName, content, ftpWatts)
      extension == "mrc" -> MrcParser.parse(fileName, content, ftpWatts)
      else -> ErgParser.parse(fileName, content, ftpWatts)
    }
  }
}
