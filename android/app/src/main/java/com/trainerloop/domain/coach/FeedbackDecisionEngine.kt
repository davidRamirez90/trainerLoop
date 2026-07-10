package com.trainerloop.domain.coach


/**
 * Arbitration (§8.3–8.4): rate limits, per-rule cooldowns, dedupe, session
 * budget, and ranking. Called once per arbitration cycle; emits ≤ 1 item.
 */
class FeedbackDecisionEngine(
  private val totalDurationSec: Int,
  private val verbosity: Double = 1.0,
  private val cooldownScale: Double = 1.0,
  private val motivationShare: Double = 0.33
) {

  private val pending = mutableListOf<AnalysisEvent>()
  private val lastFiredByRule = mutableMapOf<String, Int>()
  private var lastEmittedAtSec: Int? = null
  private var emittedCount = 0
  private var motivationCount = 0

  fun submit(events: List<AnalysisEvent>) {
    pending += events
  }

  fun arbitrate(activeSec: Int, modificationPending: Boolean): FeedbackItem? {
    pending.removeAll { it.expiresAtSec < activeSec }
    if (pending.isEmpty()) return null

    val budget = (totalDurationSec * verbosity / BUDGET_SECONDS_PER_ITEM).toInt().coerceAtLeast(4)
    val motivationBudget = (budget * motivationShare).toInt()

    val candidate = pending
      .filter { event ->
        val p0 = event.category.isP0
        if (!p0 && modificationPending) return@filter false
        val lastEmitted = lastEmittedAtSec
        if (!p0 && lastEmitted != null && activeSec - lastEmitted < GLOBAL_GAP_SEC) return@filter false
        if (!p0 && emittedCount >= budget) return@filter false
        if (event.category == FeedbackCategory.MOTIVATION && motivationCount >= motivationBudget) return@filter false
        val lastFired = lastFiredByRule[event.ruleId]
        val cooldown = if (p0) P0_REFIRE_SEC else categoryCooldownSec(event.category)
        lastFired == null || activeSec - lastFired >= cooldown
      }
      .maxByOrNull { it.category.tierBase + it.severity * 50 + (it.signalConfidence * 20).toInt() }
      ?: run {
        // Drop stale motivation; keep condition-backed candidates for the next cycle.
        pending.removeAll { it.category == FeedbackCategory.MOTIVATION }
        return null
      }

    pending.remove(candidate)
    pending.removeAll { it.category == FeedbackCategory.MOTIVATION }

    lastFiredByRule[candidate.ruleId] = activeSec
    lastEmittedAtSec = activeSec
    emittedCount++
    if (candidate.category == FeedbackCategory.MOTIVATION) motivationCount++

    return FeedbackItem(
      id = "${candidate.ruleId}-$activeSec",
      timestampSec = activeSec,
      category = candidate.category,
      severity = candidate.severity,
      message = candidate.message,
      ruleId = candidate.ruleId
    )
  }

  private fun categoryCooldownSec(category: FeedbackCategory): Int {
    val base = when (category) {
      FeedbackCategory.FATIGUE_MANAGEMENT -> 240
      FeedbackCategory.PACING -> 90
      FeedbackCategory.RECOVERY -> 120
      FeedbackCategory.TECHNIQUE -> 180
      FeedbackCategory.MOTIVATION -> 120
      else -> 60
    }
    return (base * cooldownScale).toInt()
  }

  companion object {
    const val GLOBAL_GAP_SEC = 45
    private const val P0_REFIRE_SEC = 60
    private const val BUDGET_SECONDS_PER_ITEM = 150
  }
}
