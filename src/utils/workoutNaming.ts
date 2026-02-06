import type { WorkoutPlan, WorkoutSegment } from '../data/workout';

interface WorkoutStructure {
  type: string;
  structure: string;
  intensity: string;
}

/**
 * Generate technical workout name based on structure and intensity
 * Format: {Type} - {Structure} @ {Intensity}
 * 
 * Examples:
 * - "Sweet Spot Intervals - 4x3min @ 91% FTP"
 * - "Threshold Intervals - 2x20min @ 100% FTP"
 * - "VO2max Intervals - 5x3min @ 115% FTP"
 * - "Recovery Ride - 45min @ 55% FTP"
 */
export function generateWorkoutName(
  plan: WorkoutPlan,
  segments: WorkoutSegment[]
): string {
  const { type, structure, intensity } = analyzeWorkoutStructure(plan, segments);
  return `${type} - ${structure} @ ${intensity}`;
}

/**
 * Analyze workout to extract type, structure, and intensity
 */
function analyzeWorkoutStructure(
  plan: WorkoutPlan,
  segments: WorkoutSegment[]
): WorkoutStructure {
  const workSegments = segments.filter((s) => s.isWork);
  
  if (workSegments.length === 0) {
    return {
      type: 'Recovery Ride',
      structure: formatDuration(segments.reduce((sum, s) => sum + s.durationSec, 0)),
      intensity: '55% FTP',
    };
  }

  const totalWorkDuration = workSegments.reduce((sum, s) => sum + s.durationSec, 0);
  const intervalCount = workSegments.length;
  const avgIntervalDuration = totalWorkDuration / intervalCount;
  const avgIntensity = calculateAvgIntensity(workSegments, plan.ftpWatts);
  
  const type = detectWorkoutType(avgIntensity, avgIntervalDuration);
  const structure = formatStructure(intervalCount, avgIntervalDuration);
  const intensity = formatIntensity(avgIntensity);

  return { type, structure, intensity };
}

/**
 * Calculate average intensity as percentage of FTP
 */
function calculateAvgIntensity(
  workSegments: WorkoutSegment[],
  ftpWatts: number
): number {
  if (ftpWatts === 0) return 0;
  
  const totalIntensity = workSegments.reduce((sum, segment) => {
    const midPower = (segment.targetRange.low + segment.targetRange.high) / 2;
    return sum + (midPower / ftpWatts) * 100;
  }, 0);
  
  return Math.round(totalIntensity / workSegments.length);
}

/**
 * Detect workout type based on intensity and interval duration
 */
function detectWorkoutType(avgIntensity: number, avgIntervalDuration: number): string {
  // Recovery: <70% FTP
  if (avgIntensity < 70) {
    return 'Recovery Ride';
  }
  
  // Endurance: 70-80% FTP
  if (avgIntensity < 80) {
    return 'Endurance Ride';
  }
  
  // Sweet Spot: 88-94% FTP
  if (avgIntensity >= 88 && avgIntensity <= 94) {
    return 'Sweet Spot Intervals';
  }
  
  // Threshold: 95-105% FTP
  if (avgIntensity >= 95 && avgIntensity <= 105) {
    return 'Threshold Intervals';
  }
  
  // VO2max: >105% FTP, shorter intervals (<5min)
  if (avgIntensity > 105 && avgIntervalDuration < 300) {
    return 'VO2max Intervals';
  }
  
  // Anaerobic: >110% FTP, very short intervals
  if (avgIntensity > 110 && avgIntervalDuration < 120) {
    return 'Anaerobic Intervals';
  }
  
  // Neuromuscular: >120% FTP, very short
  if (avgIntensity > 120 && avgIntervalDuration < 60) {
    return 'Neuromuscular Intervals';
  }
  
  // Default based on duration
  if (avgIntervalDuration >= 600) {
    return 'Tempo Intervals';
  }
  
  return 'Mixed Intervals';
}

/**
 * Format interval structure string
 */
function formatStructure(count: number, durationSec: number): string {
  const duration = formatDuration(durationSec);
  
  if (count === 1) {
    return duration;
  }
  
  return `${count}x${duration}`;
}

/**
 * Format intensity as percentage
 */
function formatIntensity(intensity: number): string {
  return `${intensity}% FTP`;
}

/**
 * Format duration in compact form
 */
function formatDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  
  if (hours > 0) {
    return `${hours}h${minutes > 0 ? minutes : ''}`;
  }
  
  return `${minutes}min`;
}

/**
 * Extract workout type category (for grouping/filtering)
 */
export function getWorkoutCategory(plan: WorkoutPlan, segments: WorkoutSegment[]): string {
  const { type } = analyzeWorkoutStructure(plan, segments);
  
  if (type.includes('Recovery')) return 'recovery';
  if (type.includes('Endurance')) return 'endurance';
  if (type.includes('Tempo')) return 'tempo';
  if (type.includes('Sweet Spot')) return 'sweet_spot';
  if (type.includes('Threshold')) return 'threshold';
  if (type.includes('VO2max')) return 'vo2max';
  if (type.includes('Anaerobic')) return 'anaerobic';
  if (type.includes('Neuromuscular')) return 'neuromuscular';
  
  return 'mixed';
}

/**
 * Check if workout is high intensity
 */
export function isHighIntensityWorkout(plan: WorkoutPlan, segments: WorkoutSegment[]): boolean {
  const workSegments = segments.filter((s) => s.isWork);
  if (workSegments.length === 0) return false;
  
  const avgIntensity = calculateAvgIntensity(workSegments, plan.ftpWatts);
  return avgIntensity > 100;
}
