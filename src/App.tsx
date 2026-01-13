import type { ChangeEvent, CSSProperties } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import './App.css';
import { WorkoutChart } from './components/WorkoutChart';
import type { WorkoutPlan, WorkoutSegment } from './data/workout';
import { useBluetoothDevices } from './hooks/useBluetoothDevices';
import { useBluetoothTelemetry } from './hooks/useBluetoothTelemetry';
import { useFtmsControl } from './hooks/useFtmsControl';
import { useTelemetrySimulation } from './hooks/useTelemetrySimulation';
import { useWorkoutClock } from './hooks/useWorkoutClock';
import { formatDuration } from './utils/time';
import { parseWorkoutFile } from './utils/workoutImport';
import { getTargetRangeAtTime } from './utils/workout';

const IDLE_SEGMENT: WorkoutSegment = {
  id: 'idle',
  label: 'Idle',
  durationSec: 1,
  targetRange: { low: 0, high: 0 },
  phase: 'warmup',
  isWork: false,
};

const AUTO_PAUSE_THRESHOLD_SEC = 5;

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
  const [activePlan, setActivePlan] = useState<WorkoutPlan | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [importName, setImportName] = useState<string | null>(null);
  const [ergEnabled, setErgEnabled] = useState(true);
  const [autoResumeOnWork, setAutoResumeOnWork] = useState(false);
  const [autoPauseArmed, setAutoPauseArmed] = useState(true);
  const [showResumeOverlay, setShowResumeOverlay] = useState(false);
  const lastWorkRef = useRef<number | null>(null);
  const resumeTimeoutRef = useRef<number | null>(null);
  const prevRunningRef = useRef(false);
  const hasPlan = !!activePlan && activePlan.segments.length > 0;
  const activeSegments = hasPlan ? activePlan.segments : [];
  const clockSegments = activeSegments;
  const targetSegments = hasPlan ? activeSegments : [IDLE_SEGMENT];

  const clock = useWorkoutClock(clockSegments);
  const {
    bluetoothAvailable,
    trainer,
    hrSensor,
    connectTrainer,
    connectHeartRate,
    disconnectTrainer,
    disconnectHeartRate,
    trainerDevice,
    hrDevice,
  } = useBluetoothDevices();
  const bluetoothTelemetry = useBluetoothTelemetry({
    trainerDevice,
    hrDevice,
    elapsedSec: clock.activeSec,
    isRecording: clock.isRunning,
    sessionId: clock.sessionId,
  });
  const simulation = useTelemetrySimulation(
    clockSegments,
    clock.activeSec,
    clock.isRunning,
    clock.sessionId
  );
  const telemetrySamples = bluetoothTelemetry.samples.length
    ? bluetoothTelemetry.samples
    : simulation.samples;
  const sessionElapsedSec = clock.elapsedSec;
  const activeSec = clock.activeSec;
  const totalDurationSec = clock.totalDurationSec;
  const isRunning = clock.isRunning;
  const isComplete = clock.isComplete;
  const isSessionActive = clock.isSessionActive;
  const hasStarted = isSessionActive || activeSec > 0;
  const isPaused = hasPlan && hasStarted && !isRunning && !isComplete;
  const liveStatus = !hasPlan
    ? 'NO WORKOUT'
    : isRunning
      ? 'LIVE'
      : isComplete
        ? 'DONE'
        : hasStarted
          ? 'PAUSED'
          : 'READY';
  const liveStatusClass = !hasPlan
    ? 'idle'
    : isRunning
      ? 'live'
      : isComplete
        ? 'complete'
        : hasStarted
          ? 'paused'
          : 'ready';
  const latestSample = telemetrySamples[telemetrySamples.length - 1];
  const { segment, index, endSec, targetRange } = getTargetRangeAtTime(
    targetSegments,
    activeSec
  );
  const ftpWatts = activePlan?.ftpWatts ?? 0;

  const { low: targetLow, high: targetHigh } = targetRange;
  const targetMid = (targetLow + targetHigh) / 2;
  const ftmsControl = useFtmsControl({
    trainerDevice,
    targetWatts: targetMid,
    isActive: isRunning && ergEnabled && hasPlan,
  });

  const displayPower = latestSample ? Math.round(latestSample.powerWatts) : null;
  const displayHr = latestSample ? Math.round(latestSample.hrBpm) : null;
  const displayCadence = latestSample ? Math.round(latestSample.cadenceRpm) : null;
  const bluetoothLatest = bluetoothTelemetry.latest;
  const simulationLatest = simulation.samples[simulation.samples.length - 1] ?? null;
  const canDetectWork = trainer.status === 'connected' || bluetoothTelemetry.isActive;
  const latestTelemetry = canDetectWork ? bluetoothLatest : simulationLatest;
  const latestPower = latestTelemetry?.powerWatts ?? 0;
  const latestCadence = latestTelemetry?.cadenceRpm ?? 0;
  const hasWorkTelemetry = latestPower > 0 || latestCadence > 0;

  const avgPower = useMemo(() => {
    if (!telemetrySamples.length) {
      return 0;
    }
    const total = telemetrySamples.reduce(
      (sum, sample) => sum + sample.powerWatts,
      0
    );
    return Math.round(total / telemetrySamples.length);
  }, [telemetrySamples]);

  const normalizedPower = avgPower ? Math.round(avgPower * 1.03) : 0;
  const tss = avgPower && hasPlan
    ? Math.round((activeSec / 3600) * Math.pow(avgPower / ftpWatts, 2) * 100)
    : 0;
  const kj = avgPower ? Math.round((avgPower * activeSec) / 1000) : 0;

  const compliance = displayPower && targetMid > 0
    ? Math.round((displayPower / targetMid) * 100)
    : 0;

  const remainingSec = Math.max(totalDurationSec - activeSec, 0);
  const segmentRemainingSec = Math.max(endSec - activeSec, 0);

  const workSegments = activeSegments.filter((seg) => seg.isWork);
  const totalIntervals = workSegments.length;
  const workIndexBySegment = activeSegments.reduce<number[]>((acc, seg) => {
    const current = acc.length ? acc[acc.length - 1] : 0;
    acc.push(seg.isWork ? current + 1 : current);
    return acc;
  }, []);
  const currentIntervalIndex = hasPlan && totalIntervals > 0
    ? Math.max(1, workIndexBySegment[index] || 1)
    : 0;

  const progressPhases = useMemo(
    () => (hasPlan ? buildPhaseProgress(activeSegments, activeSec) : []),
    [activeSegments, activeSec, hasPlan]
  );

  const coachMessage = !hasPlan
    ? {
        title: 'Import a workout',
        body: 'Load a workout file to begin and unlock live coaching.',
      }
    : compliance >= 97 && compliance <= 105
      ? {
          title: 'Great work',
          body: "Excellent power control. You're nailing the target within 5%.",
        }
      : {
          title: 'Hold steady',
          body: 'Settle the effort and smooth out the cadence over the next minute.',
        };

  const intervalLabel = hasPlan
    ? segment.isWork
      ? 'WORK'
      : segment.phase === 'recovery'
        ? 'RECOVERY'
        : segment.phase.toUpperCase()
    : 'IDLE';
  const targetLabel = hasPlan
    ? `${Math.round(targetLow)}-${Math.round(targetHigh)}W`
    : '--';
  const complianceLabel = hasPlan ? `${compliance}%` : '--';
  const intervalRemainingLabel = hasPlan ? formatDuration(segmentRemainingSec) : '--:--';
  const activeLabel = hasPlan ? formatDuration(activeSec) : '--:--';
  const elapsedLabel = hasPlan ? formatDuration(sessionElapsedSec) : '--:--';
  const remainingLabel = hasPlan ? formatDuration(remainingSec) : '--:--';
  const intervalCountLabel = hasPlan
    ? `${currentIntervalIndex}/${totalIntervals}`
    : '--/--';
  const planName = activePlan?.name ?? 'No workout loaded';
  const planSubtitle = activePlan?.subtitle ?? 'Import a workout to begin.';
  const sessionSubtitle = hasPlan
    ? activePlan?.subtitle ?? 'Imported workout'
    : 'Import a workout file to preview.';

  const deviceRows = [
    {
      key: 'trainer',
      label: 'Trainer',
      state: trainer,
      connect: connectTrainer,
      disconnect: disconnectTrainer,
    },
    {
      key: 'hr',
      label: 'HR Sensor',
      state: hrSensor,
      connect: connectHeartRate,
      disconnect: disconnectHeartRate,
    },
  ];
  const trainerTelemetryError = bluetoothTelemetry.error;
  const trainerControlError = ftmsControl.error;
  const trainerControlStatus = ftmsControl.status;
  const trainerControlLabel = trainerControlStatus === 'ready'
    ? ergEnabled
      ? hasPlan && isRunning
        ? 'ERG control active'
        : 'ERG control ready'
      : 'ERG control disabled'
    : trainerControlStatus === 'requesting'
      ? 'ERG control arming'
      : trainerControlStatus === 'error'
        ? 'ERG control error'
        : 'ERG control idle';
  const startLabel = isComplete
    ? 'Restart'
    : hasStarted && !isRunning
      ? 'Resume'
      : 'Start';
  const ergToggleLabel = ergEnabled ? 'ERG On' : 'ERG Off';

  useEffect(() => {
    return () => {
      if (resumeTimeoutRef.current) {
        window.clearTimeout(resumeTimeoutRef.current);
        resumeTimeoutRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    lastWorkRef.current = null;
    setAutoResumeOnWork(false);
    setAutoPauseArmed(true);
  }, [clock.sessionId]);

  useEffect(() => {
    if (!canDetectWork || !autoResumeOnWork) {
      return;
    }
    if (!hasWorkTelemetry || !hasPlan || isRunning || isComplete || !isSessionActive) {
      return;
    }
    clock.start();
    ftmsControl.startWorkout();
    setAutoResumeOnWork(false);
    setAutoPauseArmed(true);
  }, [
    autoResumeOnWork,
    canDetectWork,
    clock.start,
    ftmsControl.startWorkout,
    hasPlan,
    hasWorkTelemetry,
    isComplete,
    isRunning,
    isSessionActive,
  ]);

  useEffect(() => {
    if (!canDetectWork || !hasPlan || !isRunning) {
      return;
    }
    if (hasWorkTelemetry) {
      lastWorkRef.current = sessionElapsedSec;
      if (!autoPauseArmed) {
        setAutoPauseArmed(true);
      }
      return;
    }
    if (!autoPauseArmed) {
      return;
    }
    const lastWork = lastWorkRef.current;
    if (lastWork === null) {
      return;
    }
    if (sessionElapsedSec - lastWork >= AUTO_PAUSE_THRESHOLD_SEC) {
      clock.pause();
      ftmsControl.pauseWorkout();
      setAutoResumeOnWork(true);
    }
  }, [
    autoPauseArmed,
    canDetectWork,
    clock.pause,
    ftmsControl.pauseWorkout,
    hasPlan,
    hasWorkTelemetry,
    isRunning,
    sessionElapsedSec,
  ]);

  useEffect(() => {
    const wasRunning = prevRunningRef.current;
    if (!wasRunning && isRunning) {
      setShowResumeOverlay(true);
      if (resumeTimeoutRef.current) {
        window.clearTimeout(resumeTimeoutRef.current);
        resumeTimeoutRef.current = null;
      }
      resumeTimeoutRef.current = window.setTimeout(() => {
        setShowResumeOverlay(false);
      }, 2000);
    } else if (wasRunning && !isRunning) {
      if (resumeTimeoutRef.current) {
        window.clearTimeout(resumeTimeoutRef.current);
        resumeTimeoutRef.current = null;
      }
      setShowResumeOverlay(false);
    }
    prevRunningRef.current = isRunning;
  }, [isRunning]);

  const handleStart = () => {
    if (!hasPlan) {
      setImportError('Import a workout to start the session.');
      return;
    }
    const isSessionStarting = !isSessionActive;
    clock.startSession();
    if (canDetectWork && isSessionStarting && !hasWorkTelemetry) {
      setAutoResumeOnWork(true);
      return;
    }
    clock.start();
    ftmsControl.startWorkout();
    setAutoResumeOnWork(false);
    if (canDetectWork && !hasWorkTelemetry) {
      setAutoPauseArmed(false);
    } else {
      setAutoPauseArmed(true);
    }
  };

  const handlePause = () => {
    clock.pause();
    ftmsControl.pauseWorkout();
    setAutoResumeOnWork(false);
  };

  const handleStop = () => {
    clock.stop();
    ftmsControl.stopWorkout();
    setAutoResumeOnWork(false);
    setAutoPauseArmed(true);
  };

  const handleImport = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setImportError(null);
    try {
      const text = await file.text();
      const parsed = parseWorkoutFile(file.name, text);
      clock.stop();
      setActivePlan(parsed);
      setImportName(file.name);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unable to import workout.';
      setImportError(message);
      setImportName(null);
    } finally {
      event.target.value = '';
    }
  };

  return (
    <div className="app">
      <header className="top-bar">
        <div className="title-block">
          <button className="back-button" type="button" aria-label="Back">
            ←
          </button>
          <div>
            <div className="title">{planName}</div>
            <div className="subtitle">{planSubtitle}</div>
          </div>
        </div>
        <div className={`live-status ${liveStatusClass}`}>
          <span className="live-dot" />
          {liveStatus}
        </div>
      </header>

      <section
        className="panel session-panel"
        style={{ '--delay': '0.05s' } as CSSProperties}
      >
        <div className="session-info">
          <div className="panel-title">SESSION CONTROL</div>
          <div className="session-title">{planName}</div>
          <div className="session-subtitle">{sessionSubtitle}</div>
          {importName ? (
            <div className="session-meta">Imported: {importName}</div>
          ) : null}
          {importError ? (
            <div className="session-error">{importError}</div>
          ) : null}
        </div>
        <div className="session-actions">
          <label className={`session-button ${hasStarted ? 'disabled' : ''}`}>
            Import Workout
            <input
              className="file-input"
              type="file"
              accept=".json,.erg,.mrc,.zwo,application/json"
              onChange={handleImport}
              disabled={hasStarted}
            />
          </label>
          <button
            className="session-button primary"
            type="button"
            onClick={handleStart}
            disabled={!hasPlan || isRunning}
          >
            {startLabel}
          </button>
          <button
            className="session-button"
            type="button"
            onClick={handlePause}
            disabled={!isRunning}
          >
            Pause
          </button>
          <button
            className="session-button danger"
            type="button"
            onClick={handleStop}
            disabled={!hasStarted}
          >
            Stop
          </button>
          <button
            className={`session-button toggle ${ergEnabled ? 'on' : 'off'}`}
            type="button"
            onClick={() => setErgEnabled((prev) => !prev)}
            disabled={trainer.status !== 'connected' || !hasPlan}
          >
            {ergToggleLabel}
          </button>
        </div>
      </section>

      <section
        className="panel workout-panel"
        style={{ '--delay': '0.1s' } as CSSProperties}
      >
        <div className="panel-header">
          <div className="panel-title">WORKOUT PROFILE</div>
          <div className="panel-meta">
            <div>
              <span>Elapsed</span>
              <strong>{elapsedLabel}</strong>
            </div>
            <div>
              <span>FTP</span>
              <strong>{hasPlan ? `${ftpWatts}W` : '--'}</strong>
            </div>
          </div>
        </div>
        {hasPlan ? (
          <>
            <div className="workout-chart-shell">
              <WorkoutChart
                segments={activeSegments}
                samples={telemetrySamples}
                elapsedSec={activeSec}
                ftpWatts={ftpWatts}
                isRecording={isRunning}
              />
              {isPaused ? (
                <div className="chart-overlay paused">
                  <span className="overlay-icon pause" />
                </div>
              ) : null}
              {showResumeOverlay ? (
                <div className="chart-overlay resume">
                  <span className="overlay-icon play" />
                </div>
              ) : null}
            </div>
            <div className="chart-legend">
              <div className="legend-item">
                <span className="legend-swatch" />
                Target Zones
              </div>
              <div className="legend-item">
                <span className="legend-line" />
                Actual
              </div>
              <div className="legend-item">
                <span className="legend-line hr" />
                HR
              </div>
            </div>
          </>
        ) : (
          <div className="workout-placeholder">
            Import a workout file to see the timeline and targets.
          </div>
        )}
      </section>

      <section className="metrics-row">
        <div className="panel metric-card" style={{ '--delay': '0.2s' } as CSSProperties}>
          <div className="metric-header">
            <span>POWER</span>
            <span className="metric-tag">{hasPlan ? (segment.isWork ? 'ERG' : 'RES') : '--'}</span>
          </div>
          <div className="metric-value">
            {displayPower === null ? <span className="muted">--</span> : displayPower}
            <span className="unit">W</span>
          </div>
          <div className="metric-sub">
            <div>Target</div>
            <div className="muted">{targetLabel}</div>
          </div>
          <div className="metric-sub">
            <div>Compliance</div>
            <div className={`accent ${compliance >= 100 ? 'good' : ''}`}>
              {complianceLabel}
            </div>
          </div>
        </div>

        <div className="panel metric-card interval-card" style={{ '--delay': '0.25s' } as CSSProperties}>
          <div className="metric-header">
            <span>Interval</span>
            <span className="muted">
              {intervalCountLabel}
            </span>
          </div>
          <div className="metric-value">
            {intervalRemainingLabel}
          </div>
          <div className="pill">{intervalLabel}</div>
        </div>

        <div className="panel metric-card mini" style={{ '--delay': '0.3s' } as CSSProperties}>
          <div className="metric-header">Active</div>
          <div className="metric-value">{activeLabel}</div>
        </div>

        <div className="panel metric-card mini" style={{ '--delay': '0.35s' } as CSSProperties}>
          <div className="metric-header">Remaining</div>
          <div className="metric-value">{remainingLabel}</div>
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
          {hasPlan ? (
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
          ) : (
            <div className="progress-placeholder">
              Import a workout to view interval progress.
            </div>
          )}
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
          {!bluetoothAvailable ? (
            <div className="device-warning">
              Bluetooth is unavailable. Use Chrome or Edge with HTTPS/localhost.
            </div>
          ) : null}
          <div className="device-list">
            {deviceRows.map((row) => {
              const { state } = row;
              const isConnected = state.status === 'connected';
              const isConnecting = state.status === 'connecting';
              const name = state.name || row.label;
              const statusLabel = isConnected
                ? 'Connected'
                : isConnecting
                  ? 'Connecting...'
                  : 'Not connected';
              const infoParts = [state.manufacturer, state.model].filter(Boolean);
              const errorMessage =
                row.key === 'trainer'
                  ? [state.error, trainerTelemetryError, trainerControlError]
                    .filter(Boolean)
                    .join(' / ')
                  : state.error;
              return (
                <div key={row.key} className="device-row">
                  <div className="device-info">
                    <div className="device-name">{name}</div>
                    <div className="device-status">{row.label} - {statusLabel}</div>
                    {infoParts.length ? (
                      <div className="device-meta">{infoParts.join(' - ')}</div>
                    ) : null}
                    {row.key === 'trainer' ? (
                      <div className="device-meta">{trainerControlLabel}</div>
                    ) : null}
                    {row.key === 'trainer' && state.features ? (
                      <div className="device-meta">FTMS features: {state.features}</div>
                    ) : null}
                    {errorMessage ? (
                      <div className="device-error">{errorMessage}</div>
                    ) : null}
                  </div>
                  <div className="device-actions">
                    {state.battery !== null ? (
                      <div className="device-battery">
                        <span className="battery-dot" />
                        {state.battery}%
                      </div>
                    ) : null}
                    {isConnected ? (
                      <button
                        className="device-button disconnect"
                        type="button"
                        onClick={row.disconnect}
                      >
                        Disconnect
                      </button>
                    ) : (
                      <button
                        className="device-button"
                        type="button"
                        onClick={row.connect}
                        disabled={!bluetoothAvailable || isConnecting}
                      >
                        {isConnecting ? 'Connecting...' : 'Connect'}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>
    </div>
  );
}

export default App;
