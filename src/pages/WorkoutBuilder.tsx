import { useState } from 'react';

interface WorkoutBuilderProps {
  onBack: () => void;
}

export function WorkoutBuilder({ onBack }: WorkoutBuilderProps) {
  const [workoutText, setWorkoutText] = useState(`Warmup
- 10m 50%

Main set 4x
- 3m 90%
- 2m 50%

Cooldown
- 5m 40%`);

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
              placeholder="e.g., Sweet Spot Intervals"
            />
          </div>

          <div className="form-group">
            <label htmlFor="workout-text">Workout Definition</label>
            <textarea
              id="workout-text"
              value={workoutText}
              onChange={(e) => setWorkoutText(e.target.value)}
              rows={20}
              className="workout-textarea"
            />
          </div>

          <div className="builder-actions">
            <button className="btn btn-primary" type="button">
              Save to Library
            </button>
            <button className="btn" type="button">
              Export ZWO
            </button>
            <button className="btn" type="button">
              Preview
            </button>
          </div>
        </div>

        <div className="builder-preview">
          <h3>Preview</h3>
          <div className="preview-placeholder">
            Workout preview will appear here
          </div>

          <div className="syntax-help">
            <h4>Syntax Guide</h4>
            <ul>
              <li><code>- 5m 200w</code> — 5 min at 200 watts</li>
              <li><code>- 3m 85%</code> — 3 min at 85% FTP</li>
              <li><code>- 2m Z3</code> — 2 min at Zone 3</li>
              <li><code>- 4m 200w 90rpm</code> — With cadence target</li>
              <li><code>Main set 4x</code> — Repeat following blocks 4 times</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
