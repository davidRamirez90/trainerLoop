import { useId } from 'react';
import type { TelemetrySample } from '../types';

interface MiniWorkoutChartProps {
  samples: TelemetrySample[];
  ftpWatts: number;
  width?: number;
  height?: number;
}

const ZONE_STOPS = [
  { max: 0.55, color: '#6C7A89' },
  { max: 0.75, color: '#3B8EA5' },
  { max: 0.88, color: '#5FAF5F' },
  { max: 0.94, color: '#C9A227' },
  { max: 1.05, color: '#E57A1F' },
  { max: 1.2, color: '#D64541' },
  { max: Number.POSITIVE_INFINITY, color: '#8C2A2A' },
];

export function MiniWorkoutChart({
  samples,
  ftpWatts,
  width = 400,
  height = 100,
}: MiniWorkoutChartProps) {
  if (samples.length === 0 || ftpWatts <= 0) {
    return (
      <svg width={width} height={height} style={{ opacity: 0.3 }}>
        <text x={width / 2} y={height / 2} textAnchor="middle" fill="#666">
          No data
        </text>
      </svg>
    );
  }

  // Filter out dropouts and sort by time
  const validSamples = samples
    .filter((s) => !s.dropout && s.powerWatts > 0)
    .sort((a, b) => a.timeSec - b.timeSec);

  if (validSamples.length === 0) {
    return (
      <svg width={width} height={height} style={{ opacity: 0.3 }}>
        <text x={width / 2} y={height / 2} textAnchor="middle" fill="#666">
          No power data
        </text>
      </svg>
    );
  }

  const maxTime = validSamples[validSamples.length - 1]?.timeSec ?? 0;
  const maxPower = Math.max(...validSamples.map((s) => s.powerWatts), ftpWatts * 1.1);

  // Scale functions
  const scaleX = (time: number) => (maxTime > 0 ? (time / maxTime) * width : 0);
  const scaleY = (power: number) => height - (power / maxPower) * (height - 10) - 5;

  // Build zone background bands
  const zoneBands = ZONE_STOPS.map((stop, index) => {
    const prevMax = index === 0 ? 0 : ZONE_STOPS[index - 1].max;
    const y1 = scaleY(stop.max * ftpWatts);
    const y2 = scaleY(prevMax * ftpWatts);
    return {
      y: y1,
      height: Math.max(0, y2 - y1),
      color: stop.color,
    };
  }).filter((band) => band.height > 0);

  // Build power path
  let pathD = '';
  validSamples.forEach((sample, index) => {
    const x = scaleX(sample.timeSec);
    const y = scaleY(sample.powerWatts);
    if (index === 0) {
      pathD = `M ${x} ${y}`;
    } else {
      pathD += ` L ${x} ${y}`;
    }
  });

  // Create gradient with zone colors - stable ID using useId
  const baseId = useId();
  const gradientId = `powerGradient-${baseId.replace(/:/g, '')}`;

  return (
    <svg
      width={width}
      height={height}
      style={{ display: 'block' }}
      viewBox={`0 0 ${width} ${height}`}
    >
      <defs>
        <linearGradient id={gradientId} x1="0%" y1="0%" x2="0%" y2="100%">
          {zoneBands.map((band, i) => (
            <stop
              key={i}
              offset={`${(1 - (band.y + band.height / 2) / height) * 100}%`}
              stopColor={band.color}
              stopOpacity={0.15}
            />
          ))}
        </linearGradient>
      </defs>

      {/* Zone background */}
      {zoneBands.map((band, i) => (
        <rect
          key={i}
          x={0}
          y={band.y}
          width={width}
          height={band.height}
          fill={band.color}
          opacity={0.1}
        />
      ))}

      {/* Zone lines */}
      {zoneBands.map((band, i) => (
        <line
          key={`line-${i}`}
          x1={0}
          y1={band.y}
          x2={width}
          y2={band.y}
          stroke={band.color}
          strokeWidth={1}
          opacity={0.3}
        />
      ))}

      {/* Power line with zone coloring */}
      {validSamples.length > 1 && (
        <>
          {/* Fill area under curve */}
          <path
            d={`${pathD} L ${width} ${height} L 0 ${height} Z`}
            fill={`url(#${gradientId})`}
            opacity={0.4}
          />
          {/* Power line */}
          <path
            d={pathD}
            fill="none"
            stroke="#65c7ff"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      )}

      {/* FTP line */}
      <line
        x1={0}
        y1={scaleY(ftpWatts)}
        x2={width}
        y2={scaleY(ftpWatts)}
        stroke="#E57A1F"
        strokeWidth={1.5}
        strokeDasharray="4 4"
        opacity={0.7}
      />
    </svg>
  );
}
