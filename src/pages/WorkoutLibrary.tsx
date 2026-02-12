interface WorkoutLibraryProps {
  onBack: () => void;
}

export function WorkoutLibrary({ onBack }: WorkoutLibraryProps) {
  return (
    <div className="page workout-library-page">
      <header className="page-header">
        <button className="back-button" onClick={onBack} type="button">
          ← Back
        </button>
        <h1>Workout Library</h1>
      </header>

      <div className="library-toolbar">
        <button className="btn btn-primary" type="button">
          + New Workout
        </button>
        <input
          type="text"
          placeholder="Search workouts..."
          className="search-input"
        />
      </div>

      <div className="workout-list">
        <div className="workout-list-empty">
          <p>No custom workouts yet.</p>
          <p>Create your first workout using the Workout Builder!</p>
        </div>
      </div>
    </div>
  );
}
