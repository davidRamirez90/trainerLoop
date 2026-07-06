package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CoachProfile(
  val id: String,
  val name: String,
  val description: String,
  val rules: CoachRules,
  val interventions: CoachInterventions,
  val voice: CoachVoice,
  val messages: CoachMessages
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
data class CoachVoice(val tone: String, val style: String)

@Serializable
data class CoachMessages(
  val suggestions: Map<String, List<String>>,
  val completion: List<String> = emptyList(),
  val encouragement: List<String> = emptyList()
)
