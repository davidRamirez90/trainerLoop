import type { ChangeEvent, CSSProperties } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import './App.css';
import { WorkoutChart } from './components/WorkoutChart';
import { CoachPanel } from './components/CoachPanel';
import type { WorkoutPlan, WorkoutSegment } from './data/workout';
import { useCoachEngine } from './hooks/useCoachEngine';
import { useBluetoothDevices } from './hooks/useBluetoothDevices';
import { useBluetoothTelemetry } from './hooks/useBluetoothTelemetry';
import { useFtmsControl } from './hooks/useFtmsControl';
import { useTelemetryProcessing } from './hooks/useTelemetryProcessing';
import { useWorkoutClock } from './hooks/useWorkoutClock';
import type { CoachSuggestion } from './types/coach';
import { getCoachProfileById, getCoachProfiles } from './utils/coachProfiles';
import { buildCoachNotes } from './utils/coachNotes';
import { formatDuration } from './utils/time';
import { buildFitFile } from './utils/fit';
import { parseWorkoutFile } from './utils/workoutImport';
import { getTargetRangeAtTime, getTotalDurationSec } from './utils/workout';
import { buildSessionSummary, addSessionToStorage, type SessionData } from './utils/sessionStorage';
import type { TelemetrySample } from './types';

const IDLE_SEGMENT: WorkoutSegment = {
  id: 'idle',
  label: 'Idle',
  durationSec: 1,
  targetRange: { low: 0, high: 0 },
  phase: 'warmup',
  isWork: false,
};

const EMPTY_SEGMENTS: WorkoutSegment[] = [];
const IDLE_SEGMENTS: WorkoutSegment[] = [IDLE_SEGMENT];

const AUTO_PAUSE_THRESHOLD_SEC = 5;
const DEFAULT_CADENCE_RANGE = { low: 70, high: 100 };
const CADENCE_RANGE_BUFFER_RPM = 4;
const CADENCE_RANGE_RELAX_RATE = 0.08;
const FREE_RIDE_EXTENSION_SEC = 900;
const FREE_RIDE_EXTENSION_BUFFER_SEC = 60;
const ERG_RAMP_SEC = 12;
const ERG_START_TARGET_WATTS = 50;

const pad2 = (value: number) => String(value).padStart(2, '0');

const buildFitFilename = (planName: string, startTimeMs: number) => {
  const safeName = planName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  const date = new Date(startTimeMs);
  const stamp = `${date.getFullYear()}${pad2(date.getMonth() + 1)}${pad2(
    date.getDate()
  )}-${pad2(date.getHours())}${pad2(date.getMinutes())}`;
  return `${safeName || 'trainer-loop'}-${stamp}.fit`;
};

const downloadFitFile = (payload: Uint8Array, filename: string) => {
  const buffer = payload.buffer as ArrayBuffer;
  const slice = buffer.slice(payload.byteOffset, payload.byteOffset + payload.byteLength);
  const blob = new Blob([slice], { type: 'application/octet-stream' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
};

type ZoneRange = {
  label: string;
  low: string;
  high: string;
};

type ZoneTemplate = {
  name: string;
  zones: {
    label: string;
    low: number;
    high: number | null;
  }[];
};

type UserProfile = {
  nickname: string;
  weightKg: string;
  ftpWatts: string;
  ergBiasWatts: string;
  thresholdHr: string;
  maxHr: string;
  hrZones: ZoneRange[];
  powerZones: ZoneRange[];
};

type FtpChoice = {
  workoutPlan: WorkoutPlan;
  profilePlan: WorkoutPlan;
  workoutFtpWatts: number;
  profileFtpWatts: number;
  workoutSource: 'file' | 'default';
  selected: 'workout' | 'profile';
};

type IntensityOverride = {
  fromIndex: number;
  offsetPct: number;
};

const DEFAULT_HR_ZONE_LABELS = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'];
const DEFAULT_POWER_ZONE_LABELS = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5', 'Z6', 'Z7'];
const HR_ZONE_COLORS = [
  '#6c7a89',
  '#3b8ea5',
  '#5faf5f',
  '#c9a227',
  '#e57a1f',
  '#d64541',
  '#8c2a2a',
];
const COGGAN_HR_TEMPLATE: ZoneTemplate = {
  name: 'Coggan 5',
  zones: [
    { label: 'Z1', low: 0.0, high: 0.68 },
    { label: 'Z2', low: 0.69, high: 0.83 },
    { label: 'Z3', low: 0.84, high: 0.94 },
    { label: 'Z4', low: 0.95, high: 1.05 },
    { label: 'Z5', low: 1.06, high: 1.2 },
  ],
};
const EIGHTY_TWENTY_POWER_TEMPLATE: ZoneTemplate = {
  name: '80/20',
  zones: [
    { label: 'Z1', low: 0.5, high: 0.7 },
    { label: 'Z2', low: 0.7, high: 0.83 },
    { label: 'X', low: 0.83, high: 0.91 },
    { label: 'Z3', low: 0.91, high: 1.0 },
    { label: 'Y', low: 1.0, high: 1.02 },
    { label: 'Z4', low: 1.02, high: 1.1 },
    { label: 'Z5', low: 1.1, high: null },
  ],
};

const buildZones = (labels: string[]): ZoneRange[] => {
  return labels.map((label) => ({
    label,
    low: '',
    high: '',
  }));
};

const buildEmptyProfile = (): UserProfile => ({
  nickname: '',
  weightKg: '',
  ftpWatts: '',
  ergBiasWatts: '',
  thresholdHr: '',
  maxHr: '',
  hrZones: buildZones(DEFAULT_HR_ZONE_LABELS),
  powerZones: buildZones(DEFAULT_POWER_ZONE_LABELS),
});

const sanitizeZones = (zones: ZoneRange[] | undefined, fallback: string[]) => {
  if (!zones?.length) {
    return buildZones(fallback);
  }
  return zones.map((zone, index) => ({
    label: zone.label ?? fallback[index] ?? `Z${index + 1}`,
    low: zone.low ?? '',
    high: zone.high ?? '',
  }));
};

const PROFILE_STORAGE_KEY = 'trainerLoop.profile.v1';
const COACH_PROFILE_STORAGE_KEY = 'trainerLoop.coachProfileId.v1';

const loadProfileFromStorage = (): UserProfile => {
  if (typeof window === 'undefined') {
    return buildEmptyProfile();
  }
  try {
    const raw = window.localStorage.getItem(PROFILE_STORAGE_KEY);
    if (!raw) {
      return buildEmptyProfile();
    }
    const parsed = JSON.parse(raw) as UserProfile;
    return {
      ...buildEmptyProfile(),
      ...parsed,
      hrZones: sanitizeZones(parsed.hrZones, DEFAULT_HR_ZONE_LABELS),
      powerZones: sanitizeZones(parsed.powerZones, DEFAULT_POWER_ZONE_LABELS),
    };
  } catch {
    return buildEmptyProfile();
  }
};

const saveProfileToStorage = (profile: UserProfile) => {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
  } catch {
    // Ignore storage errors (quota or unavailable).
  }
};

const loadCoachProfileIdFromStorage = (): string | null => {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    return window.localStorage.getItem(COACH_PROFILE_STORAGE_KEY);
  } catch {
    return null;
  }
};

const saveCoachProfileIdToStorage = (profileId: string) => {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(COACH_PROFILE_STORAGE_KEY, profileId);
  } catch {
    // Ignore storage errors.
  }
};

const cloneProfile = (profile: UserProfile): UserProfile => ({
  ...profile,
  hrZones: profile.hrZones.map((zone) => ({ ...zone })),
  powerZones: profile.powerZones.map((zone) => ({ ...zone })),
});

const parsePositiveNumber = (value: string) => {
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null;
  }
  return parsed;
};

const parseNumber = (value: string) => {
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  return parsed;
};

const formatSignedWatts = (value: number) =>
  `${value > 0 ? '+' : ''}${value}W`;

const clampValue = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

const getIntensityOffsetForIndex = (
  overrides: IntensityOverride[],
  index: number
) => {
  if (!overrides.length) {
    return 0;
  }
  const sorted = [...overrides].sort((a, b) => a.fromIndex - b.fromIndex);
  const match = [...sorted].reverse().find((item) => item.fromIndex <= index);
  return match?.offsetPct ?? 0;
};

const parseZoneBoundary = (value: string) => {
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  return parsed;
};

const getHrZone = (value: number | null, zones: ZoneRange[]) => {
  if (value === null) {
    return null;
  }
  for (let index = 0; index < zones.length; index += 1) {
    const zone = zones[index];
    const low = parseZoneBoundary(zone.low);
    const high = parseZoneBoundary(zone.high);
    if (low === null && high === null) {
      continue;
    }
    const minValue = low ?? 0;
    const maxValue = high ?? Number.POSITIVE_INFINITY;
    if (value >= minValue && value <= maxValue) {
      return {
        label: zone.label,
        color: HR_ZONE_COLORS[index] ?? HR_ZONE_COLORS[HR_ZONE_COLORS.length - 1],
      };
    }
  }
  return null;
};

const formatZoneValue = (value: number) => `${Math.round(value)}`;

const buildZonesFromTemplate = (
  base: number,
  template: ZoneTemplate,
  options?: { capLast?: number }
): ZoneRange[] => {
  const lastIndex = template.zones.length - 1;
  return template.zones.map((zone, index) => {
    if (zone.high === null) {
      return {
        label: zone.label,
        low: formatZoneValue(base * zone.low),
        high: '',
      };
    }
    const highValue =
      options?.capLast !== undefined && index === lastIndex
        ? options.capLast
        : base * zone.high;
    return {
      label: zone.label,
      low: formatZoneValue(base * zone.low),
      high: formatZoneValue(highValue),
    };
  });
};

function App() {
  const [activePlan, setActivePlan] = useState<WorkoutPlan | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [importNotice, setImportNotice] = useState<string | null>(null);
  const [ftpChoice, setFtpChoice] = useState<FtpChoice | null>(null);
  const [startError, setStartError] = useState<string | null>(null);
  const [ergEnabled, setErgEnabled] = useState(true);
  const [autoResumeOnWork, setAutoResumeOnWork] = useState(false);
  const [autoPauseArmed, setAutoPauseArmed] = useState(true);
  const [showResumeOverlay, setShowResumeOverlay] = useState(false);
  const [showPower3s, setShowPower3s] = useState(false);
  const [cadenceScale, setCadenceScale] = useState(DEFAULT_CADENCE_RANGE);
  const [isFreeRide, setIsFreeRide] = useState(false);
  const [freeRideDurationSec, setFreeRideDurationSec] = useState(FREE_RIDE_EXTENSION_SEC);
  const [showCompletionPrompt, setShowCompletionPrompt] = useState(false);
  const [showStopPrompt, setShowStopPrompt] = useState(false);
  const [stopPromptElapsedSec, setStopPromptElapsedSec] = useState<number | null>(null);
  const [sessionSamples, setSessionSamples] = useState<TelemetrySample[]>([]);
  const [ergRampStartSec, setErgRampStartSec] = useState<number | null>(null);
  const [ergRampComplete, setErgRampComplete] = useState(false);
  const [profile, setProfile] = useState<UserProfile>(() =>
    loadProfileFromStorage()
  );
  const [draftProfile, setDraftProfile] = useState<UserProfile>(() =>
    buildEmptyProfile()
  );
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const coachProfiles = useMemo(() => getCoachProfiles(), []);
  const [selectedCoachProfileId, setSelectedCoachProfileId] = useState<string | null>(
    () => loadCoachProfileIdFromStorage()
  );
  const activeCoachProfile = useMemo(
    () => getCoachProfileById(coachProfiles, selectedCoachProfileId),
    [coachProfiles, selectedCoachProfileId]
  );
  const [intensityOverrides, setIntensityOverrides] = useState<IntensityOverride[]>([]);
  const [recoveryExtensions, setRecoveryExtensions] = useState<Record<string, number>>({});
  const lastWorkRef = useRef<number | null>(null);
  const resumeTimeoutRef = useRef<number | null>(null);
  const prevRunningRef = useRef(false);
  const stopPromptWasRunningRef = useRef(false);
  const lastRecordedSecRef = useRef<number | null>(null);
  const hasPlan = !!activePlan && activePlan.segments.length > 0;
  const baseSegments = hasPlan ? activePlan.segments : EMPTY_SEGMENTS;
  const adjustedSegments = useMemo(() => {
    if (!baseSegments.length) {
      return baseSegments;
    }
    return baseSegments.map((segment, index) => {
      const offsetPct = getIntensityOffsetForIndex(intensityOverrides, index);
      const scale = segment.isWork ? 1 + offsetPct / 100 : 1;
      const applyScale = (range: { low: number; high: number }) => ({
        low: range.low * scale,
        high: range.high * scale,
      });
      const extension =
        segment.phase === 'recovery' ? recoveryExtensions[segment.id] ?? 0 : 0;
      return {
        ...segment,
        durationSec: segment.durationSec + extension,
        targetRange: segment.isWork
          ? applyScale(segment.targetRange)
          : segment.targetRange,
        rampToRange:
          segment.isWork && segment.rampToRange
            ? applyScale(segment.rampToRange)
            : segment.rampToRange,
      };
    });
  }, [baseSegments, intensityOverrides, recoveryExtensions]);
  const activeSegments = adjustedSegments;
  const planDurationSec = useMemo(
    () => getTotalDurationSec(activeSegments),
    [activeSegments]
  );
  const freeRideSegment = useMemo<WorkoutSegment>(
    () => ({
      ...IDLE_SEGMENT,
      id: 'free-ride',
      label: 'Free Ride',
      durationSec: freeRideDurationSec,
      phase: 'cooldown',
      isWork: false,
    }),
    [freeRideDurationSec]
  );
  const displaySegments = hasPlan
    ? isFreeRide
      ? [...activeSegments, freeRideSegment]
      : activeSegments
    : IDLE_SEGMENTS;
  const clockSegments = displaySegments;
  const targetSegments = displaySegments;

  const clock = useWorkoutClock(clockSegments);
  const {
    bluetoothAvailable,
    trainer,
    hrSensor,
    connectTrainer,
    connectHeartRate,
    disconnectTrainer,
    disconnectHeartRate,
    trainerDevice,
    hrDevice,
  } = useBluetoothDevices();
  const bluetoothTelemetry = useBluetoothTelemetry({
    trainerDevice,
    hrDevice,
    elapsedSec: clock.activeSec,
    isRecording: clock.isRunning,
    sessionId: clock.sessionId,
  });
  const sessionElapsedSec = clock.elapsedSec;
  const activeSec = clock.activeSec;
  const totalDurationSec = clock.totalDurationSec;
  const isRunning = clock.isRunning;
  const isComplete = clock.isComplete;
  const isSessionActive = clock.isSessionActive;
  const sessionStartMs = clock.sessionStartMs;
  const rawSamples = bluetoothTelemetry.samples;
  const latestTelemetry = bluetoothTelemetry.latest;
  const sessionElapsedSecRef = useRef(sessionElapsedSec);
  const latestTelemetryRef = useRef(latestTelemetry);
  const isRunningRef = useRef(isRunning);

  useEffect(() => {
    if (!coachProfiles.length) {
      return;
    }
    const exists = coachProfiles.some(
      (coach) => coach.id === selectedCoachProfileId
    );
    if (!exists) {
      const fallbackId = coachProfiles[0]?.id ?? null;
      if (fallbackId) {
        setSelectedCoachProfileId(fallbackId);
        saveCoachProfileIdToStorage(fallbackId);
      }
    }
  }, [coachProfiles, selectedCoachProfileId]);

  useEffect(() => {
    setIntensityOverrides([]);
    setRecoveryExtensions({});
  }, [clock.sessionId]);

  useEffect(() => {
    sessionElapsedSecRef.current = sessionElapsedSec;
  }, [sessionElapsedSec]);

  useEffect(() => {
    latestTelemetryRef.current = latestTelemetry;
  }, [latestTelemetry]);

  useEffect(() => {
    isRunningRef.current = isRunning;
  }, [isRunning]);

  useEffect(() => {
    if (!isSessionActive) {
      return undefined;
    }
    const intervalId = window.setInterval(() => {
      if (!isRunningRef.current) {
        return;
      }
      const nextTelemetry = latestTelemetryRef.current;
      const hasAny =
        nextTelemetry.powerWatts !== null ||
        nextTelemetry.cadenceRpm !== null ||
        nextTelemetry.hrBpm !== null;
      if (!hasAny) {
        return;
      }
      const timeSec = sessionElapsedSecRef.current;
      if (!Number.isFinite(timeSec)) {
        return;
      }
      setSessionSamples((prevSamples) => {
        const lastRecorded = lastRecordedSecRef.current ?? -1;
        if (timeSec <= lastRecorded) {
          return prevSamples;
        }
        lastRecordedSecRef.current = timeSec;
        return [
          ...prevSamples,
          {
            timeSec,
            powerWatts: nextTelemetry.powerWatts ?? 0,
            cadenceRpm: nextTelemetry.cadenceRpm ?? 0,
            hrBpm: nextTelemetry.hrBpm ?? 0,
          },
        ];
      });
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, [isSessionActive]);
  const processedTelemetry = useTelemetryProcessing({
    samples: rawSamples,
    elapsedSec: activeSec,
    isRunning,
  });
  const telemetrySamples = processedTelemetry.samples;
  const hasStarted = isSessionActive || activeSec > 0;
  const isPaused = hasPlan && hasStarted && !isRunning && !isComplete;
  const liveStatus = !hasPlan
    ? 'NO WORKOUT'
    : isRunning
      ? 'LIVE'
      : isComplete
        ? 'DONE'
        : hasStarted
          ? 'PAUSED'
          : 'READY';
  const liveStatusClass = !hasPlan
    ? 'idle'
    : isRunning
      ? 'live'
      : isComplete
        ? 'complete'
        : hasStarted
          ? 'paused'
          : 'ready';
  const latestSample = processedTelemetry.latestSample;
  const {
    segment,
    index,
    startSec,
    endSec,
    elapsedInSegmentSec,
    targetRange,
  } = getTargetRangeAtTime(targetSegments, activeSec);
  const ftpWatts = activePlan?.ftpWatts ?? 0;
  const hrSensorConnected =
    hrSensor.status === 'connected' || hrSensor.status === 'connecting';
  const powerConnected = trainer.status === 'connected';
  const sessionError = importError ?? startError;

  const { low: targetLow, high: targetHigh } = targetRange;
  const targetMid = (targetLow + targetHigh) / 2;
  const intensityOffsetPct = getIntensityOffsetForIndex(intensityOverrides, index);
  const ergBiasWatts = parseNumber(profile.ergBiasWatts) ?? 0;
  const ergBiasRounded = Math.round(ergBiasWatts);
  const ergTargetBaseWatts = targetMid + ergBiasWatts;
  const isErgRamping = !ergRampComplete && ergEnabled && hasPlan && isRunning;
  const ergTargetWatts = isErgRamping
    ? Math.min(ergTargetBaseWatts, ERG_START_TARGET_WATTS)
    : ergTargetBaseWatts;
  const ftmsControl = useFtmsControl({
    trainerDevice,
    targetWatts: ergTargetWatts,
    isActive: isRunning && ergEnabled && hasPlan,
  });

  const bluetoothLatest = latestTelemetry;
  const displayPower = latestSample ? Math.round(latestSample.powerWatts) : null;
  const displayHr =
    bluetoothLatest.hrBpm !== null && bluetoothLatest.hrBpm > 0
      ? Math.round(bluetoothLatest.hrBpm)
      : null;
  const displayCadence = latestSample ? Math.round(latestSample.cadenceRpm) : null;
  const canDetectWork = trainer.status === 'connected' || bluetoothTelemetry.isActive;
  const latestPower = bluetoothLatest.powerWatts ?? 0;
  const latestCadence = bluetoothLatest.cadenceRpm ?? 0;
  const hasWorkTelemetry = latestPower > 0 || latestCadence > 0;

  const handleCoachAction = useCallback(
    (suggestion: CoachSuggestion) => {
      if (!activeCoachProfile) {
        return;
      }
      if (suggestion.action === 'adjust_intensity_up' || suggestion.action === 'adjust_intensity_down') {
        const percent = suggestion.payload?.percent ?? activeCoachProfile.interventions.intensityAdjustPct.step;
        const delta = suggestion.action === 'adjust_intensity_up' ? percent : -percent;
        const fromIndex = suggestion.payload?.segmentIndex ?? index;
        setIntensityOverrides((prev) => {
          const currentOffset = getIntensityOffsetForIndex(prev, fromIndex);
          const nextOffset = clampValue(
            currentOffset + delta,
            activeCoachProfile.interventions.intensityAdjustPct.min,
            activeCoachProfile.interventions.intensityAdjustPct.max
          );
          if (nextOffset === currentOffset) {
            return prev;
          }
          return [...prev, { fromIndex, offsetPct: nextOffset }];
        });
        return;
      }

      if (suggestion.action === 'extend_recovery') {
        const segmentId = suggestion.payload?.segmentId;
        if (!segmentId) {
          return;
        }
        const step = activeCoachProfile.interventions.recoveryExtendSec.step;
        const max = activeCoachProfile.interventions.recoveryExtendSec.max;
        setRecoveryExtensions((prev) => {
          const current = prev[segmentId] ?? 0;
          const next = Math.min(current + step, max);
          if (next === current) {
            return prev;
          }
          return { ...prev, [segmentId]: next };
        });
        return;
      }

      if (suggestion.action === 'skip_remaining_on_intervals') {
        if (!hasPlan || isFreeRide) {
          return;
        }
        const fromIndex = suggestion.payload?.segmentIndex ?? index;
        let cooldownIndex: number | null = null;
        for (let i = Math.max(fromIndex + 1, 0); i < activeSegments.length; i += 1) {
          if (activeSegments[i].phase === 'cooldown') {
            cooldownIndex = i;
            break;
          }
        }
        const startAt = cooldownIndex === null
          ? planDurationSec
          : activeSegments.slice(0, cooldownIndex).reduce((sum, seg) => sum + seg.durationSec, 0);
        clock.seek(startAt);
      }
    },
    [
      activeCoachProfile,
      activeSegments,
      clock,
      hasPlan,
      index,
      isFreeRide,
      planDurationSec,
    ]
  );

  const {
    suggestions: coachSuggestions,
    events: coachEvents,
    acceptSuggestion,
    rejectSuggestion,
  } = useCoachEngine({
    profile: activeCoachProfile,
    segments: activeSegments,
    segment,
    segmentIndex: index,
    segmentStartSec: startSec,
    segmentEndSec: endSec,
    elapsedInSegmentSec,
    activeSec,
    isRunning,
    hasPlan: hasPlan && !isFreeRide,
    isComplete,
    targetRange,
    samples: telemetrySamples,
    sessionId: clock.sessionId,
    intensityOffsetPct,
    onApplyAction: handleCoachAction,
  });

  const avgPower = useMemo(() => {
    if (!rawSamples.length) {
      return 0;
    }
    const total = rawSamples.reduce(
      (sum, sample) => sum + sample.powerWatts,
      0
    );
    return Math.round(total / rawSamples.length);
  }, [rawSamples]);

  const intervalAvgPower = useMemo(() => {
    if (!hasPlan || !rawSamples.length || !segment) {
      return 0;
    }
    let rangeStart = startSec;
    let rangeEnd = Math.min(activeSec, endSec);
    if (segment.phase === 'recovery' && index > 0) {
      const previousSegment = activeSegments[index - 1];
      if (previousSegment?.isWork) {
        rangeEnd = startSec;
        rangeStart = Math.max(0, startSec - previousSegment.durationSec);
      }
    }
    const samplesInRange = rawSamples.filter(
      (sample) => sample.timeSec >= rangeStart && sample.timeSec <= rangeEnd
    );
    if (!samplesInRange.length) {
      return 0;
    }
    const total = samplesInRange.reduce(
      (sum, sample) => sum + sample.powerWatts,
      0
    );
    return Math.round(total / samplesInRange.length);
  }, [
    activeSec,
    activeSegments,
    endSec,
    hasPlan,
    index,
    rawSamples,
    segment,
    startSec,
  ]);

  const normalizedPower = avgPower ? Math.round(avgPower * 1.03) : 0;
  const tss = avgPower && hasPlan
    ? Math.round((activeSec / 3600) * Math.pow(avgPower / ftpWatts, 2) * 100)
    : 0;
  const kj = avgPower ? Math.round((avgPower * activeSec) / 1000) : 0;
  const ifValue =
    ftpWatts > 0 && normalizedPower ? normalizedPower / ftpWatts : 0;
  const ifLabel = ifValue ? ifValue.toFixed(2) : '--';
  const tssLabel = tss ? `${tss}` : '--';
  const ifTssLabel = `${ifLabel} · ${tssLabel}`;

  const compliance = displayPower && targetMid > 0
    ? Math.round((displayPower / targetMid) * 100)
    : 0;
  const isPowerInRange =
    displayPower !== null &&
    displayPower >= targetLow &&
    displayPower <= targetHigh;

  const remainingSec = Math.max(totalDurationSec - activeSec, 0);
  const segmentRemainingSec = Math.max(endSec - activeSec, 0);
  const isRecoveryPhase = hasPlan && segment?.phase === 'recovery';
  const isWarmupPhase = hasPlan && segment?.phase === 'warmup';
  const isCooldownPhase = hasPlan && segment?.phase === 'cooldown';
  const phaseClass = hasPlan && segment ? `phase-${segment.phase}` : 'phase-idle';

  const workSegments = activeSegments.filter((seg) => seg.isWork);
  const totalIntervals = workSegments.length;
  const avgWorkDurationSec = useMemo(() => {
    if (!workSegments.length) {
      return 0;
    }
    const totalDuration = workSegments.reduce(
      (sum, seg) => sum + seg.durationSec,
      0
    );
    return totalDuration / workSegments.length;
  }, [workSegments]);
  const isMicroIntervals = avgWorkDurationSec > 0 && avgWorkDurationSec < 120;
  const isSteadyWork = avgWorkDurationSec >= 480;
  const workoutTypeClass = isMicroIntervals
    ? 'workout-micro'
    : isSteadyWork
      ? 'workout-steady'
      : 'workout-mixed';
  const workIndexBySegment = activeSegments.reduce<number[]>((acc, seg) => {
    const current = acc.length ? acc[acc.length - 1] : 0;
    acc.push(seg.isWork ? current + 1 : current);
    return acc;
  }, []);
  const currentIntervalIndex = hasPlan && totalIntervals > 0
    ? Math.max(1, workIndexBySegment[index] || 1)
    : 0;

  const profileName = profile.nickname.trim();
  const addCoachPrefix = (message: string) => {
    if (!profileName) {
      return message;
    }
    const trimmed = message.trim();
    if (!trimmed) {
      return `${profileName},`;
    }
    return `${profileName}, ${trimmed[0].toLowerCase()}${trimmed.slice(1)}`;
  };

  const coachMessage = !hasPlan
    ? {
        title: 'Import a workout',
        body: addCoachPrefix(
          'Load a workout file to begin and unlock live coaching.'
        ),
      }
    : compliance >= 97 && compliance <= 105
      ? {
          title: 'Great work',
          body: addCoachPrefix(
            "Excellent power control. You're nailing the target within 5%."
          ),
        }
      : {
          title: 'Hold steady',
          body: addCoachPrefix(
            'Settle the effort and smooth out the cadence over the next minute.'
          ),
        };
  const showCoachBanner = !hasPlan
    || (isRunning && (compliance < 97 || compliance > 105));

  const intervalLabel = isFreeRide
    ? 'FREE RIDE'
    : hasPlan && segment
      ? segment.isWork
        ? 'WORK'
        : segment.phase === 'recovery'
          ? 'RECOVERY'
          : segment.phase.toUpperCase()
      : 'IDLE';
  const targetLabel = hasPlan
    ? `${Math.round(targetLow)}-${Math.round(targetHigh)}W`
    : '--';
  const complianceLabel = hasPlan ? `${compliance}%` : '--';
  const intervalRemainingLabel = hasPlan ? formatDuration(segmentRemainingSec) : '--:--';
  const elapsedLabel = hasPlan ? formatDuration(sessionElapsedSec) : '--:--';
  const remainingLabel = hasPlan ? formatDuration(remainingSec) : '--:--';
  const segmentElapsedLabel = hasPlan ? formatDuration(elapsedInSegmentSec) : '--:--';
  const intervalCountLabel = hasPlan
    ? `${currentIntervalIndex}/${totalIntervals}`
    : '--/--';
  const planName = activePlan?.name ?? 'No workout loaded';
  const planSubtitle = activePlan?.subtitle ?? 'Import a workout to begin.';
  const nextSegment = hasPlan ? activeSegments[index + 1] ?? null : null;
  const nextTargetLabel = nextSegment
    ? `${Math.round(nextSegment.targetRange.low)}-${Math.round(
        nextSegment.targetRange.high
      )}W`
    : '--';
  const powerMeta = isRecoveryPhase
    ? {
        primaryLabel: 'Target',
        primaryValue: targetLabel,
        secondaryLabel: nextSegment ? 'Next' : 'Remaining',
        secondaryValue: nextSegment ? nextTargetLabel : intervalRemainingLabel,
      }
    : isCooldownPhase
      ? {
          primaryLabel: 'Cooldown',
          primaryValue: targetLabel,
          secondaryLabel: 'Remaining',
          secondaryValue: intervalRemainingLabel,
        }
      : isWarmupPhase
        ? {
            primaryLabel: 'Target',
            primaryValue: targetLabel,
            secondaryLabel: 'Remaining',
            secondaryValue: intervalRemainingLabel,
          }
        : {
            primaryLabel: 'Target',
            primaryValue: targetLabel,
            secondaryLabel: 'Compliance',
            secondaryValue: complianceLabel,
          };
  const ergCommandedHints: string[] = [];
  if (isErgRamping) {
    ergCommandedHints.push('ramp');
  }
  if (ergBiasRounded !== 0) {
    ergCommandedHints.push(`bias ${formatSignedWatts(ergBiasRounded)}`);
  }
  const ergCommandedLabel = hasPlan
    ? ergEnabled
      ? `${Math.round(ergTargetWatts)}W${
          ergCommandedHints.length ? ` (${ergCommandedHints.join(', ')})` : ''
        }`
      : 'ERG off'
    : '--';
  const hrZone = getHrZone(displayHr, profile.hrZones);
  const hrZoneStyle = hrZone
    ? ({ '--zone-color': hrZone.color } as CSSProperties)
    : undefined;
  const cadenceTargetRange = hasPlan && segment ? segment.cadenceRange : undefined;
  const cadenceTargetLow = cadenceTargetRange?.low ?? null;
  const cadenceTargetHigh = cadenceTargetRange?.high ?? null;
  const isCadenceTargetSingle =
    cadenceTargetRange &&
    Math.abs(cadenceTargetRange.low - cadenceTargetRange.high) < 0.5;
  const cadenceSpan = Math.max(1, cadenceScale.high - cadenceScale.low);
  const cadenceIndicator =
    displayCadence === null
      ? 0
      : clampValue(
          (displayCadence - cadenceScale.low) / cadenceSpan,
          0,
          1
        );
  const cadenceTargetStart = cadenceTargetRange
    ? clampValue(
        (cadenceTargetRange.low - cadenceScale.low) / cadenceSpan,
        0,
        1
      )
    : 0;
  const cadenceTargetEnd = cadenceTargetRange
    ? clampValue(
        (cadenceTargetRange.high - cadenceScale.low) / cadenceSpan,
        0,
        1
      )
    : 0;
  const cadenceBounds = cadenceTargetRange ?? cadenceScale;
  const cadenceState =
    displayCadence === null
      ? 'idle'
      : displayCadence < cadenceBounds.low
        ? 'low'
        : displayCadence > cadenceBounds.high
          ? 'high'
          : 'in';
  const cadenceGaugeStyle = {
    '--indicator': cadenceIndicator,
    '--target-start': cadenceTargetStart,
    '--target-end': cadenceTargetEnd,
  } as CSSProperties;
  const workoutFtpLabel = ftpChoice
    ? ftpChoice.workoutSource === 'file'
      ? 'Workout FTP (from file)'
      : 'Workout FTP (default)'
    : '';
  const ftpChoiceMessage = ftpChoice
    ? `Choose FTP for this workout: ${workoutFtpLabel} ${ftpChoice.workoutFtpWatts}W / Profile FTP ${ftpChoice.profileFtpWatts}W.`
    : '';

  const deviceRows = [
    {
      key: 'trainer',
      label: 'Trainer',
      state: trainer,
      connect: connectTrainer,
      disconnect: disconnectTrainer,
    },
    {
      key: 'hr',
      label: 'HR Sensor',
      state: hrSensor,
      connect: connectHeartRate,
      disconnect: disconnectHeartRate,
    },
  ];
  const trainerTelemetryError = bluetoothTelemetry.error;
  const trainerControlError = ftmsControl.error;
  const trainerControlStatus = ftmsControl.status;
  const trainerControlLabel = trainerControlStatus === 'ready'
    ? ergEnabled
      ? hasPlan && isRunning
        ? 'ERG control active'
        : 'ERG control ready'
      : 'ERG control disabled'
    : trainerControlStatus === 'requesting'
      ? 'ERG control arming'
      : trainerControlStatus === 'error'
        ? 'ERG control error'
        : 'ERG control idle';
  const startLabel = isComplete
    ? 'Restart'
    : hasStarted && !isRunning
      ? 'Resume'
      : 'Start';
  const ergToggleLabel = ergEnabled ? 'ERG On' : 'ERG Off';

  const handleProfileOpen = () => {
    setDraftProfile(cloneProfile(profile));
    setIsProfileOpen(true);
  };

  const handleProfileClose = () => {
    setIsProfileOpen(false);
  };

  const handleProfileSave = () => {
    const nextProfile = cloneProfile(draftProfile);
    setProfile(nextProfile);
    saveProfileToStorage(nextProfile);
    setIsProfileOpen(false);
  };

  const handleCoachProfileSelect = (profileId: string) => {
    setSelectedCoachProfileId(profileId);
    saveCoachProfileIdToStorage(profileId);
  };

  const handleDraftFieldChange =
    (
      field:
        | 'nickname'
        | 'weightKg'
        | 'ftpWatts'
        | 'ergBiasWatts'
        | 'thresholdHr'
        | 'maxHr'
    ) =>
      (event: ChangeEvent<HTMLInputElement>) => {
        const { value } = event.target;
        setDraftProfile((prev) => ({
          ...prev,
          [field]: value,
        }));
      };

  const handleZoneChange =
    (key: 'hrZones' | 'powerZones', index: number, field: 'low' | 'high') =>
      (event: ChangeEvent<HTMLInputElement>) => {
        const { value } = event.target;
        setDraftProfile((prev) => {
          const zones = prev[key].map((zone, zoneIndex) =>
            zoneIndex === index ? { ...zone, [field]: value } : zone
          );
          return {
            ...prev,
            [key]: zones,
          };
        });
      };

  const handleZoneLabelChange =
    (key: 'hrZones' | 'powerZones', index: number) =>
      (event: ChangeEvent<HTMLInputElement>) => {
        const { value } = event.target;
        setDraftProfile((prev) => {
          const zones = prev[key].map((zone, zoneIndex) =>
            zoneIndex === index ? { ...zone, label: value } : zone
          );
          return {
            ...prev,
            [key]: zones,
          };
        });
      };

  const handleAddZone = (key: 'hrZones' | 'powerZones') => () => {
    setDraftProfile((prev) => {
      const nextIndex = prev[key].length + 1;
      const nextZones = [
        ...prev[key],
        { label: `Z${nextIndex}`, low: '', high: '' },
      ];
      return {
        ...prev,
        [key]: nextZones,
      };
    });
  };

  const handleRemoveZone =
    (key: 'hrZones' | 'powerZones', index: number) => () => {
      setDraftProfile((prev) => {
        if (prev[key].length <= 1) {
          return prev;
        }
        const nextZones = prev[key].filter((_, zoneIndex) => zoneIndex !== index);
        return {
          ...prev,
          [key]: nextZones,
        };
      });
    };

  const handleApplyHrPreset = () => {
    setDraftProfile((prev) => {
      const threshold = parsePositiveNumber(prev.thresholdHr);
      if (!threshold) {
        return prev;
      }
      const maxHr = parsePositiveNumber(prev.maxHr);
      const fallbackMax =
        threshold *
        (COGGAN_HR_TEMPLATE.zones[COGGAN_HR_TEMPLATE.zones.length - 1].high ??
          1.2);
      const cap = maxHr ?? fallbackMax;
      const hrZones = buildZonesFromTemplate(threshold, COGGAN_HR_TEMPLATE, {
        capLast: cap,
      });
      return {
        ...prev,
        hrZones,
      };
    });
  };

  const handleApplyPowerPreset = () => {
    setDraftProfile((prev) => {
      const ftp = parsePositiveNumber(prev.ftpWatts);
      if (!ftp) {
        return prev;
      }
      const powerZones = buildZonesFromTemplate(
        ftp,
        EIGHTY_TWENTY_POWER_TEMPLATE
      );
      return {
        ...prev,
        powerZones,
      };
    });
  };

  const thresholdHrValue = parsePositiveNumber(draftProfile.thresholdHr);
  const ftpValue = parsePositiveNumber(draftProfile.ftpWatts);
  const canApplyHrPreset = !!thresholdHrValue;
  const canApplyPowerPreset = !!ftpValue;
  const canRemoveHrZone = draftProfile.hrZones.length > 1;
  const canRemovePowerZone = draftProfile.powerZones.length > 1;

  useEffect(() => {
    return () => {
      if (resumeTimeoutRef.current) {
        window.clearTimeout(resumeTimeoutRef.current);
        resumeTimeoutRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    lastWorkRef.current = null;
    setAutoResumeOnWork(false);
    setAutoPauseArmed(true);
  }, [clock.sessionId]);

  useEffect(() => {
    if (parsePositiveNumber(profile.ftpWatts)) {
      setImportNotice(null);
    }
  }, [profile.ftpWatts]);

  useEffect(() => {
    setIsFreeRide(false);
    setFreeRideDurationSec(FREE_RIDE_EXTENSION_SEC);
    setShowCompletionPrompt(false);
    setShowStopPrompt(false);
    setStopPromptElapsedSec(null);
    setSessionSamples([]);
    lastRecordedSecRef.current = null;
    stopPromptWasRunningRef.current = false;
    setErgRampStartSec(null);
    setErgRampComplete(false);
  }, [clock.sessionId]);

  useEffect(() => {
    if (!isRunning || ergRampComplete || !ergEnabled || !hasPlan) {
      return;
    }
    if (ergRampStartSec === null) {
      setErgRampStartSec(activeSec);
      return;
    }
    if (activeSec - ergRampStartSec >= ERG_RAMP_SEC) {
      setErgRampComplete(true);
    }
  }, [
    activeSec,
    ergEnabled,
    ergRampComplete,
    ergRampStartSec,
    hasPlan,
    isRunning,
  ]);

  useEffect(() => {
    setCadenceScale(DEFAULT_CADENCE_RANGE);
  }, [clock.sessionId, activePlan?.id]);

  useEffect(() => {
    if (!hasPlan) {
      setStartError(null);
      return;
    }
    if (!powerConnected && !hasStarted) {
      setStartError('Connect a power device to start the workout.');
      return;
    }
    if (powerConnected) {
      setStartError(null);
    }
  }, [hasPlan, hasStarted, powerConnected]);

  useEffect(() => {
    if (!isFreeRide || !hasPlan) {
      return;
    }
    const limit = planDurationSec + freeRideDurationSec;
    if (activeSec >= limit - FREE_RIDE_EXTENSION_BUFFER_SEC) {
      setFreeRideDurationSec((prev) => prev + FREE_RIDE_EXTENSION_SEC);
    }
  }, [
    activeSec,
    freeRideDurationSec,
    hasPlan,
    isFreeRide,
    planDurationSec,
  ]);

  useEffect(() => {
    setCadenceScale((prev) => {
      const defaultLow = DEFAULT_CADENCE_RANGE.low;
      const defaultHigh = DEFAULT_CADENCE_RANGE.high;
      const candidates: number[] = [];
      if (displayCadence !== null) {
        candidates.push(displayCadence);
      }
      if (cadenceTargetLow !== null) {
        candidates.push(cadenceTargetLow);
      }
      if (cadenceTargetHigh !== null) {
        candidates.push(cadenceTargetHigh);
      }
      const withinDefault =
        candidates.length === 0 ||
        candidates.every(
          (value) => value >= defaultLow && value <= defaultHigh
        );

      let nextLow = prev.low;
      let nextHigh = prev.high;

      if (!withinDefault && candidates.length) {
        const minCandidate = Math.min(...candidates, nextLow);
        const maxCandidate = Math.max(...candidates, nextHigh);
        if (minCandidate < nextLow) {
          nextLow = Math.floor(minCandidate - CADENCE_RANGE_BUFFER_RPM);
        }
        if (maxCandidate > nextHigh) {
          nextHigh = Math.ceil(maxCandidate + CADENCE_RANGE_BUFFER_RPM);
        }
      } else {
        nextLow = nextLow + (defaultLow - nextLow) * CADENCE_RANGE_RELAX_RATE;
        nextHigh = nextHigh + (defaultHigh - nextHigh) * CADENCE_RANGE_RELAX_RATE;
      }

      if (nextHigh - nextLow < 10) {
        const mid = (nextHigh + nextLow) / 2;
        nextLow = mid - 5;
        nextHigh = mid + 5;
      }

      const rounded = {
        low: Math.round(nextLow * 10) / 10,
        high: Math.round(nextHigh * 10) / 10,
      };

      if (
        Math.abs(rounded.low - prev.low) < 0.1 &&
        Math.abs(rounded.high - prev.high) < 0.1
      ) {
        return prev;
      }

      return rounded;
    });
  }, [displayCadence, cadenceTargetLow, cadenceTargetHigh]);

  useEffect(() => {
    if (!canDetectWork || !autoResumeOnWork) {
      return;
    }
    if (!hasWorkTelemetry || !hasPlan || isRunning || isComplete || !isSessionActive) {
      return;
    }
    clock.start();
    ftmsControl.startWorkout();
    setAutoResumeOnWork(false);
    setAutoPauseArmed(true);
  }, [
    autoResumeOnWork,
    canDetectWork,
    clock,
    clock.start,
    ftmsControl,
    ftmsControl.startWorkout,
    hasPlan,
    hasWorkTelemetry,
    isComplete,
    isRunning,
    isSessionActive,
  ]);

  useEffect(() => {
    if (!canDetectWork || !hasPlan || !isRunning) {
      return;
    }
    if (hasWorkTelemetry) {
      lastWorkRef.current = sessionElapsedSec;
      if (!autoPauseArmed) {
        setAutoPauseArmed(true);
      }
      return;
    }
    if (!autoPauseArmed) {
      return;
    }
    const lastWork = lastWorkRef.current;
    if (lastWork === null) {
      return;
    }
    if (sessionElapsedSec - lastWork >= AUTO_PAUSE_THRESHOLD_SEC) {
      clock.pause();
      ftmsControl.pauseWorkout();
      setAutoResumeOnWork(true);
    }
  }, [
    autoPauseArmed,
    canDetectWork,
    clock,
    clock.pause,
    ftmsControl,
    ftmsControl.pauseWorkout,
    hasPlan,
    hasWorkTelemetry,
    isRunning,
    sessionElapsedSec,
  ]);

  useEffect(() => {
    const wasRunning = prevRunningRef.current;
    if (!wasRunning && isRunning) {
      setShowResumeOverlay(true);
      if (resumeTimeoutRef.current) {
        window.clearTimeout(resumeTimeoutRef.current);
        resumeTimeoutRef.current = null;
      }
      resumeTimeoutRef.current = window.setTimeout(() => {
        setShowResumeOverlay(false);
      }, 2000);
    } else if (wasRunning && !isRunning) {
      if (resumeTimeoutRef.current) {
        window.clearTimeout(resumeTimeoutRef.current);
        resumeTimeoutRef.current = null;
      }
      setShowResumeOverlay(false);
    }
    prevRunningRef.current = isRunning;
  }, [isRunning]);

  useEffect(() => {
    if (!hasPlan || isFreeRide) {
      return;
    }
    if (isComplete) {
      setShowCompletionPrompt(true);
      setShowStopPrompt(false);
    }
  }, [hasPlan, isComplete, isFreeRide]);

  const handleStart = () => {
    if (isComplete) {
      setIsFreeRide(false);
      setFreeRideDurationSec(FREE_RIDE_EXTENSION_SEC);
      setShowCompletionPrompt(false);
    }
    if (!hasPlan) {
      setImportError('Import a workout to start the session.');
      return;
    }
    if (!powerConnected) {
      setStartError('Connect a power device to start the workout.');
      return;
    }
    setStartError(null);
    const isSessionStarting = !isSessionActive;
    clock.startSession();
    if (canDetectWork && isSessionStarting && !hasWorkTelemetry) {
      setAutoResumeOnWork(true);
      return;
    }
    clock.start();
    ftmsControl.startWorkout();
    setAutoResumeOnWork(false);
    if (canDetectWork && !hasWorkTelemetry) {
      setAutoPauseArmed(false);
    } else {
      setAutoPauseArmed(true);
    }
  };

  const handlePause = () => {
    clock.pause();
    ftmsControl.pauseWorkout();
    setAutoResumeOnWork(false);
  };

  const handleStopRequest = () => {
    if (!hasStarted) {
      return;
    }
    stopPromptWasRunningRef.current = isRunning;
    setStopPromptElapsedSec(sessionElapsedSec);
    setShowStopPrompt(true);
    setShowCompletionPrompt(false);
    setAutoResumeOnWork(false);
    if (isRunning) {
      clock.pause();
      ftmsControl.pauseWorkout();
    }
  };

  const handleStop = () => {
    clock.stop();
    ftmsControl.stopWorkout();
    setAutoResumeOnWork(false);
    setAutoPauseArmed(true);
    setIsFreeRide(false);
    setFreeRideDurationSec(FREE_RIDE_EXTENSION_SEC);
    setShowCompletionPrompt(false);
    setShowStopPrompt(false);
    setStopPromptElapsedSec(null);
    stopPromptWasRunningRef.current = false;
  };

  const handleStopAndExport = (elapsedSecOverride?: number) => {
    const elapsedForExport = elapsedSecOverride ?? sessionElapsedSec;
    const exportSamples = rawSamples.length > 0 ? rawSamples : sessionSamples;
    const timerSecForExport =
      activeSec > 0 ? activeSec : Math.max(0, elapsedForExport);
    const fallbackStart =
      Date.now() - Math.max(elapsedForExport, 0) * 1000;
    const startTimeMs = sessionStartMs ?? fallbackStart;
    const endTimeMs = Date.now();
    const fitPayload = buildFitFile({
      startTimeMs,
      elapsedSec: elapsedForExport,
      timerSec: timerSecForExport,
      samples: exportSamples,
    });
    downloadFitFile(fitPayload, buildFitFilename(planName, startTimeMs));

    // Save session to localStorage
    const coachNotes = buildCoachNotes(coachEvents);
    const sessionData: SessionData = {
      ...buildSessionSummary(
        startTimeMs,
        endTimeMs,
        timerSecForExport,
        exportSamples,
        planName,
        isComplete,
        coachNotes,
        activeCoachProfile?.id ?? null,
        coachEvents
      ),
      startTimeMs,
      endTimeMs,
      samples: exportSamples,
    };
    addSessionToStorage(sessionData);

    handleStop();
  };

  const handleContinueFreeRide = () => {
    setIsFreeRide(true);
    setShowCompletionPrompt(false);
    setShowStopPrompt(false);
    setStopPromptElapsedSec(null);
    stopPromptWasRunningRef.current = false;
    setFreeRideDurationSec((prev) =>
      prev > 0 ? prev : FREE_RIDE_EXTENSION_SEC
    );
    setErgEnabled(false);
    clock.resume();
    ftmsControl.startWorkout();
  };

  const handleCancelStopPrompt = () => {
    setShowStopPrompt(false);
    setStopPromptElapsedSec(null);
    if (stopPromptWasRunningRef.current) {
      clock.resume();
      ftmsControl.startWorkout();
    }
    stopPromptWasRunningRef.current = false;
  };

  const handleImport = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setImportError(null);
    setImportNotice(null);
    setFtpChoice(null);
    try {
      const text = await file.text();
      const profileFtpRaw = parsePositiveNumber(profile.ftpWatts);
      const profileFtp = profileFtpRaw ? Math.round(profileFtpRaw) : null;
      const workoutResult = parseWorkoutFile(file.name, text);
      let nextPlan = workoutResult.plan;

      if (profileFtp) {
        const profileResult = parseWorkoutFile(file.name, text, {
          overrideFtpWatts: profileFtp,
          fallbackFtpWatts: profileFtp,
        });
        const workoutFtp = workoutResult.meta.resolvedFtpWatts;
        if (workoutFtp !== profileFtp) {
          const workoutSource =
            workoutResult.meta.ftpSource === 'file' ? 'file' : 'default';
          const defaultChoice =
            workoutResult.meta.ftpSource === 'fallback' ? 'profile' : 'workout';
          nextPlan =
            defaultChoice === 'profile' ? profileResult.plan : workoutResult.plan;
          setFtpChoice({
            workoutPlan: workoutResult.plan,
            profilePlan: profileResult.plan,
            workoutFtpWatts: workoutFtp,
            profileFtpWatts: profileFtp,
            workoutSource,
            selected: defaultChoice,
          });
        }
      } else {
        setImportNotice(
          'Tip: complete your profile info (FTP) for best workout results.'
        );
      }
      clock.stop();
      setActivePlan(nextPlan);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unable to import workout.';
      setImportError(message);
    } finally {
      event.target.value = '';
    }
  };

  const handleFtpChoice = (choice: 'workout' | 'profile') => {
    setFtpChoice((prev) => {
      if (!prev) {
        return prev;
      }
      const nextPlan = choice === 'workout' ? prev.workoutPlan : prev.profilePlan;
      setActivePlan(nextPlan);
      if (prev.selected === choice) {
        return prev;
      }
      return { ...prev, selected: choice };
    });
  };

  return (
    <div className={`app ${phaseClass} ${workoutTypeClass}`}>
      <header className="top-bar">
        <div className="title-block">
          <button className="back-button" type="button" aria-label="Back">
            ←
          </button>
          <div>
            <div className="title">{planName}</div>
            <div className="subtitle">{planSubtitle}</div>
          </div>
        </div>
        <div className="top-actions">
          <div className={`live-status ${liveStatusClass}`}>
            <span className="live-dot" />
            {liveStatus}
          </div>
          <button
            className="settings-button"
            type="button"
            aria-label="Open profile settings"
            onClick={handleProfileOpen}
          >
            <span className="settings-icon" aria-hidden="true" />
            <span className="sr-only">Profile settings</span>
          </button>
        </div>
      </header>

      <section
        className="panel session-panel"
        style={{ '--delay': '0.05s' } as CSSProperties}
      >
        <div className="session-info">
          <div className="panel-title">SESSION CONTROL</div>
          <div className="session-title">{planName}</div>
          {sessionError ? (
            <div className="session-error">{sessionError}</div>
          ) : null}
          {ftpChoice ? (
            <div className="session-choice">
              <div className="session-info-message">{ftpChoiceMessage}</div>
              <div className="session-choice-actions">
                <button
                  className={`session-choice-button ${
                    ftpChoice.selected === 'workout' ? 'active' : ''
                  }`}
                  type="button"
                  onClick={() => handleFtpChoice('workout')}
                  disabled={ftpChoice.selected === 'workout'}
                >
                  Use {workoutFtpLabel} ({ftpChoice.workoutFtpWatts}W)
                </button>
                <button
                  className={`session-choice-button ${
                    ftpChoice.selected === 'profile' ? 'active' : ''
                  }`}
                  type="button"
                  onClick={() => handleFtpChoice('profile')}
                  disabled={ftpChoice.selected === 'profile'}
                >
                  Use Profile FTP ({ftpChoice.profileFtpWatts}W)
                </button>
              </div>
            </div>
          ) : null}
          {importNotice && !ftpChoice ? (
            <div className="session-info-message">{importNotice}</div>
          ) : null}
        </div>
        <div className="session-actions">
          <label className={`session-button ${hasStarted ? 'disabled' : ''}`}>
            Import Workout
            <input
              className="file-input"
              type="file"
              accept=".json,.erg,.mrc,.zwo,application/json"
              onChange={handleImport}
              disabled={hasStarted}
            />
          </label>
          <button
            className="session-button primary"
            type="button"
            onClick={handleStart}
            disabled={!hasPlan || isRunning || !powerConnected}
          >
            {startLabel}
          </button>
          <button
            className="session-button"
            type="button"
            onClick={handlePause}
            disabled={!isRunning}
          >
            Pause
          </button>
          <button
            className="session-button danger"
            type="button"
            onClick={handleStopRequest}
            disabled={!hasStarted}
          >
            Stop
          </button>
          <button
            className={`session-button toggle ${ergEnabled ? 'on' : 'off'}`}
            type="button"
            onClick={() => setErgEnabled((prev) => !prev)}
            disabled={trainer.status !== 'connected' || !hasPlan}
          >
            {ergToggleLabel}
          </button>
        </div>
      </section>

      {showCoachBanner ? (
        <section
          className="panel coach-banner"
          style={{ '--delay': '0.08s' } as CSSProperties}
        >
          <div className="coach-banner-title">
            <span className="coach-icon" />
            {coachMessage.title.toUpperCase()}
          </div>
          <div className="coach-banner-body">{coachMessage.body}</div>
        </section>
      ) : null}

      {hasPlan ? (
        <CoachPanel
          profile={activeCoachProfile}
          profiles={coachProfiles}
          selectedProfileId={selectedCoachProfileId}
          onSelectProfile={handleCoachProfileSelect}
          events={coachEvents}
          suggestions={coachSuggestions}
          onAcceptSuggestion={acceptSuggestion}
          onRejectSuggestion={rejectSuggestion}
        />
      ) : null}

      <section
        className="panel workout-panel"
        style={{ '--delay': '0.1s' } as CSSProperties}
      >
        <div className="panel-header">
          <div className="panel-title">WORKOUT PROFILE</div>
          <div className="panel-meta">
            <div>
              <span>Elapsed</span>
              <strong>{elapsedLabel}</strong>
            </div>
            <div>
              <span>FTP</span>
              <strong>{hasPlan ? `${ftpWatts}W` : '--'}</strong>
            </div>
          </div>
        </div>
        {hasPlan ? (
          <>
            <div className="workout-chart-shell">
              <div className="workout-chart-layout">
                <div className="workout-chart-frame">
                  <WorkoutChart
                    segments={displaySegments}
                    samples={telemetrySamples}
                    gaps={processedTelemetry.gaps}
                    elapsedSec={activeSec}
                    ftpWatts={ftpWatts}
                    hrSensorConnected={hrSensorConnected}
                    showPower3s={showPower3s}
                  />
                  {isPaused ? (
                    <div className="chart-overlay paused">
                      <span className="overlay-icon pause" />
                    </div>
                  ) : null}
                  {showResumeOverlay ? (
                    <div className="chart-overlay resume">
                      <span className="overlay-icon play" />
                    </div>
                  ) : null}
                </div>
                <div
                  className={`cadence-gauge ${cadenceState} ${
                    cadenceTargetRange ? 'has-target' : ''
                  }`}
                >
                  <div className="cadence-gauge-header">
                    <span>Cadence</span>
                    <span className="cadence-gauge-value">
                      {displayCadence === null ? '--' : displayCadence}
                      <span className="unit">rpm</span>
                    </span>
                  </div>
                  <div className="cadence-gauge-body">
                    <span className="cadence-gauge-label">
                      {Math.round(cadenceScale.high)}
                    </span>
                    <div className="cadence-gauge-track" style={cadenceGaugeStyle}>
                      <div className="cadence-gauge-range" />
                    {cadenceTargetRange ? (
                      <div
                        className={`cadence-gauge-target ${
                          isCadenceTargetSingle ? 'single' : ''
                        }`}
                      >
                        <span>
                          {isCadenceTargetSingle
                            ? Math.round(cadenceTargetRange.low)
                            : `${Math.round(cadenceTargetRange.low)}-${Math.round(
                                cadenceTargetRange.high
                              )}`}
                        </span>
                      </div>
                    ) : null}
                      <div className="cadence-gauge-indicator" />
                    </div>
                    <span className="cadence-gauge-label">
                      {Math.round(cadenceScale.low)}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div className="chart-legend">
              <div className="legend-item">
                <span className="legend-swatch" />
                Target Zones
              </div>
              <div className="legend-item">
                <span className="legend-line" />
                Power (W)
              </div>
              <button
                className={`legend-item legend-toggle ${showPower3s ? 'on' : 'off'}`}
                type="button"
                onClick={() => setShowPower3s((prev) => !prev)}
                aria-pressed={showPower3s}
              >
                <span className="legend-line avg" />
                3s Avg (W)
              </button>
              <div className="legend-item">
                <span className="legend-line hr" />
                HR
              </div>
              <div className="legend-item">
                <span className="legend-gap" />
                Dropouts
              </div>
            </div>
          </>
        ) : (
          <div className="workout-placeholder">
            Import a workout file to see the timeline and targets.
          </div>
        )}
      </section>

      <section className={`metrics-row ${phaseClass}`}>
        <div
          className="panel metric-card interval-card primary"
          style={{ '--delay': '0.2s' } as CSSProperties}
        >
          <div className="metric-header">
            <span>Interval</span>
            <span className="muted">{intervalCountLabel}</span>
          </div>
          <div className="metric-value">{intervalRemainingLabel}</div>
          <div className="metric-sub">
            <div>Elapsed</div>
            <div className="muted">{segmentElapsedLabel}</div>
          </div>
          <div className="metric-sub">
            <div>Workout Rem.</div>
            <div className="muted">{remainingLabel}</div>
          </div>
          <div className="pill">{intervalLabel}</div>
        </div>

        <div
          className="panel metric-card power-card primary"
          style={{ '--delay': '0.25s' } as CSSProperties}
        >
          <div className="metric-header">
            <span>Power</span>
            <span className="metric-tag">
              {hasPlan && segment ? (segment.isWork ? 'ERG' : 'RES') : '--'}
            </span>
          </div>
          <div className="metric-value">
            {displayPower === null ? <span className="muted">--</span> : displayPower}
            <span className="unit">W</span>
          </div>
          <div className="metric-sub">
            <div>{powerMeta.primaryLabel}</div>
            <div className="muted">{powerMeta.primaryValue}</div>
          </div>
          <div className="metric-sub">
            <div>{powerMeta.secondaryLabel}</div>
            <div
              className={
                powerMeta.secondaryLabel === 'Compliance'
                  ? `accent ${isPowerInRange ? 'good' : ''}`
                  : 'muted'
              }
            >
              {powerMeta.secondaryValue}
            </div>
          </div>
          <div className="metric-sub">
            <div>Commanded</div>
            <div className="muted">{ergCommandedLabel}</div>
          </div>
        </div>

        <div
          className="panel metric-card hr-card secondary"
          style={{ '--delay': '0.3s' } as CSSProperties}
        >
          <div className="metric-header">
            <span>Heart Rate</span>
            <span
              className={`metric-tag zone ${hrZone ? '' : 'empty'}`}
              style={hrZoneStyle}
            >
              {hrZone?.label ?? '--'}
            </span>
          </div>
          <div className="metric-value">
            {displayHr === null ? <span className="muted">--</span> : displayHr}
            <span className="unit">bpm</span>
          </div>
        </div>

        <div
          className="panel metric-card cadence-card secondary"
          style={{ '--delay': '0.35s' } as CSSProperties}
        >
          <div className="metric-header">
            <span>Cadence</span>
            <span className="metric-tag subtle">
              {cadenceTargetRange
                ? `${Math.round(cadenceTargetRange.low)}-${Math.round(
                    cadenceTargetRange.high
                  )}`
                : `${Math.round(cadenceScale.low)}-${Math.round(cadenceScale.high)}`}
            </span>
          </div>
          <div className="metric-value">
            {displayCadence === null ? <span className="muted">--</span> : displayCadence}
            <span className="unit">rpm</span>
          </div>
          <div className="metric-sub">
            <div>{cadenceTargetRange ? 'Target' : 'Range'}</div>
            <div className="muted">
              {cadenceTargetRange
                ? `${Math.round(cadenceTargetRange.low)}-${Math.round(
                    cadenceTargetRange.high
                  )} rpm`
                : `${DEFAULT_CADENCE_RANGE.low}-${DEFAULT_CADENCE_RANGE.high} rpm`}
            </div>
          </div>
        </div>
      </section>

      <section className={`secondary-grid ${phaseClass} ${isSteadyWork ? 'steady' : ''}`}>
        <div
          className="panel stat-card interval-avg"
          style={{ '--delay': '0.4s' } as CSSProperties}
        >
          <div className="stat-label">Interval Avg</div>
          <div className="stat-value">{intervalAvgPower || '--'}W</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.45s' } as CSSProperties}>
          <div className="stat-label">Norm Power</div>
          <div className="stat-value">{normalizedPower || '--'}W</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.5s' } as CSSProperties}>
          <div className="stat-label">IF / TSS</div>
          <div className="stat-value">{ifTssLabel}</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.55s' } as CSSProperties}>
          <div className="stat-label">KJ</div>
          <div className="stat-value">{kj || '--'}</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.6s' } as CSSProperties}>
          <div className="stat-label">Intervals</div>
          <div className="stat-value">{intervalCountLabel}</div>
        </div>
      </section>

      {showCompletionPrompt ? (
        <div className="modal-scrim" role="presentation">
          <div
            className="modal completion-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="completion-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <div className="modal-title" id="completion-modal-title">
                  Workout complete
                </div>
                <div className="modal-subtitle">
                  Choose to export a FIT file or keep riding in free ride mode.
                </div>
              </div>
            </div>
            <div className="modal-body completion-body">
              <div>
                Plan finished at {formatDuration(activeSec)}.
              </div>
            </div>
            <div className="modal-footer completion-actions">
              <button
                className="session-button"
                type="button"
                onClick={handleContinueFreeRide}
              >
                Continue Free Ride
              </button>
              <button
                className="session-button primary"
                type="button"
                onClick={() => handleStopAndExport()}
              >
                Stop & Export FIT
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {showStopPrompt ? (
        <div className="modal-scrim" role="presentation">
          <div
            className="modal completion-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="stop-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <div className="modal-title" id="stop-modal-title">
                  Stop workout?
                </div>
                <div className="modal-subtitle">
                  Export a FIT file now or keep riding in free ride mode.
                </div>
              </div>
            </div>
            <div className="modal-body completion-body">
              <div>
                Workout paused at {formatDuration(activeSec)}.
              </div>
            </div>
            <div className="modal-footer completion-actions">
              <button
                className="session-button"
                type="button"
                onClick={handleCancelStopPrompt}
              >
                Back to Workout
              </button>
              <button
                className="session-button"
                type="button"
                onClick={handleContinueFreeRide}
              >
                Continue Free Ride
              </button>
              <button
                className="session-button primary"
                type="button"
                onClick={() =>
                  handleStopAndExport(stopPromptElapsedSec ?? undefined)
                }
              >
                Stop & Export FIT
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {isProfileOpen ? (
        <div
          className="modal-scrim"
          role="presentation"
          onClick={handleProfileClose}
        >
          <div
            className="modal profile-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="profile-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <div className="modal-title" id="profile-modal-title">
                  Athlete Profile
                </div>
                <div className="modal-subtitle">
                  Update your training stats or sync from Intervals.icu.
                </div>
              </div>
              <button
                className="modal-close"
                type="button"
                aria-label="Close profile"
                onClick={handleProfileClose}
              >
                x
              </button>
            </div>
            <div className="modal-body">
              <div className="profile-form">
                <label className="profile-field">
                  <span>Nickname</span>
                  <input
                    type="text"
                    value={draftProfile.nickname}
                    onChange={handleDraftFieldChange('nickname')}
                    placeholder="e.g. Alex"
                  />
                </label>
                <label className="profile-field">
                  <span>Weight (kg)</span>
                  <input
                    type="number"
                    min="0"
                    step="0.1"
                    inputMode="decimal"
                    value={draftProfile.weightKg}
                    onChange={handleDraftFieldChange('weightKg')}
                    placeholder="e.g. 68.5"
                  />
                </label>
                <label className="profile-field">
                  <span>FTP (W)</span>
                  <input
                    type="number"
                    min="0"
                    step="1"
                    inputMode="numeric"
                    value={draftProfile.ftpWatts}
                    onChange={handleDraftFieldChange('ftpWatts')}
                    placeholder="e.g. 260"
                  />
                </label>
                <label className="profile-field">
                  <span>ERG Bias (W)</span>
                  <input
                    type="number"
                    step="1"
                    inputMode="decimal"
                    value={draftProfile.ergBiasWatts}
                    onChange={handleDraftFieldChange('ergBiasWatts')}
                    placeholder="e.g. +5 or -5"
                  />
                </label>
                <label className="profile-field">
                  <span>Threshold HR (bpm)</span>
                  <input
                    type="number"
                    min="0"
                    step="1"
                    inputMode="numeric"
                    value={draftProfile.thresholdHr}
                    onChange={handleDraftFieldChange('thresholdHr')}
                    placeholder="e.g. 172"
                  />
                </label>
                <label className="profile-field">
                  <span>Max HR (bpm)</span>
                  <input
                    type="number"
                    min="0"
                    step="1"
                    inputMode="numeric"
                    value={draftProfile.maxHr}
                    onChange={handleDraftFieldChange('maxHr')}
                    placeholder="e.g. 190"
                  />
                </label>
              </div>
              <div className="zone-grid">
                <div className="zone-card">
                  <div className="zone-header-row">
                    <div className="zone-header">Heart Rate Zones</div>
                    <div className="zone-actions">
                      <button
                        className="zone-button"
                        type="button"
                        onClick={handleApplyHrPreset}
                        disabled={!canApplyHrPreset}
                      >
                        Auto-fill ({COGGAN_HR_TEMPLATE.name})
                      </button>
                      <button
                        className="zone-button"
                        type="button"
                        onClick={handleAddZone('hrZones')}
                      >
                        Add Zone
                      </button>
                    </div>
                  </div>
                  <div className="zone-note">
                    Uses threshold HR. Max HR caps the top zone.
                  </div>
                  <div className="zone-list">
                    {draftProfile.hrZones.map((zone, index) => (
                      <div key={index} className="zone-row">
                        <input
                          className="zone-label-input"
                          type="text"
                          value={zone.label}
                          onChange={handleZoneLabelChange('hrZones', index)}
                          placeholder="Label"
                        />
                        <input
                          type="number"
                          min="0"
                          step="1"
                          inputMode="numeric"
                          value={zone.low}
                          onChange={handleZoneChange('hrZones', index, 'low')}
                          placeholder="Low"
                        />
                        <span className="zone-sep">-</span>
                        <input
                          type="number"
                          min="0"
                          step="1"
                          inputMode="numeric"
                          value={zone.high}
                          onChange={handleZoneChange('hrZones', index, 'high')}
                          placeholder="High"
                        />
                        <span className="zone-unit">bpm</span>
                        <button
                          className="zone-remove"
                          type="button"
                          onClick={handleRemoveZone('hrZones', index)}
                          disabled={!canRemoveHrZone}
                          aria-label={`Remove heart rate zone ${zone.label}`}
                        >
                          x
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="zone-card">
                  <div className="zone-header-row">
                    <div className="zone-header">Power Zones</div>
                    <div className="zone-actions">
                      <button
                        className="zone-button"
                        type="button"
                        onClick={handleApplyPowerPreset}
                        disabled={!canApplyPowerPreset}
                      >
                        Auto-fill ({EIGHTY_TWENTY_POWER_TEMPLATE.name})
                      </button>
                      <button
                        className="zone-button"
                        type="button"
                        onClick={handleAddZone('powerZones')}
                      >
                        Add Zone
                      </button>
                    </div>
                  </div>
                  <div className="zone-note">
                    Uses FTP with 80/20 Cycling zone ranges.
                  </div>
                  <div className="zone-list">
                    {draftProfile.powerZones.map((zone, index) => (
                      <div key={index} className="zone-row">
                        <input
                          className="zone-label-input"
                          type="text"
                          value={zone.label}
                          onChange={handleZoneLabelChange('powerZones', index)}
                          placeholder="Label"
                        />
                        <input
                          type="number"
                          min="0"
                          step="1"
                          inputMode="numeric"
                          value={zone.low}
                          onChange={handleZoneChange('powerZones', index, 'low')}
                          placeholder="Low"
                        />
                        <span className="zone-sep">-</span>
                        <input
                          type="number"
                          min="0"
                          step="1"
                          inputMode="numeric"
                          value={zone.high}
                          onChange={handleZoneChange('powerZones', index, 'high')}
                          placeholder="High"
                        />
                        <span className="zone-unit">W</span>
                        <button
                          className="zone-remove"
                          type="button"
                          onClick={handleRemoveZone('powerZones', index)}
                          disabled={!canRemovePowerZone}
                          aria-label={`Remove power zone ${zone.label}`}
                        >
                          x
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
              <div className="settings-card devices-card">
                <div className="settings-card-title">Device Connections</div>
                <div className="settings-card-note">
                  Pair your trainer, HR, cadence, and other sensors here.
                </div>
                {!bluetoothAvailable ? (
                  <div className="device-warning">
                    Bluetooth is unavailable. Use Chrome or Edge with HTTPS/localhost.
                  </div>
                ) : null}
                <div className="device-list">
                  {deviceRows.map((row) => {
                    const { state } = row;
                    const isConnected = state.status === 'connected';
                    const isConnecting = state.status === 'connecting';
                    const name = state.name || row.label;
                    const statusLabel = isConnected
                      ? 'Connected'
                      : isConnecting
                        ? 'Connecting...'
                        : 'Not connected';
                    const infoParts = [state.manufacturer, state.model].filter(Boolean);
                    const errorMessage =
                      row.key === 'trainer'
                        ? [state.error, trainerTelemetryError, trainerControlError]
                          .filter(Boolean)
                          .join(' / ')
                        : state.error;
                    return (
                      <div key={row.key} className="device-row">
                        <div className="device-info">
                          <div className="device-name">{name}</div>
                          <div className="device-status">{row.label} - {statusLabel}</div>
                          {infoParts.length ? (
                            <div className="device-meta">{infoParts.join(' - ')}</div>
                          ) : null}
                          {row.key === 'trainer' ? (
                            <div className="device-meta">{trainerControlLabel}</div>
                          ) : null}
                          {row.key === 'trainer' && state.features ? (
                            <div className="device-meta">FTMS features: {state.features}</div>
                          ) : null}
                          {errorMessage ? (
                            <div className="device-error">{errorMessage}</div>
                          ) : null}
                        </div>
                        <div className="device-actions">
                          {state.battery !== null ? (
                            <div className="device-battery">
                              <span className="battery-dot" />
                              {state.battery}%
                            </div>
                          ) : null}
                          {isConnected ? (
                            <button
                              className="device-button disconnect"
                              type="button"
                              onClick={row.disconnect}
                            >
                              Disconnect
                            </button>
                          ) : (
                            <button
                              className="device-button"
                              type="button"
                              onClick={row.connect}
                              disabled={!bluetoothAvailable || isConnecting}
                            >
                              {isConnecting ? 'Connecting...' : 'Connect'}
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
              <div className="integration-card">
                <div>
                  <div className="integration-title">Intervals.icu</div>
                  <div className="integration-note">
                    Connect to auto-fill FTP, HR, and zones. OAuth support coming soon.
                  </div>
                </div>
                <button className="device-button" type="button" disabled>
                  Connect (Soon)
                </button>
              </div>
            </div>
            <div className="modal-footer">
              <button
                className="session-button"
                type="button"
                onClick={handleProfileClose}
              >
                Cancel
              </button>
              <button
                className="session-button primary"
                type="button"
                onClick={handleProfileSave}
              >
                Save Profile
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default App;
