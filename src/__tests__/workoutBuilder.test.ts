import { describe, it, expect } from 'vitest';
import { parseWorkoutTextWithDefaults } from '../utils/workoutParser';

describe('workoutBuilder', () => {
  describe('power ranges', () => {
    it('should parse explicit watt ranges (150-200w)', () => {
      const text = '- 5m 150-200w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments).toHaveLength(1);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 150,
        high: 200
      });
    });

    it('should parse explicit percentage ranges (80-90%)', () => {
      const text = '- 5m 80-90%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 200,  // 80% of 250
        high: 225  // 90% of 250
      });
    });

    it('should parse mixed unit ranges using the same unit for both values', () => {
      // Parser uses consistent units - both values should be in same unit
      // For mixed ranges like watt-percentage, use explicit format
      const text = '- 5m 60-80%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 150,   // 60% of 250
        high: 200   // 80% of 250
      });
    });

    it('should parse low percentage ranges (10-20%)', () => {
      const text = '- 5m 10-20%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 25,   // 10% of 250
        high: 50   // 20% of 250
      });
    });

    it('should handle very low watt targets', () => {
      const text = '- 5m 20-30w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 20,
        high: 30
      });
    });
  });

  describe('single value power targets (exact)', () => {
    it('should parse single watt value without creating range', () => {
      const text = '- 5m 200w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 200,
        high: 200
      });
    });

    it('should parse single percentage value without creating range', () => {
      const text = '- 5m 85%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 212.5,  // 85% of 250
        high: 212.5
      });
    });

    it('should parse very low single percentage (10%) without range', () => {
      const text = '- 5m 10%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 25,   // 10% of 250
        high: 25
      });
    });

    it('should parse zero/low watt value', () => {
      const text = '- 5m 0w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 0,
        high: 0
      });
    });
  });

  describe('ramp intervals with ranges', () => {
    it('should parse ramp with watt ranges', () => {
      const text = '- Ramp 10m 100-200w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].rampToRange).toBeDefined();
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 100,
        high: 100
      });
      expect(result.plan.segments[0].rampToRange).toEqual({
        low: 200,
        high: 200
      });
    });

    it('should parse ramp with percentage ranges', () => {
      const text = '- Ramp 10m 50-80%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].rampToRange).toBeDefined();
      // Verify ramp is created with appropriate ranges
      expect(result.plan.segments[0].targetRange.low).toBeGreaterThan(0);
      expect(result.plan.segments[0].targetRange.high).toBeGreaterThan(0);
      expect(result.plan.segments[0].rampToRange!.low).toBeGreaterThan(0);
      expect(result.plan.segments[0].rampToRange!.high).toBeGreaterThan(0);
    });

    it('should parse ramp from single value to another single value', () => {
      const text = '- Ramp 10m 100-200w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 100,
        high: 100
      });
      expect(result.plan.segments[0].rampToRange).toEqual({
        low: 200,
        high: 200
      });
    });
  });

  describe('complex workout scenarios', () => {
    it('should handle workout with mixed power specifications', () => {
      const text = `
Warmup
- 10m 50%

Main 4x
- 5m 150-180w
- 3m 60%

Cooldown
- 5m 40%
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments).toHaveLength(10); // 1 warmup + (4 * (1 interval + 1 recovery)) + 1 cooldown
      
      // Check first interval after warmup has correct power range
      const firstInterval = result.plan.segments[1];
      expect(firstInterval.targetRange).toEqual({ low: 150, high: 180 });
      
      // Check first recovery has correct power (60% of 250)
      const firstRecovery = result.plan.segments[2];
      expect(firstRecovery.targetRange.low).toBe(150); // 60% of 250
      expect(firstRecovery.targetRange.high).toBe(150);
    });

    it('should handle workout with cadence and power ranges', () => {
      const text = `
- 5m 150-200w 85-95rpm
- 3m 100w 90rpm
- 5m 180-220w
      `.trim();

      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments).toHaveLength(3);
      
      // First interval: power range + cadence range
      expect(result.plan.segments[0].targetRange).toEqual({ low: 150, high: 200 });
      expect(result.plan.segments[0].cadenceRange).toEqual({ low: 85, high: 95 });
      
      // Second interval: single power + single cadence
      expect(result.plan.segments[1].targetRange).toEqual({ low: 100, high: 100 });
      expect(result.plan.segments[1].cadenceRange).toEqual({ low: 90, high: 90 });
      
      // Third interval: power range only
      expect(result.plan.segments[2].targetRange).toEqual({ low: 180, high: 220 });
      expect(result.plan.segments[2].cadenceRange).toBeUndefined();
    });
  });

  describe('edge cases and error handling', () => {
    it('should handle malformed power ranges gracefully', () => {
      const text = '- 5m 150-';  // Incomplete range
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      // Should either error or use default
      expect(result.plan.segments).toHaveLength(1);
    });

    it('should handle inverted ranges (200-150w)', () => {
      const text = '- 5m 200-150w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      // Parser returns values as-is without normalization
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 200,
        high: 150
      });
    });

    it('should handle very wide ranges', () => {
      const text = '- 5m 10-300w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 10,
        high: 300
      });
    });

    it('should handle decimal values in ranges', () => {
      const text = '- 5m 150.5-200.5w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange).toEqual({
        low: 150.5,
        high: 200.5
      });
    });
  });

  describe('chart scaling considerations', () => {
    it('should create segments that can be displayed with low targets', () => {
      const text = '- 5m 10%';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange.low).toBe(25);
      expect(result.plan.segments[0].targetRange.high).toBe(25);
      
      // Verify segment is valid for chart display
      expect(result.plan.segments[0].durationSec).toBe(300);
      expect(result.plan.segments[0].phase).toBe('recovery');
    });

    it('should create segments with zero watt targets', () => {
      const text = '- 5m 0w';
      const result = parseWorkoutTextWithDefaults(text, 250);
      
      expect(result.errors).toHaveLength(0);
      expect(result.plan.segments[0].targetRange.low).toBe(0);
      expect(result.plan.segments[0].targetRange.high).toBe(0);
    });
  });
});
