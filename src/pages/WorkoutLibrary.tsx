import { useState, useMemo, useCallback } from 'react';
import type { WorkoutPlan } from '../data/workout';
import type { SavedWorkout } from '../utils/workoutLibrary';
import {
  getWorkoutLibrary,
  deleteWorkout,
  duplicateWorkout,
  sortWorkouts,
  searchWorkouts,
  exportWorkoutToJSON,
  importWorkoutFromJSON,
} from '../utils/workoutLibrary';
import { downloadZWOFile } from '../utils/zwoExport';
import { useToast } from '../hooks/useToast';
import { WorkoutChart } from '../components/WorkoutChart';
import { useTheme } from '../hooks/useTheme';

interface WorkoutLibraryProps {
  onBack: () => void;
  onLoadWorkout: (plan: WorkoutPlan) => void;
}

type SortOption = 'name' | 'date' | 'duration';

export function WorkoutLibrary({ onBack, onLoadWorkout }: WorkoutLibraryProps) {
  const { theme } = useTheme();
  const { success, error } = useToast();
  const [workouts, setWorkouts] = useState<SavedWorkout[]>(() => getWorkoutLibrary());
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('date');
  const [selectedWorkout, setSelectedWorkout] = useState<SavedWorkout | null>(null);
  const [showImportDialog, setShowImportDialog] = useState(false);
  const [importText, setImportText] = useState('');

  // Filter and sort workouts
  const filteredWorkouts = useMemo(() => {
    let result = workouts;
    
    if (searchQuery.trim()) {
      result = searchWorkouts(searchQuery);
    }
    
    return sortWorkouts(result, sortBy);
  }, [workouts, searchQuery, sortBy]);

  // Refresh workouts list
  const refreshWorkouts = useCallback(() => {
    setWorkouts(getWorkoutLibrary());
  }, []);

  const handleDelete = useCallback((id: string) => {
    if (window.confirm('Are you sure you want to delete this workout?')) {
      if (deleteWorkout(id)) {
        refreshWorkouts();
        if (selectedWorkout?.id === id) {
          setSelectedWorkout(null);
        }
        success('Workout deleted');
      } else {
        error('Failed to delete workout');
      }
    }
  }, [selectedWorkout, refreshWorkouts, success, error]);

  const handleDuplicate = useCallback((id: string) => {
    const duplicated = duplicateWorkout(id);
    if (duplicated) {
      refreshWorkouts();
      success('Workout duplicated');
    } else {
      error('Failed to duplicate workout');
    }
  }, [refreshWorkouts, success, error]);

  const handleExportJSON = useCallback((workout: SavedWorkout) => {
    try {
      const json = exportWorkoutToJSON(workout);
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${workout.name.replace(/\s+/g, '_')}.json`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);
      
      success('Workout exported');
    } catch {
      error('Failed to export workout');
    }
  }, [success, error]);

  const handleExportZWO = useCallback((workout: SavedWorkout) => {
    try {
      downloadZWOFile(workout.plan, workout.name);
      success('ZWO file downloaded');
    } catch {
      error('Failed to export ZWO file');
    }
  }, [success, error]);

  const handleImport = useCallback(() => {
    if (!importText.trim()) {
      error('Please paste workout JSON');
      return;
    }
    
    const imported = importWorkoutFromJSON(importText);
    if (imported) {
      refreshWorkouts();
      setShowImportDialog(false);
      setImportText('');
      success('Workout imported successfully');
    } else {
      error('Failed to import workout. Invalid format.');
    }
  }, [importText, refreshWorkouts, success, error]);

  const handleLoad = useCallback((workout: SavedWorkout) => {
    onLoadWorkout(workout.plan);
  }, [onLoadWorkout]);

  const formatDuration = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${mins}m`;
    }
    return `${mins}m`;
  };

  const formatDate = (timestamp: number): string => {
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  return (
    <div className="page workout-library-page">
      <header className="page-header">
        <button className="back-button" onClick={onBack} type="button">
          ← Back
        </button>
        <h1>Workout Library</h1>
      </header>

      <div className="library-toolbar">
        <div className="library-search">
          <input
            type="text"
            placeholder="Search workouts..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="library-search-input"
          />
        </div>
        <div className="library-filters">
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as SortOption)}
            className="library-sort-select"
          >
            <option value="date">Sort by Date</option>
            <option value="name">Sort by Name</option>
            <option value="duration">Sort by Duration</option>
          </select>
          <button
            className="btn"
            type="button"
            onClick={() => setShowImportDialog(true)}
          >
            Import
          </button>
        </div>
      </div>

      <div className="library-content">
        <div className="library-list">
          {filteredWorkouts.length === 0 ? (
            <div className="library-empty">
              <p>No workouts found</p>
              {workouts.length === 0 ? (
                <p className="library-empty-hint">
                  Create your first workout using the Workout Builder!
                </p>
              ) : (
                <p className="library-empty-hint">
                  Try adjusting your search
                </p>
              )}
            </div>
          ) : (
            filteredWorkouts.map((workout) => {
              const totalDuration = workout.plan.segments.reduce(
                (sum, seg) => sum + seg.durationSec,
                0
              );
              const workDuration = workout.plan.segments
                .filter((seg) => seg.isWork)
                .reduce((sum, seg) => sum + seg.durationSec, 0);
              const intervalCount = workout.plan.segments.filter((seg) => seg.isWork).length;

              return (
                <div
                  key={workout.id}
                  className={`library-card ${selectedWorkout?.id === workout.id ? 'selected' : ''}`}
                  onClick={() => setSelectedWorkout(workout)}
                  role="button"
                  tabIndex={0}
                >
                  <div className="library-card-header">
                    <h3 className="library-card-title">{workout.name}</h3>
                    <span className="library-card-date">
                      {formatDate(workout.updatedAt)}
                    </span>
                  </div>
                  {workout.description && (
                    <p className="library-card-description">{workout.description}</p>
                  )}
                  <div className="library-card-stats">
                    <span className="library-stat">
                      {formatDuration(totalDuration)}
                    </span>
                    <span className="library-stat">
                      {intervalCount} intervals
                    </span>
                    <span className="library-stat">
                      {formatDuration(workDuration)} work
                    </span>
                    <span className="library-stat">
                      FTP: {workout.ftpWatts}W
                    </span>
                  </div>
                  <div className="library-card-actions">
                    <button
                      className="library-action-btn primary"
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleLoad(workout);
                      }}
                    >
                      Load
                    </button>
                    <button
                      className="library-action-btn"
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDuplicate(workout.id);
                      }}
                    >
                      Duplicate
                    </button>
                    <button
                      className="library-action-btn"
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleExportZWO(workout);
                      }}
                    >
                      ZWO
                    </button>
                    <button
                      className="library-action-btn"
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleExportJSON(workout);
                      }}
                    >
                      JSON
                    </button>
                    <button
                      className="library-action-btn danger"
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDelete(workout.id);
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {selectedWorkout && (
          <div className="library-preview">
            <div className="library-preview-header">
              <h3>{selectedWorkout.name}</h3>
              <button
                className="library-action-btn primary"
                type="button"
                onClick={() => handleLoad(selectedWorkout)}
              >
                Load Workout
              </button>
            </div>
            <div className="library-chart-container">
              <WorkoutChart
                segments={selectedWorkout.plan.segments}
                samples={[]}
                gaps={[]}
                elapsedSec={0}
                ftpWatts={selectedWorkout.ftpWatts}
                hrSensorConnected={false}
                showPower3s={false}
                intensityOverrides={[]}
                recoveryExtensions={{}}
                thresholdHr={null}
                currentHr={null}
                theme={theme}
              />
            </div>
          </div>
        )}
      </div>

      {/* Import Dialog */}
      {showImportDialog && (
        <div
          className="modal-scrim"
          onClick={() => setShowImportDialog(false)}
          role="presentation"
        >
          <div
            className="modal"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="import-dialog-title"
          >
            <div className="modal-header">
              <div>
                <div className="modal-title" id="import-dialog-title">
                  Import Workout
                </div>
                <div className="modal-subtitle">
                  Paste workout JSON to import
                </div>
              </div>
              <button
                className="modal-close"
                type="button"
                aria-label="Close"
                onClick={() => setShowImportDialog(false)}
              >
                ×
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label htmlFor="import-json">Workout JSON</label>
                <textarea
                  id="import-json"
                  value={importText}
                  onChange={(e) => setImportText(e.target.value)}
                  rows={10}
                  className="workout-textarea"
                  placeholder="Paste exported workout JSON here..."
                />
              </div>
            </div>
            <div className="modal-footer">
              <button
                className="session-button"
                type="button"
                onClick={() => setShowImportDialog(false)}
              >
                Cancel
              </button>
              <button
                className="session-button primary"
                type="button"
                onClick={handleImport}
              >
                Import
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
