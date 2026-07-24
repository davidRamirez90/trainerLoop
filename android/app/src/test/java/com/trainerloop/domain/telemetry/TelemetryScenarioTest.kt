package com.trainerloop.domain.telemetry

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryScenarioTest {
  private val target = TargetRange(200, 220)

  @Test
  fun `good adherence is deterministic and stays near target`() {
    val first = TelemetryScenarioGenerator.sample(
      TelemetryScenario.GOOD_ADHERENCE,
      elapsedSec = 30,
      targetRange = target,
      phase = SegmentPhase.WORK
    )
    val second = TelemetryScenarioGenerator.sample(
      TelemetryScenario.GOOD_ADHERENCE,
      elapsedSec = 30,
      targetRange = target,
      phase = SegmentPhase.WORK
    )

    assertEquals(first, second)
    assertTrue(first.powerWatts in target.low..target.high)
    assertTrue(first.cadenceRpm in 88..90)
    assertFalse(first.dropout)
  }

  @Test
  fun `low cadence scenario builds heart-rate drift while holding power`() {
    val early = TelemetryScenarioGenerator.sample(
      TelemetryScenario.LOW_CADENCE_HR_DRIFT,
      elapsedSec = 10,
      targetRange = target,
      phase = SegmentPhase.WORK
    )
    val late = TelemetryScenarioGenerator.sample(
      TelemetryScenario.LOW_CADENCE_HR_DRIFT,
      elapsedSec = 70,
      targetRange = target,
      phase = SegmentPhase.WORK
    )

    assertTrue(late.cadenceRpm < early.cadenceRpm)
    assertTrue(late.hrBpm > early.hrBpm)
    assertTrue(late.powerWatts in (target.low - 5)..(target.high + 5))
  }

  @Test
  fun `high recovery HR stays elevated only in recovery`() {
    val work = TelemetryScenarioGenerator.sample(
      TelemetryScenario.HIGH_RECOVERY_HR,
      elapsedSec = 30,
      targetRange = target,
      phase = SegmentPhase.WORK
    )
    val recovery = TelemetryScenarioGenerator.sample(
      TelemetryScenario.HIGH_RECOVERY_HR,
      elapsedSec = 30,
      targetRange = TargetRange(100, 120),
      phase = SegmentPhase.RECOVERY
    )

    assertTrue(recovery.hrBpm > work.hrBpm)
    assertEquals(76, recovery.cadenceRpm)
  }

  @Test
  fun `dropout fixture follows a repeatable five-second window`() {
    val clean = TelemetryScenarioGenerator.sample(
      TelemetryScenario.SENSOR_DROPOUT,
      elapsedSec = 14,
      targetRange = target,
      phase = SegmentPhase.WORK
    )
    val dropout = TelemetryScenarioGenerator.sample(
      TelemetryScenario.SENSOR_DROPOUT,
      elapsedSec = 17,
      targetRange = target,
      phase = SegmentPhase.WORK
    )
    val recovered = TelemetryScenarioGenerator.sample(
      TelemetryScenario.SENSOR_DROPOUT,
      elapsedSec = 20,
      targetRange = target,
      phase = SegmentPhase.WORK
    )

    assertFalse(clean.dropout)
    assertTrue(dropout.dropout)
    assertFalse(recovered.dropout)
  }
}
