import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import uPlot from 'uplot';

import type { WorkoutSegment } from '../data/workout';
import type { TelemetrySample } from '../types';
import { formatDuration } from '../utils/time';
import { getTotalDurationSec } from '../utils/workout';

import 'uplot/dist/uPlot.min.css';

const TARGET_COLORS = {
  work: 'rgba(244, 150, 62, 0.35)',
  workStroke: 'rgba(244, 150, 62, 0.7)',
  recovery: 'rgba(79, 132, 185, 0.25)',
  recoveryStroke: 'rgba(79, 132, 185, 0.5)',
};

const ACTUAL_STROKE = '#65c7ff';

const useChartSize = (ref: React.RefObject<HTMLDivElement>) => {
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
  elapsedSec: number;
};

export const WorkoutChart = ({ segments, samples, elapsedSec }: WorkoutChartProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const plotRef = useRef<uPlot | null>(null);
  const segmentsRef = useRef(segments);
  const elapsedRef = useRef(elapsedSec);
  const size = useChartSize(containerRef);

  const totalDurationSec = useMemo(() => getTotalDurationSec(segments), [segments]);
  const maxTarget = useMemo(() => {
    const peakTargets = segments.map((segment) =>
      Math.max(
        segment.targetRange.high,
        segment.rampToRange?.high ?? segment.targetRange.high
      )
    );
    return Math.max(...peakTargets) + 40;
  }, [segments]);

  const data = useMemo(() => {
    const times = samples.map((sample) => sample.timeSec);
    const power = samples.map((sample) => sample.powerWatts);
    return [times, power] as uPlot.AlignedData;
  }, [samples]);

  useEffect(() => {
    segmentsRef.current = segments;
  }, [segments]);

  useEffect(() => {
    elapsedRef.current = elapsedSec;
    if (plotRef.current) {
      plotRef.current.redraw();
    }
  }, [elapsedSec]);

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

        if (segment.isWork) {
          ctx.fillStyle = TARGET_COLORS.work;
          ctx.strokeStyle = TARGET_COLORS.workStroke;
        } else {
          ctx.fillStyle = TARGET_COLORS.recovery;
          ctx.strokeStyle = TARGET_COLORS.recoveryStroke;
        }

        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(x0, yLowStart);
        ctx.lineTo(x1, yLowEnd);
        ctx.lineTo(x1, yHighEnd);
        ctx.lineTo(x0, yHighStart);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();

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
          range: [0, maxTarget],
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
      ],
      series: [
        {
          label: 'time',
        },
        {
          label: 'Actual',
          stroke: ACTUAL_STROKE,
          width: 2,
        },
      ],
      legend: {
        show: false,
      },
      hooks: {
        draw: [drawTargetBands, drawTimeMarker],
      },
    };

    plotRef.current = new uPlot(options, data, containerRef.current);

    return () => {
      plotRef.current?.destroy();
      plotRef.current = null;
    };
  }, [data, maxTarget, size.height, size.width, totalDurationSec]);

  useEffect(() => {
    if (plotRef.current) {
      plotRef.current.setData(data);
    }
  }, [data]);

  return <div ref={containerRef} className="workout-chart" />;
};
