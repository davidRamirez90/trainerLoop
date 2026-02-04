# Coach Suggestions Enhancement - Implementation Plan

**Status:** Completed  
**Completed:** 2026-01-30  
**Last Updated:** 2026-01-30  
**Estimated Total Time:** 5-6 hours development + 1 hour testing

---

## Overview

Transform coach suggestions from placeholder messages into a powerful, actionable workout intervention system with clear visual feedback, impact preview, and coach personality integration.

---

## Current Issues

1. **Static Coach Banner** - Shows "Import a workout" or "Hold steady" messages BEFORE workout starts (hardcoded UI, not from coach engine)
2. **Encouragement Events** - Fires every 5-8 minutes saying "Nice work", "Keep it up" (non-actionable clutter)
3. **Hardcoded Rationale** - "Power is below target..." doesn't use coach's voice/style
4. **Missing UX Feedback** - No preview of impact before accepting, no confirmation after, no highlighting of affected segments
5. **No Original vs Adjusted** - User can't see what changed in interval panel

---

## Architecture

### Coach Engine (Already Working ✓)
- Uses profile-based thresholds from `profile.rules`
- Each coach has unique detection thresholds:
  - Default: 5min before suggestions, 4min cooldown, 5% intensity steps
  - Javier Sola: 7min before suggestions, 5min cooldown, 3% intensity steps
  - Frank Overton: Sweet Spot focus, 5min/4min/5%

### Suggestion Actions
1. `adjust_intensity_up` - Increase power targets
2. `adjust_intensity_down` - Decrease power targets  
3. `extend_recovery` - Add time to recovery segment
4. `skip_remaining_on_intervals` - Jump to cooldown (critical decision)

---

## Implementation Phases

### Phase 1: Foundation (45 min) 🔄 IN PROGRESS
**Goal:** Remove non-actionable content, clean foundation

**Changes:**
1. **Remove static coach banner** (`src/App.tsx` lines 806-827)
   - Delete `coachMessage` constant
   - Delete `showCoachBanner` state and rendering
   
2. **Remove encouragement system** (`src/hooks/useCoachEngine.ts` lines 512-556)
   - Delete useEffect that fires encouragement events
   - Keep only actionable suggestions

3. **Add ERG mode awareness** to coach engine
   - Adjust reduce condition for ERG mode (power won't drop, use cadence + HR instead)

**Files Modified:**
- `src/App.tsx`
- `src/hooks/useCoachEngine.ts`

---

### Phase 2: Coach Personality Integration (60 min) ⏳
**Goal:** All messages use coach's voice and messaging style

**Changes:**
1. **Create CriticalSuggestionModal.tsx** (`src/components/CriticalSuggestionModal.tsx`)
   - Prominent modal for `skip_remaining_on_intervals` only
   - Uses `profile.messages.suggestions.skip_remaining_on_intervals` for question
   - Dynamic rationale building from metrics
   - Personalized button text per coach
   
   **Examples:**
   - Frank Overton: "Fatigue is building and power is dropping. Skip remaining intervals and cool down to fight another day?"
   - Javier Sola: "You've gotten the stimulus. Terminate properly and focus on recovery for tomorrow."

2. **Update rationale messages** in useCoachEngine.ts
   - Instead of hardcoded "Power is below target...", pull from profile
   - Add `rationale` field to profile.messages.suggestions in CoachProfile type

**Files Created:**
- `src/components/CriticalSuggestionModal.tsx`

**Files Modified:**
- `src/hooks/useCoachEngine.ts`
- `src/types/coach.ts` (add rationale field)

---

### Phase 3: Impact Preview in CoachPanel (45 min) ⏳
**Goal:** Show before/after values and affected segments

**Changes:**
1. **Enhanced suggestion display** in CoachPanel.tsx
   - Show both percentage AND wattage: "200-220W → 210-231W (+5%)"
   - Show affected segment count: "Affects 3 remaining intervals"
   - Show rationale prominently: "Rationale: Power and HR are steady..."

2. **New helper functions:**
   - `calculateImpactPreview()` - compute before/after wattage
   - `getAffectedSegmentCount()` - count remaining intervals affected
   - `getSuggestionIcon()` - return icon per type (⚡, 🕐, ✕)

**Visual Layout:**
```
┌─ SUGGESTION ─────────────────┐
│ ⚡ Increase targets by 5%?    │
│                              │
│ Impact: 200-220W → 210-231W  │
│ Affects: 3 remaining intervals│
│                              │
│ Rationale: "Power and HR are │
│  steady; you can push more." │
│                              │
│ [Accept] [Reject]            │
└──────────────────────────────┘
```

**Files Modified:**
- `src/components/CoachPanel.tsx`

---

### Phase 4: Toast Notification System (45 min) ⏳
**Goal:** Clear confirmation feedback after accepting/rejecting

**Changes:**
1. **Create ToastNotification.tsx** (`src/components/ToastNotification.tsx`)
   - Position: bottom-center
   - Duration: 4-5 seconds
   - Auto-dismiss
   - Different colors per type:
     - Intensity up: Green (#4CAF50)
     - Intensity down: Orange (#FF9800)
     - Recovery extend: Blue (#2196F3)
     - Skip intervals: Purple/Red (#9C27B0)

2. **Toast Messages** (using coach personality):
   - Accept intensity: "✓ Intensity +5% applied (210-231W for 3 intervals)"
   - Accept recovery: "✓ Recovery extended +30s (120s total)"
   - Reject: "✗ Suggestion rejected"
   - Skip: "⏭ Jumped to cooldown - 4 intervals skipped"

3. **Integration:**
   - Add `ToastContainer` to App.tsx layout
   - Pass `showToast()` to CoachPanel

**Files Created:**
- `src/components/ToastNotification.tsx`

**Files Modified:**
- `src/App.tsx`

---

### Phase 5: Segment Badges in WorkoutChart (60 min) ⏳
**Goal:** Visual indication of which segments are modified

**Changes:**
1. **Pass override data** to WorkoutChart
   - New props: `intensityOverrides`, `recoveryExtensions`

2. **Badge rendering** in `drawTargetBands()`
   - Small icon in top-right of affected segments:
     - ⚡ (intensity/power changes)
     - 🕐+30s (duration/recovery extension)
     - ✕ (skipped/cancelled intervals)
   - Keep existing color tinting for intensity zones

3. **Tooltip on hover:**
   - "Original: 200W | Adjusted: 210W (+5%)"
   - "Extended by 30s (90s → 120s)"

**Files Modified:**
- `src/components/WorkoutChart.tsx`
- `src/App.tsx` (pass new props)

---

### Phase 6: Interval Panel Override Display (45 min) ⏳
**Goal:** Show original vs adjusted targets in current interval card

**Changes:**
1. **Modified interval card** (App.tsx lines 1829-1848)
   When current segment has overrides:
   ```
   ┌─ INTERVAL ──────────────┐
   │ WORK           3/8      │
   │ 02:34 remaining         │
   ├─────────────────────────┤
   │ ⚡ Original  200-220W   │ ← NEW
   │ ⚡ Adjusted  210-231W   │ ← NEW
   │    (+5% via coach)      │ ← NEW
   ├─────────────────────────┤
   │ Elapsed     01:26       │
   │ Workout Rem 12:45       │
   └─────────────────────────┘
   ```

2. **Helper functions:**
   - `getOriginalTargetForSegment()` - lookup base segment
   - `getCurrentOverrideInfo()` - get active override info

**Files Modified:**
- `src/App.tsx`

---

### Phase 7: CSS Styling (30 min) ⏳
**Goal:** Consistent styling for all new components

**Changes to App.css:**
1. Toast notification styles (bottom-center, variants)
2. Critical modal overlay (backdrop, centered modal)
3. Enhanced suggestion cards (impact preview layout)
4. Segment badges (small icon positioning)
5. Interval override display (original/adjusted layout)

**Files Modified:**
- `src/App.css`

---

### Phase 8: Unit Tests & Testing (60 min) ⏳
**Goal:** Verify functionality and provide testing scenarios

**Unit Tests:**
1. `useCoachEngine.test.ts` - Suggestion triggers, cooldown logic, ERG mode
2. `CoachPanel.test.tsx` - Impact preview rendering, accept/reject
3. `CriticalSuggestionModal.test.tsx` - Modal rendering, coach personality
4. `WorkoutChart.test.tsx` - Badge rendering, tooltips
5. `sessionStorage.test.ts` - Override persistence

**Real Bike Testing Scenarios:**

#### Scenario 1: Intensity Increase
**Workout:** 4x5min @ 90% FTP
1. Complete first interval steady (85-90 RPM, stable HR)
2. Wait 45+ seconds into second interval
3. **Expected:** Coach suggests +3-5% intensity

#### Scenario 2: Intensity Decrease  
**Workout:** 3x8min @ 95% FTP
1. Start strong, then slow cadence to 60-70 RPM after 2-3 min
2. Let HR rise significantly (drift > 7%)
3. **Expected:** After 30 seconds, coach suggests -3-5%

#### Scenario 3: Extend Recovery
**Workout:** 5x3min VO2max with 2min recovery
1. Complete first interval at max effort
2. Keep HR elevated during recovery
3. **Expected:** Coach suggests +30-45 seconds

#### Scenario 4: Skip Intervals
**Workout:** 6x3min threshold
1. Interval 1: Struggle last minute, high HR
2. Reject "reduce intensity" suggestion
3. Interval 2: Struggle again
4. Reject "reduce intensity" again
5. Interval 3: Continue struggling
6. **Expected:** Skip modal appears

**Test Checklist:**
- [ ] Impact preview shows correct before/after
- [ ] Accepting applies change and shows toast
- [ ] Rejecting prevents similar suggestion
- [ ] Recovery extension works
- [ ] Skip modal appears with coach personality
- [ ] Segment badges appear on modified intervals
- [ ] Interval panel shows original vs adjusted

---

## ERG Mode Clarification

**Important:** In ERG mode, adherence won't drop below target because trainer forces power.

**Fatigue detection in ERG mode uses:**
- **Cadence drop** - User slows RPM to manage effort
- **HR drift** - Cardiovascular stress even at target power
- **Cadence variance** - Unstable pedaling indicates fatigue

**Revised reduce condition for ERG mode:**
- Ignore adherence threshold
- Focus on: `hrDrift >= intervene_threshold` OR `cadenceVariance >= intervene_threshold` OR `cadenceDrop >= threshold`

---

## Files Summary

### Files to Create:
- `src/components/CriticalSuggestionModal.tsx`
- `src/components/ToastNotification.tsx`

### Files to Modify:
- `src/App.tsx` - Remove banner, add interval override, integrate toast/modal
- `src/hooks/useCoachEngine.ts` - Remove encouragement, add ERG mode logic, profile-based rationale
- `src/components/CoachPanel.tsx` - Impact preview, rationale display
- `src/components/WorkoutChart.tsx` - Badge rendering
- `src/types/coach.ts` - Add rationale field to profile schema
- `src/App.css` - Styles for all new components

### Test Files:
- `src/__tests__/useCoachEngine.test.ts`
- `src/__tests__/CoachPanel.test.tsx`
- `src/__tests__/CriticalSuggestionModal.test.tsx`
- `src/__tests__/WorkoutChart.test.tsx`

---

## Progress Tracker

- [x] Phase 1: Foundation - Remove non-actionable content
- [x] Phase 2: Coach Personality Integration
- [x] Phase 3: Impact Preview in CoachPanel
- [x] Phase 4: Toast Notification System
- [x] Phase 5: Segment Badges in WorkoutChart
- [x] Phase 6: Interval Panel Override Display
- [x] Phase 7: CSS Styling
- [x] Phase 8: Unit Tests & Testing

---

## Notes

- Keep existing color tinting for intensity zones in WorkoutChart
- All messages must use coach's `voice.tone` and `voice.style`
- Skip modal is the only critical suggestion with prominent display
- Toast duration: 4-5 seconds (user preference)
- Badge icons: ⚡ intensity, 🕐 duration, ✕ skipped
