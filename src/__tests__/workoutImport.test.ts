import { describe, it, expect } from 'vitest';
import { parseWorkoutFile, normalizeWorkoutPlan } from '../utils/workoutImport';
import type { WorkoutPlan } from '../data/workout';


describe('workoutImport utilities', () => {
  describe('normalizeWorkoutPlan', () => {
    const validWorkout: WorkoutPlan = {
      id: 'test-workout',
      name: 'Test Workout',
      subtitle: 'A test workout',
      ftpWatts: 250,
      segments: [
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
      ],
    };

    it('parses a valid workout plan', () => {
      const result = normalizeWorkoutPlan(validWorkout, 'Fallback', 'Fallback Subtitle');
      expect(result.id).toBe('test-workout');
      expect(result.name).toBe('Test Workout');
      expect(result.subtitle).toBe('A test workout');
      expect(result.ftpWatts).toBe(250);
      expect(result.segments).toHaveLength(2);
    });

    it('uses fallback name when name is missing', () => {
      const workoutWithoutName = { ...validWorkout, name: '' };
      const result = normalizeWorkoutPlan(workoutWithoutName, 'Fallback Name', 'Subtitle');
      expect(result.name).toBe('Fallback Name');
    });

    it('uses fallback name when name is whitespace only', () => {
      const workoutWithoutName = { ...validWorkout, name: '   ' };
      const result = normalizeWorkoutPlan(workoutWithoutName, 'Fallback Name', 'Subtitle');
      expect(result.name).toBe('Fallback Name');
    });

    it('uses fallback subtitle when subtitle is missing', () => {
      const workoutWithoutSubtitle = { ...validWorkout, subtitle: '' };
      const result = normalizeWorkoutPlan(workoutWithoutSubtitle, 'Name', 'Fallback Subtitle');
      expect(result.subtitle).toBe('Fallback Subtitle');
    });

    it('generates id when id is missing', () => {
      const workoutWithoutId = { ...validWorkout, id: '' };
      const result = normalizeWorkoutPlan(workoutWithoutId, 'Name', 'Subtitle');
      expect(result.id).toMatch(/^import-\d+$/);
    });

    it('throws when segments is not an array', () => {
      const invalidWorkout = { ...validWorkout, segments: 'not-an-array' };
      expect(() => normalizeWorkoutPlan(invalidWorkout, 'Name', 'Subtitle')).toThrow(
        'Workout must include a non-empty segments array.'
      );
    });

    it('throws when segments is empty array', () => {
      const invalidWorkout = { ...validWorkout, segments: [] };
      expect(() => normalizeWorkoutPlan(invalidWorkout, 'Name', 'Subtitle')).toThrow(
        'Workout must include a non-empty segments array.'
      );
    });

    it('throws when ftpWatts is missing', () => {
      const invalidWorkout = { ...validWorkout, ftpWatts: undefined };
      expect(() => normalizeWorkoutPlan(invalidWorkout, 'Name', 'Subtitle')).toThrow(
        'ftpWatts must be a number.'
      );
    });

    it('throws when input is not an object', () => {
      expect(() => normalizeWorkoutPlan(null, 'Name', 'Subtitle')).toThrow(
        'Workout JSON must be an object.'
      );
      expect(() => normalizeWorkoutPlan('string' as unknown as object, 'Name', 'Subtitle')).toThrow(
        'Workout JSON must be an object.'
      );
    });
  });

  describe('parseWorkoutFile - JSON format', () => {
    it('parses JSON workout file', () => {
      const jsonContent = JSON.stringify({
        name: 'JSON Workout',
        subtitle: 'Test',
        ftpWatts: 250,
        segments: [
          {
            id: 'seg-1',
            label: 'Warmup',
            durationSec: 300,
            targetRange: { low: 100, high: 150 },
            phase: 'warmup',
            isWork: false,
          },
        ],
      });
      const result = parseWorkoutFile('workout.json', jsonContent);
      expect(result.plan.name).toBe('JSON Workout');
      expect(result.plan.ftpWatts).toBe(250);
      expect(result.plan.segments).toHaveLength(1);
    });

    it('detects JSON by content starting with {', () => {
      const content = `{"name":"Test","subtitle":"Test","ftpWatts":250,"segments":[{"id":"seg1","label":"Test","durationSec":300,"targetRange":{"low":100,"high":150},"phase":"warmup","isWork":false}]}`;
      const result = parseWorkoutFile('workout.erg', content);
      expect(result.plan.name).toBe('Test');
    });

    it('uses filename as fallback name for JSON', () => {
      const jsonContent = JSON.stringify({
        ftpWatts: 250,
        segments: [
          {
            durationSec: 300,
            targetRange: { low: 100, high: 150 },
            phase: 'warmup',
          },
        ],
      });
      const result = parseWorkoutFile('my-custom-workout.json', jsonContent);
      expect(result.plan.name).toBe('my-custom-workout');
    });
  });

  describe('parseWorkoutFile - ERG/MRC format', () => {
    it('parses basic ERG file', () => {
      const ergContent = `[COURSE]
FTP = 250
MINUTES POWER
0 100
5 200
10 150
`;
      const result = parseWorkoutFile('test.erg', ergContent);
      expect(result.plan.name).toBe('test');
      expect(result.plan.ftpWatts).toBe(250);
      expect(result.meta.ftpSource).toBe('file');
    });

    it('uses fallback FTP when not in file', () => {
      const ergContent = `[COURSE]
MINUTES POWER
0 100
5 200
`;
      const result = parseWorkoutFile('test.erg', ergContent);
      expect(result.plan.ftpWatts).toBe(250); // default
      expect(result.meta.ftpSource).toBe('fallback');
    });

    it('allows override FTP option', () => {
      const ergContent = `[COURSE]
FTP = 250
MINUTES POWER
0 100
5 200
`;
      const result = parseWorkoutFile('test.erg', ergContent, { overrideFtpWatts: 300 });
      expect(result.plan.ftpWatts).toBe(300);
      expect(result.meta.ftpSource).toBe('override');
    });

    it('handles SECONDS format', () => {
      const ergContent = `[COURSE]
FTP = 250
SECONDS POWER
0 100
60 200
120 150
`;
      const result = parseWorkoutFile('test.erg', ergContent);
      expect(result.plan.segments.length).toBeGreaterThan(0);
    });

    it('handles PERCENT format', () => {
      const ergContent = `[COURSE]
FTP = 250
MINUTES PERCENT
0 40
5 80
`;
      const result = parseWorkoutFile('test.erg', ergContent);
      expect(result.plan.segments[0].targetRange.low).toBe(100); // 40% of 250
    });

    it('throws for files with less than 2 data points', () => {
      const ergContent = `[COURSE]
FTP = 250
MINUTES POWER
0 100
`;
      expect(() => parseWorkoutFile('test.erg', ergContent)).toThrow(
        'ERG/MRC file must include at least two data points.'
      );
    });

    it('throws for empty file', () => {
      expect(() => parseWorkoutFile('test.erg', '')).toThrow(
        'ERG/MRC file must include at least two data points.'
      );
    });
  });

  describe('parseWorkoutFile - ZWO format', () => {
    it('parses basic ZWO XML', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<workout>
  <name>ZWO Workout</name>
  <description>Test</description>
  <steadyState Duration="300" Power="0.5"/>
</workout>`;
      const result = parseWorkoutFile('test.zwo', zwoContent);
      expect(result.plan.name).toBe('ZWO Workout');
      expect(result.plan.segments.length).toBe(1);
    });

    it('uses filename as name when name element is missing', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<workout>
  <description>Test</description>
  <steadyState Duration="300" Power="0.5"/>
</workout>`;
      const result = parseWorkoutFile('my-zwo-workout.zwo', zwoContent);
      expect(result.plan.name).toBe('my-zwo-workout');
    });

    it('parses warmup element', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<workout>
  <warmup Duration="300" PowerLow="0.4" PowerHigh="0.6"/>
</workout>`;
      const result = parseWorkoutFile('test.zwo', zwoContent);
      expect(result.plan.segments[0].phase).toBe('warmup');
      expect(result.plan.segments[0].label).toBe('Warmup');
    });

    it('parses cooldown element', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<workout>
  <cooldown Duration="300" PowerLow="0.4" PowerHigh="0.5"/>
</workout>`;
      const result = parseWorkoutFile('test.zwo', zwoContent);
      expect(result.plan.segments[0].phase).toBe('cooldown');
      expect(result.plan.segments[0].label).toBe('Cooldown');
    });

    it('throws for invalid XML', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<invalid>`;
      expect(() => parseWorkoutFile('test.zwo', zwoContent)).toThrow('Invalid ZWO XML.');
    });

    it('throws when workout element is missing', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<workout>
</workout>`;
      expect(() => parseWorkoutFile('test.zwo', zwoContent)).toThrow(
        'No workout steps found in this ZWO file.'
      );
    });

    it('throws when no workout steps found', () => {
      const zwoContent = `<?xml version="1.0" encoding="UTF-8"?>
<workout>
  <name>Empty</name>
</workout>`;
      expect(() => parseWorkoutFile('test.zwo', zwoContent)).toThrow(
        'No workout steps found in this ZWO file.'
      );
    });
  });
});
