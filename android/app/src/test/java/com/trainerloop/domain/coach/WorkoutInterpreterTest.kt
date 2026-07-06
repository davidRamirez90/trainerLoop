package com.trainerloop.domain.coach

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutInterpreterTest {

  private val ftp = 250

  private fun step(
    id: String,
    durationSec: Int,
    watts: Int,
    phase: SegmentPhase = SegmentPhase.WORK,
    isWork: Boolean = phase == SegmentPhase.WORK
  ) = WorkoutSegment.Step(
    id = id, durationSec = durationSec, label = null, phase = phase,
    isWork = isWork, targetRange = TargetRange(watts, watts)
  )

  private fun vo2Workout(): List<WorkoutSegment> {
    val segments = mutableListOf<WorkoutSegment>(
      step("wu", 600, 130, SegmentPhase.WARMUP)
    )
    repeat(5) { i ->
      segments += step("work$i", 240, 300)
      segments += step("rec$i", 240, 100, SegmentPhase.RECOVERY)
    }
    segments += step("cd", 600, 100, SegmentPhase.COOLDOWN)
    return segments
  }

  @Test
  fun `classifies vo2 workout segments`() {
    val plan = WorkoutInterpreter.interpret(vo2Workout(), ftp)
    assertEquals(SegmentClass.WARMUP, plan.segments.first().segmentClass)
    assertEquals(SegmentClass.VO2MAX, plan.segments[1].segmentClass) // 300 W = 120% FTP
    assertEquals(SegmentClass.RECOVERY, plan.segments[2].segmentClass)
    assertEquals(SegmentClass.COOLDOWN, plan.segments.last().segmentClass)
  }

  @Test
  fun `detects 5x4 interval set`() {
    val plan = WorkoutInterpreter.interpret(vo2Workout(), ftp)
    assertEquals(1, plan.sets.size)
    assertEquals(5, plan.sets.first().blockCount)
    assertEquals(SegmentClass.VO2MAX, plan.sets.first().workClass)
  }

  @Test
  fun `infers vo2 intent`() {
    val plan = WorkoutInterpreter.interpret(vo2Workout(), ftp)
    assertEquals(WorkoutIntent.VO2_DEV, plan.intent)
  }

  @Test
  fun `infers endurance intent`() {
    val plan = WorkoutInterpreter.interpret(listOf(step("z2", 3600, 162)), ftp)
    assertEquals(WorkoutIntent.AEROBIC_ENDURANCE, plan.intent)
    assertEquals(SegmentClass.ENDURANCE, plan.segments.first().segmentClass)
  }

  @Test
  fun `infers recovery intent`() {
    val plan = WorkoutInterpreter.interpret(listOf(step("easy", 1800, 125)), ftp)
    assertEquals(WorkoutIntent.RECOVERY, plan.intent)
  }

  @Test
  fun `context reports block numbers and final interval`() {
    val plan = WorkoutInterpreter.interpret(vo2Workout(), ftp)
    // Third work interval starts at 600 + 2*(240+240) = 1560
    val ctx = WorkoutInterpreter.contextAt(plan, 1600)
    assertNotNull(ctx)
    assertEquals(3, ctx!!.blockNumber)
    assertEquals(2, ctx.blocksRemaining)
    assertEquals(SegmentClass.VO2MAX, ctx.segmentClass)
    assertTrue(!ctx.isFinalWorkInterval)

    // Fifth work interval starts at 600 + 4*480 = 2520
    val last = WorkoutInterpreter.contextAt(plan, 2560)!!
    assertEquals(5, last.blockNumber)
    assertTrue(last.isFinalWorkInterval)
  }

  @Test
  fun `progress is effort-weighted`() {
    val plan = WorkoutInterpreter.interpret(vo2Workout(), ftp)
    // Halfway by time (t=2100, total 3600) is past halfway by effort:
    // the cooldown contributes almost nothing.
    val ctx = WorkoutInterpreter.contextAt(plan, 1800)!!
    assertTrue(ctx.workoutProgressPct > 50.0)
  }

  @Test
  fun `planned tss is plausible`() {
    val plan = WorkoutInterpreter.interpret(vo2Workout(), ftp)
    assertTrue("tss=${plan.plannedTss}", plan.plannedTss in 40.0..120.0)
    assertTrue("if=${plan.plannedIf}", plan.plannedIf in 0.6..1.1)
  }
}
