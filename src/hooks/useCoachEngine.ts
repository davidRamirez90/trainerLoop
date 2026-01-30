import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { TargetRange, WorkoutSegment } from '../data/workout';
import type { TelemetrySample } from '../types';
import type {
  CoachAction,
  CoachEvent,
  CoachProfile,
  CoachSuggestion,
} from '../types/coach';

type CoachEngineInput = {
  profile: CoachProfile | null;
  segments: WorkoutSegment[];
  segment: WorkoutSegment | undefined;
  segmentIndex: number;
  segmentStartSec: number;
  segmentEndSec: number;
  elapsedInSegmentSec: number;
  activeSec: number;
  isRunning: boolean;
  hasPlan: boolean;
  isComplete: boolean;
  targetRange: TargetRange;
  samples: TelemetrySample[];
  sessionId: number;
  intensityOffsetPct: number;
  ergEnabled?: boolean;
  onApplyAction?: (suggestion: CoachSuggestion) => void;
};

type WindowMetrics = {
  avgPower: number;
  adherencePct: number;
  cadenceVariance: number;
  hrDriftPct: number;
};

type IntervalSummary = {
  adherencePct: number;
  cadenceVariance: number;
  hrDriftPct: number;
};

const ADHERENCE_WINDOW_SEC = 30;
const STABILITY_WINDOW_SEC = 90;
const HR_DRIFT_WINDOW_SEC = 120;
const MIN_SAMPLES = 4;

const createId = () => {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const getWindowSamples = (
  samples: TelemetrySample[],
  fromSec: number,
  toSec: number
) =>
  samples.filter(
    (sample) => sample.timeSec >= fromSec && sample.timeSec <= toSec
  );

const average = (values: number[]) =>
  values.length
    ? values.reduce((sum, value) => sum + value, 0) / values.length
    : 0;

const stddev = (values: number[]) => {
  if (values.length === 0) {
    return 0;
  }
  const mean = average(values);
  const variance = average(values.map((value) => (value - mean) ** 2));
  return Math.sqrt(variance);
};

const computeMetrics = (
  samples: TelemetrySample[],
  fromSec: number,
  toSec: number,
  targetMid: number
): WindowMetrics | null => {
  const window = getWindowSamples(samples, fromSec, toSec);
  if (window.length < MIN_SAMPLES) {
    return null;
  }
  const avgPower = average(window.map((sample) => sample.powerWatts));
  const cadenceValues = window
    .map((sample) => sample.cadenceRpm)
    .filter((value) => value > 0);
  const hrValues = window
    .map((sample) => sample.hrBpm)
    .filter((value) => value > 0);
  const adherencePct = targetMid > 0 ? (avgPower / targetMid) * 100 : 0;
  const cadenceVariance = stddev(cadenceValues);
  const hrDriftPct =
    hrValues.length > 1
      ? ((Math.max(...hrValues) - Math.min(...hrValues)) / Math.min(...hrValues)) *
        100
      : 0;

  return {
    avgPower,
    adherencePct,
    cadenceVariance,
    hrDriftPct,
  };
};

const pickMessage = (messages: string[], fallback: string) => {
  if (!messages.length) {
    return fallback;
  }
  return messages[Math.floor(Math.random() * messages.length)];
};

const applyTemplate = (
  template: string,
  data: { percent?: number; seconds?: number }
) =>
  template
    .replace('{{percent}}', data.percent !== undefined ? `${data.percent}` : '')
    .replace('{{seconds}}', data.seconds !== undefined ? `${data.seconds}` : '');

const buildSuggestionMessage = (
  profile: CoachProfile,
  action: CoachAction,
  payload: { percent?: number; seconds?: number }
) => {
  const key = action;
  const templates = profile.messages.suggestions[key];
  const fallback = 'Coach suggestion available.';
  return applyTemplate(pickMessage(templates, fallback), payload);
};

const buildRationaleMessage = (profile: CoachProfile, action: CoachAction) => {
  const key = `${action}_rationale` as keyof typeof profile.messages.suggestions;
  const templates = profile.messages.suggestions[key];
  const fallbacks: Record<CoachAction, string> = {
    adjust_intensity_up: 'Metrics indicate you can handle more intensity.',
    adjust_intensity_down: 'Fatigue indicators suggest reducing intensity.',
    extend_recovery: 'Recovery metrics indicate more time needed.',
    skip_remaining_on_intervals: 'Multiple indicators suggest terminating the session.',
  };
  return pickMessage(templates, fallbacks[action]);
};

const buildCompletionMessage = (profile: CoachProfile) =>
  pickMessage(profile.messages.completion, 'Session complete.');

const getTargetMid = (range: TargetRange) => (range.low + range.high) / 2;

export const useCoachEngine = ({
  profile,
  segments,
  segment,
  segmentIndex,
  segmentStartSec,
  segmentEndSec,
  elapsedInSegmentSec,
  activeSec,
  isRunning,
  hasPlan,
  isComplete,
  targetRange,
  samples,
  sessionId,
  intensityOffsetPct,
  ergEnabled,
  onApplyAction,
}: CoachEngineInput) => {
  const [suggestions, setSuggestions] = useState<CoachSuggestion[]>([]);
  const [events, setEvents] = useState<CoachEvent[]>([]);

  const suggestionsRef = useRef<CoachSuggestion[]>([]);
  const lastSuggestionAtRef = useRef<number | null>(null);
  const lastSegmentRef = useRef<{
    id: string;
    index: number;
    isWork: boolean;
    startSec: number;
    endSec: number;
    phase: string;
  } | null>(null);
  const completedWorkIntervalsRef = useRef<IntervalSummary[]>([]);
  const completionLoggedRef = useRef(false);

  useEffect(() => {
    suggestionsRef.current = suggestions;
  }, [suggestions]);

  const pendingSuggestion = useMemo(
    () => suggestions.find((item) => item.status === 'pending') ?? null,
    [suggestions]
  );

  const canSuggest = useCallback(() => {
    if (!profile || !hasPlan || !isRunning || !segment) {
      return false;
    }
    if (activeSec < profile.rules.minElapsedSecondsForSuggestions) {
      return false;
    }
    if (pendingSuggestion) {
      return false;
    }
    const lastSuggestionAt = lastSuggestionAtRef.current;
    if (
      lastSuggestionAt !== null &&
      activeSec - lastSuggestionAt < profile.rules.cooldownSeconds
    ) {
      return false;
    }
    return true;
  }, [activeSec, hasPlan, isRunning, pendingSuggestion, profile, segment]);

  const addEvent = useCallback((event: CoachEvent) => {
    setEvents((prev) => [...prev, event]);
  }, []);

  const addSuggestion = useCallback(
    (suggestion: CoachSuggestion) => {
      setSuggestions((prev) => [...prev, suggestion]);
      addEvent({
        id: createId(),
        kind: 'suggestion',
        timestampSec: suggestion.createdAtSec,
        message: suggestion.message,
        suggestionId: suggestion.id,
        action: suggestion.action,
        payload: suggestion.payload,
      });
      lastSuggestionAtRef.current = suggestion.createdAtSec;
    },
    [addEvent]
  );

  const acceptSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestionsRef.current.find(
        (item) => item.id === suggestionId
      );
      if (!suggestion || suggestion.status !== 'pending') {
        return;
      }
      setSuggestions((prev) =>
        prev.map((item) =>
          item.id === suggestionId ? { ...item, status: 'accepted' } : item
        )
      );
      addEvent({
        id: createId(),
        kind: 'decision',
        timestampSec: activeSec,
        message: `Accepted: ${suggestion.message}`,
        suggestionId,
        action: suggestion.action,
        payload: suggestion.payload,
        decision: 'accepted',
      });
      onApplyAction?.({ ...suggestion, status: 'accepted' });
    },
    [activeSec, addEvent, onApplyAction]
  );

  const rejectSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestionsRef.current.find(
        (item) => item.id === suggestionId
      );
      if (!suggestion || suggestion.status !== 'pending') {
        return;
      }
      setSuggestions((prev) =>
        prev.map((item) =>
          item.id === suggestionId ? { ...item, status: 'rejected' } : item
        )
      );
      addEvent({
        id: createId(),
        kind: 'decision',
        timestampSec: activeSec,
        message: `Rejected: ${suggestion.message}`,
        suggestionId,
        action: suggestion.action,
        payload: suggestion.payload,
        decision: 'rejected',
      });
    },
    [activeSec, addEvent]
  );

  useEffect(() => {
    setSuggestions([]);
    setEvents([]);
    lastSuggestionAtRef.current = null;
    lastSegmentRef.current = null;
    completedWorkIntervalsRef.current = [];
    completionLoggedRef.current = false;
  }, [sessionId]);

  useEffect(() => {
    if (!profile || !segment || !hasPlan) {
      return;
    }
    const previous = lastSegmentRef.current;
    if (previous && previous.id !== segment.id && previous.isWork) {
      const previousSegment = segments[previous.index];
      const targetMid = previousSegment
        ? getTargetMid(previousSegment.targetRange)
        : 0;
      const metrics = computeMetrics(
        samples,
        previous.startSec,
        previous.endSec,
        targetMid
      );
      if (metrics) {
        completedWorkIntervalsRef.current = [
          ...completedWorkIntervalsRef.current,
          {
            adherencePct: metrics.adherencePct,
            cadenceVariance: metrics.cadenceVariance,
            hrDriftPct: metrics.hrDriftPct,
          },
        ].slice(-8);
      }

      if (
        segment.phase === 'recovery' &&
        metrics &&
        canSuggest() &&
        metrics.adherencePct <= profile.rules.targetAdherencePct.intervene &&
        metrics.hrDriftPct >= profile.rules.hrDriftPct.warn
      ) {
        const seconds = profile.interventions.recoveryExtendSec.step;
        addSuggestion({
          id: createId(),
          action: 'extend_recovery',
          payload: {
            seconds,
            segmentId: segment.id,
            segmentIndex,
          },
          message: buildSuggestionMessage(profile, 'extend_recovery', { seconds }),
          rationale: buildRationaleMessage(profile, 'extend_recovery'),
          createdAtSec: activeSec,
          status: 'pending',
        });
      }

      const failedIntervals = completedWorkIntervalsRef.current.slice(-2);
      const rejectDownSuggestions = suggestionsRef.current
        .filter(
          (item) =>
            item.action === 'adjust_intensity_down' && item.status === 'rejected'
        )
        .slice(-2);
      const allowSkip = profile.interventions.allowSkipRemainingOnIntervals;
      const meetsFailure =
        failedIntervals.length === 2 &&
        failedIntervals.every(
          (interval) =>
            interval.adherencePct <= profile.rules.targetAdherencePct.intervene ||
            interval.hrDriftPct >= profile.rules.hrDriftPct.intervene ||
            interval.cadenceVariance >=
              profile.rules.cadenceVarianceRpm.intervene
        );
      if (
        allowSkip &&
        failedIntervals.length >= 2 &&
        meetsFailure &&
        rejectDownSuggestions.length >= 2 &&
        canSuggest()
      ) {
        addSuggestion({
          id: createId(),
          action: 'skip_remaining_on_intervals',
          payload: {
            segmentIndex,
          },
          message: buildSuggestionMessage(profile, 'skip_remaining_on_intervals', {}),
          rationale: buildRationaleMessage(profile, 'skip_remaining_on_intervals'),
          createdAtSec: activeSec,
          status: 'pending',
        });
      }
    }

    lastSegmentRef.current = {
      id: segment.id,
      index: segmentIndex,
      isWork: segment.isWork,
      startSec: segmentStartSec,
      endSec: segmentEndSec,
      phase: segment.phase,
    };
  }, [
    activeSec,
    addSuggestion,
    canSuggest,
    hasPlan,
    profile,
    segment,
    segmentEndSec,
    segmentIndex,
    segmentStartSec,
    segments,
    samples,
  ]);

  useEffect(() => {
    if (!profile || !segment || !hasPlan || !isRunning) {
      return;
    }
    if (!segment.isWork) {
      return;
    }
    if (!canSuggest()) {
      return;
    }
    if (elapsedInSegmentSec < ADHERENCE_WINDOW_SEC) {
      return;
    }

    const targetMid = getTargetMid(targetRange);
    const recentMetrics = computeMetrics(
      samples,
      Math.max(0, activeSec - ADHERENCE_WINDOW_SEC),
      activeSec,
      targetMid
    );
    const stabilityMetrics = computeMetrics(
      samples,
      Math.max(0, activeSec - STABILITY_WINDOW_SEC),
      activeSec,
      targetMid
    );
    const driftMetrics = computeMetrics(
      samples,
      Math.max(0, activeSec - HR_DRIFT_WINDOW_SEC),
      activeSec,
      targetMid
    );
    if (!recentMetrics || !stabilityMetrics || !driftMetrics) {
      return;
    }

    const canAdjustUp =
      intensityOffsetPct + profile.interventions.intensityAdjustPct.step <=
      profile.interventions.intensityAdjustPct.max;
    const canAdjustDown =
      intensityOffsetPct - profile.interventions.intensityAdjustPct.step >=
      profile.interventions.intensityAdjustPct.min;

    const reduceCondition =
      (recentMetrics.adherencePct <= profile.rules.targetAdherencePct.intervene ||
        ergEnabled) &&
      (driftMetrics.hrDriftPct >= profile.rules.hrDriftPct.intervene ||
        driftMetrics.cadenceVariance >=
          profile.rules.cadenceVarianceRpm.intervene);

    if (reduceCondition && canAdjustDown) {
      const percent = profile.interventions.intensityAdjustPct.step;
      addSuggestion({
        id: createId(),
        action: 'adjust_intensity_down',
        payload: { percent, segmentIndex },
        message: buildSuggestionMessage(profile, 'adjust_intensity_down', {
          percent,
        }),
        rationale: buildRationaleMessage(profile, 'adjust_intensity_down'),
        createdAtSec: activeSec,
        status: 'pending',
      });
      return;
    }

    const recentFailures = suggestionsRef.current
      .filter((item) => item.status === 'rejected')
      .slice(-2);
    const allowIncrease =
      recentFailures.length < 2 &&
      elapsedInSegmentSec >= 45 &&
      stabilityMetrics.adherencePct >= profile.rules.targetAdherencePct.warn &&
      stabilityMetrics.cadenceVariance <=
        profile.rules.cadenceVarianceRpm.warn &&
      driftMetrics.hrDriftPct <= profile.rules.hrDriftPct.warn;

    if (allowIncrease && canAdjustUp) {
      const percent = profile.interventions.intensityAdjustPct.step;
      addSuggestion({
        id: createId(),
        action: 'adjust_intensity_up',
        payload: { percent, segmentIndex },
        message: buildSuggestionMessage(profile, 'adjust_intensity_up', {
          percent,
        }),
        rationale: buildRationaleMessage(profile, 'adjust_intensity_up'),
        createdAtSec: activeSec,
        status: 'pending',
      });
    }
  }, [
    activeSec,
    addSuggestion,
    canSuggest,
    elapsedInSegmentSec,
    ergEnabled,
    hasPlan,
    intensityOffsetPct,
    isRunning,
    profile,
    segment,
    segmentIndex,
    samples,
    targetRange,
  ]);

  useEffect(() => {
    if (!profile || !hasPlan || !isComplete || completionLoggedRef.current) {
      return;
    }
    addEvent({
      id: createId(),
      kind: 'completion',
      timestampSec: activeSec,
      message: buildCompletionMessage(profile),
    });
    completionLoggedRef.current = true;
  }, [activeSec, addEvent, hasPlan, isComplete, profile]);

  return {
    suggestions,
    events,
    acceptSuggestion,
    rejectSuggestion,
  };
};
