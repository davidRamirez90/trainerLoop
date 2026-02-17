import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import uPlot from 'uplot';

import type { WorkoutSegment } from '../data/workout';
import type { TelemetryGap, TelemetrySample } from '../types';
import type { Theme } from '../hooks/useTheme';
import { formatDuration } from '../utils/time';
import { getTotalDurationSec } from '../utils/workout';

import 'uplot/dist/uPlot.min.css';

// Theme-aware color constants
const CHART_COLORS = {
  dark: {
    actualStroke: '#65c7ff',
    powerSmoothStroke: '#a8def7',
    hrStroke: '#D64541',
    zoneStops: [
      { max: 0.55, color: '#6C7A89' },
      { max: 0.75, color: '#3B8EA5' },
      { max: 0.88, color: '#5FAF5F' },
      { max: 0.94, color: '#C9A227' },
      { max: 1.05, color: '#E57A1F' },
      { max: 1.2, color: '#D64541' },
      { max: Number.POSITIVE_INFINITY, color: '#8C2A2A' },
    ],
    gapFill: 'rgba(214, 69, 65, 0.12)',
    gapStroke: 'rgba(214, 69, 65, 0.4)',
    gridColor: 'rgba(255, 255, 255, 0.1)',
    tooltipBg: 'rgba(15, 21, 33, 0.95)',
    tooltipBorder: 'rgba(38, 52, 71, 0.8)',
  },
  light: {
    actualStroke: '#0066cc',
    powerSmoothStroke: '#4a90d9',
    hrStroke: '#dc2626',
    zoneStops: [
      { max: 0.55, color: '#4a5568' },
      { max: 0.75, color: '#2563eb' },
      { max: 0.88, color: '#059669' },
      { max: 0.94, color: '#b45309' },
      { max: 1.05, color: '#ea580c' },
      { max: 1.2, color: '#dc2626' },
      { max: Number.POSITIVE_INFINITY, color: '#991b1b' },
    ],
    gapFill: 'rgba(220, 38, 38, 0.12)',
    gapStroke: 'rgba(220, 38, 38, 0.4)',
    gridColor: 'rgba(0, 0, 0, 0.1)',
    tooltipBg: 'rgba(255, 255, 255, 0.98)',
    tooltipBorder: 'rgba(200, 210, 220, 0.8)',
  },
};

const ZONE_LABELS = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5', 'Z6', 'Z7'];
const POLYGON_ALPHA = 0.18;
const RANGE_FILL_ALPHA = 0.45;
const RANGE_STROKE_ALPHA = 0.75;
const LINE_STROKE_ALPHA = 0.85;
const POWER_SMOOTH_WINDOW_SEC = 3;
const DEFAULT_HR_RANGE = { min: 80, max: 180 };
const HR_RANGE_MIN = 40;
const HR_RANGE_MAX = 220;
const HR_EARLY_ANCHOR_SEC = 120;
const HR_EARLY_SAMPLE_COUNT = 30;
const HR_MIN_SPAN_EASY = 35;
const HR_MIN_SPAN_WORK = 45;

const clampValue = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

const hexToRgba = (value: string, alpha: number) => {
  const hex = value.replace('#', '');
  const normalized = hex.length === 3
    ? hex
        .split('')
        .map((char) => char + char)
        .join('')
    : hex;
  const red = Number.parseInt(normalized.slice(0, 2), 16);
  const green = Number.parseInt(normalized.slice(2, 4), 16);
  const blue = Number.parseInt(normalized.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
};

const getZoneColor = (ratio: number, zoneStops: typeof CHART_COLORS.dark.zoneStops) =>
  (zoneStops.find((stop) => ratio <= stop.max) ?? zoneStops[zoneStops.length - 1])
    .color;

const getZoneLabel = (ratio: number, zoneStops: typeof CHART_COLORS.dark.zoneStops) => {
  const index = zoneStops.findIndex((stop) => ratio <= stop.max);
  const safeIndex = index >= 0 ? index : ZONE_LABELS.length - 1;
  return ZONE_LABELS[safeIndex] ?? `Z${safeIndex + 1}`;
};

const getSegmentTargetWatts = (segment: WorkoutSegment) => {
  const start = segment.targetRange;
  const end = segment.rampToRange ?? segment.targetRange;
  const startMid = (start.low + start.high) / 2;
  const endMid = (end.low + end.high) / 2;
  return (startMid + endMid) / 2;
};

const formatRange = (low: number, high: number, suffix: string) => {
  const roundedLow = Math.round(low);
  const roundedHigh = Math.round(high);
  if (roundedLow === roundedHigh) {
    return `${roundedLow}${suffix}`;
  }
  return `${roundedLow}-${roundedHigh}${suffix}`;
};

const getPowerTargetLabel = (segment: WorkoutSegment) => {
  const start = segment.targetRange;
  const end = segment.rampToRange ?? segment.targetRange;
  const startLabel = formatRange(start.low, start.high, 'W');
  const endLabel = formatRange(end.low, end.high, 'W');
  const hasRamp = !!segment.rampToRange &&
    (segment.rampToRange.low !== segment.targetRange.low ||
      segment.rampToRange.high !== segment.targetRange.high);
  if (hasRamp) {
    return `Ramp ${startLabel} -> ${endLabel}`;
  }
  return `Target ${startLabel}`;
};

const getCadenceLabel = (segment: WorkoutSegment) => {
  if (!segment.cadenceRange) {
    return 'No target';
  }
  return formatRange(segment.cadenceRange.low, segment.cadenceRange.high, ' rpm');
};

const ensureHrSpan = (min: number, max: number, minSpan: number) => {
  if (max - min >= minSpan) {
    return { min, max };
  }
  const mid = (min + max) / 2;
  return {
    min: mid - minSpan / 2,
    max: mid + minSpan / 2,
  };
};

const getInitialHrRange = (
  thresholdHr: number | null,
  currentHr: number | null,
  startsEasy: boolean
) => {
  const minSpan = startsEasy ? HR_MIN_SPAN_EASY : HR_MIN_SPAN_WORK;
  const thresholdLow =
    thresholdHr !== null
      ? thresholdHr - (startsEasy ? 60 : 50)
      : null;
  const thresholdHigh =
    thresholdHr !== null
      ? thresholdHr + (startsEasy ? 15 : 30)
      : null;
  const currentLow =
    currentHr !== null
      ? currentHr - (startsEasy ? 15 : 20)
      : null;
  const currentHigh =
    currentHr !== null
      ? currentHr + (startsEasy ? 25 : 35)
      : null;

  const minCandidates = [
    thresholdLow,
    currentLow,
    DEFAULT_HR_RANGE.min,
  ].filter((value): value is number => value !== null);
  const maxCandidates = [
    thresholdHigh,
    currentHigh,
    DEFAULT_HR_RANGE.max,
  ].filter((value): value is number => value !== null);

  let min = Math.min(...minCandidates);
  let max = Math.max(...maxCandidates);
  min = clampValue(min, HR_RANGE_MIN, HR_RANGE_MAX);
  max = clampValue(max, HR_RANGE_MIN, HR_RANGE_MAX);
  ({ min, max } = ensureHrSpan(min, max, minSpan));
  min = clampValue(min, HR_RANGE_MIN, HR_RANGE_MAX);
  max = clampValue(max, HR_RANGE_MIN, HR_RANGE_MAX);
  if (min === max) {
    if (min === HR_RANGE_MAX) {
      min = HR_RANGE_MAX - 1;
    } else {
      max = clampValue(max + 1, HR_RANGE_MIN, HR_RANGE_MAX);
    }
  }

  return { min, max };
};

const useChartSize = (ref: React.RefObject<HTMLDivElement | null>) => {
  const [size, setSize] = useState({ width: 0, height: 0 });

  useLayoutEffect(() => {
    if (!ref.current) {
      return;
    }

    const updateSize = () => {
      if (!ref.current) {
        return;
      }
      setSize({
        width: ref.current.clientWidth,
        height: ref.current.clientHeight,
      });
    };

    updateSize();

    const observer = new ResizeObserver(() => updateSize());
    observer.observe(ref.current);

    return () => observer.disconnect();
  }, [ref]);

  return size;
};

const getSegmentBadges = (
  segment: WorkoutSegment,
  index: number,
  intensityOverrides?: Array<{ fromIndex: number; offsetPct: number }>,
  recoveryExtensions?: Record<string, number>
): { hasIntensityBadge: boolean; hasRecoveryBadge: boolean; offsetPct: number; extensionSec: number } => {
  let offsetPct = 0;
  let hasIntensityBadge = false;
  if (intensityOverrides) {
    for (const override of intensityOverrides) {
      if (override.fromIndex <= index) {
        offsetPct = override.offsetPct;
        hasIntensityBadge = offsetPct !== 0 && segment.isWork;
      }
    }
  }

  const extensionSec = recoveryExtensions?.[segment.id] ?? 0;
  const hasRecoveryBadge = extensionSec > 0 && segment.phase === 'recovery';

  return { hasIntensityBadge, hasRecoveryBadge, offsetPct, extensionSec };
};

type WorkoutChartProps = {
  segments: WorkoutSegment[];
  samples: TelemetrySample[];
  gaps: TelemetryGap[];
  elapsedSec: number;
  ftpWatts: number;
  hrSensorConnected: boolean;
  showPower3s: boolean;
  intensityOverrides?: Array<{ fromIndex: number; offsetPct: number }>;
  recoveryExtensions?: Record<string, number>;
  thresholdHr: number | null;
  currentHr: number | null;
  theme?: Theme;
};

type HoverState = {
  index: number;
  x: number;
  y: number;
};

export const WorkoutChart = ({
  segments,
  samples,
  gaps,
  elapsedSec,
  ftpWatts,
  hrSensorConnected,
  showPower3s,
  intensityOverrides,
  recoveryExtensions,
  thresholdHr,
  currentHr,
  theme = 'dark',
}: WorkoutChartProps) => {
  const colors = CHART_COLORS[theme];
  const containerRef = useRef<HTMLDivElement>(null);
  const plotRef = useRef<uPlot | null>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const segmentsRef = useRef(segments);
  const elapsedRef = useRef(elapsedSec);
  const gapsRef = useRef(gaps);
  const intensityOverridesRef = useRef(intensityOverrides);
  const recoveryExtensionsRef = useRef(recoveryExtensions);
  const size = useChartSize(containerRef);
  const [hoverState, setHoverState] = useState<HoverState | null>(null);
  const ftpScale = Math.max(ftpWatts, 1);
  const segmentTimeline = useMemo(() => {
    const timeline: Array<{ segment: typeof segments[0]; index: number; startSec: number; endSec: number }> = [];
    let cursor = 0;
    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i];
      const startSec = cursor;
      const endSec = cursor + segment.durationSec;
      cursor = endSec;
      timeline.push({ segment, index: i, startSec, endSec });
    }
    return timeline;
  }, [segments]);
  const hrValues = useMemo(() => {
    if (!hrSensorConnected) {
      return samples.map(() => null);
    }
    return samples.map((sample) => (
      !sample.dropout && sample.hrBpm > 0 ? sample.hrBpm : null
    ));
  }, [hrSensorConnected, samples]);
  const hasHrData = useMemo(
    () => hrValues.some((value) => value !== null),
    [hrValues]
  );
  const showHeartRate = hrSensorConnected && hasHrData;
  const showHeartRateAxis = showHeartRate;
  const startsEasy =
    segments[0]?.phase === 'warmup' || segments[0]?.phase === 'recovery';
  const initialHrRange = useMemo(
    () => getInitialHrRange(thresholdHr, currentHr, startsEasy),
    [currentHr, startsEasy, thresholdHr]
  );

  const totalDurationSec = useMemo(() => getTotalDurationSec(segments), [segments]);
  const { yMin, yMax } = useMemo(() => {
    if (segments.length === 0) {
      return { yMin: 50, yMax: 300 };
    }
    const lows: number[] = [];
    const highs: number[] = [];
    segments.forEach((segment) => {
      const start = segment.targetRange;
      const end = segment.rampToRange ?? segment.targetRange;
      lows.push(start.low, end.low);
      highs.push(start.high, end.high);
    });
    const minTarget = Math.min(...lows);
    const maxTarget = Math.max(...highs);
    const span = Math.max(1, maxTarget - minTarget);
    const paddedMin = Math.max(0, minTarget - span * 0.25);
    const paddedMax = maxTarget + span * 0.2;
    return {
      yMin: Math.floor(paddedMin),
      yMax: Math.ceil(paddedMax),
    };
  }, [segments]);

  const powerValues = useMemo(
    () => samples.map((sample) => (sample.dropout ? null : sample.powerWatts)),
    [samples]
  );
  const smoothPowerValues = useMemo(() => {
    if (!samples.length) {
      return [];
    }
    const window: TelemetrySample[] = [];
    let sum = 0;

    return samples.map((sample) => {
      if (sample.dropout) {
        window.length = 0;
        sum = 0;
        return null;
      }

      const cutoff = sample.timeSec - POWER_SMOOTH_WINDOW_SEC;
      while (window.length && window[0].timeSec <= cutoff) {
        const removed = window.shift();
        if (removed) {
          sum -= removed.powerWatts;
        }
      }

      window.push(sample);
      sum += sample.powerWatts;

      if (!window.length) {
        return null;
      }

      return sum / window.length;
    });
  }, [samples]);

  const { hrMin, hrMax } = useMemo(() => {
    if (!hasHrData) {
      return {
        hrMin: Math.floor(initialHrRange.min),
        hrMax: Math.ceil(initialHrRange.max),
      };
    }
    const values = hrValues.filter((value): value is number => value !== null);
    if (!values.length) {
      return {
        hrMin: Math.floor(initialHrRange.min),
        hrMax: Math.ceil(initialHrRange.max),
      };
    }
    const minHr = Math.min(...values);
    const maxHr = Math.max(...values);
    const shouldAnchor =
      values.length < HR_EARLY_SAMPLE_COUNT || elapsedSec < HR_EARLY_ANCHOR_SEC;
    const anchoredMin = shouldAnchor
      ? Math.min(minHr, initialHrRange.min)
      : minHr;
    const anchoredMax = shouldAnchor
      ? Math.max(maxHr, initialHrRange.max)
      : maxHr;
    const span = Math.max(1, anchoredMax - anchoredMin);
    const paddedMin = clampValue(anchoredMin - span * 0.1, HR_RANGE_MIN, HR_RANGE_MAX);
    const paddedMax = clampValue(anchoredMax + span * 0.1, HR_RANGE_MIN, HR_RANGE_MAX);
    return {
      hrMin: Math.floor(paddedMin),
      hrMax: Math.ceil(paddedMax),
    };
  }, [elapsedSec, hasHrData, hrValues, initialHrRange]);

  const data = useMemo(() => {
    const times = samples.map((sample) => sample.timeSec);
    return [times, powerValues, smoothPowerValues, hrValues] as uPlot.AlignedData;
  }, [hrValues, powerValues, samples, smoothPowerValues]);

  const handleMouseLeave = () => setHoverState(null);

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    const plot = plotRef.current;
    const container = containerRef.current;
    if (!plot || !container) {
      return;
    }
    const rect = container.getBoundingClientRect();
    const overRect = plot.over.getBoundingClientRect();
    const xInOver = event.clientX - overRect.left;
    const yInOver = event.clientY - overRect.top;
    if (
      xInOver < 0 ||
      xInOver > overRect.width ||
      yInOver < 0 ||
      yInOver > overRect.height
    ) {
      setHoverState(null);
      return;
    }
    const timeSec = plot.posToVal(xInOver, 'x');
    if (!Number.isFinite(timeSec) || timeSec < 0 || timeSec > totalDurationSec) {
      setHoverState(null);
      return;
    }
    let hoveredIndex: number | null = null;
    for (let index = 0; index < segmentTimeline.length; index += 1) {
      const segment = segmentTimeline[index];
      if (timeSec >= segment.startSec && timeSec < segment.endSec) {
        hoveredIndex = index;
        break;
      }
    }
    if (hoveredIndex === null && segmentTimeline.length) {
      const last = segmentTimeline[segmentTimeline.length - 1];
      if (timeSec >= last.endSec) {
        hoveredIndex = segmentTimeline.length - 1;
      }
    }
    if (hoveredIndex === null) {
      setHoverState(null);
      return;
    }
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    let posX = x + 12;
    let posY = y + 12;
    const tooltip = tooltipRef.current;
    if (tooltip) {
      const maxX = rect.width - tooltip.offsetWidth - 8;
      const maxY = rect.height - tooltip.offsetHeight - 8;
      posX = clampValue(posX, 8, Math.max(8, maxX));
      posY = clampValue(posY, 8, Math.max(8, maxY));
    }
    setHoverState((prev) => {
      if (
        prev &&
        prev.index === hoveredIndex &&
        Math.abs(prev.x - posX) < 0.5 &&
        Math.abs(prev.y - posY) < 0.5
      ) {
        return prev;
      }
      return { index: hoveredIndex, x: posX, y: posY };
    });
  };

  useEffect(() => {
    segmentsRef.current = segments;
  }, [segments]);

  useEffect(() => {
    elapsedRef.current = elapsedSec;
    if (plotRef.current) {
      plotRef.current.redraw();
    }
  }, [elapsedSec]);

  useEffect(() => {
    gapsRef.current = gaps;
    if (plotRef.current) {
      plotRef.current.redraw();
    }
  }, [gaps]);

  useEffect(() => {
    intensityOverridesRef.current = intensityOverrides;
    if (plotRef.current) {
      plotRef.current.redraw();
    }
  }, [intensityOverrides]);

  useEffect(() => {
    recoveryExtensionsRef.current = recoveryExtensions;
    if (plotRef.current) {
      plotRef.current.redraw();
    }
  }, [recoveryExtensions]);

  useLayoutEffect(() => {
    if (!containerRef.current || size.width === 0 || size.height === 0) {
      return;
    }

    if (plotRef.current) {
      plotRef.current.destroy();
      plotRef.current = null;
    }

    const drawTargetBands = (u: uPlot) => {
      const ctx = u.ctx;
      const currentSegments = segmentsRef.current;
      let cursor = 0;
      const yBottom = u.valToPos(yMin, 'y', true);

      ctx.save();
      currentSegments.forEach((segment, index) => {
        const start = cursor;
        const end = cursor + segment.durationSec;
        const x0 = u.valToPos(start, 'x', true);
        const x1 = u.valToPos(end, 'x', true);
        const { low: lowStart, high: highStart } = segment.targetRange;
        const { low: lowEnd, high: highEnd } = segment.rampToRange ?? segment.targetRange;
        const yLowStart = u.valToPos(lowStart, 'y', true);
        const yHighStart = u.valToPos(highStart, 'y', true);
        const yLowEnd = u.valToPos(lowEnd, 'y', true);
        const yHighEnd = u.valToPos(highEnd, 'y', true);
        const targetWatts = getSegmentTargetWatts(segment);
        const zoneColor = getZoneColor(targetWatts / ftpScale, colors.zoneStops);
        const zoneFill = hexToRgba(zoneColor, POLYGON_ALPHA);
        const rangeFill = hexToRgba(zoneColor, RANGE_FILL_ALPHA);
        const rangeStroke = hexToRgba(zoneColor, RANGE_STROKE_ALPHA);
        const lineStroke = hexToRgba(zoneColor, LINE_STROKE_ALPHA);
        const isRange =
          segment.targetRange.low !== segment.targetRange.high ||
          (segment.rampToRange &&
            segment.rampToRange.low !== segment.rampToRange.high);

        ctx.fillStyle = zoneFill;
        ctx.beginPath();
        ctx.moveTo(x0, yBottom);
        ctx.lineTo(x1, yBottom);
        ctx.lineTo(x1, yHighEnd);
        ctx.lineTo(x0, yHighStart);
        ctx.closePath();
        ctx.fill();

        if (isRange) {
          ctx.fillStyle = rangeFill;
          ctx.strokeStyle = rangeStroke;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(x0, yLowStart);
          ctx.lineTo(x1, yLowEnd);
          ctx.lineTo(x1, yHighEnd);
          ctx.lineTo(x0, yHighStart);
          ctx.closePath();
          ctx.fill();
          ctx.stroke();
        } else {
          ctx.strokeStyle = lineStroke;
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(x0, yHighStart);
          ctx.lineTo(x1, yHighEnd);
          ctx.stroke();
        }

        const badges = getSegmentBadges(
          segment,
          index,
          intensityOverridesRef.current,
          recoveryExtensionsRef.current
        );

        const segmentWidth = x1 - x0;
        if (segmentWidth > 40) {
          let badgeX = x1 - 8;
          const badgeY = u.bbox.top + 12;

          if (badges.hasRecoveryBadge) {
            const extensionText = `+${badges.extensionSec}s`;
            ctx.font = 'bold 11px system-ui, -apple-system, sans-serif';
            const textWidth = ctx.measureText(extensionText).width;
            const badgeWidth = textWidth + 8;
            const badgeHeight = 16;
            const badgeLeft = badgeX - badgeWidth;

            ctx.fillStyle = 'rgba(46, 204, 113, 0.9)';
            ctx.beginPath();
            ctx.roundRect(badgeLeft, badgeY - badgeHeight / 2, badgeWidth, badgeHeight, 3);
            ctx.fill();

            ctx.fillStyle = '#ffffff';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(extensionText, badgeLeft + badgeWidth / 2, badgeY);

            badgeX -= badgeWidth + 4;
          }

          if (badges.hasIntensityBadge) {
            const sign = badges.offsetPct > 0 ? '+' : '';
            const intensityText = `${sign}${Math.round(badges.offsetPct)}%`;
            ctx.font = 'bold 11px system-ui, -apple-system, sans-serif';
            const textWidth = ctx.measureText(intensityText).width;
            const badgeWidth = textWidth + 20;
            const badgeHeight = 16;
            const badgeLeft = badgeX - badgeWidth;

            const isIncrease = badges.offsetPct > 0;
            ctx.fillStyle = isIncrease ? 'rgba(231, 76, 60, 0.9)' : 'rgba(52, 152, 219, 0.9)';
            ctx.beginPath();
            ctx.roundRect(badgeLeft, badgeY - badgeHeight / 2, badgeWidth, badgeHeight, 3);
            ctx.fill();

            ctx.fillStyle = '#ffffff';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('⚡', badgeLeft + 8, badgeY);
            ctx.fillText(intensityText, badgeLeft + badgeWidth / 2 + 4, badgeY);
          }
        }

        cursor = end;
      });
      ctx.restore();
    };

    const drawTimeMarker = (u: uPlot) => {
      const ctx = u.ctx;
      const time = elapsedRef.current;
      if (!time) {
        return;
      }
      const x = u.valToPos(time, 'x', true);
      ctx.save();
      ctx.strokeStyle = 'rgba(101, 199, 255, 0.7)';
      ctx.setLineDash([4, 6]);
      ctx.beginPath();
      ctx.moveTo(x, u.bbox.top);
      ctx.lineTo(x, u.bbox.top + u.bbox.height);
      ctx.stroke();
      ctx.restore();
    };

    const drawGapBands = (u: uPlot) => {
      const ctx = u.ctx;
      const currentGaps = gapsRef.current;
      if (!currentGaps.length) {
        return;
      }
      ctx.save();
      ctx.fillStyle = colors.gapFill;
      ctx.strokeStyle = colors.gapStroke;
      ctx.lineWidth = 1;

      currentGaps.forEach((gap) => {
        const start = Math.max(gap.startSec, 0);
        const end = Math.max(gap.endSec, start);
        const x0 = u.valToPos(start, 'x', true);
        const x1 = u.valToPos(end, 'x', true);
        const width = x1 - x0;
        if (width <= 0) {
          return;
        }
        ctx.fillRect(x0, u.bbox.top, width, u.bbox.height);
        ctx.beginPath();
        ctx.moveTo(x0, u.bbox.top);
        ctx.lineTo(x0, u.bbox.top + u.bbox.height);
        ctx.moveTo(x1, u.bbox.top);
        ctx.lineTo(x1, u.bbox.top + u.bbox.height);
        ctx.stroke();
      });

      ctx.restore();
    };

    const options: uPlot.Options = {
      width: size.width,
      height: size.height,
      padding: [18, 16, 26, 46],
      scales: {
        x: {
          time: false,
          range: [0, totalDurationSec],
        },
        y: {
          range: [yMin, yMax],
        },
        hr: {
          range: [hrMin, hrMax],
        },
      },
      axes: [
        {
          stroke: '#5b6d84',
          grid: { stroke: 'rgba(91, 109, 132, 0.25)' },
          ticks: { stroke: 'rgba(91, 109, 132, 0.4)' },
          values: (_, ticks) => ticks.map((tick) => formatDuration(tick)),
        },
        {
          stroke: '#5b6d84',
          grid: { stroke: 'rgba(91, 109, 132, 0.25)' },
          ticks: { stroke: 'rgba(91, 109, 132, 0.4)' },
        },
        {
          scale: 'hr',
          side: 1,
          show: showHeartRateAxis,
          stroke: colors.hrStroke,
          grid: { show: false },
          ticks: { stroke: 'rgba(214, 69, 65, 0.45)' },
          values: (_, ticks) => ticks.map((tick) => `${Math.round(tick)}`),
        },
      ],
      series: [
        {
          label: 'time',
        },
        {
          label: 'Power (W)',
          stroke: colors.actualStroke,
          width: 2,
          points: { show: false },
        },
        {
          label: 'Power 3s Avg',
          stroke: colors.powerSmoothStroke,
          width: 2,
          show: showPower3s,
          points: { show: false },
        },
        {
          label: 'HR',
          scale: 'hr',
          stroke: colors.hrStroke,
          width: 2,
          show: showHeartRate,
          points: { show: false },
        },
      ],
      legend: {
        show: false,
      },
      hooks: {
        draw: [drawTargetBands, drawGapBands, drawTimeMarker],
      },
    };

    plotRef.current = new uPlot(options, data, containerRef.current);

    return () => {
      plotRef.current?.destroy();
      plotRef.current = null;
    };
  }, [
    data,
    ftpScale,
    hrMax,
    hrMin,
    showHeartRate,
    showHeartRateAxis,
    showPower3s,
    size.height,
    size.width,
    totalDurationSec,
    yMax,
    yMin,
  ]);

  useEffect(() => {
    if (plotRef.current) {
      plotRef.current.setData(data);
    }
  }, [data]);

  const hoveredSegment = hoverState
    ? segmentTimeline[hoverState.index] ?? null
    : null;
  const tooltipTitle = hoveredSegment?.segment.label || 'Segment';
  const durationLabel = hoveredSegment
    ? formatDuration(hoveredSegment.segment.durationSec)
    : '--';
  const powerTargetLabel = hoveredSegment
    ? getPowerTargetLabel(hoveredSegment.segment)
    : '--';
  const cadenceLabel = hoveredSegment ? getCadenceLabel(hoveredSegment.segment) : '--';
  const zoneLabel = hoveredSegment && ftpWatts > 0
    ? getZoneLabel(getSegmentTargetWatts(hoveredSegment.segment) / ftpWatts, colors.zoneStops)
    : '--';

  return (
    <div
      ref={containerRef}
      className="workout-chart"
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      {hoverState && hoveredSegment ? (
        <div
          ref={tooltipRef}
          className="workout-tooltip"
          style={{ left: hoverState.x, top: hoverState.y }}
        >
          <div className="workout-tooltip-title">{tooltipTitle}</div>
          <div className="workout-tooltip-row">
            <span>Duration</span>
            <strong>{durationLabel}</strong>
          </div>
          <div className="workout-tooltip-row">
            <span>Power</span>
            <strong>{powerTargetLabel}</strong>
          </div>
          <div className="workout-tooltip-row">
            <span>Cadence</span>
            <strong>{cadenceLabel}</strong>
          </div>
          <div className="workout-tooltip-row">
            <span>Zone</span>
            <strong>{zoneLabel}</strong>
          </div>
        </div>
      ) : null}
    </div>
  );
};
