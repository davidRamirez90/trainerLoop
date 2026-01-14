import type {
  SegmentPhase,
  TargetRange,
  WorkoutPlan,
  WorkoutSegment,
} from '../data/workout';

const DEFAULT_FTP_WATTS = 250;
const WORK_THRESHOLD = 0.85;

const parseNumber = (value: unknown, label: string) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    throw new Error(`${label} must be a number.`);
  }
  return value;
};

const parseTargetRange = (value: unknown, label: string): TargetRange => {
  if (!value || typeof value !== 'object') {
    throw new Error(`${label} must be an object.`);
  }
  const { low, high } = value as { low?: unknown; high?: unknown };
  return {
    low: parseNumber(low, `${label}.low`),
    high: parseNumber(high, `${label}.high`),
  };
};

const parsePhase = (value: unknown, label: string): SegmentPhase => {
  if (
    value === 'warmup' ||
    value === 'work' ||
    value === 'recovery' ||
    value === 'cooldown'
  ) {
    return value;
  }
  throw new Error(`${label} must be warmup, work, recovery, or cooldown.`);
};

const slugFromFileName = (fileName: string) =>
  fileName.replace(/\.[^/.]+$/, '').trim();

export const normalizeWorkoutPlan = (
  raw: unknown,
  fallbackName: string,
  fallbackSubtitle: string
): WorkoutPlan => {
  if (!raw || typeof raw !== 'object') {
    throw new Error('Workout JSON must be an object.');
  }
  const data = raw as Record<string, unknown>;
  const segmentsValue = data.segments;
  if (!Array.isArray(segmentsValue) || segmentsValue.length === 0) {
    throw new Error('Workout must include a non-empty segments array.');
  }

  const ftpWatts = parseNumber(data.ftpWatts, 'ftpWatts');
  const name =
    typeof data.name === 'string' && data.name.trim()
      ? data.name.trim()
      : fallbackName;
  const subtitle =
    typeof data.subtitle === 'string' && data.subtitle.trim()
      ? data.subtitle.trim()
      : fallbackSubtitle;
  const id =
    typeof data.id === 'string' && data.id.trim()
      ? data.id.trim()
      : `import-${Date.now()}`;

  const segments = segmentsValue.map((segment, index) => {
    if (!segment || typeof segment !== 'object') {
      throw new Error(`Segment ${index + 1} must be an object.`);
    }
    const segmentData = segment as Record<string, unknown>;
    const phase = parsePhase(segmentData.phase, `segments[${index}].phase`);
    const targetRange = parseTargetRange(
      segmentData.targetRange,
      `segments[${index}].targetRange`
    );
    const cadenceRange = segmentData.cadenceRange
      ? parseTargetRange(
          segmentData.cadenceRange,
          `segments[${index}].cadenceRange`
        )
      : undefined;
    const rampToRange = segmentData.rampToRange
      ? parseTargetRange(segmentData.rampToRange, `segments[${index}].rampToRange`)
      : undefined;
    return {
      id:
        typeof segmentData.id === 'string' && segmentData.id.trim()
          ? segmentData.id.trim()
          : `segment-${index + 1}`,
      label:
        typeof segmentData.label === 'string' && segmentData.label.trim()
          ? segmentData.label.trim()
          : `Segment ${index + 1}`,
      durationSec: parseNumber(
        segmentData.durationSec,
        `segments[${index}].durationSec`
      ),
      targetRange,
      cadenceRange,
      rampToRange,
      phase,
      isWork:
        typeof segmentData.isWork === 'boolean' ? segmentData.isWork : phase === 'work',
    };
  });

  return {
    id,
    name,
    subtitle,
    ftpWatts,
    segments,
  };
};

const toWattsFromPercent = (value: number, ftpWatts: number) => {
  const percent = value <= 1 ? value * 100 : value;
  return Math.round((percent / 100) * ftpWatts);
};

const toWatts = (value: number, ftpWatts: number) =>
  value > 3 ? Math.round(value) : Math.round(value * ftpWatts);

const classifyPhase = (
  watts: number,
  ftpWatts: number,
  index: number,
  lastIndex: number
): SegmentPhase => {
  if (index === 0) {
    return 'warmup';
  }
  if (index >= lastIndex) {
    return 'cooldown';
  }
  return watts >= ftpWatts * WORK_THRESHOLD ? 'work' : 'recovery';
};

const buildSegment = (
  index: number,
  label: string,
  durationSec: number,
  startWatts: number,
  endWatts: number,
  phase: SegmentPhase,
  isWork: boolean
): WorkoutSegment | null => {
  const roundedDuration = Math.round(durationSec);
  if (roundedDuration <= 0) {
    return null;
  }

  const targetRange = { low: startWatts, high: startWatts };
  const rampToRange =
    startWatts !== endWatts ? { low: endWatts, high: endWatts } : undefined;

  return {
    id: `segment-${index + 1}`,
    label,
    durationSec: roundedDuration,
    targetRange,
    rampToRange,
    phase,
    isWork,
  };
};

const parseErgMrc = (text: string, fileName: string): WorkoutPlan => {
  const lines = text.split(/\r?\n/);
  const header: Record<string, string> = {};
  let timeUnit: 'minutes' | 'seconds' = 'minutes';
  let powerUnit: 'watts' | 'percent' | null = null;

  const dataPoints: { timeSec: number; power: number }[] = [];

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) {
      continue;
    }
    if (line.startsWith('[') && line.endsWith(']')) {
      continue;
    }

    if (line.includes('=')) {
      const [key, ...rest] = line.split('=');
      header[key.trim().toUpperCase()] = rest.join('=').trim();
      continue;
    }

    const tokens = line.split(/\s+/);
    const token0 = tokens[0]?.toUpperCase();
    const token1 = tokens[1]?.toUpperCase();
    if (tokens.length === 2 && (token0 === 'MINUTES' || token0 === 'SECONDS')) {
      timeUnit = token0 === 'SECONDS' ? 'seconds' : 'minutes';
      powerUnit = token1 === 'PERCENT' ? 'percent' : 'watts';
      continue;
    }

    if (!/^[+-]?\d/.test(line)) {
      continue;
    }

    const timeValue = Number.parseFloat(tokens[0]);
    const powerValue = Number.parseFloat(tokens[1]);
    if (!Number.isFinite(timeValue) || !Number.isFinite(powerValue)) {
      continue;
    }
    const timeSec = timeUnit === 'minutes' ? timeValue * 60 : timeValue;
    dataPoints.push({ timeSec, power: powerValue });
  }

  if (dataPoints.length < 2) {
    throw new Error('ERG/MRC file must include at least two data points.');
  }

  const ftpWattsRaw =
    header.FTP ||
    header['FTP WATTS'] ||
    header['FTP_WATTS'] ||
    header['FTPWATTS'];
  const ftpWatts = ftpWattsRaw ? Number.parseFloat(ftpWattsRaw) : DEFAULT_FTP_WATTS;

  const resolvedPowerUnit =
    powerUnit ?? (dataPoints[0].power <= 2 ? 'percent' : 'watts');

  const points = dataPoints
    .slice()
    .sort((a, b) => a.timeSec - b.timeSec)
    .map((point) => ({
      ...point,
      powerWatts:
        resolvedPowerUnit === 'percent'
          ? toWattsFromPercent(point.power, ftpWatts)
          : Math.round(point.power),
    }));

  if (points[0].timeSec > 0) {
    points.unshift({ ...points[0], timeSec: 0 });
  }

  const segments: WorkoutSegment[] = [];
  let workCount = 0;
  let recoveryCount = 0;

  for (let index = 0; index < points.length - 1; index += 1) {
    const start = points[index];
    const end = points[index + 1];
    const duration = end.timeSec - start.timeSec;
    if (duration <= 0) {
      continue;
    }
    const phase = classifyPhase(
      start.powerWatts,
      ftpWatts,
      index,
      points.length - 2
    );
    const isWork = phase === 'work';
    let label = `Segment ${index + 1}`;
    if (phase === 'warmup') {
      label = 'Warmup';
    } else if (phase === 'cooldown') {
      label = 'Cooldown';
    } else if (isWork) {
      workCount += 1;
      label = `Interval ${workCount}`;
    } else {
      recoveryCount += 1;
      label = `Recovery ${recoveryCount}`;
    }

    const segment = buildSegment(
      index,
      label,
      duration,
      start.powerWatts,
      end.powerWatts,
      phase,
      isWork
    );
    if (segment) {
      segments.push(segment);
    }
  }

  if (!segments.length) {
    throw new Error('No segments could be parsed from this workout.');
  }

  const name = header['FILE NAME'] || header.DESCRIPTION || slugFromFileName(fileName);
  const subtitle = header.DESCRIPTION || 'Imported ERG/MRC workout';

  return {
    id: slugFromFileName(fileName) || `import-${Date.now()}`,
    name,
    subtitle,
    ftpWatts: Number.isFinite(ftpWatts) ? Math.round(ftpWatts) : DEFAULT_FTP_WATTS,
    segments,
  };
};

const getAttrNumber = (node: Element, attr: string, fallback?: number) => {
  const value = node.getAttribute(attr);
  if (value === null || value === '') {
    if (fallback === undefined) {
      throw new Error(`Missing ${attr} attribute in ${node.tagName}.`);
    }
    return fallback;
  }
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`Invalid ${attr} attribute in ${node.tagName}.`);
  }
  return parsed;
};

const getOptionalAttrNumber = (node: Element, attr: string) => {
  const value = node.getAttribute(attr);
  if (value === null || value === '') {
    return null;
  }
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`Invalid ${attr} attribute in ${node.tagName}.`);
  }
  return parsed;
};

const getCadenceRange = (node: Element): TargetRange | undefined => {
  const cadenceLow = getOptionalAttrNumber(node, 'CadenceLow');
  const cadenceHigh = getOptionalAttrNumber(node, 'CadenceHigh');
  if (cadenceLow !== null || cadenceHigh !== null) {
    const low = cadenceLow ?? cadenceHigh;
    const high = cadenceHigh ?? cadenceLow;
    if (low !== null && high !== null) {
      return {
        low: Math.min(low, high),
        high: Math.max(low, high),
      };
    }
  }
  const cadence = getOptionalAttrNumber(node, 'Cadence');
  if (cadence !== null) {
    return { low: cadence, high: cadence };
  }
  return undefined;
};

const parseZwo = (text: string, fileName: string): WorkoutPlan => {
  const parser = new DOMParser();
  const xml = parser.parseFromString(text, 'application/xml');
  if (xml.querySelector('parsererror')) {
    throw new Error('Invalid ZWO XML.');
  }

  const workoutNode = xml.querySelector('workout');
  if (!workoutNode) {
    throw new Error('ZWO file missing <workout> definition.');
  }

  const nameNode = xml.querySelector('name');
  const descriptionNode = xml.querySelector('description');
  const name = nameNode?.textContent?.trim() || slugFromFileName(fileName);
  const subtitle = descriptionNode?.textContent?.trim() || 'Imported ZWO workout';

  const ftpWatts = DEFAULT_FTP_WATTS;
  const segments: WorkoutSegment[] = [];
  let segmentIndex = 0;
  let intervalCount = 0;
  let recoveryCount = 0;

  const createSegment = (
    label: string,
    durationSec: number,
    low: number,
    high: number,
    phase: SegmentPhase,
    isWork: boolean,
    cadenceRange?: TargetRange
  ) => {
    const segment = buildSegment(
      segmentIndex,
      label,
      durationSec,
      low,
      high,
      phase,
      isWork
    );
    if (segment) {
      if (cadenceRange) {
        segment.cadenceRange = cadenceRange;
      }
      segmentIndex += 1;
      segments.push(segment);
    }
  };

  const createRangeSegment = (
    label: string,
    durationSec: number,
    low: number,
    high: number,
    phase: SegmentPhase,
    isWork: boolean,
    cadenceRange?: TargetRange
  ) => {
    const roundedDuration = Math.round(durationSec);
    if (roundedDuration <= 0) {
      return;
    }
    segments.push({
      id: `segment-${segmentIndex + 1}`,
      label,
      durationSec: roundedDuration,
      targetRange: { low, high },
      cadenceRange,
      phase,
      isWork,
    });
    segmentIndex += 1;
  };

  const toWattsWithFallback = (value: number) => toWatts(value, ftpWatts);

  Array.from(workoutNode.children).forEach((child) => {
    const tag = child.tagName.toLowerCase();

    if (tag === 'warmup') {
      const duration = getAttrNumber(child, 'Duration');
      const low = toWattsWithFallback(getAttrNumber(child, 'PowerLow'));
      const high = toWattsWithFallback(getAttrNumber(child, 'PowerHigh'));
      const cadenceRange = getCadenceRange(child);
      createSegment('Warmup', duration, low, high, 'warmup', false, cadenceRange);
      return;
    }

    if (tag === 'cooldown') {
      const duration = getAttrNumber(child, 'Duration');
      const low = toWattsWithFallback(getAttrNumber(child, 'PowerLow'));
      const high = toWattsWithFallback(getAttrNumber(child, 'PowerHigh'));
      const cadenceRange = getCadenceRange(child);
      createSegment('Cooldown', duration, low, high, 'cooldown', false, cadenceRange);
      return;
    }

    if (tag === 'ramp') {
      const duration = getAttrNumber(child, 'Duration');
      const low = toWattsWithFallback(getAttrNumber(child, 'PowerLow'));
      const high = toWattsWithFallback(getAttrNumber(child, 'PowerHigh'));
      const isWork = high >= ftpWatts * WORK_THRESHOLD;
      const phase: SegmentPhase = isWork ? 'work' : 'recovery';
      const cadenceRange = getCadenceRange(child);
      createSegment('Ramp', duration, low, high, phase, isWork, cadenceRange);
      return;
    }

    if (tag === 'steadystate') {
      const duration = getAttrNumber(child, 'Duration');
      const power = getOptionalAttrNumber(child, 'Power');
      const powerLow = getOptionalAttrNumber(child, 'PowerLow');
      const powerHigh = getOptionalAttrNumber(child, 'PowerHigh');
      let resolvedLow: number;
      let resolvedHigh: number;
      if (power !== null) {
        resolvedLow = power;
        resolvedHigh = power;
      } else if (powerLow !== null && powerHigh !== null) {
        resolvedLow = powerLow;
        resolvedHigh = powerHigh;
      } else if (powerLow !== null) {
        resolvedLow = powerLow;
        resolvedHigh = powerLow;
      } else if (powerHigh !== null) {
        resolvedLow = powerHigh;
        resolvedHigh = powerHigh;
      } else {
        throw new Error('Missing Power, PowerLow, or PowerHigh attribute in SteadyState.');
      }
      const lowWatts = toWattsWithFallback(resolvedLow);
      const highWatts = toWattsWithFallback(resolvedHigh);
      const rangeLow = Math.min(lowWatts, highWatts);
      const rangeHigh = Math.max(lowWatts, highWatts);
      const targetWatts = Math.round((rangeLow + rangeHigh) / 2);
      const isWork = targetWatts >= ftpWatts * WORK_THRESHOLD;
      const phase: SegmentPhase = isWork ? 'work' : 'recovery';
      const label = isWork ? `Interval ${intervalCount + 1}` : `Steady ${recoveryCount + 1}`;
      if (isWork) {
        intervalCount += 1;
      } else {
        recoveryCount += 1;
      }
      const cadenceRange = getCadenceRange(child);
      if (rangeLow !== rangeHigh) {
        createRangeSegment(label, duration, rangeLow, rangeHigh, phase, isWork, cadenceRange);
      } else {
        createSegment(label, duration, rangeLow, rangeHigh, phase, isWork, cadenceRange);
      }
      return;
    }

    if (tag === 'freeride') {
      const duration = getAttrNumber(child, 'Duration');
      const powerAttr = child.getAttribute('Power');
      const powerLowAttr = child.getAttribute('PowerLow');
      const powerHighAttr = child.getAttribute('PowerHigh');
      const powerLow =
        powerLowAttr && Number.isFinite(Number.parseFloat(powerLowAttr))
          ? Number.parseFloat(powerLowAttr)
          : null;
      const power =
        powerAttr && Number.isFinite(Number.parseFloat(powerAttr))
          ? Number.parseFloat(powerAttr)
          : null;
      const powerHigh =
        powerHighAttr && Number.isFinite(Number.parseFloat(powerHighAttr))
          ? Number.parseFloat(powerHighAttr)
          : null;
      const low = powerLow !== null
        ? toWattsWithFallback(powerLow)
        : power !== null
          ? toWattsWithFallback(power)
          : toWattsWithFallback(0.55);
      const high = powerHigh !== null
        ? toWattsWithFallback(powerHigh)
        : low;
      const rangeLow = Math.min(low, high);
      const rangeHigh = Math.max(low, high);
      const cadenceRange = getCadenceRange(child);
      if (rangeLow !== rangeHigh) {
        createRangeSegment(
          'Free Ride',
          duration,
          rangeLow,
          rangeHigh,
          'recovery',
          false,
          cadenceRange
        );
      } else {
        createSegment(
          'Free Ride',
          duration,
          rangeLow,
          rangeHigh,
          'recovery',
          false,
          cadenceRange
        );
      }
      return;
    }

    if (tag === 'intervalst') {
      const repeat = Math.max(1, Math.round(getAttrNumber(child, 'Repeat', 1)));
      const onDuration = getAttrNumber(child, 'OnDuration');
      const offDuration = getAttrNumber(child, 'OffDuration');
      const onPower = toWattsWithFallback(getAttrNumber(child, 'OnPower'));
      const offPower = toWattsWithFallback(getAttrNumber(child, 'OffPower'));
      const cadenceRange = getCadenceRange(child);
      for (let rep = 0; rep < repeat; rep += 1) {
        intervalCount += 1;
        createSegment(
          `Interval ${intervalCount}`,
          onDuration,
          onPower,
          onPower,
          'work',
          true,
          cadenceRange
        );
        recoveryCount += 1;
        createSegment(
          `Recovery ${recoveryCount}`,
          offDuration,
          offPower,
          offPower,
          'recovery',
          false,
          cadenceRange
        );
      }
    }
  });

  if (!segments.length) {
    throw new Error('No workout steps found in this ZWO file.');
  }

  return {
    id: slugFromFileName(fileName) || `import-${Date.now()}`,
    name,
    subtitle,
    ftpWatts,
    segments,
  };
};

export const parseWorkoutFile = (fileName: string, text: string): WorkoutPlan => {
  const trimmed = text.trim();
  const fallbackName = slugFromFileName(fileName) || 'Imported Workout';
  const fallbackSubtitle = 'Imported workout';
  const extension = fileName.split('.').pop()?.toLowerCase();

  if (extension === 'json' || trimmed.startsWith('{') || trimmed.startsWith('[')) {
    const parsed = JSON.parse(trimmed);
    return normalizeWorkoutPlan(parsed, fallbackName, fallbackSubtitle);
  }

  if (extension === 'zwo' || trimmed.startsWith('<')) {
    return parseZwo(trimmed, fileName);
  }

  if (extension === 'erg' || extension === 'mrc') {
    return parseErgMrc(trimmed, fileName);
  }

  return parseErgMrc(trimmed, fileName);
};
