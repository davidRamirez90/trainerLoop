import type { WorkoutPlan, WorkoutSegment } from '../data/workout';

export interface ParsedWorkout {
  plan: WorkoutPlan;
  errors: string[];
}

interface ParserOptions {
  ftpWatts: number;
  powerZones?: Record<string, { low: number; high: number }>;
}

interface ParsedLine {
  type: 'header' | 'step' | 'repeat' | 'empty' | 'comment';
  content: string;
  repeatCount?: number;
}

// Parse duration from string like "30s", "5m", "1m30s", "1h30m"
function parseDuration(durationStr: string): number | null {
  const trimmed = durationStr.trim().toLowerCase();
  
  // Try pattern: 1h30m, 1h, 30m, 30s, 1m30s
  const hourMatch = trimmed.match(/(\d+)h/);
  const minuteMatch = trimmed.match(/(\d+)m/);
  const secondMatch = trimmed.match(/(\d+)s/);
  
  let totalSeconds = 0;
  let hasMatch = false;
  
  if (hourMatch) {
    totalSeconds += parseInt(hourMatch[1], 10) * 3600;
    hasMatch = true;
  }
  
  if (minuteMatch) {
    totalSeconds += parseInt(minuteMatch[1], 10) * 60;
    hasMatch = true;
  }
  
  if (secondMatch) {
    totalSeconds += parseInt(secondMatch[1], 10);
    hasMatch = true;
  }
  
  // If no units specified, assume minutes for values < 10, seconds otherwise
  if (!hasMatch) {
    const numValue = parseFloat(trimmed);
    if (!isNaN(numValue) && numValue > 0) {
      // If it's a whole number less than 10, treat as minutes
      // Otherwise treat as seconds
      return numValue < 10 && Number.isInteger(numValue) 
        ? numValue * 60 
        : numValue;
    }
    return null;
  }
  
  return totalSeconds > 0 ? totalSeconds : null;
}

// Parse power specification: "200w", "85%", "Z3", "150-200w"
function parsePower(
  powerStr: string, 
  ftpWatts: number,
  powerZones?: Record<string, { low: number; high: number }>
): { low: number; high: number } | null {
  const trimmed = powerStr.trim();
  
  // Check for range: 150-200w or 80-90%
  const rangeMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*(w|%)?$/i);
  if (rangeMatch) {
    const low = parseFloat(rangeMatch[1]);
    const high = parseFloat(rangeMatch[2]);
    const unit = rangeMatch[3]?.toLowerCase();
    
    if (unit === 'w' || (!unit && low > 10)) {
      return { low, high };
    } else {
      // Percentage or assumed percentage
      return {
        low: ftpWatts * (low / 100),
        high: ftpWatts * (high / 100),
      };
    }
  }
  
  // Single value with unit: 200w
  const wattsMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*w$/i);
  if (wattsMatch) {
    const watts = parseFloat(wattsMatch[1]);
    // Single value = exact target (no tolerance range)
    return { low: watts, high: watts };
  }

  // Percentage: 85%
  const percentMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*%$/);
  if (percentMatch) {
    const percent = parseFloat(percentMatch[1]) / 100;
    const watts = ftpWatts * percent;
    // Single value = exact target (no tolerance range)
    return { low: watts, high: watts };
  }
  
  // Zone notation: Z3, Z2, etc.
  const zoneMatch = trimmed.match(/^Z(\d+)$/i);
  if (zoneMatch && powerZones) {
    const zoneKey = `Z${zoneMatch[1]}`;
    const zone = powerZones[zoneKey];
    if (zone) {
      return { low: zone.low * ftpWatts, high: zone.high * ftpWatts };
    }
  }
  
  // Plain number - try to interpret
  const numValue = parseFloat(trimmed);
  if (!isNaN(numValue) && numValue > 0) {
    if (numValue <= 5) {
      // Likely a zone number without Z prefix
      const zoneKey = `Z${Math.round(numValue)}`;
      if (powerZones?.[zoneKey]) {
        const zone = powerZones[zoneKey];
        return { low: zone.low * ftpWatts, high: zone.high * ftpWatts };
      }
    }
    // Assume watts if > 50, otherwise percentage
    if (numValue > 50) {
      // Single value = exact target (no tolerance range)
      return { low: numValue, high: numValue };
    } else {
      const watts = ftpWatts * (numValue / 100);
      // Single value = exact target (no tolerance range)
      return { low: watts, high: watts };
    }
  }
  
  return null;
}

// Parse cadence: "90rpm", "90 rpm"
function parseCadence(cadenceStr: string): { low: number; high: number } | null {
  const trimmed = cadenceStr.trim().toLowerCase();
  
  // Range: 85-95rpm
  const rangeMatch = trimmed.match(/^(\d+)\s*-\s*(\d+)\s*rpm?$/);
  if (rangeMatch) {
    return {
      low: parseInt(rangeMatch[1], 10),
      high: parseInt(rangeMatch[2], 10),
    };
  }
  
  // Single value: 90rpm
  const singleMatch = trimmed.match(/^(\d+)\s*rpm?$/);
  if (singleMatch) {
    const rpm = parseInt(singleMatch[1], 10);
    return { low: rpm, high: rpm };
  }
  
  return null;
}



// Tokenize a line into its components
function tokenizeLine(line: string): {
  label?: string;
  duration?: number;
  power?: string;
  cadence?: string;
  isRamp: boolean;
} {
  const trimmed = line.replace(/^-\s*/, '').trim();
  
  // Check for ramp keyword at start or end
  const isRamp = /^ramp\s+/i.test(trimmed) || /\sramp$/i.test(trimmed);
  let workingLine = trimmed.replace(/^ramp\s+/i, '').replace(/\sramp$/i, '').trim();
  
  const result: ReturnType<typeof tokenizeLine> = { isRamp };
  
  // Extract text label (everything before first number/pattern)
  const labelMatch = workingLine.match(/^([^0-9-]+?)(?=\s+\d|\s*$)/);
  if (labelMatch && labelMatch[1].trim()) {
    result.label = labelMatch[1].trim();
    workingLine = workingLine.slice(labelMatch[0].length).trim();
  }
  
  // Find duration (should be first number sequence with optional units, but not followed by %)
  const durationMatch = workingLine.match(/^(\d+h?\d*m?\d*s?|\d+(?:\.\d+)?(?:min|sec|m|s)?)\b(?!\s*%)/i);
  if (durationMatch) {
    const duration = parseDuration(durationMatch[1]);
    if (duration) {
      result.duration = duration;
      workingLine = workingLine.slice(durationMatch[0].length).trim();
    }
  }
  
  // Find cadence (ends with rpm)
  const cadenceMatch = workingLine.match(/(\d+(?:\s*-\s*\d+)?\s*rpm?)(?=\s|$)/i);
  if (cadenceMatch) {
    result.cadence = cadenceMatch[1];
    workingLine = workingLine.replace(cadenceMatch[0], '').trim();
  }
  
  // Remaining should be power
  if (workingLine) {
    result.power = workingLine;
  }
  
  return result;
}

// Pre-process lines to handle repeats
function preprocessLines(lines: string[]): ParsedLine[] {
  const result: ParsedLine[] = [];
  let pendingRepeat: number | null = null;
  
  for (const line of lines) {
    const trimmed = line.trim();
    
    // Skip empty lines and comments
    if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('#')) {
      result.push({ type: 'empty', content: trimmed });
      continue;
    }
    
    // Check for repeat declaration: "Main set 4x" or "4x"
    const repeatMatch = trimmed.match(/^(.*?)(\d+)x\s*$/i);
    if (repeatMatch && !trimmed.startsWith('-')) {
      pendingRepeat = parseInt(repeatMatch[2], 10);
      result.push({
        type: 'repeat',
        content: trimmed,
        repeatCount: pendingRepeat,
      });
      continue;
    }
    
    // Check for step (starts with -)
    if (trimmed.startsWith('-')) {
      result.push({ type: 'step', content: trimmed });
      continue;
    }
    
    // Otherwise it's a header
    result.push({ type: 'header', content: trimmed });
    pendingRepeat = null; // Reset repeat on new header
  }
  
  return result;
}

// Generate unique segment ID
let segmentCounter = 0;
function generateSegmentId(): string {
  return `seg-${++segmentCounter}-${Date.now().toString(36)}`;
}

// Determine phase from header text
function determinePhase(headerText: string): 'warmup' | 'work' | 'recovery' | 'cooldown' {
  const lower = headerText.toLowerCase();
  if (lower.includes('warm') || lower.includes('wu')) return 'warmup';
  if (lower.includes('cool') || lower.includes('cd')) return 'cooldown';
  if (lower.includes('recovery') || lower.includes('rest')) return 'recovery';
  return 'work';
}

// Parse the complete workout text
export function parseWorkoutText(
  text: string, 
  options: ParserOptions
): ParsedWorkout {
  segmentCounter = 0;
  const errors: string[] = [];
  const segments: WorkoutSegment[] = [];
  
  const lines = text.split('\n');
  const parsedLines = preprocessLines(lines);
  
  let currentPhase: 'warmup' | 'work' | 'recovery' | 'cooldown' = 'work';
  let repeatBuffer: WorkoutSegment[] = [];
  let repeatCount: number | null = null;
  
  for (let i = 0; i < parsedLines.length; i++) {
    const line = parsedLines[i];
    
    if (line.type === 'empty') continue;
    
    if (line.type === 'header') {
      // Flush any pending repeats
      if (repeatBuffer.length > 0 && repeatCount) {
        for (let r = 0; r < repeatCount; r++) {
          segments.push(...repeatBuffer.map(seg => ({
            ...seg,
            id: generateSegmentId(),
            label: r === 0 ? seg.label : `${seg.label} (R${r + 1})`,
          })));
        }
        repeatBuffer = [];
        repeatCount = null;
      }
      
      currentPhase = determinePhase(line.content);
      continue;
    }
    
    if (line.type === 'repeat') {
      // Flush any pending repeats before starting new one
      if (repeatBuffer.length > 0 && repeatCount) {
        for (let r = 0; r < repeatCount; r++) {
          segments.push(...repeatBuffer.map(seg => ({
            ...seg,
            id: generateSegmentId(),
            label: r === 0 ? seg.label : `${seg.label} (R${r + 1})`,
          })));
        }
        repeatBuffer = [];
      }
      repeatCount = line.repeatCount ?? 2;
      repeatBuffer = [];
      continue;
    }
    
    if (line.type === 'step') {
      const tokens = tokenizeLine(line.content);
      
      // Default power based on phase
      let defaultPowerPercent = 0.5;
      if (currentPhase === 'warmup') defaultPowerPercent = 0.55;
      if (currentPhase === 'work') defaultPowerPercent = 0.9;
      if (currentPhase === 'recovery') defaultPowerPercent = 0.5;
      if (currentPhase === 'cooldown') defaultPowerPercent = 0.5;
      
      // Determine if this is a recovery interval based on power (only for 'work' phase)
      let effectivePhase = currentPhase;
      if (currentPhase === 'work' && tokens.power) {
        const powerRange = parsePower(tokens.power, options.ftpWatts, options.powerZones);
        if (powerRange) {
          const avgPower = (powerRange.low + powerRange.high) / 2;
          if ((avgPower / options.ftpWatts) <= 0.55) {
            effectivePhase = 'recovery';
          }
        }
      }
      
      const segment: WorkoutSegment = {
        id: generateSegmentId(),
        label: tokens.label || (effectivePhase === 'recovery' ? 'Recovery' : effectivePhase === 'work' ? 'Interval' : effectivePhase.charAt(0).toUpperCase() + effectivePhase.slice(1)),
        durationSec: tokens.duration || 300,
        targetRange: { 
          low: options.ftpWatts * defaultPowerPercent,
          high: options.ftpWatts * (defaultPowerPercent + 0.05),
        },
        phase: effectivePhase,
        isWork: effectivePhase === 'work',
      };
      
      // Parse power if specified
      if (tokens.power) {
        const powerRange = parsePower(tokens.power, options.ftpWatts, options.powerZones);
        if (powerRange) {
          segment.targetRange = powerRange;
          // Update isWork based on power level (work if > 60% FTP)
          segment.isWork = (powerRange.low / options.ftpWatts) > 0.6;
        } else {
          errors.push(`Line ${i + 1}: Could not parse power "${tokens.power}"`);
        }
      }
      
      // Parse cadence if specified
      if (tokens.cadence) {
        const cadenceRange = parseCadence(tokens.cadence);
        if (cadenceRange) {
          segment.cadenceRange = cadenceRange;
        }
      }
      
      // Handle ramps
      if (tokens.isRamp && tokens.power) {
        const rampMatch = tokens.power.match(/(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)/);
        if (rampMatch) {
          const startPower = parsePower(rampMatch[1], options.ftpWatts, options.powerZones);
          const endPower = parsePower(rampMatch[2], options.ftpWatts, options.powerZones);
          if (startPower && endPower) {
            segment.targetRange = startPower;
            segment.rampToRange = endPower;
          }
        }
      }
      
      if (repeatCount) {
        // Collecting steps for a repeat block
        repeatBuffer.push(segment);
      } else {
        segments.push(segment);
      }
    }
  }
  
  // Flush any remaining repeat buffer
  if (repeatBuffer.length > 0 && repeatCount) {
    for (let r = 0; r < repeatCount; r++) {
      segments.push(...repeatBuffer.map(seg => ({
        ...seg,
        id: generateSegmentId(),
        label: r === 0 ? seg.label : `${seg.label} (R${r + 1})`,
      })));
    }
  }
  
  // Calculate total duration
  const totalSeconds = segments.reduce((sum, seg) => sum + seg.durationSec, 0);
  
  const plan: WorkoutPlan = {
    id: `custom-${Date.now().toString(36)}`,
    name: 'Custom Workout',
    subtitle: `${segments.length} segments • ${Math.round(totalSeconds / 60)} min`,
    ftpWatts: options.ftpWatts,
    segments,
  };
  
  return { plan, errors };
}

// Convenience function with default FTP
export function parseWorkoutTextWithDefaults(
  text: string, 
  ftpWatts: number = 250
): ParsedWorkout {
  return parseWorkoutText(text, { ftpWatts });
}
