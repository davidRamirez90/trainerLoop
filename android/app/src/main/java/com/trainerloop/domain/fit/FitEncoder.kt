package com.trainerloop.domain.fit

import com.trainerloop.data.model.TelemetrySample

object FitEncoder {
  private const val FIT_EPOCH_MS = 631065600000L
  private const val FIT_PROTOCOL_VERSION = 0x10
  private const val FIT_PROFILE_VERSION = 0x0100
  private const val FIT_HEADER_SIZE = 14

  private const val BASE_TYPE_ENUM = 0x00
  private const val BASE_TYPE_UINT8 = 0x02
  private const val BASE_TYPE_UINT16 = 0x84
  private const val BASE_TYPE_UINT32 = 0x86
  private const val BASE_TYPE_SINT32 = 0x85

  private const val INVALID_SINT32 = 0x7fffffff
  private val SEMICIRCLES_PER_DEGREE = (1L shl 31) / 180.0

  private const val INVALID_UINT8 = 0xff
  private const val INVALID_UINT16 = 0xffff
  private const val INVALID_UINT32 = 0xffffffff

  private data class FitField(
    val num: Int,
    val size: Int,
    val baseType: Int
  )

  private fun toFitTimestamp(timestampMs: Long): Int =
    kotlin.math.max(0, ((timestampMs - FIT_EPOCH_MS) / 1000).toInt())

  private fun clamp(value: Int, min: Int, max: Int): Int =
    kotlin.math.min(max, kotlin.math.max(min, value))

  private fun clamp(value: Double, min: Double, max: Double): Double =
    kotlin.math.min(max, kotlin.math.max(min, value))

  private fun clamp(value: Long, min: Long, max: Long): Long =
    kotlin.math.min(max, kotlin.math.max(min, value))

  private fun encodeUint16(value: Int): List<Int> = listOf(
    value and 0xff,
    (value shr 8) and 0xff
  )

  private fun encodeUint32(value: Long): List<Int> = listOf(
    (value and 0xff).toInt(),
    ((value shr 8) and 0xff).toInt(),
    ((value shr 16) and 0xff).toInt(),
    ((value shr 24) and 0xff).toInt()
  )

  private fun encodeValue(field: FitField, value: Long?): List<Int> {
    if (field.baseType == BASE_TYPE_SINT32) {
      val v = value ?: INVALID_SINT32
      return encodeUint32(v.toLong() and 0xffffffffL) // two's complement LE
    }
    if (value == null) {
      return when (field.baseType) {
        BASE_TYPE_UINT16 -> encodeUint16(INVALID_UINT16)
        BASE_TYPE_UINT32 -> encodeUint32(INVALID_UINT32.toLong())
        else -> listOf(INVALID_UINT8)
      }
    }

    return when (field.baseType) {
      BASE_TYPE_UINT16 -> {
        val next = clamp(value, 0, INVALID_UINT16.toLong())
        encodeUint16(next.toInt())
      }
      BASE_TYPE_UINT32 -> {
        val next = clamp(value, 0, INVALID_UINT32.toLong())
        encodeUint32(next)
      }
      else -> {
        val next = clamp(value, 0, INVALID_UINT8.toLong())
        if (field.size == 1) listOf(next.toInt()) else List(field.size) { next.toInt() }
      }
    }
  }

  private fun buildDefinitionMessage(
    localType: Int,
    globalMessageNumber: Int,
    fields: List<FitField>
  ): List<Int> {
    val bytes = mutableListOf<Int>()
    bytes.add(0x40 or (localType and 0x0f))
    bytes.add(0x00)
    bytes.add(0x00)
    bytes.add(globalMessageNumber and 0xff)
    bytes.add((globalMessageNumber shr 8) and 0xff)
    bytes.add(fields.size and 0xff)
    fields.forEach { field ->
      bytes.add(field.num and 0xff)
      bytes.add(field.size and 0xff)
      bytes.add(field.baseType and 0xff)
    }
    return bytes
  }

  private fun buildDataMessage(
    localType: Int,
    fields: List<FitField>,
    values: List<Number?>
  ): List<Int> {
    val bytes = mutableListOf<Int>()
    bytes.add(localType and 0x0f)
    fields.forEachIndexed { index, field ->
      bytes.addAll(encodeValue(field, values[index]?.toLong()))
    }
    return bytes
  }

  private val CRC_TABLE = intArrayOf(
    0x0000,
    0xcc01,
    0xd801,
    0x1400,
    0xf001,
    0x3c00,
    0x2800,
    0xe401,
    0xa001,
    0x6c00,
    0x7800,
    0xb401,
    0x5000,
    0x9c01,
    0x8801,
    0x4400
  )

  private fun crc16(bytes: List<Int>): Int {
    var crc = 0
    bytes.forEach { byte ->
      val value = byte and 0xff
      var tmp = CRC_TABLE[crc and 0x0f]
      crc = (crc shr 4) and 0x0fff
      crc = crc xor tmp xor CRC_TABLE[value and 0x0f]
      tmp = CRC_TABLE[crc and 0x0f]
      crc = (crc shr 4) and 0x0fff
      crc = crc xor tmp xor CRC_TABLE[(value shr 4) and 0x0f]
    }
    return crc and 0xffff
  }

  private fun normalizeSamples(samples: List<TelemetrySample>): List<TelemetrySample> {
    val normalized = mutableListOf<TelemetrySample>()
    samples.forEach { sample ->
      val timeSec = kotlin.math.max(0, sample.timeSec)
      val nextSample = sample.copy(timeSec = timeSec)
      val last = normalized.lastOrNull()
      if (last != null && last.timeSec == timeSec) {
        normalized[normalized.size - 1] = nextSample
      } else if (last == null || timeSec > last.timeSec) {
        normalized.add(nextSample)
      }
    }
    return normalized
  }

  private fun computeAverage(
    samples: List<TelemetrySample>,
    selector: (TelemetrySample) -> Int,
    include: (Int) -> Boolean
  ): Double? {
    var sum = 0.0
    var count = 0
    samples.forEach { sample ->
      val value = selector(sample)
      if (!include(value)) return@forEach
      sum += value
      count += 1
    }
    return if (count == 0) null else sum / count
  }

  private fun computeMax(
    samples: List<TelemetrySample>,
    selector: (TelemetrySample) -> Int,
    include: (Int) -> Boolean
  ): Int? {
    var maxValue: Int? = null
    samples.forEach { sample ->
      val value = selector(sample)
      if (!include(value)) return@forEach
      if (maxValue == null || value > maxValue!!) {
        maxValue = value
      }
    }
    return maxValue
  }

  fun encode(
    startTimeMs: Long,
    elapsedSec: Int,
    samples: List<TelemetrySample>,
    sport: Int = 2
  ): ByteArray {
    val normalizedSamples = normalizeSamples(samples)
    val fitStartTimestamp = toFitTimestamp(startTimeMs)
    val lastSampleSec = normalizedSamples.lastOrNull()?.timeSec ?: 0
    val timerSec = kotlin.math.max(0, lastSampleSec)
    val totalElapsedSec = kotlin.math.max(elapsedSec, timerSec)
    val fitEndTimestamp = fitStartTimestamp + totalElapsedSec
    val totalElapsedMs = totalElapsedSec.toLong() * 1000L
    val totalTimerMs = timerSec.toLong() * 1000L

    val avgPower = computeAverage(normalizedSamples, { it.powerWatts }, { true })
    val avgCadence = computeAverage(normalizedSamples, { it.cadenceRpm }, { it > 0 })
    val avgHr = computeAverage(normalizedSamples, { it.hrBpm }, { it > 0 })
    val maxPower = computeMax(normalizedSamples, { it.powerWatts }, { true })
    val maxHr = computeMax(normalizedSamples, { it.hrBpm }, { it > 0 })

    val fileIdFields = listOf(
      FitField(0, 1, BASE_TYPE_ENUM),
      FitField(1, 2, BASE_TYPE_UINT16),
      FitField(2, 2, BASE_TYPE_UINT16),
      FitField(4, 4, BASE_TYPE_UINT32)
    )
    val fileCreatorFields = listOf(
      FitField(0, 2, BASE_TYPE_UINT16),
      FitField(1, 1, BASE_TYPE_UINT8)
    )
    val recordFields = listOf(
      FitField(253, 4, BASE_TYPE_UINT32),
      FitField(7, 2, BASE_TYPE_UINT16),
      FitField(4, 1, BASE_TYPE_UINT8),
      FitField(3, 1, BASE_TYPE_UINT8),
      FitField(6, 2, BASE_TYPE_UINT16),  // speed, m/s * 1000
      FitField(5, 4, BASE_TYPE_UINT32),  // distance, cm
      FitField(2, 2, BASE_TYPE_UINT16),  // altitude, (m + 500) * 5
      FitField(0, 4, BASE_TYPE_SINT32),  // position_lat, semicircles
      FitField(1, 4, BASE_TYPE_SINT32)   // position_long, semicircles
    )
    val sessionFields = listOf(
      FitField(253, 4, BASE_TYPE_UINT32),
      FitField(2, 4, BASE_TYPE_UINT32),
      FitField(5, 1, BASE_TYPE_ENUM),
      FitField(7, 4, BASE_TYPE_UINT32),
      FitField(8, 4, BASE_TYPE_UINT32),
      FitField(9, 4, BASE_TYPE_UINT32),  // total_distance, cm
      FitField(18, 1, BASE_TYPE_UINT8), // avg_cadence
      FitField(16, 1, BASE_TYPE_UINT8),
      FitField(17, 1, BASE_TYPE_UINT8),
      FitField(20, 2, BASE_TYPE_UINT16), // avg_power
      FitField(21, 2, BASE_TYPE_UINT16)  // max_power
    )
    val activityFields = listOf(
      FitField(253, 4, BASE_TYPE_UINT32),
      FitField(0, 4, BASE_TYPE_UINT32),
      FitField(1, 2, BASE_TYPE_UINT16),
      FitField(2, 1, BASE_TYPE_ENUM)
    )

    val dataBytes = mutableListOf<Int>()

    dataBytes.addAll(buildDefinitionMessage(0, 0, fileIdFields))
    dataBytes.addAll(buildDataMessage(0, fileIdFields, listOf(4, 1, 0, fitStartTimestamp)))

    dataBytes.addAll(buildDefinitionMessage(1, 49, fileCreatorFields))
    dataBytes.addAll(buildDataMessage(1, fileCreatorFields, listOf(1, 1)))

    dataBytes.addAll(buildDefinitionMessage(2, 20, recordFields))
    normalizedSamples.forEach { sample ->
      val cadence = if (sample.cadenceRpm > 0) sample.cadenceRpm else null
      val hr = if (sample.hrBpm > 0) sample.hrBpm else null
      val speed = sample.virtualSpeedKph?.let { (it / 3.6 * 1000).toInt() }
      val distance = sample.virtualDistanceM?.let { (it * 100).toLong() }
      val altitude = sample.virtualAltitudeM?.let { ((it + 500.0) * 5).toInt() }
      val lat = sample.positionLat?.let { (it * SEMICIRCLES_PER_DEGREE).toInt() }
      val lon = sample.positionLon?.let { (it * SEMICIRCLES_PER_DEGREE).toInt() }
      dataBytes.addAll(
        buildDataMessage(
          2,
          recordFields,
          listOf(
            fitStartTimestamp + sample.timeSec, sample.powerWatts, cadence, hr,
            speed, distance, altitude, lat, lon
          )
        )
      )
    }

    val totalDistanceCm = normalizedSamples
      .lastOrNull { it.virtualDistanceM != null }
      ?.virtualDistanceM?.let { (it * 100).toLong() }

    dataBytes.addAll(buildDefinitionMessage(3, 18, sessionFields))
    dataBytes.addAll(
      buildDataMessage(
        3,
        sessionFields,
        listOf(
          fitEndTimestamp,
          fitStartTimestamp,
          sport,
          totalElapsedMs,
          totalTimerMs,
          totalDistanceCm,
          avgCadence?.toInt(),
          avgHr?.toInt(),
          maxHr,
          avgPower?.toInt(),
          maxPower
        )
      )
    )

    dataBytes.addAll(buildDefinitionMessage(4, 34, activityFields))
    dataBytes.addAll(
      buildDataMessage(
        4,
        activityFields,
        listOf(fitEndTimestamp, totalTimerMs, 1, 0)
      )
    )

    val header = MutableList(FIT_HEADER_SIZE) { 0 }
    header[0] = FIT_HEADER_SIZE
    header[1] = FIT_PROTOCOL_VERSION
    header[2] = FIT_PROFILE_VERSION and 0xff
    header[3] = (FIT_PROFILE_VERSION shr 8) and 0xff
    val dataSize = dataBytes.size
    header[4] = dataSize and 0xff
    header[5] = (dataSize shr 8) and 0xff
    header[6] = (dataSize shr 16) and 0xff
    header[7] = (dataSize shr 24) and 0xff
    header[8] = 0x2e
    header[9] = 0x46
    header[10] = 0x49
    header[11] = 0x54
    val headerCrc = crc16(header.subList(0, 12))
    header[12] = headerCrc and 0xff
    header[13] = (headerCrc shr 8) and 0xff

    val fileSize = header.size + dataBytes.size + 2
    val fileBytes = MutableList(fileSize) { 0 }
    header.forEachIndexed { index, value -> fileBytes[index] = value }
    dataBytes.forEachIndexed { index, value -> fileBytes[header.size + index] = value }
    val fileCrc = crc16(header + dataBytes)
    fileBytes[header.size + dataBytes.size] = fileCrc and 0xff
    fileBytes[header.size + dataBytes.size + 1] = (fileCrc shr 8) and 0xff

    return ByteArray(fileBytes.size) { fileBytes[it].toByte() }
  }
}
