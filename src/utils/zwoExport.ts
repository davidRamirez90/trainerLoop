import type { WorkoutPlan, WorkoutSegment } from '../data/workout';

interface ZWOInterval {
  type: string;
  attributes: Record<string, string | number>;
}

function segmentToZWOInterval(segment: WorkoutSegment, ftpWatts: number): ZWOInterval | null {
  const duration = Math.round(segment.durationSec);
  const powerLow = segment.targetRange.low / ftpWatts;
  const powerHigh = segment.targetRange.high / ftpWatts;
  const cadence = segment.cadenceRange?.low;
  
  // Determine interval type based on segment properties
  if (segment.rampToRange) {
    // Ramp interval
    return {
      type: segment.phase === 'warmup' ? 'Warmup' : 
            segment.phase === 'cooldown' ? 'Cooldown' : 
            'Ramp',
      attributes: {
        Duration: duration,
        PowerLow: Math.round(powerLow * 1000) / 1000,
        PowerHigh: Math.round((segment.rampToRange.high / ftpWatts) * 1000) / 1000,
        ...(cadence && { Cadence: Math.round(cadence) }),
      },
    };
  }
  
  // Steady state or other interval types
  const type = segment.phase === 'warmup' ? 'Warmup' :
               segment.phase === 'cooldown' ? 'Cooldown' :
               segment.phase === 'recovery' ? 'SteadyState' :
               'SteadyState';
  
  // For warmup/cooldown, use range if power varies significantly
  if (type === 'Warmup' || type === 'Cooldown') {
    const variance = Math.abs(powerHigh - powerLow);
    if (variance > 0.05) {
      return {
        type,
        attributes: {
          Duration: duration,
          PowerLow: Math.round(powerLow * 1000) / 1000,
          PowerHigh: Math.round(powerHigh * 1000) / 1000,
          ...(cadence && { Cadence: Math.round(cadence) }),
        },
      };
    }
  }
  
  // Steady state - use average power
  const power = (powerLow + powerHigh) / 2;
  
  return {
    type,
    attributes: {
      Duration: duration,
      Power: Math.round(power * 1000) / 1000,
      ...(cadence && { Cadence: Math.round(cadence) }),
    },
  };
}

function escapeXml(unsafe: string): string {
  return unsafe.replace(/[<>&'"]/g, (c) => {
    switch (c) {
      case '<': return '&lt;';
      case '>': return '&gt;';
      case '&': return '&amp;';
      case '"': return '&quot;';
      case "'": return '&apos;';
      default: return c;
    }
  });
}

export function exportWorkoutToZWO(plan: WorkoutPlan): string {
  const { name, segments, ftpWatts } = plan;
  
  // Build ZWO intervals
  const intervals: ZWOInterval[] = [];
  
  for (const segment of segments) {
    const interval = segmentToZWOInterval(segment, ftpWatts);
    if (interval) {
      intervals.push(interval);
    }
  }
  
  // Calculate total duration in seconds
  const totalDuration = segments.reduce((sum, seg) => sum + seg.durationSec, 0);
  
  // Build XML
  const lines: string[] = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<workout_file>',
    '  <author>Trainer Loop</author>',
    `  <name>${escapeXml(name)}</name>`,
    '  <description></description>',
    '  <sportType>bike</sportType>',
    `  <duration>${Math.round(totalDuration)}</duration>`,
    '  <workout>',
  ];
  
  for (const interval of intervals) {
    const attrs = Object.entries(interval.attributes)
      .map(([key, value]) => `${key}="${value}"`)
      .join(' ');
    
    // Self-closing tag for intervals without text content
    lines.push(`    <${interval.type} ${attrs}/>`);
  }
  
  lines.push('  </workout>');
  lines.push('</workout_file>');
  
  return lines.join('\n');
}

export function downloadZWOFile(plan: WorkoutPlan, filename?: string): void {
  const zwoContent = exportWorkoutToZWO(plan);
  const blob = new Blob([zwoContent], { type: 'application/xml' });
  const url = URL.createObjectURL(blob);
  
  const safeName = (filename || plan.name)
    .replace(/[^a-zA-Z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '_');
  
  const downloadName = `${safeName || 'workout'}.zwo`;
  
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = downloadName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  
  URL.revokeObjectURL(url);
}

// Validate ZWO content (basic validation)
export function validateZWOContent(content: string): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  if (!content.includes('<workout_file>')) {
    errors.push('Missing workout_file root element');
  }
  
  if (!content.includes('</workout_file>')) {
    errors.push('Missing closing workout_file tag');
  }
  
  if (!content.includes('<workout>')) {
    errors.push('Missing workout element');
  }
  
  // Check for common issues
  const openTags = (content.match(/<\w+/g) || []).length;
  const closeTags = (content.match(/<\/\w+>/g) || []).length;
  const selfClosing = (content.match(/\/>/g) || []).length;
  
  if (openTags !== closeTags + selfClosing) {
    errors.push('Potentially unbalanced tags');
  }
  
  return {
    valid: errors.length === 0,
    errors,
  };
}
