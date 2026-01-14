import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import uPlot from 'uplot';

import type { WorkoutSegment } from '../data/workout';
import type { TelemetryGap, TelemetrySample } from '../types';
import { formatDuration } from '../utils/time';
import { getTotalDurationSec } from '../utils/workout';

import 'uplot/dist/uPlot.min.css';

const ACTUAL_STROKE = '#65c7ff';
const POWER_SMOOTH_STROKE = '#a8def7';
const HR_STROKE = '#D64541';
const ZONE_STOPS = [
  { max: 0.55, color: '#6C7A89' },
  { max: 0.75, color: '#3B8EA5' },
  { max: 0.88, color: '#5FAF5F' },
  { max: 0.94, color: '#C9A227' },
  { max: 1.05, color: '#E57A1F' },
  { max: 1.2, color: '#D64541' },
  { max: Number.POSITIVE_INFINITY, color: '#8C2A2A' },
];
const POLYGON_ALPHA = 0.18;
const RANGE_FILL_ALPHA = 0.45;
const RANGE_STROKE_ALPHA = 0.75;
const LINE_STROKE_ALPHA = 0.85;
const POWER_SMOOTH_WINDOW_SEC = 3;
const GAP_FILL = 'rgba(214, 69, 65, 0.12)';
const GAP_STROKE = 'rgba(214, 69, 65, 0.4)';

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

const getZoneColor = (ratio: number) =>
  (ZONE_STOPS.find((stop) => ratio <= stop.max) ?? ZONE_STOPS[ZONE_STOPS.length - 1])
    .color;

const getSegmentTargetWatts = (segment: WorkoutSegment) => {
  const start = segment.targetRange;
  const end = segment.rampToRange ?? segment.targetRange;
  const startMid = (start.low + start.high) / 2;
  const endMid = (end.low + end.high) / 2;
  return (startMid + endMid) / 2;
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

type WorkoutChartProps = {
  segments: WorkoutSegment[];
  samples: TelemetrySample[];
  gaps: TelemetryGap[];
  elapsedSec: number;
  ftpWatts: number;
  hrSensorConnected: boolean;
  showPower3s: boolean;
};

export const WorkoutChart = ({
  segments,
  samples,
  gaps,
  elapsedSec,
  ftpWatts,
  hrSensorConnected,
  showPower3s,
}: WorkoutChartProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const plotRef = useRef<uPlot | null>(null);
  const segmentsRef = useRef(segments);
  const elapsedRef = useRef(elapsedSec);
  const gapsRef = useRef(gaps);
  const size = useChartSize(containerRef);
  const ftpScale = Math.max(ftpWatts, 1);
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
    const paddedMin = Math.max(50, minTarget - span * 0.25);
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
      return { hrMin: 80, hrMax: 180 };
    }
    const values = hrValues.filter((value): value is number => value !== null);
    if (!values.length) {
      return { hrMin: 80, hrMax: 180 };
    }
    const minHr = Math.min(...values);
    const maxHr = Math.max(...values);
    const span = Math.max(1, maxHr - minHr);
    const paddedMin = Math.max(40, minHr - span * 0.1);
    const paddedMax = Math.min(220, maxHr + span * 0.1);
    return {
      hrMin: Math.floor(paddedMin),
      hrMax: Math.ceil(paddedMax),
    };
  }, [hasHrData, hrValues]);

  const data = useMemo(() => {
    const times = samples.map((sample) => sample.timeSec);
    return [times, powerValues, smoothPowerValues, hrValues] as uPlot.AlignedData;
  }, [hrValues, powerValues, samples, smoothPowerValues]);

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
      currentSegments.forEach((segment) => {
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
        const zoneColor = getZoneColor(targetWatts / ftpScale);
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
      ctx.fillStyle = GAP_FILL;
      ctx.strokeStyle = GAP_STROKE;
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
          stroke: HR_STROKE,
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
          stroke: ACTUAL_STROKE,
          width: 2,
          points: { show: false },
        },
        {
          label: 'Power 3s Avg',
          stroke: POWER_SMOOTH_STROKE,
          width: 2,
          show: showPower3s,
          points: { show: false },
        },
        {
          label: 'HR',
          scale: 'hr',
          stroke: HR_STROKE,
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

  return <div ref={containerRef} className="workout-chart" />;
};
