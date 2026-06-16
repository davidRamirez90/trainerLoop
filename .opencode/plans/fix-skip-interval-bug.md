# Fix Skip Interval Bug - Implementation Plan

## Problem Summary
When clicking "Skip Interval", the workout resets to the beginning instead of jumping to the next segment. This happens because:
1. The current implementation shortens the segment duration via `segmentShortenings`
2. This changes the segment durations array
3. The time-to-segment mapping becomes invalid
4. `activeSec` now maps to the wrong segment or past the end

## Root Cause
The `handleSkipSegment` function modifies segment durations without accounting for the current time position. When segments change, the mapping from `activeSec` to segment index breaks.

## Solution
Change "Skip Interval" behavior from "shortening" to "seeking" - jump to the end of the current segment (next segment boundary).

## Implementation Steps

### Step 1: Fix handleSkipSegment Function
**File**: `src/App.tsx`
**Location**: Lines 726-753 (handleSkipSegment useCallback)

**Current Code**:
```typescript
const handleSkipSegment = useCallback(() => {
  if (!hasPlan || !segment || !isRunning) {
    return;
  }
  if (segment.phase === 'recovery') {
    return;
  }
  const remainingSec = endSec - activeSec;
  if (remainingSec <= 5) {
    return;
  }
  const shortening = remainingSec - 5;
  setSegmentShortenings((prev) => ({
    ...prev,
    [segment.id]: (prev[segment.id] ?? 0) + shortening,
  }));
  success(`Interval shortened to 5s remaining`);
}, [hasPlan, segment, isRunning, endSec, activeSec, success]);
```

**New Code**:
```typescript
const handleSkipSegment = useCallback(() => {
  if (!hasPlan || !segment || !isRunning) {
    return;
  }
  // Skip is disabled for recovery phases
  if (segment.phase === 'recovery') {
    return;
  }
  const remainingSec = endSec - activeSec;
  if (remainingSec <= 5) {
    return;
  }
  // Seek to the end of the current segment to skip to the next one
  clock.seek(endSec);
  success(`Skipped to next interval`);
}, [hasPlan, segment, isRunning, endSec, activeSec, clock, success]);
```

**Changes**:
- Remove `setSegmentShortenings` call and shortening logic
- Add `clock.seek(endSec)` to jump to end of current segment
- Update success message to "Skipped to next interval"
- Add `clock` to dependency array

### Step 2: Remove Debug Logging
**File**: `src/App.tsx`
**Location**: Lines 678-724 (handleCoachAction skip_remaining_on_intervals)

Remove all `console.log` statements added during debugging:
- Lines 696-710: Debug logs in skip_remaining_on_intervals handler

**File**: `src/hooks/useWorkoutClock.ts`
**Location**: Lines 155-177 (seek function)

Remove all `console.log` statements:
- Line 167: 'DEBUG useWorkoutClock.seek called with'
- Line 168: 'totalDurationSec'
- Line 170: 'clamped value'
- Line 176: 'Workout complete!'
- Line 182: 'Seek complete. isRunning'

**File**: `src/App.tsx`
**Location**: Lines 2834-2850 (CriticalSuggestionModal onAccept/onReject)

Remove debug logs:
- Lines 2852-2855: Debug logs in onAccept
- Line 2858: Debug log in onReject

### Step 3: Verify Coach Suggestion Skip
**File**: `src/App.tsx`
**Location**: Lines 678-712 (skip_remaining_on_intervals handler)

The coach suggestion skip (`skip_remaining_on_intervals`) already uses `clock.seek()` correctly:
- Line 710: `clock.seek(startAt)` where startAt is calculated from cooldown position

No changes needed for coach suggestions.

### Step 4: Update Tests
**File**: `src/__tests__/workoutImport.test.ts`

The test "parses complete workout with flat-power cooldown exported from app" was added during debugging. Verify this test passes and consider keeping it as regression test.

### Step 5: Verify No Regressions
Run full test suite:
```bash
npm test
```

### Step 6: Build Verification
Build the app to ensure no TypeScript errors:
```bash
npm run build
```

### Step 7: Manual Testing Checklist
1. Load a workout with multiple segments
2. Start the workout
3. Click "Skip Interval" button during warmup
4. Verify it jumps to the next segment (not reset to start)
5. Click "Skip Interval" during a work interval
6. Verify it jumps to the recovery/cooldown
7. Test coach suggestion skip (if coach profile suggests skipping)
8. Verify cooldown detection works correctly

## Expected Behavior After Fix
- Clicking "Skip Interval" jumps to the next segment boundary
- No more resetting to start of workout
- Coach suggestion skip continues to work as before
- Smooth transition between segments

## Code Quality Notes
- Keep the existing early return checks (hasPlan, segment, isRunning, recovery phase)
- Maintain the 5-second minimum threshold
- Use existing `clock.seek()` API
- Follow existing code style and patterns

## Risk Assessment
**Low Risk** - The fix is straightforward:
- Replaces complex shortening logic with simple seek
- Uses existing, tested `clock.seek()` function
- Maintains all existing guard conditions
- Behavior is more intuitive (skip = jump forward)

## Alternative Considered
Instead of seeking to `endSec`, could seek to `endSec - 5` to leave 5 seconds as originally intended. However, seeking to the exact boundary is cleaner and more predictable.
