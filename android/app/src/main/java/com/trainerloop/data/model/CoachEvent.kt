package com.trainerloop.data.model

data class CoachEvent(
  val id: String,
  val sessionId: String,
  val timestamp: String,
  val type: CoachEventType,
  val message: String,
  val rationale: String? = null,
  val suggestion: CoachSuggestion? = null,
  val userResponse: CoachResponse? = null
)

enum class CoachEventType { ENCOURAGEMENT, SUGGESTION, COMPLETION }

data class CoachResponse(val response: ResponseType, val respondedAt: String)

enum class ResponseType { ACCEPTED, REJECTED }
