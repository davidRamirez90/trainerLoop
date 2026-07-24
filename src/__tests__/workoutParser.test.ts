import { describe, it, expect } from 'vitest';
import { parseWorkoutText, parseWorkoutTextWithDefaults } from '../utils/workoutParser';
import type { WorkoutSegment } from '../data/workout';

describe('workoutParser', () => {
  describe('basic parsing', () => {
    it('should parse a simple warmup and cooldown', () => {
      const text = `
Warmup
- 10m 50%

Cooldown
- 5m 40%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments).toHaveLength(2);
      expect(result.plan.segments[0].phase).toBe('warmup');
      expect(result.plan.segments[0].durationSec).toBe(600);
      expect(result.plan.segments[1].phase).toBe('cooldown');
    });

    it('should parse intervals with power in watts', () => {
      const text = `
- 5m 200w
- 3m 150w
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments).toHaveLength(2);
      expect(result.plan.segments[0].targetRange.low).toBe(200);
      expect(result.plan.segments[0].targetRange.high).toBe(200);
    });

    it('should parse intervals with power as percentage', () => {
      const text = `
- 5m 80%
- 3m 50%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange.low).toBe(200);
      expect(result.plan.segments[0].targetRange.high).toBe(200);
    });

    it('should parse intervals with power ranges', () => {
      const text = `- 5m 180-220w`;

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange.low).toBe(180);
      expect(result.plan.segments[0].targetRange.high).toBe(220);
    });
  });

  describe('duration parsing', () => {
    it('should parse various duration formats', () => {
      const testCases = [
        { input: '- 30s 50%', expected: 30 },
        { input: '- 5m 50%', expected: 300 },
        { input: '- 1h 50%', expected: 3600 },
        { input: '- 1h30m 50%', expected: 5400 },
        { input: '- 1m30s 50%', expected: 90 },
        { input: '- 90s 50%', expected: 90 },
      ];

      for (const testCase of testCases) {
        const result = parseWorkoutTextWithDefaults(testCase.input, 250);
        expect(result.plan.segments[0].durationSec).toBe(testCase.expected);
      }
    });

    it('should default to 5 minutes if no duration specified', () => {
      const result = parseWorkoutTextWithDefaults('- 50%', 250);
      expect(result.plan.segments[0].durationSec).toBe(300);
    });
  });

  describe('cadence parsing', () => {
    it('should parse single cadence value', () => {
      const result = parseWorkoutTextWithDefaults('- 5m 80% 90rpm', 250);
      expect(result.plan.segments[0].cadenceRange).toEqual({ low: 90, high: 90 });
    });

    it('should parse cadence range', () => {
      const result = parseWorkoutTextWithDefaults('- 5m 80% 85-95rpm', 250);
      expect(result.plan.segments[0].cadenceRange).toEqual({ low: 85, high: 95 });
    });
  });

  describe('repeat blocks', () => {
    it('should parse simple repeat block', () => {
      const text = `
Main set 3x
- 3m 90%
- 2m 50%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.plan.segments).toHaveLength(6); // 2 segments × 3 repeats
      expect(result.plan.segments[0].label).toBe('Interval');
      expect(result.plan.segments[1].label).toBe('Recovery');
    });

    it('should handle multiple repeat blocks', () => {
      const text = `
Block 1 2x
- 2m 90%
- 1m 50%

Block 2 2x
- 3m 85%
- 2m 50%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.plan.segments).toHaveLength(8); // (2 + 2) × 2 repeats
    });
  });

  describe('phase detection', () => {
    it('should detect warmup phase', () => {
      const result = parseWorkoutTextWithDefaults('Warmup\n- 10m 50%', 250);
      expect(result.plan.segments[0].phase).toBe('warmup');
      expect(result.plan.segments[0].isWork).toBe(false);
    });

    it('should detect cooldown phase', () => {
      const result = parseWorkoutTextWithDefaults('Cooldown\n- 5m 40%', 250);
      expect(result.plan.segments[0].phase).toBe('cooldown');
      expect(result.plan.segments[0].isWork).toBe(false);
    });

    it('should detect recovery phase', () => {
      const result = parseWorkoutTextWithDefaults('Recovery\n- 5m 40%', 250);
      expect(result.plan.segments[0].phase).toBe('recovery');
      expect(result.plan.segments[0].isWork).toBe(false);
    });

    it('should default to work phase', () => {
      const result = parseWorkoutTextWithDefaults('- 5m 90%', 250);
      expect(result.plan.segments[0].phase).toBe('work');
      expect(result.plan.segments[0].isWork).toBe(true);
    });
  });

  describe('custom labels', () => {
    it('should parse custom labels', () => {
      const result = parseWorkoutTextWithDefaults('- Hard interval 5m 90%', 250);
      expect(result.plan.segments[0].label).toBe('Hard interval');
    });

    it('should use default labels based on phase', () => {
      const result = parseWorkoutTextWithDefaults(
        'Warmup\n- 10m 50%\n\nRecovery\n- 5m 50%', 
        250
      );
      expect(result.plan.segments[0].label).toBe('Warmup');
      expect(result.plan.segments[1].label).toBe('Recovery');
    });
  });

  describe('ramp intervals', () => {
    it('should parse ramp with power range', () => {
      const result = parseWorkoutTextWithDefaults('- Ramp 10m 50-90%', 250);
      expect(result.plan.segments[0].rampToRange).toBeDefined();
      // Verify ramp ranges are set
      expect(result.plan.segments[0].targetRange.low).toBeGreaterThan(0);
      expect(result.plan.segments[0].targetRange.high).toBeGreaterThan(0);
    });

    it('should parse ramp with watts', () => {
      const result = parseWorkoutTextWithDefaults('- Ramp 10m 100-200w', 250);
      expect(result.plan.segments[0].rampToRange).toBeDefined();
      expect(result.plan.segments[0].targetRange.low).toBe(100);
      expect(result.plan.segments[0].rampToRange!.high).toBe(200);
    });
  });

  describe('zone parsing', () => {
    it('should parse zone notation with provided zones', () => {
      const powerZones = {
        Z1: { low: 0.5, high: 0.7 },
        Z2: { low: 0.7, high: 0.9 },
        Z3: { low: 0.9, high: 1.0 },
      };

      const result = parseWorkoutText(
        '- 10m Z2',
        { ftpWatts: 250, powerZones }
      );

      expect(result.plan.segments[0].targetRange.low).toBe(175);
      expect(result.plan.segments[0].targetRange.high).toBe(225);
    });
  });

  describe('complex workouts', () => {
    it('should parse a complete structured workout', () => {
      const text = `
Warmup
- 10m 50%
- 5m 60-75% Ramp

Main set 4x
- 4m 90% 85-95rpm
- 3m 50% easy spin

Cooldown
- 10m 40%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);

      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments.length).toBeGreaterThan(0);
      
      // Check warmup
      expect(result.plan.segments[0].phase).toBe('warmup');
      
      // Check ramp
      const rampSeg = result.plan.segments.find((s: WorkoutSegment) => s.rampToRange);
      expect(rampSeg).toBeDefined();
      
      // Check repeated intervals (should have work intervals)
      const workSegments = result.plan.segments.filter((s: WorkoutSegment) => s.isWork);
      expect(workSegments.length).toBeGreaterThan(0);
      
      // Check cooldown
      const cooldownSegs = result.plan.segments.filter((s: WorkoutSegment) => s.phase === 'cooldown');
      expect(cooldownSegs.length).toBeGreaterThan(0);
    });
  });

  describe('error handling', () => {
    it('should report errors for invalid power specifications', () => {
      const result = parseWorkoutTextWithDefaults('- 5m invalid', 250);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should still return a plan even with errors', () => {
      const result = parseWorkoutTextWithDefaults('- 5m invalid', 250);
      expect(result.plan.segments.length).toBeGreaterThan(0);
    });
  });

  describe('metadata calculation', () => {
    it('should calculate workout subtitle with segment count and duration', () => {
      const result = parseWorkoutTextWithDefaults(
        '- 5m 80%\n- 3m 50%', 
        250
      );
      
      expect(result.plan.subtitle).toContain('2 segments');
      expect(result.plan.subtitle).toContain('8 min');
    });

    it('should use provided FTP in plan', () => {
      const result = parseWorkoutTextWithDefaults('- 5m 80%', 300);
      expect(result.plan.ftpWatts).toBe(300);
    });
  });

  describe('edge cases', () => {
    it('should handle empty input', () => {
      const result = parseWorkoutTextWithDefaults('', 250);
      expect(result.plan.segments).toHaveLength(0);
    });

    it('should handle comments and empty lines', () => {
      const text = `
// This is a comment
Warmup
- 5m 50%

# Another comment
- 3m 80%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);
      expect(result.plan.segments).toHaveLength(2);
    });

    it('should handle very long workouts', () => {
      // 2 steps repeated 10 times = 20 segments
      const text = 'Main set 10x\n- 1m 90%\n- 1m 50%';

      const result = parseWorkoutTextWithDefaults(text, 250);
      expect(result.plan.segments).toHaveLength(20);
    });
  });
});
