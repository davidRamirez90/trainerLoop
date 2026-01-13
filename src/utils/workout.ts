import type { WorkoutSegment } from '../data/workout';

export const getTotalDurationSec = (segments: WorkoutSegment[]) =>
  segments.reduce((total, segment) => total + segment.durationSec, 0);

export const getSegmentAtTime = (segments: WorkoutSegment[], elapsedSec: number) => {
  let cursor = 0;
  for (let index = 0; index < segments.length; index += 1) {
    const segment = segments[index];
    const startSec = cursor;
    const endSec = cursor + segment.durationSec;
    if (elapsedSec < endSec) {
      return { segment, index, startSec, endSec };
    }
    cursor = endSec;
  }

  const lastIndex = Math.max(segments.length - 1, 0);
  const lastSegment = segments[lastIndex];
  return {
    segment: lastSegment,
    index: lastIndex,
    startSec: Math.max(cursor - lastSegment.durationSec, 0),
    endSec: cursor,
  };
};
