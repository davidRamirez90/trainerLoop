export type SegmentPhase = 'warmup' | 'work' | 'recovery' | 'cooldown';

export type TargetRange = {
  low: number;
  high: number;
};

export type WorkoutSegment = {
  id: string;
  label: string;
  durationSec: number;
  targetRange: TargetRange;
  rampToRange?: TargetRange;
  phase: SegmentPhase;
  isWork: boolean;
};

export type WorkoutPlan = {
  id: string;
  name: string;
  subtitle: string;
  ftpWatts: number;
  segments: WorkoutSegment[];
};

export const workoutPlan: WorkoutPlan = {
  id: 'sweet-spot-4x3',
  name: 'Sweet Spot Intervals',
  subtitle: '4x3 min · 91% FTP',
  ftpWatts: 290,
  segments: [
    {
      id: 'warmup-1',
      label: 'Warmup',
      durationSec: 300,
      targetRange: { low: 120, high: 140 },
      rampToRange: { low: 150, high: 170 },
      phase: 'warmup',
      isWork: false,
    },
    {
      id: 'work-1',
      label: 'Interval 1',
      durationSec: 180,
      targetRange: { low: 250, high: 275 },
      phase: 'work',
      isWork: true,
    },
    {
      id: 'recovery-1',
      label: 'Recovery 1',
      durationSec: 120,
      targetRange: { low: 150, high: 170 },
      phase: 'recovery',
      isWork: false,
    },
    {
      id: 'work-2',
      label: 'Interval 2',
      durationSec: 180,
      targetRange: { low: 250, high: 275 },
      phase: 'work',
      isWork: true,
    },
    {
      id: 'recovery-2',
      label: 'Recovery 2',
      durationSec: 120,
      targetRange: { low: 150, high: 170 },
      phase: 'recovery',
      isWork: false,
    },
    {
      id: 'work-3',
      label: 'Interval 3',
      durationSec: 180,
      targetRange: { low: 250, high: 275 },
      phase: 'work',
      isWork: true,
    },
    {
      id: 'recovery-3',
      label: 'Recovery 3',
      durationSec: 120,
      targetRange: { low: 150, high: 170 },
      phase: 'recovery',
      isWork: false,
    },
    {
      id: 'work-4',
      label: 'Interval 4',
      durationSec: 180,
      targetRange: { low: 250, high: 275 },
      phase: 'work',
      isWork: true,
    },
    {
      id: 'cooldown-1',
      label: 'Cooldown',
      durationSec: 300,
      targetRange: { low: 150, high: 170 },
      rampToRange: { low: 120, high: 135 },
      phase: 'cooldown',
      isWork: false,
    },
  ],
};
