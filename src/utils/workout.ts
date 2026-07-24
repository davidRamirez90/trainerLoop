import type { TargetRange, WorkoutSegment } from '../data/workout';

export const getTotalDurationSec = (segments: WorkoutSegment[]) =>
  segments.reduce((total, segment) => total + segment.durationSec, 0);

export const getSegmentAtTime = (segments: WorkoutSegment[], elapsedSec: number) => {
  if (segments.length === 0) {
    return { segment: undefined, index: 0, startSec: 0, endSec: 0 };
  }

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

  const lastIndex = segments.length - 1;
  const lastSegment = segments[lastIndex];
  return {
    segment: lastSegment,
    index: lastIndex,
    startSec: Math.max(cursor - lastSegment.durationSec, 0),
    endSec: cursor,
  };
};

const lerp = (start: number, end: number, ratio: number) =>
  start + (end - start) * ratio;

export const getSegmentTargetRange = (
  segment: WorkoutSegment,
  elapsedInSegmentSec: number
): TargetRange => {
  if (!segment.rampToRange || segment.durationSec === 0) {
    return segment.targetRange;
  }

  const ratio = Math.min(Math.max(elapsedInSegmentSec / segment.durationSec, 0), 1);
  return {
    low: lerp(segment.targetRange.low, segment.rampToRange.low, ratio),
    high: lerp(segment.targetRange.high, segment.rampToRange.high, ratio),
  };
};

export const getTargetRangeAtTime = (segments: WorkoutSegment[], elapsedSec: number) => {
  if (segments.length === 0) {
    return {
      segment: undefined,
      index: 0,
      startSec: 0,
      endSec: 0,
      elapsedInSegmentSec: 0,
      targetRange: { low: 0, high: 0 },
    };
  }
  const { segment, index, startSec, endSec } = getSegmentAtTime(
    segments,
    elapsedSec
  );
  if (!segment) {
    return {
      segment: undefined,
      index: 0,
      startSec: 0,
      endSec: 0,
      elapsedInSegmentSec: 0,
      targetRange: { low: 0, high: 0 },
    };
  }
  const elapsedInSegmentSec = Math.max(
    0,
    Math.min(elapsedSec - startSec, segment.durationSec)
  );
  const targetRange = getSegmentTargetRange(segment, elapsedInSegmentSec);

  return {
    segment,
    index,
    startSec,
    endSec,
    elapsedInSegmentSec,
    targetRange,
  };
};
