import type { WorkoutPlan, WorkoutSegment } from '../data/workout';
import type { TelemetrySample } from '../types';
import { calculateAveragePower, calculateNormalizedPower, calculateTSS } from './trainingMetrics';

export interface WorkoutDescriptionData {
  goal: string;
  adherence: number;
  avgPower: number;
  tss: number;
  np: number;
}

/**
 * Generate workout description with goal and metrics
 * 
 * Examples:
 * - "Accumulate 20 minutes in threshold range. Adherence: 94% | Avg Power: 285W | TSS: 65"
 * - "Recovery ride as part of cooldown block week. Adherence: 98% | Avg Power: 145W | TSS: 28"
 * - "Build aerobic capacity with 24min sweet spot work. Adherence: 91% | Avg Power: 265W | TSS: 82"
 */
export function generateWorkoutDescription(
  plan: WorkoutPlan,
  segments: WorkoutSegment[],
  samples: TelemetrySample[],
  adherencePercent: number
): string {
  const goal = generateGoalStatement(segments, plan);
  const avgPower = calculateAveragePower(samples);
  const np = calculateNormalizedPower(samples);
  const tss = calculateTSS(samples, plan.ftpWatts);
  
  const metrics = [
    `Adherence: ${Math.round(adherencePercent)}%`,
    avgPower > 0 ? `Avg Power: ${Math.round(avgPower)}W` : null,
    tss > 0 ? `TSS: ${Math.round(tss)}` : null,
    np > 0 ? `NP: ${Math.round(np)}W` : null,
  ].filter(Boolean);
  
  return `${goal} ${metrics.join(' | ')}`;
}

/**
 * Generate goal statement based on workout structure
 */
function generateGoalStatement(segments: WorkoutSegment[], plan: WorkoutPlan): string {
  const workSegments = segments.filter((s) => s.isWork);
  
  if (workSegments.length === 0) {
    return 'Recovery ride as part of training block.';
  }
  
  const totalWorkMinutes = Math.round(
    workSegments.reduce((sum, s) => sum + s.durationSec, 0) / 60
  );
  
  const intensityZone = detectPrimaryZone(workSegments, plan.ftpWatts);
  
  const goals: Record<string, string> = {
    recovery: `Complete ${totalWorkMinutes} minute recovery session to promote recovery`,
    endurance: `Build aerobic base with ${totalWorkMinutes} minutes of endurance work`,
    tempo: `Develop muscular endurance with ${totalWorkMinutes} minutes of tempo work`,
    sweet_spot: `Build aerobic capacity with ${totalWorkMinutes} minutes of sweet spot work`,
    threshold: `Accumulate ${totalWorkMinutes} minutes in threshold range`,
    vo2max: `Develop aerobic power with ${totalWorkMinutes} minutes of VO2max work`,
    anaerobic: `Improve anaerobic capacity with ${totalWorkMinutes} minutes of anaerobic work`,
    neuromuscular: `Develop neuromuscular power with ${totalWorkMinutes} minutes of sprint work`,
  };
  
  return goals[intensityZone] || `Complete ${totalWorkMinutes} minutes of structured work`;
}

/**
 * Detect primary training zone
 */
function detectPrimaryZone(
  workSegments: WorkoutSegment[],
  ftpWatts: number
): string {
  if (ftpWatts === 0) return 'mixed';
  
  const avgIntensity = workSegments.reduce((sum, segment) => {
    const midPower = (segment.targetRange.low + segment.targetRange.high) / 2;
    return sum + (midPower / ftpWatts) * 100;
  }, 0) / workSegments.length;
  
  if (avgIntensity < 70) return 'recovery';
  if (avgIntensity < 80) return 'endurance';
  if (avgIntensity < 88) return 'tempo';
  if (avgIntensity < 95) return 'sweet_spot';
  if (avgIntensity < 105) return 'threshold';
  if (avgIntensity < 110) return 'vo2max';
  if (avgIntensity < 120) return 'anaerobic';
  return 'neuromuscular';
}

/**
 * Generate short description for import display
 */
export function generateShortDescription(
  plan: WorkoutPlan,
  segments: WorkoutSegment[]
): string {
  const workSegments = segments.filter((s) => s.isWork);
  const totalWorkMinutes = Math.round(
    workSegments.reduce((sum, s) => sum + s.durationSec, 0) / 60
  );
  const intervalCount = workSegments.length;
  
  if (intervalCount === 0) {
    return 'Recovery ride';
  }
  
  const avgDuration = Math.round(
    totalWorkMinutes / intervalCount
  );
  
  return `${intervalCount}x${avgDuration}min intervals @ ${calculateAvgIntensity(workSegments, plan.ftpWatts)}% FTP`;
}

/**
 * Calculate average intensity
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
