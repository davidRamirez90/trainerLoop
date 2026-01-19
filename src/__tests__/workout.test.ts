import { describe, it, expect } from 'vitest';
import type { WorkoutSegment } from '../data/workout';
import { getTotalDurationSec, getSegmentAtTime } from '../utils/workout';

describe('workout utilities', () => {
  describe('getTotalDurationSec', () => {
    it('returns 0 for empty segments array', () => {
      const result = getTotalDurationSec([]);
      expect(result).toBe(0);
    });

    it('calculates total duration of single segment', () => {
      const segments: WorkoutSegment[] = [
        {
          id: 'seg-1',
          label: 'Segment 1',
          durationSec: 300,
          targetRange: { low: 100, high: 150 },
          phase: 'warmup',
          isWork: false,
        },
      ];
      const result = getTotalDurationSec(segments);
      expect(result).toBe(300);
    });

    it('calculates total duration of multiple segments', () => {
      const segments: WorkoutSegment[] = [
        {
          id: 'seg-1',
          label: 'Warmup',
          durationSec: 300,
          targetRange: { low: 100, high: 150 },
          phase: 'warmup',
          isWork: false,
        },
        {
          id: 'seg-2',
          label: 'Work',
          durationSec: 600,
          targetRange: { low: 200, high: 250 },
          phase: 'work',
          isWork: true,
        },
        {
          id: 'seg-3',
          label: 'Cooldown',
          durationSec: 300,
          targetRange: { low: 100, high: 150 },
          phase: 'cooldown',
          isWork: false,
        },
      ];
      const result = getTotalDurationSec(segments);
      expect(result).toBe(1200); // 300 + 600 + 300
    });

    it('handles fractional segment durations', () => {
      const segments: WorkoutSegment[] = [
        {
          id: 'seg-1',
          label: 'Segment 1',
          durationSec: 180.5,
          targetRange: { low: 100, high: 150 },
          phase: 'work',
          isWork: true,
        },
        {
          id: 'seg-2',
          label: 'Segment 2',
          durationSec: 120.3,
          targetRange: { low: 100, high: 150 },
          phase: 'recovery',
          isWork: false,
        },
      ];
      const result = getTotalDurationSec(segments);
      expect(result).toBe(300.8); // 180.5 + 120.3
    });
  });

  describe('getSegmentAtTime', () => {
    const createSegment = (id: string, durationSec: number, label = 'Segment'): WorkoutSegment => ({
      id,
      label,
      durationSec,
      targetRange: { low: 100, high: 150 },
      phase: 'work',
      isWork: true,
    });

    it('returns the first segment at time 0', () => {
      const segments = [
        createSegment('seg-1', 300),
        createSegment('seg-2', 600),
      ];
      const result = getSegmentAtTime(segments, 0);
      expect(result.segment.id).toBe('seg-1');
      expect(result.index).toBe(0);
      expect(result.startSec).toBe(0);
      expect(result.endSec).toBe(300);
    });

    it('returns the correct segment for time within first segment', () => {
      const segments = [
        createSegment('seg-1', 300),
        createSegment('seg-2', 600),
      ];
      const result = getSegmentAtTime(segments, 150);
      expect(result.segment.id).toBe('seg-1');
      expect(result.index).toBe(0);
      expect(result.startSec).toBe(0);
      expect(result.endSec).toBe(300);
    });

    it('returns the correct segment for time at segment boundary', () => {
      const segments = [
        createSegment('seg-1', 300),
        createSegment('seg-2', 600),
      ];
      const result = getSegmentAtTime(segments, 300);
      expect(result.segment.id).toBe('seg-2');
      expect(result.index).toBe(1);
      expect(result.startSec).toBe(300);
      expect(result.endSec).toBe(900);
    });

    it('returns the correct segment for time in second segment', () => {
      const segments = [
        createSegment('seg-1', 300),
        createSegment('seg-2', 600),
      ];
      const result = getSegmentAtTime(segments, 500);
      expect(result.segment.id).toBe('seg-2');
      expect(result.index).toBe(1);
      expect(result.startSec).toBe(300);
      expect(result.endSec).toBe(900);
    });

    it('returns the last segment for time beyond total duration', () => {
      const segments = [
        createSegment('seg-1', 300),
        createSegment('seg-2', 600),
      ];
      const result = getSegmentAtTime(segments, 1500);
      expect(result.segment.id).toBe('seg-2');
      expect(result.index).toBe(1);
      expect(result.startSec).toBe(300);
      expect(result.endSec).toBe(900);
    });

    it('handles empty array', () => {
      const result = getSegmentAtTime([], 100);
      expect(result.segment).toBeUndefined();
      expect(result.index).toBe(0);
      expect(result.startSec).toBe(0);
      expect(result.endSec).toBe(0);
    });
  });
});
