package com.trainerloop.data.model

sealed class CoachAction {
  data class AdjustIntensityUp(val percent: Int) : CoachAction()
  data class AdjustIntensityDown(val percent: Int) : CoachAction()
  data class ExtendRecovery(val seconds: Int) : CoachAction()
  object SkipRemainingOnIntervals : CoachAction()
}

data class CoachSuggestion(
  val id: String,
  val action: CoachAction,
  val message: String,
  val rationale: String,
  val segmentIndex: Int?,
  val status: SuggestionStatus = SuggestionStatus.PENDING
)

enum class SuggestionStatus { PENDING, ACCEPTED, REJECTED }
