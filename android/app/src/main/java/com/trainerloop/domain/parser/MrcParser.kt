package com.trainerloop.domain.parser

import com.trainerloop.data.model.Workout

object MrcParser {
  fun parse(name: String, content: String, ftpWatts: Int = 250): Workout {
    return ErgMrcShared.parse(content, name, ftpWatts)
  }
}
