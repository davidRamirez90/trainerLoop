package com.trainerloop.domain.coach

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.WorkoutSegment
import kotlin.math.abs

/**
 * Runs once at workout load: classifies segments, detects interval sets, and
 * infers workout intent. Pure function of (segments, ftp).
 */
object WorkoutInterpreter {

  fun interpret(segments: List<WorkoutSegment>, ftp: Int): WorkoutPlanModel {
    val classified = classify(segments, ftp)
    val sets = detectSets(classified)
    val withSets = classified.map { seg ->
      seg.copy(setId = sets.entries.find { seg.index in it.value }?.key?.id)
    }
    val (tss, intensityFactor) = plannedLoad(withSets, ftp)
    return WorkoutPlanModel(
      intent = inferIntent(withSets, intensityFactor, ftp),
      segments = withSets,
      sets = sets.keys.toList(),
      plannedTss = tss,
      plannedIf = intensityFactor,
      totalDurationSec = segments.sumOf { it.durationSec }
    )
  }

  fun contextAt(plan: WorkoutPlanModel, elapsedSec: Int): IntervalContext? {
    val seg = plan.segments.lastOrNull { elapsedSec >= it.startSec }
      ?.takeIf { elapsedSec < it.endSec || it == plan.segments.last() }
      ?: return null
    val set = plan.sets.find { it.id == seg.setId }
    val setMembers = plan.segments.filter { it.setId == seg.setId && it.segment.isWork }
    val blockNumber = if (set != null && seg.segment.isWork) {
      setMembers.indexOfFirst { it.index == seg.index } + 1
    } else if (set != null) {
      // recovery inside a set belongs to the preceding work block
      setMembers.count { it.index < seg.index }
    } else 1
    val workSegments = plan.segments.filter { it.segment.isWork }
    val totalWeight = plan.segments.sumOf { loadWeight(it) }
    val weightAt = loadWeightUpTo(plan, elapsedSec)
    return IntervalContext(
      classified = seg,
      set = set,
      blockNumber = blockNumber.coerceAtLeast(1),
      blocksRemaining = if (set != null) (set.blockCount - blockNumber).coerceAtLeast(0) else 0,
      elapsedInSegmentSec = elapsedSec - seg.startSec,
      remainingInSegmentSec = (seg.endSec - elapsedSec).coerceAtLeast(0),
      workoutProgressPct = if (totalWeight > 0) (weightAt / totalWeight) * 100 else 0.0,
      isFinalWorkInterval = seg.segment.isWork && workSegments.lastOrNull()?.index == seg.index,
      nextSegmentClass = plan.segments.getOrNull(seg.index + 1)?.segmentClass,
      intent = plan.intent
    )
  }

  private fun classify(segments: List<WorkoutSegment>, ftp: Int): List<ClassifiedSegment> {
    var startSec = 0
    val firstWorkIndex = segments.indexOfFirst { it.isWork }
    val lastWorkIndex = segments.indexOfLast { it.isWork }
    return segments.mapIndexed { index, seg ->
      val mid = targetMid(seg)
      val pctFtp = if (ftp > 0) mid / ftp * 100 else 0.0
      val cls = when {
        seg is WorkoutSegment.FreeRide -> SegmentClass.FREE_RIDE
        seg.phase == SegmentPhase.WARMUP ||
          (firstWorkIndex >= 0 && index < firstWorkIndex && pctFtp < 76) -> SegmentClass.WARMUP
        seg.phase == SegmentPhase.COOLDOWN ||
          (lastWorkIndex >= 0 && index > lastWorkIndex && pctFtp < 76) -> SegmentClass.COOLDOWN
        !seg.isWork || pctFtp < 55 -> SegmentClass.RECOVERY
        pctFtp <= 75 -> SegmentClass.ENDURANCE
        pctFtp <= 87 -> SegmentClass.TEMPO
        pctFtp <= 94 -> SegmentClass.SWEET_SPOT
        pctFtp <= 105 -> SegmentClass.THRESHOLD
        pctFtp <= 120 -> SegmentClass.VO2MAX
        pctFtp <= 150 -> SegmentClass.ANAEROBIC
        else -> SegmentClass.SPRINT
      }
      ClassifiedSegment(
        index = index,
        segment = seg,
        segmentClass = cls,
        setId = null,
        startSec = startSec,
        endSec = startSec + seg.durationSec,
        targetMidWatts = mid
      ).also { startSec += seg.durationSec }
    }
  }

  /**
   * Consecutive (work, recovery) pairs with matching class and duration (±10%)
   * form one set. Returns set → member segment indices (work + recoveries).
   */
  private fun detectSets(classified: List<ClassifiedSegment>): Map<IntervalSet, List<Int>> {
    val sets = linkedMapOf<IntervalSet, List<Int>>()
    var i = 0
    var setCounter = 0
    while (i < classified.size) {
      val first = classified[i]
      if (!first.segment.isWork) { i++; continue }
      val members = mutableListOf(first.index)
      var blockCount = 1
      var j = i + 1
      while (j < classified.size) {
        val rec = classified.getOrNull(j)
        val nextWork = if (rec != null && !rec.segment.isWork &&
          rec.segmentClass in setOf(SegmentClass.RECOVERY, SegmentClass.WARMUP, SegmentClass.COOLDOWN)
        ) classified.getOrNull(j + 1) else rec
        if (nextWork == null || !nextWork.segment.isWork) break
        val matches = nextWork.segmentClass == first.segmentClass &&
          withinTolerance(nextWork.segment.durationSec, first.segment.durationSec)
        if (!matches) break
        if (nextWork !== rec) members.add(rec!!.index)
        members.add(nextWork.index)
        blockCount++
        j = nextWork.index + 1
      }
      if (blockCount >= 2) {
        sets[IntervalSet(
          id = "set-${setCounter++}",
          workClass = first.segmentClass,
          blockCount = blockCount,
          workDurationSec = first.segment.durationSec
        )] = members
        i = members.max() + 1
      } else {
        i++
      }
    }
    return sets
  }

  private fun withinTolerance(a: Int, b: Int): Boolean =
    abs(a - b) <= (b * 0.10).coerceAtLeast(2.0)

  private fun plannedLoad(segments: List<ClassifiedSegment>, ftp: Int): Pair<Double, Double> {
    if (ftp <= 0) return 0.0 to 0.0
    var tss = 0.0
    var weightedIfSq = 0.0
    var totalSec = 0
    segments.forEach { seg ->
      val segIf = seg.targetMidWatts / ftp
      tss += seg.segment.durationSec * segIf * segIf / 3600.0 * 100.0
      weightedIfSq += seg.segment.durationSec * segIf * segIf
      totalSec += seg.segment.durationSec
    }
    val intensityFactor = if (totalSec > 0) kotlin.math.sqrt(weightedIfSq / totalSec) else 0.0
    return tss to intensityFactor
  }

  private fun loadWeightUpTo(plan: WorkoutPlanModel, elapsedSec: Int): Double {
    var weight = 0.0
    plan.segments.forEach { seg ->
      if (elapsedSec <= seg.startSec) return weight
      val sec = minOf(elapsedSec, seg.endSec) - seg.startSec
      val fraction = if (seg.segment.durationSec > 0) sec.toDouble() / seg.segment.durationSec else 0.0
      weight += loadWeight(seg) * fraction.coerceIn(0.0, 1.0)
    }
    return weight
  }

  /**
   * Effort-weighted progress unit (W² · sec — proportional to planned TSS), so
   * "60% through" reflects the work, not just the time, in back-loaded workouts.
   */
  private fun loadWeight(seg: ClassifiedSegment): Double =
    seg.targetMidWatts * seg.targetMidWatts * seg.segment.durationSec

  private fun inferIntent(
    segments: List<ClassifiedSegment>,
    intensityFactor: Double,
    ftp: Int
  ): WorkoutIntent {
    val work = segments.filter { it.segment.isWork }
    val workTimeByClass = work.groupBy { it.segmentClass }
      .mapValues { (_, segs) -> segs.sumOf { it.segment.durationSec * (it.targetMidWatts / ftp.coerceAtLeast(1)) } }
    val totalWorkWeight = workTimeByClass.values.sum()
    val dominant = workTimeByClass.maxByOrNull { it.value }
    val dominantShare = if (totalWorkWeight > 0 && dominant != null) dominant.value / totalWorkWeight else 0.0

    val hasAboveZ2Work = work.any {
      it.segmentClass !in setOf(SegmentClass.ENDURANCE, SegmentClass.RECOVERY, SegmentClass.WARMUP, SegmentClass.COOLDOWN)
    }
    if (intensityFactor < 0.60 && !hasAboveZ2Work) return WorkoutIntent.RECOVERY

    val totalSec = segments.sumOf { it.segment.durationSec }
    val enduranceSec = segments.filter { it.segmentClass == SegmentClass.ENDURANCE }
      .sumOf { it.segment.durationSec }
    if (totalSec > 0 && enduranceSec.toDouble() / totalSec >= 0.70 && intensityFactor in 0.60..0.75) {
      return WorkoutIntent.AEROBIC_ENDURANCE
    }

    if (dominant == null || dominantShare < 0.50) return WorkoutIntent.MIXED
    return when (dominant.key) {
      SegmentClass.TEMPO, SegmentClass.SWEET_SPOT -> WorkoutIntent.TEMPO_SS
      SegmentClass.THRESHOLD -> WorkoutIntent.THRESHOLD_DEV
      SegmentClass.VO2MAX -> WorkoutIntent.VO2_DEV
      SegmentClass.ANAEROBIC -> WorkoutIntent.ANAEROBIC_CAP
      SegmentClass.SPRINT -> WorkoutIntent.NEUROMUSCULAR
      SegmentClass.ENDURANCE -> WorkoutIntent.AEROBIC_ENDURANCE
      else -> WorkoutIntent.MIXED
    }
  }

  private fun targetMid(seg: WorkoutSegment): Double = when (seg) {
    is WorkoutSegment.Step -> (seg.targetRange.low + seg.targetRange.high) / 2.0
    is WorkoutSegment.Ramp -> (seg.startPower + seg.endPower) / 2.0
    is WorkoutSegment.FreeRide -> 0.0
  }
}
