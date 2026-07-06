package com.trainerloop.data.repository

import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.fit.FitEncoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Rebuilds a FIT file from a stored session and uploads it to intervals.icu,
 * stamping [SessionData.icuSyncedAt] on success. Shared by the post-workout
 * auto-upload and the manual retry on the session detail screen.
 */
class IcuActivityUploader(
  private val sessionRepository: SessionRepository,
  private val upload: suspend (fitBytes: ByteArray, name: String) -> Boolean,
  private val nowIso: () -> String = { Instant.now().toString() }
) {

  suspend fun uploadSession(session: SessionData): Boolean {
    val samples: List<TelemetrySample> = try {
      Json.decodeFromString(ListSerializer(TelemetrySample.serializer()), session.samplesJson)
    } catch (_: Exception) {
      emptyList()
    }
    if (samples.isEmpty()) return false

    val startTimeMs = try {
      Instant.parse(session.startedAt).toEpochMilli()
    } catch (_: Exception) {
      return false
    }

    val fitBytes = FitEncoder.encode(
      startTimeMs = startTimeMs,
      elapsedSec = session.durationSec,
      samples = samples
    )

    val ok = try {
      upload(fitBytes, session.workoutName)
    } catch (_: Exception) {
      false
    }
    if (ok) sessionRepository.markIcuSynced(session.id, nowIso())
    return ok
  }
}
