import type { TelemetrySample } from '../types';

/**
 * Calculate average power from samples
 */
export function calculateAveragePower(samples: TelemetrySample[]): number {
  if (samples.length === 0) return 0;
  
  const totalPower = samples.reduce((sum, s) => sum + s.powerWatts, 0);
  return totalPower / samples.length;
}

/**
 * Calculate Normalized Power (NP)
 * Simplified implementation using 30-second rolling average
 * 
 * True NP calculation:
 * 1. Calculate 30-second rolling average
 * 2. Raise each value to 4th power
 * 3. Take average of those values
 * 4. Take 4th root
 * 
 * For simplicity, we use average power as approximation
 * This can be enhanced later with proper rolling window
 */
export function calculateNormalizedPower(samples: TelemetrySample[]): number {
  if (samples.length === 0) return 0;
  
  // For now, use average power with a slight adjustment
  // True NP is typically 5-10% higher than avg power for variable efforts
  const avgPower = calculateAveragePower(samples);
  const variability = calculateVariabilityIndex(samples);
  
  // Approximate NP using variability index
  // VI of 1.0 = steady effort, higher VI = more variable
  return avgPower * Math.min(variability, 1.15);
}

/**
 * Calculate Training Stress Score (TSS)
 * 
 * Formula: TSS = (duration_hours × NP × IF) / (FTP × 3600) × 100
 * Where IF (Intensity Factor) = NP / FTP
 * 
 * Simplified: TSS ≈ (duration_hours × normalized_power²) / (FTP × 36)
 */
export function calculateTSS(samples: TelemetrySample[], ftp: number): number {
  if (samples.length === 0 || ftp === 0) return 0;
  
  const durationHours = samples.length / 3600; // Assuming 1 sample per second
  const normalizedPower = calculateNormalizedPower(samples);
  const intensityFactor = normalizedPower / ftp;
  
  // TSS formula
  const tss = durationHours * normalizedPower * intensityFactor * 100 / ftp;
  
  return Math.max(0, tss);
}

/**
 * Calculate Variability Index (VI)
 * VI = NP / Avg Power
 * Higher VI = more variable effort (harder on body)
 */
export function calculateVariabilityIndex(samples: TelemetrySample[]): number {
  if (samples.length === 0) return 1;
  
  const avgPower = calculateAveragePower(samples);
  if (avgPower === 0) return 1;
  
  // Calculate standard deviation to determine variability
  const squaredDiffs = samples.map(s => Math.pow(s.powerWatts - avgPower, 2));
  const avgSquaredDiff = squaredDiffs.reduce((a, b) => a + b, 0) / samples.length;
  const standardDeviation = Math.sqrt(avgSquaredDiff);
  
  // VI approximation based on coefficient of variation
  const coefficientOfVariation = standardDeviation / avgPower;
  
  // Map CV to VI (steady = 1.0, variable = 1.05-1.2)
  return 1 + (coefficientOfVariation * 0.3);
}

/**
 * Calculate Intensity Factor (IF)
 * IF = NP / FTP
 */
export function calculateIntensityFactor(samples: TelemetrySample[], ftp: number): number {
  if (ftp === 0) return 0;
  
  const normalizedPower = calculateNormalizedPower(samples);
  return normalizedPower / ftp;
}

/**
 * Calculate average heart rate (excluding zeros)
 */
export function calculateAverageHR(samples: TelemetrySample[]): number {
  const hrSamples = samples.filter(s => s.hrBpm > 0);
  if (hrSamples.length === 0) return 0;
  
  const totalHR = hrSamples.reduce((sum, s) => sum + s.hrBpm, 0);
  return totalHR / hrSamples.length;
}

/**
 * Calculate average cadence (excluding zeros)
 */
export function calculateAverageCadence(samples: TelemetrySample[]): number {
  const cadenceSamples = samples.filter(s => s.cadenceRpm > 0);
  if (cadenceSamples.length === 0) return 0;
  
  const totalCadence = cadenceSamples.reduce((sum, s) => sum + s.cadenceRpm, 0);
  return totalCadence / cadenceSamples.length;
}

/**
 * Calculate max power
 */
export function calculateMaxPower(samples: TelemetrySample[]): number {
  if (samples.length === 0) return 0;
  return Math.max(...samples.map(s => s.powerWatts));
}

/**
 * Calculate max heart rate
 */
export function calculateMaxHR(samples: TelemetrySample[]): number {
  const hrSamples = samples.filter(s => s.hrBpm > 0);
  if (hrSamples.length === 0) return 0;
  return Math.max(...hrSamples.map(s => s.hrBpm));
}

/**
 * Calculate work in kJ
 * Work = average power × duration in seconds / 1000
 */
export function calculateWork(samples: TelemetrySample[]): number {
  const avgPower = calculateAveragePower(samples);
  const durationSec = samples.length; // Assuming 1 sample per second
  return (avgPower * durationSec) / 1000;
}

/**
 * Calculate power zones distribution
 * Returns percentage of time spent in each zone
 */
export function calculatePowerZoneDistribution(
  samples: TelemetrySample[],
  ftp: number
): Record<string, number> {
  if (samples.length === 0 || ftp === 0) {
    return {
      z1: 0, z2: 0, z3: 0, z4: 0, z5: 0, z6: 0, z7: 0
    };
  }
  
  // Coggan power zones as % of FTP
  const zones = {
    z1: { name: 'Active Recovery', max: 0.55 },
    z2: { name: 'Endurance', max: 0.75 },
    z3: { name: 'Tempo', max: 0.90 },
    z4: { name: 'Threshold', max: 1.05 },
    z5: { name: 'VO2max', max: 1.20 },
    z6: { name: 'Anaerobic', max: 1.50 },
    z7: { name: 'Neuromuscular', max: 999 },
  };
  
  const distribution: Record<string, number> = {
    z1: 0, z2: 0, z3: 0, z4: 0, z5: 0, z6: 0, z7: 0
  };
  
  samples.forEach(sample => {
    const intensity = sample.powerWatts / ftp;
    
    if (intensity <= zones.z1.max) distribution.z1++;
    else if (intensity <= zones.z2.max) distribution.z2++;
    else if (intensity <= zones.z3.max) distribution.z3++;
    else if (intensity <= zones.z4.max) distribution.z4++;
    else if (intensity <= zones.z5.max) distribution.z5++;
    else if (intensity <= zones.z6.max) distribution.z6++;
    else distribution.z7++;
  });
  
  // Convert to percentages
  const total = samples.length;
  Object.keys(distribution).forEach(key => {
    distribution[key] = Math.round((distribution[key] / total) * 100);
  });
  
  return distribution;
}
