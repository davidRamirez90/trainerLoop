package com.trainerloop.data.source.local

import android.content.Context
import com.trainerloop.data.model.CoachProfile
import kotlinx.serialization.json.Json

/** Loads coach personality profiles from the coach_profiles asset directory. */
object CoachProfileLoader {

  private val json = Json { ignoreUnknownKeys = true }
  private const val DIR = "coach_profiles"

  fun listProfiles(context: Context): List<CoachProfile> =
    (context.assets.list(DIR) ?: emptyArray())
      .filter { it.endsWith(".json") }
      .mapNotNull { parse(context, it) }
      .sortedBy { it.name }

  fun load(context: Context, id: String): CoachProfile? =
    parse(context, "$id.json")

  private fun parse(context: Context, fileName: String): CoachProfile? = runCatching {
    context.assets.open("$DIR/$fileName").bufferedReader().use {
      json.decodeFromString(CoachProfile.serializer(), it.readText())
    }
  }.getOrNull()
}
