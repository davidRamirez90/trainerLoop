# Debug Analysis: Skip Interval Bug

## Current Behavior
1. User clicks "Skip Interval" button
2. `handleSkipSegment` adds shortening to `segmentShortenings[segment.id]`
3. `adjustedSegments` recalculates with new (shorter) durations via useMemo
4. `getTargetRangeAtTime(targetSegments, activeSec)` is called
5. `getSegmentAtTime` loops through NEW segments with OLD activeSec
6. Since segments are now shorter, activeSec maps to wrong segment or past end
7. This causes the workout to appear to "reset" or jump to wrong segment

## Root Cause
The skip logic modifies segment durations without accounting for the current position in time. When segment durations change, the mapping from `activeSec` to segment index becomes invalid.

## Fix Options

### Option A: Seek to end of current segment after shortening
After applying the shortening, immediately seek to the end of the current segment (or near it). This is the most intuitive behavior - "Skip Interval" should jump to the next segment.

Pros:
- Intuitive UX - skip means jump forward
- Simple implementation
- Maintains time continuity

Cons:
- Changes existing behavior (currently just shortens)

### Option B: Recalculate activeSec proportionally
When segments change, scale activeSec to maintain the same relative position.

Pros:
- Preserves shortening behavior
- Maintains relative position

Cons:
- Complex math
- May still have edge cases
- Users might not understand why position changed

### Option C: Track position independently of segment durations
Store "segmentIndex" and "elapsedInSegment" separately from segment durations.

Pros:
- Robust against segment changes
- Clean separation of concerns

Cons:
- Significant refactoring
- Risk of breaking other features

## Recommendation: Option A
"Skip Interval" should seek to the end of the current segment (or next segment boundary). This is what users expect when clicking "skip".

## Implementation Plan
1. Add debug logging to confirm the issue
2. Modify handleSkipSegment to seek to end of current segment
3. Update tests if needed
4. Remove debug logging
5. Test the fix
