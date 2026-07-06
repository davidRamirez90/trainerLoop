package com.trainerloop.domain

import com.trainerloop.data.model.CoachAction
import com.trainerloop.data.model.CoachProfile

object CoachMessageBuilder {
  private const val PERCENT = "{{percent}}"
  private const val SECONDS = "{{seconds}}"

  fun suggestionMessage(profile: CoachProfile, action: CoachAction): String {
    val (key, data) = action.templateKeyAndData()
    val template = profile.messages.suggestions[key]?.randomOrNull() ?: defaultMessage(action)
    return apply(template, data)
  }

  fun rationale(profile: CoachProfile, action: CoachAction): String {
    val (key, data) = action.templateKeyAndData()
    val rationaleKey = "${key}_rationale"
    val template = profile.messages.suggestions[rationaleKey]?.randomOrNull() ?: defaultRationale(action)
    return apply(template, data)
  }

  fun completionMessage(profile: CoachProfile): String {
    return profile.messages.completion.randomOrNull() ?: "Session complete."
  }

  private fun apply(template: String, data: TemplateData): String {
    return template
      .replace(PERCENT, data.percent?.toString() ?: "")
      .replace(SECONDS, data.seconds?.toString() ?: "")
  }

  private fun CoachAction.templateKeyAndData(): Pair<String, TemplateData> = when (this) {
    is CoachAction.AdjustIntensityUp -> "adjust_intensity_up" to TemplateData(percent = percent)
    is CoachAction.AdjustIntensityDown -> "adjust_intensity_down" to TemplateData(percent = percent)
    is CoachAction.ExtendRecovery -> "extend_recovery" to TemplateData(seconds = seconds)
    is CoachAction.SkipRemainingOnIntervals -> "skip_remaining_on_intervals" to TemplateData()
  }

  private fun defaultMessage(action: CoachAction): String = when (action) {
    is CoachAction.AdjustIntensityUp -> "Increase intensity by {{percent}}%."
    is CoachAction.AdjustIntensityDown -> "Decrease intensity by {{percent}}%."
    is CoachAction.ExtendRecovery -> "Extend recovery by {{seconds}} seconds."
    is CoachAction.SkipRemainingOnIntervals -> "Skip remaining intervals."
  }

  private fun defaultRationale(action: CoachAction): String = when (action) {
    is CoachAction.AdjustIntensityUp -> "Metrics indicate you can handle more intensity."
    is CoachAction.AdjustIntensityDown -> "Fatigue indicators suggest reducing intensity."
    is CoachAction.ExtendRecovery -> "Recovery metrics indicate more time needed."
    is CoachAction.SkipRemainingOnIntervals -> "Multiple indicators suggest terminating the session."
  }

  private data class TemplateData(val percent: Int? = null, val seconds: Int? = null)
}
