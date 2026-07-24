interface WorkoutHistoryProps {
  onBack: () => void;
}

export function WorkoutHistory({ onBack }: WorkoutHistoryProps) {
  return (
    <div className="page history-page">
      <header className="page-header">
        <button className="back-button" onClick={onBack} type="button">
          ← Back
        </button>
        <h1>Workout History</h1>
      </header>

      <div className="history-content">
        <div className="history-stats">
          <div className="stat-card">
            <span className="stat-value">0</span>
            <span className="stat-label">Total Workouts</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">0h</span>
            <span className="stat-label">Total Time</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">0 TSS</span>
            <span className="stat-label">Total Load</span>
          </div>
        </div>

        <div className="history-list">
          <p className="empty-message">No workout history yet.</p>
        </div>
      </div>
    </div>
  );
}
