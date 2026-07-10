package com.trainerloop.domain

import java.net.URLDecoder

/**
 * Names returned by the ICU sync path may be application/x-www-form-urlencoded.
 * Decode that representation once at the import boundary; ordinary names that
 * already contain whitespace are left alone so a literal '+' is not rewritten.
 */
object WorkoutNameCodec {

  fun decodeIcuName(value: String): String {
    if (value.any { it.isWhitespace() } || !value.contains('+')) return value
    return runCatching {
      URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)
  }

  /** Conservative normalization for names from sessions saved before the fix. */
  fun normalizeStoredName(value: String): String {
    if (value.any { it.isWhitespace() } || value.count { it == '+' } < 2) return value
    return decodeIcuName(value)
  }
}
