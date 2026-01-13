export type SegmentPhase = 'warmup' | 'work' | 'recovery' | 'cooldown';

export type WorkoutSegment = {
  id: string;
  label: string;
  durationSec: number;
  targetRangeWatts: [number, number];
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
      targetRangeWatts: [140, 165],
      phase: 'warmup',
      isWork: false,
    },
    {
      id: 'work-1',
      label: 'Interval 1',
      durationSec: 180,
      targetRangeWatts: [250, 275],
      phase: 'work',
      isWork: true,
    },
    {
      id: 'recovery-1',
      label: 'Recovery 1',
      durationSec: 120,
      targetRangeWatts: [150, 170],
      phase: 'recovery',
      isWork: false,
    },
    {
      id: 'work-2',
      label: 'Interval 2',
      durationSec: 180,
      targetRangeWatts: [250, 275],
      phase: 'work',
      isWork: true,
    },
    {
      id: 'recovery-2',
      label: 'Recovery 2',
      durationSec: 120,
      targetRangeWatts: [150, 170],
      phase: 'recovery',
      isWork: false,
    },
    {
      id: 'work-3',
      label: 'Interval 3',
      durationSec: 180,
      targetRangeWatts: [250, 275],
      phase: 'work',
      isWork: true,
    },
    {
      id: 'recovery-3',
      label: 'Recovery 3',
      durationSec: 120,
      targetRangeWatts: [150, 170],
      phase: 'recovery',
      isWork: false,
    },
    {
      id: 'work-4',
      label: 'Interval 4',
      durationSec: 180,
      targetRangeWatts: [250, 275],
      phase: 'work',
      isWork: true,
    },
    {
      id: 'cooldown-1',
      label: 'Cooldown',
      durationSec: 300,
      targetRangeWatts: [130, 150],
      phase: 'cooldown',
      isWork: false,
    },
  ],
};
