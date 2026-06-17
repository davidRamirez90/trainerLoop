package com.trainerloop.data.model

data class CoachProfile(
  val id: String,
  val name: String,
  val description: String,
  val rules: CoachRules,
  val interventions: CoachInterventions,
  val voice: CoachVoice,
  val messages: CoachMessages
)

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

data class CoachInterventions(
  val intensityAdjustStepPct: Double,
  val intensityAdjustMinPct: Double,
  val intensityAdjustMaxPct: Double,
  val recoveryExtendStepSec: Int,
  val recoveryExtendMaxSec: Int,
  val allowSkipRemainingOnIntervals: Boolean
)

data class CoachVoice(val tone: String, val style: String)

data class CoachMessages(
  val suggestions: Map<String, List<String>>,
  val completion: List<String>,
  val encouragement: List<String>
)
