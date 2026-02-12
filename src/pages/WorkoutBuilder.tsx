import { useState, useMemo, useCallback } from 'react';
import type { WorkoutPlan } from '../data/workout';
import { parseWorkoutText } from '../utils/workoutParser';
import { WorkoutChart } from '../components/WorkoutChart';
import { useTheme } from '../hooks/useTheme';

interface WorkoutBuilderProps {
  onBack: () => void;
  onLoadWorkout: (plan: WorkoutPlan) => void;
  userFtp: number;
}

const DEFAULT_WORKOUT_TEXT = `Warmup
- 10m 50%

Main set 4x
- 3m 90% 85-95rpm
- 2m 50% easy spin

Cooldown
- 5m 40%`;

export function WorkoutBuilder({ onBack, onLoadWorkout, userFtp }: WorkoutBuilderProps) {
  const { theme } = useTheme();
  const [workoutName, setWorkoutName] = useState('');
  const [workoutText, setWorkoutText] = useState(DEFAULT_WORKOUT_TEXT);
  const [showPreview, setShowPreview] = useState(true);

  // Parse workout in real-time
  const parsedResult = useMemo(() => {
    return parseWorkoutText(workoutText, { ftpWatts: userFtp });
  }, [workoutText, userFtp]);

  const { plan, errors } = parsedResult;

  // Calculate workout stats
  const workoutStats = useMemo(() => {
    if (plan.segments.length === 0) return null;
    
    const totalSeconds = plan.segments.reduce((sum, seg) => sum + seg.durationSec, 0);
    const workSeconds = plan.segments
      .filter(seg => seg.isWork)
      .reduce((sum, seg) => sum + seg.durationSec, 0);
    const avgPower = plan.segments.length > 0 
      ? plan.segments.reduce((sum, seg) => sum + (seg.targetRange.low + seg.targetRange.high) / 2, 0) / plan.segments.length
      : 0;
    
    return {
      totalMinutes: Math.round(totalSeconds / 60),
      workMinutes: Math.round(workSeconds / 60),
      intervals: plan.segments.filter(seg => seg.isWork).length,
      avgPower: Math.round(avgPower),
    };
  }, [plan]);

  const handleLoadWorkout = useCallback(() => {
    if (plan.segments.length === 0) return;
    
    const planWithName = {
      ...plan,
      name: workoutName || plan.name,
    };
    
    onLoadWorkout(planWithName);
  }, [plan, workoutName, onLoadWorkout]);

  const hasErrors = errors.length > 0;
  const hasSegments = plan.segments.length > 0;

  return (
    <div className="page workout-builder-page">
      <header className="page-header">
        <button className="back-button" onClick={onBack} type="button">
          ← Back
        </button>
        <h1>Workout Builder</h1>
      </header>

      <div className="builder-layout">
        <div className="builder-editor">
          <div className="form-group">
            <label htmlFor="workout-name">Workout Name</label>
            <input
              id="workout-name"
              type="text"
              value={workoutName}
              onChange={(e) => setWorkoutName(e.target.value)}
              placeholder="e.g., Sweet Spot Intervals"
              className="builder-input"
            />
          </div>

          <div className="form-group">
            <div className="builder-label-row">
              <label htmlFor="workout-text">Workout Definition</label>
              <span className="builder-ftp-badge">FTP: {userFtp}W</span>
            </div>
            <textarea
              id="workout-text"
              value={workoutText}
              onChange={(e) => setWorkoutText(e.target.value)}
              rows={20}
              className={`workout-textarea ${hasErrors ? 'has-errors' : ''}`}
              placeholder="Enter your workout definition..."
              spellCheck={false}
            />
            {hasErrors && (
              <div className="builder-errors">
                {errors.map((error, idx) => (
                  <div key={idx} className="builder-error">
                    ⚠️ {error}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="builder-actions">
            <button 
              className="btn btn-primary" 
              type="button"
              disabled={!hasSegments || hasErrors}
              onClick={handleLoadWorkout}
            >
              Load & Start Workout
            </button>
            <button 
              className="btn" 
              type="button"
              onClick={() => setShowPreview(!showPreview)}
            >
              {showPreview ? 'Hide Preview' : 'Show Preview'}
            </button>
            <button 
              className="btn" 
              type="button"
              disabled
              title="Coming in Phase 4"
            >
              Save to Library
            </button>
          </div>
        </div>

        <div className={`builder-preview ${showPreview ? 'visible' : ''}`}>
          <div className="builder-preview-header">
            <h3>Preview</h3>
            {workoutStats && (
              <div className="builder-stats">
                <div className="builder-stat">
                  <span className="builder-stat-value">{workoutStats.totalMinutes}m</span>
                  <span className="builder-stat-label">Total</span>
                </div>
                <div className="builder-stat">
                  <span className="builder-stat-value">{workoutStats.workMinutes}m</span>
                  <span className="builder-stat-label">Work</span>
                </div>
                <div className="builder-stat">
                  <span className="builder-stat-value">{workoutStats.intervals}</span>
                  <span className="builder-stat-label">Intervals</span>
                </div>
                <div className="builder-stat">
                  <span className="builder-stat-value">{workoutStats.avgPower}W</span>
                  <span className="builder-stat-label">Avg Power</span>
                </div>
              </div>
            )}
          </div>

          <div className="builder-chart-container">
            {hasSegments ? (
              <WorkoutChart
                segments={plan.segments}
                samples={[]}
                gaps={[]}
                elapsedSec={0}
                ftpWatts={userFtp}
                hrSensorConnected={false}
                showPower3s={false}
                intensityOverrides={[]}
                recoveryExtensions={{}}
                thresholdHr={null}
                currentHr={null}
                theme={theme}
              />
            ) : (
              <div className="preview-placeholder">
                <p>Enter a workout definition to see the preview</p>
                <p className="preview-hint">Use the syntax guide below for help</p>
              </div>
            )}
          </div>

          <div className="syntax-help">
            <h4>Syntax Guide</h4>
            <div className="syntax-grid">
              <div className="syntax-item">
                <code>- 5m 200w</code>
                <span>5 min at 200 watts</span>
              </div>
              <div className="syntax-item">
                <code>- 3m 85%</code>
                <span>3 min at 85% FTP</span>
              </div>
              <div className="syntax-item">
                <code>- 2m Z3</code>
                <span>2 min at Zone 3</span>
              </div>
              <div className="syntax-item">
                <code>- 4m 200w 90rpm</code>
                <span>With cadence target</span>
              </div>
              <div className="syntax-item">
                <code>Main set 4x</code>
                <span>Repeat following blocks 4 times</span>
              </div>
              <div className="syntax-item">
                <code>- Ramp 10m 50-90%</code>
                <span>Ramp from 50% to 90% FTP</span>
              </div>
            </div>
            
            <div className="syntax-phases">
              <h5>Phase Headers</h5>
              <p>Warmup, Work, Recovery, Cooldown — organize your workout sections</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
