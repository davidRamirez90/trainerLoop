package com.trainerloop.domain.coach

import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import kotlin.math.sqrt

/**
 * Per-tick session state, maintained incrementally (O(1) per sample):
 * rolling windows, interval ledger, fatigue score, per-signal confidence.
 */
class AthleteStateModel(private val profile: UserProfile) {

  data class AthleteState(
    val power30s: Double,
    val power3s: Double,
    val hr10s: Double?,
    val hrSlopePerMin: Double?,
    val cadence10s: Double?,
    val segmentAvgPower: Double,
    val segmentAvgHr: Double?,
    val segmentStartHr: Double?,
    val segmentAvgCadence: Double?,
    val segmentTimeInBandPct: Double,
    val segmentPowerCv: Double,
    val fatigueScore: Double,
    val powerConfidence: Double,
    val hrConfidence: Double,
    val cadenceConfidence: Double,
    val sessionBaselineHr: Double?
  )

  private val power3 = RollingWindow(3)
  private val power30 = RollingWindow(30)
  private val hr10 = RollingWindow(10)
  private val hr60 = RollingWindow(60)
  private val cadence10 = RollingWindow(10)
  private val hrValid = RollingFlagWindow(60)
  private val cadenceValid = RollingFlagWindow(60)
  private val powerValid = RollingFlagWindow(60)

  // Current-segment accumulators, flushed on segment boundary.
  private var segIndex = -1
  private var segPowerSum = 0.0
  private var segPowerSqSum = 0.0
  private var segPowerN = 0
  private var segHrSum = 0.0
  private var segHrN = 0
  private var segStartHr: Double? = null
  private var segEndHrWindow = RollingWindow(15)
  private var segCadenceSum = 0.0
  private var segCadenceN = 0
  private var segInBand = 0

  // Recovery tracking
  private var recoveryStartHr: Double? = null
  private var recoveryElapsed = 0
  private var hrr60: Double? = null

  private var baselineHr: Double? = null
  private var fatigue = 0.0

  val ledger = mutableListOf<IntervalRecord>()
  val recoveries = mutableListOf<RecoveryRecord>()

  /** HRR60 for the recovery currently in progress, once 60 s in; else null. */
  fun currentRecoveryHrr60(): Double? = hrr60

  fun onSample(sample: TelemetrySample, ctx: IntervalContext?, targetMid: Double, bandPct: Double): AthleteState {
    handleSegmentChange(ctx, targetMid)

    val hrOk = sample.hrBpm in (profile.restingHr - 15)..(profile.maxHr + 5) && sample.hrBpm > 0 && !sample.dropout
    val cadOk = sample.cadenceRpm > 0 && !sample.dropout
    val pwrOk = !sample.dropout

    powerValid.add(pwrOk)
    hrValid.add(hrOk)
    cadenceValid.add(cadOk)

    if (pwrOk) {
      power3.add(sample.powerWatts.toDouble())
      power30.add(sample.powerWatts.toDouble())
      segPowerSum += sample.powerWatts
      segPowerSqSum += sample.powerWatts.toDouble() * sample.powerWatts
      segPowerN++
      if (targetMid > 0) {
        val low = targetMid * (1 - bandPct)
        val high = targetMid * (1 + bandPct)
        if (sample.powerWatts >= low && sample.powerWatts <= high) segInBand++
      }
    }
    if (hrOk) {
      hr10.add(sample.hrBpm.toDouble())
      hr60.add(sample.hrBpm.toDouble())
      segHrSum += sample.hrBpm
      segHrN++
      segEndHrWindow.add(sample.hrBpm.toDouble())
      if (segStartHr == null) segStartHr = sample.hrBpm.toDouble()
    }
    if (cadOk) {
      cadence10.add(sample.cadenceRpm.toDouble())
      segCadenceSum += sample.cadenceRpm
      segCadenceN++
    }

    // Session HR baseline: rolling HR near the end of the warmup.
    if (ctx?.segmentClass == SegmentClass.WARMUP && hr10.count > 5) {
      baselineHr = hr10.mean()
    }

    // Recovery HRR60 tracking.
    if (ctx != null && ctx.segmentClass == SegmentClass.RECOVERY) {
      recoveryElapsed++
      if (recoveryElapsed == 60) {
        val start = recoveryStartHr
        val now = hr10.mean()
        hrr60 = if (start != null && now != null) start - now else null
      }
    }

    updateFatigue(ctx)

    return AthleteState(
      power30s = power30.mean() ?: 0.0,
      power3s = power3.mean() ?: 0.0,
      hr10s = hr10.mean(),
      hrSlopePerMin = hr60.slopePerMin(),
      cadence10s = cadence10.mean(),
      segmentAvgPower = if (segPowerN > 0) segPowerSum / segPowerN else 0.0,
      segmentAvgHr = if (segHrN > 0) segHrSum / segHrN else null,
      segmentStartHr = segStartHr,
      segmentAvgCadence = if (segCadenceN > 0) segCadenceSum / segCadenceN else null,
      segmentTimeInBandPct = if (segPowerN > 0) segInBand * 100.0 / segPowerN else 0.0,
      segmentPowerCv = segmentPowerCv(),
      fatigueScore = fatigue,
      powerConfidence = powerValid.fraction(),
      hrConfidence = hrValid.fraction(),
      cadenceConfidence = cadenceValid.fraction(),
      sessionBaselineHr = baselineHr
    )
  }

  /** Flush rolling windows across pauses > 30 s or seeks. */
  fun invalidateWindows() {
    power3.clear(); power30.clear(); hr10.clear(); hr60.clear(); cadence10.clear()
    hrValid.clear(); cadenceValid.clear(); powerValid.clear()
    segPowerSum = 0.0; segPowerSqSum = 0.0; segPowerN = 0
    segHrSum = 0.0; segHrN = 0; segStartHr = null
    segEndHrWindow = RollingWindow(15)
    segCadenceSum = 0.0; segCadenceN = 0; segInBand = 0
    recoveryStartHr = null; recoveryElapsed = 0; hrr60 = null
  }

  private fun handleSegmentChange(ctx: IntervalContext?, targetMid: Double) {
    val newIndex = ctx?.classified?.index ?: -1
    if (newIndex == segIndex) return

    // Close out the previous segment.
    if (segIndex >= 0 && lastCtx?.isWork == true && segPowerN > 10) {
      val avgPower = segPowerSum / segPowerN
      val prevTarget = lastTargetMid
      ledger.add(
        IntervalRecord(
          setId = lastCtx?.set?.id,
          blockNumber = lastCtx?.blockNumber ?: 1,
          segmentClass = lastCtx?.segmentClass ?: SegmentClass.FREE_RIDE,
          targetMidWatts = prevTarget,
          avgPower = avgPower,
          adherencePct = if (prevTarget > 0) avgPower / prevTarget * 100 else 0.0,
          timeInTargetPct = segInBand * 100.0 / segPowerN,
          powerCv = segmentPowerCv(),
          avgHr = if (segHrN > 0) segHrSum / segHrN else null,
          endHr = segEndHrWindow.mean(),
          hrDriftPct = hrDriftPct(),
          avgCadence = if (segCadenceN > 0) segCadenceSum / segCadenceN else null
        )
      )
      if (ledger.size > MAX_LEDGER) ledger.removeAt(0)
    }
    if (segIndex >= 0 && lastCtx?.segmentClass == SegmentClass.RECOVERY) {
      recoveries.add(RecoveryRecord(startHr = recoveryStartHr, hrr60 = hrr60, endHr = segEndHrWindow.mean()))
      if (recoveries.size > MAX_LEDGER) recoveries.removeAt(0)
    }

    // Reset accumulators for the new segment.
    segIndex = newIndex
    lastCtx = ctx
    lastTargetMid = targetMid
    segPowerSum = 0.0; segPowerSqSum = 0.0; segPowerN = 0
    segHrSum = 0.0; segHrN = 0; segStartHr = null
    segEndHrWindow = RollingWindow(15)
    segCadenceSum = 0.0; segCadenceN = 0; segInBand = 0
    if (ctx?.segmentClass == SegmentClass.RECOVERY) {
      recoveryStartHr = hr10.mean()
      recoveryElapsed = 0
      hrr60 = null
    }
  }

  private var lastCtx: IntervalContext? = null
  private var lastTargetMid: Double = 0.0

  private fun hrDriftPct(): Double? {
    val start = segStartHr ?: return null
    val end = segEndHrWindow.mean() ?: return null
    return if (start > 0) (end - start) / start * 100 else null
  }

  private fun segmentPowerCv(): Double {
    if (segPowerN < 5) return 0.0
    val mean = segPowerSum / segPowerN
    if (mean <= 0) return 0.0
    val variance = (segPowerSqSum / segPowerN - mean * mean).coerceAtLeast(0.0)
    return sqrt(variance) / mean * 100
  }

  /**
   * 0–100 EWMA composite (§4.4, simplified for MVP): within-interval HR drift,
   * recovery deficit, cross-interval cadence decline, rising power CV.
   */
  private fun updateFatigue(ctx: IntervalContext?) {
    if (ctx == null) return
    var instantaneous = 0.0

    // HR drift beyond an intent-adjusted allowance in the recent ledger.
    val allowance = when (ctx.intent) {
      WorkoutIntent.VO2_DEV, WorkoutIntent.ANAEROBIC_CAP, WorkoutIntent.NEUROMUSCULAR -> 8.0
      WorkoutIntent.THRESHOLD_DEV -> 6.0
      else -> 4.0
    }
    val recentDrifts = ledger.takeLast(3).mapNotNull { it.hrDriftPct }
    if (recentDrifts.isNotEmpty()) {
      val excess = (recentDrifts.average() - allowance).coerceAtLeast(0.0)
      instantaneous += (excess * 8).coerceAtMost(30.0)
    }

    // Recovery deficit: incomplete recoveries (HRR60 < 15 bpm).
    val recentRecoveries = recoveries.takeLast(2).mapNotNull { it.hrr60 }
    if (recentRecoveries.size == 2 && recentRecoveries.all { it < 15 }) instantaneous += 20.0

    // Cross-interval cadence decline within the same class.
    val sameClass = ledger.takeLast(3).filter { it.segmentClass == ctx.segmentClass }
    val cadences = sameClass.mapNotNull { it.avgCadence }
    if (cadences.size >= 2 && cadences.zipWithNext().all { (a, b) -> b <= a - 3 }) instantaneous += 15.0

    // Rising power CV across intervals (ERG smoothness deterioration).
    val cvs = ledger.takeLast(3).map { it.powerCv }
    if (cvs.size >= 2 && cvs.last() > 6 && cvs.zipWithNext().all { (a, b) -> b > a }) instantaneous += 10.0

    // Current-interval drift beyond allowance while working.
    if (ctx.isWork) {
      val drift = hrDriftPct()
      if (drift != null && drift > allowance) instantaneous += ((drift - allowance) * 5).coerceAtMost(25.0)
    }

    fatigue = (FATIGUE_EWMA * instantaneous.coerceAtMost(100.0) + (1 - FATIGUE_EWMA) * fatigue)
      .coerceIn(0.0, 100.0)
  }

  private class RollingWindow(private val capacity: Int) {
    private val values = DoubleArray(capacity)
    private var head = 0
    var count = 0; private set
    private var sum = 0.0

    fun add(v: Double) {
      if (count == capacity) sum -= values[head]
      values[head] = v
      head = (head + 1) % capacity
      if (count < capacity) count++ else Unit
      sum += v
    }

    fun mean(): Double? = if (count > 0) sum / count else null

    /** Least-squares slope over the window contents, per minute. */
    fun slopePerMin(): Double? {
      if (count < 10) return null
      val n = count
      var sx = 0.0; var sy = 0.0; var sxy = 0.0; var sxx = 0.0
      for (i in 0 until n) {
        val idx = (head - n + i + capacity * 2) % capacity
        val y = values[idx]
        sx += i; sy += y; sxy += i.toDouble() * y; sxx += i.toDouble() * i
      }
      val denom = n * sxx - sx * sx
      if (denom == 0.0) return null
      return (n * sxy - sx * sy) / denom * 60.0
    }

    fun clear() { head = 0; count = 0; sum = 0.0 }
  }

  private class RollingFlagWindow(private val capacity: Int) {
    private val flags = BooleanArray(capacity)
    private var head = 0
    private var count = 0
    private var trueCount = 0

    fun add(v: Boolean) {
      if (count == capacity && flags[head]) trueCount--
      flags[head] = v
      head = (head + 1) % capacity
      if (count < capacity) count++
      if (v) trueCount++
    }

    fun fraction(): Double = if (count == 0) 1.0 else trueCount.toDouble() / count

    fun clear() { head = 0; count = 0; trueCount = 0 }
  }

  companion object {
    private const val MAX_LEDGER = 64
    private const val FATIGUE_EWMA = 0.02
  }
}
