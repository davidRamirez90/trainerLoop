package com.trainerloop.domain.coach

import com.trainerloop.data.model.TelemetrySample
import kotlin.math.exp
import kotlin.math.sin

/**
 * Scenario simulator (§13.3): a synthetic athlete with configurable HR
 * kinetics, drift, cadence behavior, and failure injections (strap death,
 * ERG spiral, blow-up, sandbagging, dropouts). Deterministic — no RNG.
 */
class AthleteSimulator(
  private val ftp: Int = 250,
  private val restingHr: Double = 55.0,
  /** HR gained per unit of %FTP at steady state (hrSs = resting + gain·%FTP). */
  private val hrGainPerFtpFraction: Double = 95.0,
  private val hrTauSec: Double = 40.0,
  /** Upward HR drift, bpm per second of ride time. */
  private val hrDriftPerSec: Double = 0.004,
  private val baseCadence: Int = 92,
  /** Cadence lost per 10 min of riding (gradual self-selected fade). */
  private val cadenceFadePer10Min: Int = 1,
  /** Rider holds this fraction of target (1.0 = perfect ERG; <1 = sandbagging). */
  private val powerAdherence: Double = 1.0,
  /** From this ride second on, the HR strap reports nothing. */
  private val hrStrapDiesAtSec: Int? = null,
  /** From this ride second on, cadence and power collapse (ERG spiral / blow-up). */
  private val blowUpAtSec: Int? = null,
  /** Sample dropout windows (no data, dropout flag set). */
  private val dropouts: List<IntRange> = emptyList()
) {

  fun ride(plan: WorkoutPlanModel): List<TelemetrySample> {
    var hr = restingHr + 15
    val samples = mutableListOf<TelemetrySample>()
    for (t in 0 until plan.totalDurationSec) {
      val ctx = WorkoutInterpreter.contextAt(plan, t) ?: break
      val target = ctx.classified.targetMidWatts

      val blown = blowUpAtSec != null && t >= blowUpAtSec
      val cadence = when {
        blown -> (55 - (t - blowUpAtSec!!) / 20).coerceAtLeast(40)
        else -> baseCadence - (t / 600) * cadenceFadePer10Min
      }
      val power = when {
        blown -> target * 0.75
        else -> target * powerAdherence + 3.0 * sin(t / 7.0)
      }

      val effortFraction = power / ftp
      val hrSs = restingHr + hrGainPerFtpFraction * effortFraction + t * hrDriftPerSec
      hr += (hrSs - hr) * (1 - exp(-1.0 / hrTauSec))

      val inDropout = dropouts.any { t in it }
      val strapDead = hrStrapDiesAtSec != null && t >= hrStrapDiesAtSec
      samples += if (inDropout) {
        TelemetrySample(timeSec = t, powerWatts = 0, cadenceRpm = 0, hrBpm = 0, dropout = true)
      } else {
        TelemetrySample(
          timeSec = t,
          powerWatts = power.toInt().coerceAtLeast(0),
          cadenceRpm = cadence,
          hrBpm = if (strapDead) 0 else hr.toInt()
        )
      }
    }
    return samples
  }
}
