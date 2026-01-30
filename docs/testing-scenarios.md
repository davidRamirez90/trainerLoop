# Coach Suggestions Testing Guide

## Overview
This guide provides step-by-step testing scenarios to verify all coach suggestion features work correctly during real workouts.

## Prerequisites
- Trainer connected via BLE (ERG mode)
- Heart rate monitor connected (optional but recommended)
- Workout loaded with intervals
- Coach profile selected

---

## Test 1: Intensity Increase Suggestion

### Setup
**Workout:** 4x5min @ 90% FTP with 3min recovery
**Coach:** Any (preferably Frank Overton for clearer thresholds)

### Steps
1. Complete first interval at steady 85-90 RPM
2. Maintain consistent power (let ERG mode do the work)
3. Keep HR stable (drift < 4% from baseline)
4. Wait 45+ seconds into second interval

### Expected Result
- **Pending Suggestions section** appears at top of CoachPanel
- Shows: "⚡ Increase targets by 5%"
- **Rationale displayed:** "Power is stable and HR looks good..."
- **Impact preview:** "Power targets: +5% for remaining work intervals"
- Toast appears on Accept: "Intensity +5% applied"

### Verify
- [ ] Pending section pulses with animation
- [ ] Rationale text visible in green box
- [ ] Impact preview visible in blue box
- [ ] Accept/Reject buttons present
- [ ] Toast appears bottom-center on Accept
- [ ] Segment badge appears on chart (⚡ +5%)

---

## Test 2: Intensity Decrease Suggestion

### Setup
**Workout:** 3x8min @ 95% FTP (harder intervals)
**Coach:** Any

### Steps
1. Start first interval normally
2. After 2-3 minutes, **intentionally slow cadence to 60-70 RPM**
3. This simulates fatigue (ERG will increase resistance to maintain power)
4. Let HR rise significantly
5. Continue pedaling (don't stop!)

### Expected Result
- After 30 seconds of low cadence + elevated HR:
- Coach suggests: "⚡ Reduce targets by 5%"
- **Rationale:** "Power is below target with HR drift rising..."
- **Impact:** "Power targets: -5% for remaining work intervals"

### Verify
- [ ] Suggestion appears when adherence drops + HR rises
- [ ] Rationale explains why (fatigue indicators)
- [ ] Toast on Accept: "Intensity -5% applied"
- [ ] Interval panel shows override:
  - Original: ~~200-220W~~ → Adjusted: 190-209W (-5%)

---

## Test 3: Extend Recovery Suggestion

### Setup
**Workout:** 5x3min VO2max with 2min recovery
**Coach:** Any

### Steps
1. Complete first VO2 interval at maximum effort
2. During recovery, **keep moving but don't let HR drop much**
3. Simulate incomplete recovery (HR stays >80% max)
4. Continue to next interval

### Expected Result
- At start of recovery after hard interval:
- Suggests: "🕐 Extend recovery by 30s"
- **Rationale:** "HR still elevated from previous block..."
- **Impact:** "Recovery: +30s (30s → 60s total)"

### Verify
- [ ] Suggestion appears at recovery start
- [ ] Rationale mentions elevated HR
- [ ] Toast on Accept: "Recovery extended by 30s"
- [ ] Interval panel shows duration override:
  - Original: ~~2:00~~ → Adjusted: 2:30 (+30s)
- [ ] Workout chart shows +30s badge on recovery segment

---

## Test 4: Skip Intervals (Critical Modal)

### Setup
**Workout:** 6x3min threshold intervals
**Coach:** Must allow skip (Frank Overton, Chris Carmichael - NOT Javier Sola)

### Steps
1. **Interval 1:** Start strong but fade last minute
   - Slow cadence to 70 RPM
   - Let HR drift high (>7%)
2. When coach suggests "Reduce intensity" → Click **REJECT**
3. **Interval 2:** Repeat - struggle, high HR, variable cadence
4. When coach suggests "Reduce intensity" again → Click **REJECT**
5. **Interval 3:** Continue struggling

### Expected Result
- **Critical Modal appears** (full screen overlay)
- Shows coach personality message:
  - Frank: "Fatigue is building and power is dropping..."
  - Chris: "You've gotten the stimulus you need..."
- **Why this is suggested:**
  - Lists specific metrics (power adherence, HR drift)
  - Shows rejected suggestion count
- **What will happen:**
  - "Jump to cooldown phase immediately"
- **Buttons:** Personalized per coach
  - Frank: "Continue Sweet Spot" / "Cool Down Now"
  - Chris: "Continue Workout" / "Skip to Cooldown"

### Verify
- [ ] Modal blocks entire UI until decision
- [ ] Background dimmed/greyed
- [ ] All metrics displayed (adherence %, HR drift, cadence variance)
- [ ] Rationale shows coach's personality
- [ ] Button labels match coach voice
- [ ] Toast appears after decision
- [ ] Clock jumps to cooldown segment
- [ ] Skipped intervals marked on chart

---

## Test 5: Visual Feedback Verification

### Setup
Complete Tests 1-3 above to generate multiple overrides

### Verify Visual Elements

#### CoachPanel
- [ ] **Pending section** at top with pulsing icon
- [ ] Each suggestion shows: icon, message, rationale, impact
- [ ] Rationale in green box with "Why:" label
- [ ] Impact in blue box with "Impact:" label
- [ ] Past suggestions show in feed below (accepted/rejected status)

#### WorkoutChart
- [ ] **⚡ badges** on modified work intervals (top-right corner)
- [ ] Badge shows percentage (+5%, -3%, etc.)
- [ ] **+30s badges** on extended recovery segments
- [ ] Existing zone color tinting preserved
- [ ] Hover shows tooltip with original vs adjusted values

#### Interval Panel
When on modified interval:
- [ ] Shows "⚡ Target" row with original (strikethrough) → adjusted
- [ ] Shows percentage badge (+5%, -3%)
- [ ] Shows "🕐 Duration" row for recovery extensions
- [ ] Shows "+30s" badge

#### Toast Notifications
- [ ] Appear at bottom-center of screen
- [ ] Auto-dismiss after 4.5 seconds
- [ ] Green for success (intensity up/down applied)
- [ ] Blue for info (recovery extended)
- [ ] Manual close button works

---

## Test 6: Coach Personality Verification

### Test Each Coach

#### Frank Overton (FasCat)
- **Voice:** Motivational, concise
- **Rationale style:** Sweet Spot focused, CTL mentions
- **Test:** Should see "Sweet Spot" and "CTL" in rationale text

#### Javier Sola (UAE Team Emirates)
- **Voice:** Professional, educational
- **Rationale style:** Basics-focused, adaptation-focused
- **Test:** Should see phrases like "The body adapts..." and "Quality over complexity..."
- **Note:** This coach NEVER suggests skipping intervals

#### Chris Carmichael (CTS)
- **Voice:** Professional, educational
- **Rationale style:** Human-first, fitness-focused
- **Test:** Should see phrases like "Human-first coaching..." and "Recovery is where adaptation happens..."

#### Aldo Sassi (Italian Method)
- **Voice:** Firm, concise
- **Rationale style:** Data-driven, numbers-focused
- **Test:** Should see metrics emphasized and data analysis references

#### Michele Ferrari
- **Voice:** Direct, authoritative
- **Rationale style:** Threshold-focused, method-focused
- **Test:** Should see references to threshold zones and "the method"

### Verify
- [ ] Each coach uses their own rationale text
- [ ] Skip modal buttons match coach personality
- [ ] Messages feel distinct per coach

---

## Test 7: Edge Cases

### Empty State
1. Load app without workout
2. **Verify:** No static "Import a workout" banner appears
3. CoachPanel shows "Coach updates will appear here"

### No Suggestions
1. Complete workout perfectly with stable metrics
2. **Verify:** No pending suggestions appear
3. Coach feed empty or shows completion message only

### Multiple Pending Suggestions
1. Trigger multiple suggestions (rare but possible)
2. **Verify:** Shows "PENDING SUGGESTIONS (N)" with count
3. Each suggestion has own Accept/Reject buttons
4. Can accept/reject individually

### Rejection Cooldown
1. Reject intensity increase suggestion
2. Try to trigger another immediately
3. **Verify:** No new suggestion until cooldown expires (4-5 minutes)

---

## Troubleshooting

### Suggestion Not Appearing
- Check that enough time elapsed (5-7 minutes depending on coach)
- Verify HR monitor connected for drift detection
- Check that previous suggestion cooldown expired
- Verify not in recovery phase (no intensity adjustments in recovery)

### Toast Not Showing
- Check browser console for errors
- Verify build completed successfully
- Ensure ToastNotification component rendered

### Badges Not Appearing on Chart
- Check that intensityOverrides state populated
- Verify WorkoutChart receives props correctly
- Check browser console for canvas errors

### Rationale Not Showing
- Verify coach profile has rationale fields
- Check that suggestions have rationale property
- Verify CoachPanel renders rationale section

---

## Success Criteria

All tests pass when:
- ✅ Suggestions appear based on actual metrics (not random)
- ✅ Rationale text matches selected coach's personality
- ✅ Impact preview shows clear before/after
- ✅ Accepting applies change immediately with visual feedback
- ✅ Toasts confirm actions taken
- ✅ Workout chart shows badges on affected segments
- ✅ Interval panel displays original vs adjusted
- ✅ Skip intervals shows prominent modal with full context
- ✅ No static/hardcoded messages appear
- ✅ No "encouragement" clutter during workout

---

## Recording Results

For each test, record:
1. **Date/Time:** When tested
2. **Coach Used:** Which profile
3. **Workout:** File name/type
4. **Result:** PASS / FAIL / PARTIAL
5. **Notes:** Any issues or observations
6. **Screenshots:** If UI elements incorrect

---

**Happy Testing!**
