package com.trainerloop.domain.fit

import com.trainerloop.data.model.TelemetrySample

/**
 * Minimal FIT decoder (§13.2): extracts record messages (power, cadence, HR)
 * from an activity file into [TelemetrySample]s for the replay harness.
 * Lenient by design — unknown messages/fields are skipped by size, CRCs are
 * not validated, and both endiannesses, compressed-timestamp headers, and
 * developer fields are handled.
 */
object FitDecoder {

  private const val FIT_EPOCH_MS = 631065600000L
  private const val RECORD_MSG = 20
  private const val INVALID_UINT8 = 0xff
  private const val INVALID_UINT16 = 0xffff
  private const val INVALID_UINT32 = 0xffffffffL

  data class Decoded(
    val samples: List<TelemetrySample>,
    val startTimeMs: Long
  )

  private data class FieldDef(val num: Int, val size: Int, val baseType: Int)
  private data class MsgDef(
    val globalNum: Int,
    val littleEndian: Boolean,
    val fields: List<FieldDef>,
    val devFieldBytes: Int
  )

  fun decode(bytes: ByteArray): Decoded {
    require(bytes.size >= 12) { "Not a FIT file: too short" }
    val headerSize = bytes[0].toInt() and 0xff
    require(headerSize >= 12 && bytes.size > headerSize) { "Not a FIT file: bad header" }
    val magic = String(bytes, 8, 4, Charsets.US_ASCII)
    require(magic == ".FIT") { "Not a FIT file: missing .FIT magic" }
    val dataSize = readUint32Le(bytes, 4)
    val end = minOf(bytes.size.toLong(), headerSize + dataSize).toInt()

    val defs = mutableMapOf<Int, MsgDef>()
    val records = mutableListOf<Triple<Long, Int?, Pair<Int?, Int?>>>() // ts, power, (cadence, hr)
    var lastTimestamp = 0L
    var pos = headerSize

    while (pos < end) {
      val header = bytes[pos].toInt() and 0xff
      pos++
      val compressed = header and 0x80 != 0
      if (!compressed && header and 0x40 != 0) {
        // Definition message
        val hasDev = header and 0x20 != 0
        val localType = header and 0x0f
        pos++ // reserved
        val littleEndian = bytes[pos].toInt() == 0
        pos++
        val globalNum =
          if (littleEndian) readUint16Le(bytes, pos) else readUint16Be(bytes, pos)
        pos += 2
        val fieldCount = bytes[pos].toInt() and 0xff
        pos++
        val fields = ArrayList<FieldDef>(fieldCount)
        repeat(fieldCount) {
          fields += FieldDef(
            num = bytes[pos].toInt() and 0xff,
            size = bytes[pos + 1].toInt() and 0xff,
            baseType = bytes[pos + 2].toInt() and 0xff
          )
          pos += 3
        }
        var devBytes = 0
        if (hasDev) {
          val devCount = bytes[pos].toInt() and 0xff
          pos++
          repeat(devCount) {
            devBytes += bytes[pos + 1].toInt() and 0xff
            pos += 3
          }
        }
        defs[localType] = MsgDef(globalNum, littleEndian, fields, devBytes)
      } else {
        // Data message (normal or compressed-timestamp)
        val localType = if (compressed) (header shr 5) and 0x03 else header and 0x0f
        val def = defs[localType] ?: throw IllegalArgumentException(
          "FIT data message references undefined local type $localType at offset ${pos - 1}"
        )
        if (compressed) {
          val offset = (header and 0x1f).toLong()
          val rolled = (lastTimestamp and 0x1fL.inv()) or offset
          lastTimestamp = if (rolled >= lastTimestamp) rolled else rolled + 0x20
        }
        var timestamp: Long? = null
        var power: Int? = null
        var cadence: Int? = null
        var hr: Int? = null
        for (f in def.fields) {
          if (def.globalNum == RECORD_MSG || f.num == 253) {
            val v = readValue(bytes, pos, f, def.littleEndian)
            when (f.num) {
              253 -> timestamp = v
              7 -> power = v?.toInt()
              4 -> cadence = v?.toInt()
              3 -> hr = v?.toInt()
            }
          }
          pos += f.size
        }
        pos += def.devFieldBytes
        if (timestamp != null) lastTimestamp = timestamp
        if (def.globalNum == RECORD_MSG) {
          records += Triple(lastTimestamp, power, cadence to hr)
        }
      }
    }

    if (records.isEmpty()) return Decoded(emptyList(), 0L)
    val t0 = records.first().first
    val samples = records.map { (ts, power, ch) ->
      TelemetrySample(
        timeSec = (ts - t0).toInt(),
        powerWatts = power ?: 0,
        cadenceRpm = ch.first ?: 0,
        hrBpm = ch.second ?: 0
      )
    }
    return Decoded(samples, t0 * 1000 + FIT_EPOCH_MS)
  }

  /** Reads a numeric field value, or null when it holds the invalid sentinel. */
  private fun readValue(bytes: ByteArray, pos: Int, f: FieldDef, le: Boolean): Long? {
    return when (f.baseType and 0x1f) {
      0x00, 0x02, 0x0a -> { // enum, uint8, uint8z
        val v = bytes[pos].toInt() and 0xff
        if (v == INVALID_UINT8) null else v.toLong()
      }
      0x04, 0x0b -> { // uint16, uint16z
        val v = if (le) readUint16Le(bytes, pos) else readUint16Be(bytes, pos)
        if (v == INVALID_UINT16) null else v.toLong()
      }
      0x06, 0x0c -> { // uint32, uint32z
        val v = if (le) readUint32Le(bytes, pos) else readUint32Be(bytes, pos)
        if (v == INVALID_UINT32) null else v
      }
      else -> null // signed/float/string types unused by the fields we read
    }
  }

  private fun readUint16Le(b: ByteArray, i: Int) =
    (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)

  private fun readUint16Be(b: ByteArray, i: Int) =
    ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)

  private fun readUint32Le(b: ByteArray, i: Int): Long =
    (b[i].toLong() and 0xff) or ((b[i + 1].toLong() and 0xff) shl 8) or
      ((b[i + 2].toLong() and 0xff) shl 16) or ((b[i + 3].toLong() and 0xff) shl 24)

  private fun readUint32Be(b: ByteArray, i: Int): Long =
    ((b[i].toLong() and 0xff) shl 24) or ((b[i + 1].toLong() and 0xff) shl 16) or
      ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
}
