import type { WorkoutPlan } from '../data/workout';

export interface SavedWorkout {
  id: string;
  name: string;
  description?: string;
  createdAt: number;
  updatedAt: number;
  ftpWatts: number;
  plan: WorkoutPlan;
}

const WORKOUT_LIBRARY_KEY = 'trainerLoop.workoutLibrary.v1';

export function getWorkoutLibrary(): SavedWorkout[] {
  if (typeof window === 'undefined') {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(WORKOUT_LIBRARY_KEY);
    if (!raw) {
      return [];
    }
    const workouts = JSON.parse(raw) as SavedWorkout[];
    // Validate and return
    return workouts.filter(w => w && w.id && w.plan);
  } catch {
    return [];
  }
}

export function saveWorkoutLibrary(workouts: SavedWorkout[]): void {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(WORKOUT_LIBRARY_KEY, JSON.stringify(workouts));
  } catch {
    // Ignore storage errors
  }
}

export function addWorkout(workout: Omit<SavedWorkout, 'id' | 'createdAt' | 'updatedAt'>): SavedWorkout {
  const newWorkout: SavedWorkout = {
    ...workout,
    id: `workout-${Date.now().toString(36)}-${Math.random().toString(36).substr(2, 9)}`,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  
  const library = getWorkoutLibrary();
  library.push(newWorkout);
  saveWorkoutLibrary(library);
  
  return newWorkout;
}

export function updateWorkout(id: string, updates: Partial<Omit<SavedWorkout, 'id' | 'createdAt'>>): SavedWorkout | null {
  const library = getWorkoutLibrary();
  const index = library.findIndex(w => w.id === id);
  
  if (index === -1) {
    return null;
  }
  
  library[index] = {
    ...library[index],
    ...updates,
    updatedAt: Date.now(),
  };
  
  saveWorkoutLibrary(library);
  return library[index];
}

export function deleteWorkout(id: string): boolean {
  const library = getWorkoutLibrary();
  const filtered = library.filter(w => w.id !== id);
  
  if (filtered.length === library.length) {
    return false;
  }
  
  saveWorkoutLibrary(filtered);
  return true;
}

export function getWorkoutById(id: string): SavedWorkout | null {
  const library = getWorkoutLibrary();
  return library.find(w => w.id === id) || null;
}

export function duplicateWorkout(id: string): SavedWorkout | null {
  const workout = getWorkoutById(id);
  if (!workout) {
    return null;
  }
  
  const duplicated: SavedWorkout = {
    ...workout,
    id: `workout-${Date.now().toString(36)}-${Math.random().toString(36).substr(2, 9)}`,
    name: `${workout.name} (Copy)`,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  
  const library = getWorkoutLibrary();
  library.push(duplicated);
  saveWorkoutLibrary(library);
  
  return duplicated;
}

export function searchWorkouts(query: string): SavedWorkout[] {
  const library = getWorkoutLibrary();
  const lowerQuery = query.toLowerCase();
  
  return library.filter(w => 
    w.name.toLowerCase().includes(lowerQuery) ||
    (w.description?.toLowerCase().includes(lowerQuery))
  );
}

export function sortWorkouts(workouts: SavedWorkout[], sortBy: 'name' | 'date' | 'duration'): SavedWorkout[] {
  const sorted = [...workouts];
  
  switch (sortBy) {
    case 'name':
      return sorted.sort((a, b) => a.name.localeCompare(b.name));
    case 'date':
      return sorted.sort((a, b) => b.updatedAt - a.updatedAt);
    case 'duration': {
      const getDuration = (w: SavedWorkout) => 
        w.plan.segments.reduce((sum, seg) => sum + seg.durationSec, 0);
      return sorted.sort((a, b) => getDuration(b) - getDuration(a));
    }
    default:
      return sorted;
  }
}

export function exportWorkoutToJSON(workout: SavedWorkout): string {
  return JSON.stringify(workout, null, 2);
}

export function importWorkoutFromJSON(json: string): SavedWorkout | null {
  try {
    const data = JSON.parse(json) as SavedWorkout;
    // Validate required fields
    if (!data.name || !data.plan || !data.plan.segments) {
      return null;
    }
    
    // Create new workout with fresh ID
    return addWorkout({
      name: data.name,
      description: data.description,
      ftpWatts: data.ftpWatts,
      plan: data.plan,
    });
  } catch {
    return null;
  }
}
