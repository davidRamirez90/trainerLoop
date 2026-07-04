package com.trainerloop.data.source.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

@Serializable
data class IntervalsIcuAthlete(
  val ftp: Int? = null,
  val icu_weight: Double? = null
)

@Serializable
data class IntervalsIcuEvent(
  val id: Long,
  val name: String? = null,
  val category: String? = null
)

/** Thin client for the intervals.icu REST API — HttpURLConnection, no new HTTP dependency. */
class IntervalsIcuClient(private val apiKey: String) {

  private val json = Json { ignoreUnknownKeys = true }

  suspend fun getAthlete(athleteId: String): IntervalsIcuAthlete = withContext(Dispatchers.IO) {
    val body = request("GET", "/api/v1/athlete/$athleteId")
    json.decodeFromString(IntervalsIcuAthlete.serializer(), body)
  }

  suspend fun getTodaysWorkoutEvents(athleteId: String, date: String): List<IntervalsIcuEvent> =
    withContext(Dispatchers.IO) {
      val body = request(
        "GET",
        "/api/v1/athlete/$athleteId/events?oldest=$date&newest=$date&category=WORKOUT"
      )
      json.decodeFromString(ListSerializer(IntervalsIcuEvent.serializer()), body)
    }

  suspend fun downloadZwo(athleteId: String, eventId: Long): String = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/athlete/$athleteId/events/$eventId/download.zwo")
  }

  suspend fun uploadActivity(athleteId: String, fitBytes: ByteArray, name: String): Boolean =
    withContext(Dispatchers.IO) {
      val boundary = "TrainerLoopBoundary${System.currentTimeMillis()}"
      val conn = openConnection("POST", "/api/v1/athlete/$athleteId/activities")
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

      conn.outputStream.use { out ->
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$name.fit\"\r\n".toByteArray())
        out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
        out.write(fitBytes)
        out.write("\r\n--$boundary--\r\n".toByteArray())
      }

      val ok = conn.responseCode in 200..299
      conn.readBody()
      ok
    }

  private fun request(method: String, path: String): String {
    val conn = openConnection(method, path)
    return conn.readBody()
  }

  private fun openConnection(method: String, path: String): HttpURLConnection {
    val conn = URL("https://intervals.icu$path").openConnection() as HttpURLConnection
    conn.requestMethod = method
    val credentials = Base64.getEncoder().encodeToString("API_KEY:$apiKey".toByteArray())
    conn.setRequestProperty("Authorization", "Basic $credentials")
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    return conn
  }

  private fun HttpURLConnection.readBody(): String {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    return stream?.bufferedReader()?.use { it.readText() } ?: ""
  }
}
