package com.trainerloop.domain.coach

import com.trainerloop.data.model.WorkoutSegment

enum class SegmentClass {
  WARMUP, RECOVERY, ENDURANCE, TEMPO, SWEET_SPOT, THRESHOLD,
  VO2MAX, ANAEROBIC, SPRINT, COOLDOWN, FREE_RIDE
}

enum class WorkoutIntent {
  RECOVERY, AEROBIC_ENDURANCE, TEMPO_SS, THRESHOLD_DEV,
  VO2_DEV, ANAEROBIC_CAP, NEUROMUSCULAR, MIXED
}

data class ClassifiedSegment(
  val index: Int,
  val segment: WorkoutSegment,
  val segmentClass: SegmentClass,
  val setId: String?,
  val startSec: Int,
  val endSec: Int,
  val targetMidWatts: Double
)

data class IntervalSet(
  val id: String,
  val workClass: SegmentClass,
  val blockCount: Int,
  val workDurationSec: Int
)

data class WorkoutPlanModel(
  val intent: WorkoutIntent,
  val segments: List<ClassifiedSegment>,
  val sets: List<IntervalSet>,
  val plannedTss: Double,
  val plannedIf: Double,
  val totalDurationSec: Int
)

data class IntervalContext(
  val classified: ClassifiedSegment,
  val set: IntervalSet?,
  val blockNumber: Int,
  val blocksRemaining: Int,
  val elapsedInSegmentSec: Int,
  val remainingInSegmentSec: Int,
  val workoutProgressPct: Double,
  val isFinalWorkInterval: Boolean,
  val nextSegmentClass: SegmentClass?,
  val intent: WorkoutIntent
) {
  val segmentClass: SegmentClass get() = classified.segmentClass
  val isWork: Boolean get() = classified.segment.isWork
}

data class ExpectationEnvelope(
  val hrBand: ClosedRange<Double>?,
  val powerBand: ClosedRange<Double>,
  val cadenceBand: ClosedRange<Double>?,
  val driftAllowancePct: Double,
  val anchorQuality: Double
)

enum class FeedbackCategory(val tierBase: Int, val isP0: Boolean = false) {
  SAFETY(1000, isP0 = true),
  DATA_QUALITY(950, isP0 = true),
  WORKOUT_MODIFICATION(800),
  FATIGUE_MANAGEMENT(600),
  PACING(400),
  RECOVERY(380),
  TECHNIQUE(300),
  MOTIVATION(100)
}

/** Rule output, pre-arbitration. */
data class AnalysisEvent(
  val ruleId: String,
  val category: FeedbackCategory,
  val severity: Int,
  val message: String,
  val signalConfidence: Double = 1.0,
  /** Active-time after which the event is stale and dropped. */
  val expiresAtSec: Int
)

/** Arbitration output — what the athlete actually sees. */
data class FeedbackItem(
  val id: String,
  val timestampSec: Int,
  val category: FeedbackCategory,
  val severity: Int,
  val message: String,
  val ruleId: String
)

data class IntervalRecord(
  val setId: String?,
  val blockNumber: Int,
  val segmentClass: SegmentClass,
  val targetMidWatts: Double,
  val avgPower: Double,
  val adherencePct: Double,
  val timeInTargetPct: Double,
  val powerCv: Double,
  val avgHr: Double?,
  val endHr: Double?,
  val hrDriftPct: Double?,
  val avgCadence: Double?
)

data class RecoveryRecord(
  val startHr: Double?,
  val hrr60: Double?,
  val endHr: Double?
)
