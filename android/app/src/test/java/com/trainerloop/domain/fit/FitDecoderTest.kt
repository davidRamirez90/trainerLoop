package com.trainerloop.domain.fit

import com.trainerloop.data.model.TelemetrySample
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FitDecoderTest {

  @Test
  fun `round-trips through FitEncoder`() {
    val samples = (0 until 300).map { t ->
      TelemetrySample(
        timeSec = t,
        powerWatts = 200 + (t % 40),
        cadenceRpm = 90 - (t % 5),
        hrBpm = 130 + (t % 20)
      )
    }
    val startMs = 1_750_000_000_000L
    val decoded = FitDecoder.decode(FitEncoder.encode(startMs, 300, samples))

    assertEquals(samples.size, decoded.samples.size)
    assertEquals(samples, decoded.samples)
    // Encoder truncates to whole FIT seconds.
    assertEquals(startMs / 1000, decoded.startTimeMs / 1000)
  }

  @Test
  fun `zero cadence and hr survive as absent`() {
    val samples = listOf(TelemetrySample(timeSec = 0, powerWatts = 150, cadenceRpm = 0, hrBpm = 0))
    val decoded = FitDecoder.decode(FitEncoder.encode(0L, 1, samples))
    assertEquals(samples, decoded.samples)
  }

  @Test
  fun `decodes the recorded rides in the repo`() {
    val dir = File("../../rides")
    assumeTrue("no rides/ directory in this checkout", dir.isDirectory)
    val files = dir.listFiles { f -> f.extension == "fit" }.orEmpty()
    assumeTrue("no .fit files present", files.isNotEmpty())

    files.forEach { f ->
      val decoded = FitDecoder.decode(f.readBytes())
      assertTrue("${f.name}: no record samples decoded", decoded.samples.isNotEmpty())
      assertTrue(
        "${f.name}: timestamps must be monotonic",
        decoded.samples.zipWithNext().all { (a, b) -> b.timeSec >= a.timeSec }
      )
      assertTrue(
        "${f.name}: expected some non-zero power",
        decoded.samples.any { it.powerWatts > 0 }
      )
      println("${f.name}: ${decoded.samples.size} samples, ${decoded.samples.last().timeSec}s")
    }
  }
}
