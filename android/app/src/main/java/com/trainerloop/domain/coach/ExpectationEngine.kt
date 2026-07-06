package com.trainerloop.domain.coach

import com.trainerloop.data.model.UserProfile
import kotlin.math.exp

/**
 * Expected HR / power / cadence envelopes per interval (§5). Population
 * HR↔power map anchored on LTHR, with first-order HR kinetics and a
 * per-session calibration offset learned during the warmup.
 */
class ExpectationEngine(private val profile: UserProfile) {

  /** bpm added to all predictions once the session calibration converges. */
  var calibrationOffsetBpm: Double = 0.0
    private set
  private var calibrationSamples = 0

  private val lthr: Double = (profile.lthr?.toDouble() ?: profile.maxHr * 0.89)
  private val anchorQuality: Double = if (profile.lthr != null) 1.0 else 0.7

  fun expectationFor(
    ctx: IntervalContext,
    targetMidWatts: Double,
    fatigueScore: Double,
    cadenceBaseline: Double?
  ): ExpectationEnvelope {
    val pctFtp = if (profile.ftp > 0) targetMidWatts / profile.ftp else 0.0

    // Power band (§5.2): ±5% work, ±10% recovery/endurance.
    val bandPct = powerBandPct(ctx.segmentClass)
    val powerBand = (targetMidWatts * (1 - bandPct))..(targetMidWatts * (1 + bandPct))

    // HR band: skip entirely above threshold classes' saturation zone handled by map.
    val hrBand = if (targetMidWatts > 0 && ctx.elapsedInSegmentSec >= HR_KINETICS_BLACKOUT_SEC) {
      val steadyState = steadyStateHr(pctFtp) + calibrationOffsetBpm + fatigueScore / 100.0 * 5.0
      val hr0 = steadyState - 25 // conservative: assume HR was well below at segment entry
      val t = ctx.elapsedInSegmentSec.toDouble()
      val expected = steadyState - (steadyState - hr0) * exp(-t / TAU_SEC)
      val drift = driftAllowanceBpm(ctx) * (ctx.elapsedInSegmentSec / 60.0)
      val halfWidth = HR_BAND_BASE_BPM + (1 - anchorQuality) * 8 - calibrationConfidence() * 2
      (expected - halfWidth)..(expected + drift + halfWidth)
    } else null

    val cadenceBand = cadenceBand(ctx, cadenceBaseline)

    return ExpectationEnvelope(
      hrBand = hrBand,
      powerBand = powerBand,
      cadenceBand = cadenceBand,
      driftAllowancePct = driftAllowancePct(ctx.intent),
      anchorQuality = anchorQuality
    )
  }

  /**
   * Session calibration (§5.1): during warmup steady state, absorb the
   * day-to-day HR offset between observed and predicted.
   */
  fun calibrate(observedHr: Double, targetMidWatts: Double, elapsedInSegmentSec: Int, segClass: SegmentClass) {
    if (segClass != SegmentClass.WARMUP || targetMidWatts <= 0) return
    if (elapsedInSegmentSec < 120) return // wait for HR to settle
    val pctFtp = if (profile.ftp > 0) targetMidWatts / profile.ftp else return
    val predicted = steadyStateHr(pctFtp)
    val error = observedHr - predicted
    calibrationSamples++
    calibrationOffsetBpm += (error - calibrationOffsetBpm) / calibrationSamples
  }

  private fun calibrationConfidence(): Double = (calibrationSamples / 60.0).coerceAtMost(1.0)

  /** Piecewise-linear %FTP → bpm map anchored on LTHR (§5.1). */
  fun steadyStateHr(pctFtp: Double): Double {
    val points = listOf(
      0.40 to 0.65, 0.55 to 0.75, 0.75 to 0.85, 0.88 to 0.92, 1.00 to 1.00
    )
    if (pctFtp >= 1.15) return profile.maxHr * 0.97
    if (pctFtp > 1.00) {
      // interpolate between LTHR and 0.97 maxHR across 100–115% FTP
      val f = (pctFtp - 1.00) / 0.15
      return lthr + (profile.maxHr * 0.97 - lthr) * f
    }
    val clamped = pctFtp.coerceAtLeast(points.first().first)
    for (i in 0 until points.size - 1) {
      val (x0, y0) = points[i]
      val (x1, y1) = points[i + 1]
      if (clamped <= x1) {
        val f = (clamped - x0) / (x1 - x0)
        return (y0 + (y1 - y0) * f) * lthr
      }
    }
    return lthr
  }

  fun powerBandPct(segClass: SegmentClass): Double = when (segClass) {
    SegmentClass.RECOVERY, SegmentClass.ENDURANCE, SegmentClass.WARMUP, SegmentClass.COOLDOWN -> 0.10
    SegmentClass.SPRINT -> 0.25
    else -> 0.05
  }

  private fun cadenceBand(ctx: IntervalContext, baseline: Double?): ClosedRange<Double>? {
    val prescribed = when (val seg = ctx.classified.segment) {
      is com.trainerloop.data.model.WorkoutSegment.Step -> seg.targetCadence
      is com.trainerloop.data.model.WorkoutSegment.Ramp -> seg.targetCadence
      else -> null
    }
    if (prescribed != null) return prescribed.first.toDouble()..prescribed.last.toDouble()
    return baseline?.let { (it - 5)..(it + 5) }
  }

  private fun driftAllowanceBpm(ctx: IntervalContext): Double = when (ctx.segmentClass) {
    SegmentClass.VO2MAX, SegmentClass.ANAEROBIC -> 10.0
    SegmentClass.THRESHOLD -> 6.0
    SegmentClass.SWEET_SPOT, SegmentClass.TEMPO -> 4.0
    else -> 2.0
  }

  private fun driftAllowancePct(intent: WorkoutIntent): Double = when (intent) {
    WorkoutIntent.VO2_DEV, WorkoutIntent.ANAEROBIC_CAP, WorkoutIntent.NEUROMUSCULAR -> 8.0
    WorkoutIntent.THRESHOLD_DEV -> 6.0
    else -> 4.0
  }

  companion object {
    const val HR_KINETICS_BLACKOUT_SEC = 45
    private const val TAU_SEC = 40.0
    private const val HR_BAND_BASE_BPM = 5.0
  }
}
