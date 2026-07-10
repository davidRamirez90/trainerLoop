package com.trainerloop.data.source.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.net.URLEncoder

class IntervalsIcuHttpException(
  val statusCode: Int,
  val responseBody: String
) : Exception("intervals.icu returned HTTP $statusCode")

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
class IntervalsIcuClient(
  private val apiKey: String,
  private val baseUrl: String = "https://intervals.icu"
) {

  private val json = Json { ignoreUnknownKeys = true }

  suspend fun getAthlete(athleteId: String): IntervalsIcuAthlete = withContext(Dispatchers.IO) {
    val body = request("GET", "/api/v1/athlete/${pathSegment(athleteId)}")
    json.decodeFromString(IntervalsIcuAthlete.serializer(), body)
  }

  suspend fun getTodaysWorkoutEvents(athleteId: String, date: String): List<IntervalsIcuEvent> =
    withContext(Dispatchers.IO) {
      val body = request(
        "GET",
        "/api/v1/athlete/${pathSegment(athleteId)}/events?oldest=${queryParam(date)}&newest=${queryParam(date)}&category=WORKOUT"
      )
      json.decodeFromString(ListSerializer(IntervalsIcuEvent.serializer()), body)
    }

  suspend fun downloadZwo(athleteId: String, eventId: Long): String = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/athlete/${pathSegment(athleteId)}/events/$eventId/download.zwo")
  }

  suspend fun uploadActivity(athleteId: String, fitBytes: ByteArray, name: String): Boolean =
    withContext(Dispatchers.IO) {
      val boundary = "TrainerLoopBoundary${System.currentTimeMillis()}"
      val conn = openConnection("POST", "/api/v1/athlete/${pathSegment(athleteId)}/activities")
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

      conn.outputStream.use { out ->
        out.write("--$boundary\r\n".toByteArray())
        val safeName = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$safeName.fit\"\r\n".toByteArray())
        out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
        out.write(fitBytes)
        out.write("\r\n--$boundary--\r\n".toByteArray())
      }

      val code = conn.responseCode
      val body = conn.readBody()
      isUploadAccepted(code, body)
    }

  suspend fun updateFtp(athleteId: String, ftp: Int): Boolean = withContext(Dispatchers.IO) {
    val conn = openConnection("PUT", "/api/v1/athlete/${pathSegment(athleteId)}")
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.use { it.write("""{"ftp":$ftp}""".toByteArray()) }
    val ok = conn.responseCode in 200..299
    conn.readBody()
    ok
  }

  private fun request(method: String, path: String): String {
    val conn = openConnection(method, path)
    val code = conn.responseCode
    val body = conn.readBody()
    if (code !in 200..299) throw IntervalsIcuHttpException(code, body)
    return body
  }

  private fun pathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

  private fun queryParam(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

  private fun openConnection(method: String, path: String): HttpURLConnection {
    val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
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

  companion object {
    /** 2xx = uploaded; 422 mentioning "duplicate" = already on the server, equally fine. */
    fun isUploadAccepted(code: Int, body: String): Boolean =
      code in 200..299 || (code == 422 && body.contains("duplicate", ignoreCase = true))
  }
}
