package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CoachProfile(
  val id: String,
  val name: String,
  val description: String,
  val rules: CoachRules,
  val interventions: CoachInterventions,
  val messages: CoachMessages,
  /** Live-coach copy keyed by analytics rule id; missing keys fall back to built-in defaults. */
  val feedback: Map<String, List<String>> = emptyMap(),
  /** Scales the live-coach session budget (0.5 = half as chatty). */
  val verbosity: Double = 1.0,
  /** Scales per-category cooldowns (2.0 = waits twice as long before repeating a category). */
  val cooldownScale: Double = 1.0,
  /** Fraction of the session budget available to MOTIVATION items. */
  val motivationShare: Double = 0.33
)

@Serializable
data class CoachRules(
  val targetAdherenceWarn: Double,
  val targetAdherenceIntervene: Double,
  val hrDriftWarn: Double,
  val hrDriftIntervene: Double,
  val cadenceVarianceWarn: Double,
  val cadenceVarianceIntervene: Double,
  val minElapsedSecondsForSuggestions: Int,
  val cooldownSeconds: Int
)

@Serializable
data class CoachInterventions(
  val intensityAdjustStepPct: Double,
  val intensityAdjustMinPct: Double,
  val intensityAdjustMaxPct: Double,
  val recoveryExtendStepSec: Int,
  val recoveryExtendMaxSec: Int,
  val allowSkipRemainingOnIntervals: Boolean
)

@Serializable
data class CoachMessages(
  val suggestions: Map<String, List<String>>,
  val completion: List<String> = emptyList()
)
