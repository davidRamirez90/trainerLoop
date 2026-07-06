package com.trainerloop.data.model

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every shipped coach-profile asset must parse into CoachProfile. */
class CoachProfileAssetsTest {

  @Test
  fun `all bundled profiles parse`() {
    val json = Json { ignoreUnknownKeys = true }
    val dir = File("src/main/assets/coach_profiles")
    val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
    assertEquals(6, files.size)
    files.forEach { f ->
      val profile = json.decodeFromString(CoachProfile.serializer(), f.readText())
      assertEquals(f.nameWithoutExtension, profile.id)
      assertTrue(f.name, profile.messages.suggestions.isNotEmpty())
    }
  }
}
