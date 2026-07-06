package com.trainerloop.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackDecisionEngineTest {

  private fun event(
    ruleId: String,
    category: FeedbackCategory = FeedbackCategory.PACING,
    severity: Int = 1,
    expiresAtSec: Int = 10_000
  ) = AnalysisEvent(
    ruleId = ruleId, category = category, severity = severity,
    message = ruleId, expiresAtSec = expiresAtSec
  )

  @Test
  fun `highest tier wins`() {
    val engine = FeedbackDecisionEngine(3600)
    engine.submit(
      listOf(
        event("mot", FeedbackCategory.MOTIVATION, 0),
        event("safety", FeedbackCategory.SAFETY, 3),
        event("pacing", FeedbackCategory.PACING, 2)
      )
    )
    assertEquals("safety", engine.arbitrate(100, modificationPending = false)?.ruleId)
  }

  @Test
  fun `global gap blocks non-P0 but not P0`() {
    val engine = FeedbackDecisionEngine(3600)
    engine.submit(listOf(event("pacing")))
    assertNotNull(engine.arbitrate(100, false))

    engine.submit(listOf(event("technique", FeedbackCategory.TECHNIQUE)))
    assertNull(engine.arbitrate(110, false)) // < 45 s later

    engine.submit(listOf(event("safety", FeedbackCategory.SAFETY, 3)))
    assertNotNull(engine.arbitrate(112, false)) // P0 exempt
  }

  @Test
  fun `pending modification blocks non-P0`() {
    val engine = FeedbackDecisionEngine(3600)
    engine.submit(listOf(event("pacing")))
    assertNull(engine.arbitrate(100, modificationPending = true))
    engine.submit(listOf(event("safety", FeedbackCategory.SAFETY, 3)))
    assertNotNull(engine.arbitrate(105, modificationPending = true))
  }

  @Test
  fun `rule cooldown suppresses re-fire`() {
    val engine = FeedbackDecisionEngine(3600)
    engine.submit(listOf(event("pacing")))
    assertNotNull(engine.arbitrate(100, false))
    engine.submit(listOf(event("pacing")))
    assertNull(engine.arbitrate(150, false)) // pacing cooldown 90 s
    engine.submit(listOf(event("pacing")))
    assertNotNull(engine.arbitrate(200, false))
  }

  @Test
  fun `expired events are dropped`() {
    val engine = FeedbackDecisionEngine(3600)
    engine.submit(listOf(event("pacing", expiresAtSec = 50)))
    assertNull(engine.arbitrate(100, false))
  }

  @Test
  fun `session budget caps emissions`() {
    val engine = FeedbackDecisionEngine(600) // tiny workout → budget = 4
    var emitted = 0
    var t = 0
    repeat(20) { i ->
      t += 100
      engine.submit(listOf(event("rule-$i", FeedbackCategory.PACING)))
      if (engine.arbitrate(t, false) != null) emitted++
    }
    assertEquals(4, emitted)
  }
}
