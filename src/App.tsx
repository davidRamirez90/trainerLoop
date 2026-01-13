import type { CSSProperties } from 'react';
import { useMemo } from 'react';

import './App.css';
import { WorkoutChart } from './components/WorkoutChart';
import { workoutPlan, type WorkoutSegment } from './data/workout';
import { useTelemetrySimulation } from './hooks/useTelemetrySimulation';
import { formatDuration } from './utils/time';
import { getTargetRangeAtTime } from './utils/workout';

const connectedDevices = [
  { name: 'Wahoo KICKR', battery: 98, status: 'Trainer' },
  { name: 'Garmin HRM-Pro', battery: 100, status: 'HR Sensor' },
];

const buildPhaseProgress = (segments: WorkoutSegment[], elapsedSec: number) => {
  const totals = { warmup: 0, intervals: 0, cooldown: 0 };
  const elapsed = { warmup: 0, intervals: 0, cooldown: 0 };
  let cursor = 0;

  segments.forEach((segment) => {
    const start = cursor;
    const end = cursor + segment.durationSec;
    const segmentElapsed = Math.max(0, Math.min(elapsedSec, end) - start);

    if (segment.phase === 'warmup') {
      totals.warmup += segment.durationSec;
      elapsed.warmup += segmentElapsed;
    } else if (segment.phase === 'cooldown') {
      totals.cooldown += segment.durationSec;
      elapsed.cooldown += segmentElapsed;
    } else {
      totals.intervals += segment.durationSec;
      elapsed.intervals += segmentElapsed;
    }

    cursor = end;
  });

  return [
    {
      key: 'warmup',
      label: 'Warmup',
      totalSec: totals.warmup,
      elapsedSec: elapsed.warmup,
    },
    {
      key: 'intervals',
      label: 'Intervals',
      totalSec: totals.intervals,
      elapsedSec: elapsed.intervals,
    },
    {
      key: 'cooldown',
      label: 'Cooldown',
      totalSec: totals.cooldown,
      elapsedSec: elapsed.cooldown,
    },
  ];
};

function App() {
  const { samples, elapsedSec, totalDurationSec, isLive } = useTelemetrySimulation(
    workoutPlan.segments
  );
  const latestSample = samples[samples.length - 1];
  const { segment, index, endSec, targetRange } = getTargetRangeAtTime(
    workoutPlan.segments,
    elapsedSec
  );

  const { low: targetLow, high: targetHigh } = targetRange;
  const targetMid = (targetLow + targetHigh) / 2;

  const displayPower = latestSample ? Math.round(latestSample.powerWatts) : null;
  const displayHr = latestSample ? Math.round(latestSample.hrBpm) : null;
  const displayCadence = latestSample ? Math.round(latestSample.cadenceRpm) : null;

  const avgPower = useMemo(() => {
    if (!samples.length) {
      return 0;
    }
    const total = samples.reduce((sum, sample) => sum + sample.powerWatts, 0);
    return Math.round(total / samples.length);
  }, [samples]);

  const normalizedPower = avgPower ? Math.round(avgPower * 1.03) : 0;
  const tss = avgPower
    ? Math.round(
        (elapsedSec / 3600) * Math.pow(avgPower / workoutPlan.ftpWatts, 2) * 100
      )
    : 0;
  const kj = avgPower ? Math.round((avgPower * elapsedSec) / 1000) : 0;

  const compliance = displayPower
    ? Math.round((displayPower / targetMid) * 100)
    : 0;

  const remainingSec = Math.max(totalDurationSec - elapsedSec, 0);
  const segmentRemainingSec = Math.max(endSec - elapsedSec, 0);

  const workSegments = workoutPlan.segments.filter((seg) => seg.isWork);
  const totalIntervals = workSegments.length;
  const workIndexBySegment = workoutPlan.segments.reduce<number[]>((acc, seg) => {
    const current = acc.length ? acc[acc.length - 1] : 0;
    acc.push(seg.isWork ? current + 1 : current);
    return acc;
  }, []);
  const currentIntervalIndex = Math.max(1, workIndexBySegment[index] || 1);

  const progressPhases = useMemo(
    () => buildPhaseProgress(workoutPlan.segments, elapsedSec),
    [elapsedSec, workoutPlan.segments]
  );

  const coachMessage = compliance >= 97 && compliance <= 105
    ? {
        title: 'Great work',
        body: "Excellent power control. You're nailing the target within 5%.",
      }
    : {
        title: 'Hold steady',
        body: 'Settle the effort and smooth out the cadence over the next minute.',
      };

  const intervalLabel = segment.isWork
    ? 'WORK'
    : segment.phase === 'recovery'
      ? 'RECOVERY'
      : segment.phase.toUpperCase();

  return (
    <div className="app">
      <header className="top-bar">
        <div className="title-block">
          <button className="back-button" type="button" aria-label="Back">
            ←
          </button>
          <div>
            <div className="title">{workoutPlan.name}</div>
            <div className="subtitle">{workoutPlan.subtitle}</div>
          </div>
        </div>
        <div className={`live-status ${isLive ? 'live' : 'paused'}`}>
          <span className="live-dot" />
          {isLive ? 'LIVE' : 'PAUSED'}
        </div>
      </header>

      <section
        className="panel workout-panel"
        style={{ '--delay': '0.1s' } as CSSProperties}
      >
        <div className="panel-header">
          <div className="panel-title">WORKOUT PROFILE</div>
          <div className="panel-meta">
            <div>
              <span>Elapsed</span>
              <strong>{formatDuration(elapsedSec)}</strong>
            </div>
            <div>
              <span>FTP</span>
              <strong>{workoutPlan.ftpWatts}W</strong>
            </div>
          </div>
        </div>
        <WorkoutChart
          segments={workoutPlan.segments}
          samples={samples}
          elapsedSec={elapsedSec}
        />
        <div className="chart-legend">
          <div className="legend-item">
            <span className="legend-swatch" />
            Target Zone
          </div>
          <div className="legend-item">
            <span className="legend-line" />
            Actual
          </div>
        </div>
      </section>

      <section className="metrics-row">
        <div className="panel metric-card" style={{ '--delay': '0.2s' } as CSSProperties}>
          <div className="metric-header">
            <span>POWER</span>
            <span className="metric-tag">{segment.isWork ? 'ERG' : 'RES'}</span>
          </div>
          <div className="metric-value">
            {displayPower === null ? <span className="muted">--</span> : displayPower}
            <span className="unit">W</span>
          </div>
          <div className="metric-sub">
            <div>Target</div>
            <div className="muted">
              {targetLow}-{targetHigh}W
            </div>
          </div>
          <div className="metric-sub">
            <div>Compliance</div>
            <div className={`accent ${compliance >= 100 ? 'good' : ''}`}>
              {compliance}%
            </div>
          </div>
        </div>

        <div className="panel metric-card interval-card" style={{ '--delay': '0.25s' } as CSSProperties}>
          <div className="metric-header">
            <span>Interval</span>
            <span className="muted">
              {currentIntervalIndex}/{totalIntervals}
            </span>
          </div>
          <div className="metric-value">
            {formatDuration(segmentRemainingSec)}
          </div>
          <div className="pill">{intervalLabel}</div>
        </div>

        <div className="panel metric-card mini" style={{ '--delay': '0.3s' } as CSSProperties}>
          <div className="metric-header">Elapsed</div>
          <div className="metric-value">{formatDuration(elapsedSec)}</div>
        </div>

        <div className="panel metric-card mini" style={{ '--delay': '0.35s' } as CSSProperties}>
          <div className="metric-header">Remaining</div>
          <div className="metric-value">{formatDuration(remainingSec)}</div>
        </div>
      </section>

      <section className="secondary-grid">
        <div className="panel stat-card" style={{ '--delay': '0.4s' } as CSSProperties}>
          <div className="stat-label">Avg Power</div>
          <div className="stat-value">{avgPower || '--'}W</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.45s' } as CSSProperties}>
          <div className="stat-label">Norm Power</div>
          <div className="stat-value">{normalizedPower || '--'}W</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.5s' } as CSSProperties}>
          <div className="stat-label">TSS</div>
          <div className="stat-value">{tss || '--'}</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.55s' } as CSSProperties}>
          <div className="stat-label">KJ</div>
          <div className="stat-value">{kj || '--'}</div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.6s' } as CSSProperties}>
          <div className="stat-label">Heart Rate</div>
          <div className="stat-value">
            {displayHr === null ? '--' : displayHr}
            <span className="unit">bpm</span>
          </div>
        </div>
        <div className="panel stat-card" style={{ '--delay': '0.65s' } as CSSProperties}>
          <div className="stat-label">Cadence</div>
          <div className="stat-value">
            {displayCadence === null ? '--' : displayCadence}
            <span className="unit">rpm</span>
          </div>
        </div>
      </section>

      <section className="bottom-row">
        <div className="panel progress-card" style={{ '--delay': '0.7s' } as CSSProperties}>
          <div className="progress-header">Interval Progress</div>
          <div className="progress-bar">
            {progressPhases.map((phase) => {
              const ratio = phase.totalSec
                ? Math.min(phase.elapsedSec / phase.totalSec, 1)
                : 0;
              return (
                <div
                  key={phase.key}
                  className={`progress-segment ${phase.key}`}
                  style={{ '--progress': ratio } as CSSProperties}
                >
                  <div className="progress-fill" />
                  <span>{phase.label}</span>
                </div>
              );
            })}
          </div>
          <div className="coach-card">
            <div className="coach-title">
              <span className="coach-icon" />
              {coachMessage.title.toUpperCase()}
            </div>
            <div className="coach-body">{coachMessage.body}</div>
          </div>
        </div>

        <div className="panel devices-card" style={{ '--delay': '0.75s' } as CSSProperties}>
          <div className="progress-header">Connected Devices</div>
          <div className="device-list">
            {connectedDevices.map((device) => (
              <div key={device.name} className="device-row">
                <div>
                  <div className="device-name">{device.name}</div>
                  <div className="device-status">{device.status}</div>
                </div>
                <div className="device-battery">
                  <span className="battery-dot" />
                  {device.battery}%
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}

export default App;
