# TrainerLoop — three Home directions

Review proposals · 11 October 2026 · production Home remains unchanged.

## Decision in one paragraph

**Focus** makes launching today's structured session the primary job. **Console** puts equipment and numerical preparation ahead of launch. **Journal** places the planned session beside the last completed ride, giving training a chronological context. These are different priorities and navigation models, not just alternative palettes. I recommend **Focus** for the stated goal of keeping only essential training functionality; choose Console if checking equipment and FTP is part of every ride, or Journal if reviewing training is as important as starting it.

## Deliverables and truthfulness

`index.html` is a self-contained comparison report, with all three full-size PNGs embedded, research, navigation diagrams, and this specification. `proposal-1-focus.png`, `proposal-2-console.png`, and `proposal-3-journal.png` are reproducible native Compose renders, not generated illustrations. They use Robolectric/Roborazzi, without an emulator. Source is in `android/app/src/debug/java/com/trainerloop/ui/designproposals/HomeProposals.kt`; screenshot fixtures are in `HomeProposalScreenshotTest.kt`. The composables are presentation-only concepts with inert actions. No production routing or services were changed; the report's diagrams describe proposed behavior, not an implemented interaction prototype.

All concepts share a deterministic review scenario: Alex Morgan; FTP 250 W; connected KICKR CORE and HR sensor (124 bpm in Console); today's intervals.icu plan “Threshold 3 × 10”, 51 minutes, three 10-minute work intervals at 235–250 W; a saved 30-minute Endurance ride on 9 October. These are synthetic fixtures, not evidence of a real account or hardware connection. The 51-minute total includes 10-minute warmup, two 3-minute recoveries, and 5-minute cooldown. Charts use proportional segment widths; warmup/cooldown are simplified blocks in these concepts and must become ramps using the actual segment model when implemented. Source attribution denotes the intended planned-workout scenario, not a sync performed during rendering.

The captures show a 411 × 891 dp app viewport with concept navigation included. Android status/navigation bars are excluded. Only the connected/planned state is rendered. Other states below are specification requirements, not rendered or verified implementations.

## Current product audit

Reviewed HomeScreen/HomeViewModel, the existing screenshot fixture, the documented screen inventory, and navigation behavior. Retained destinations are Home, Workouts, Preview, Player, Completion, History, Session Detail, Profile, and the Devices dialog. Supporting dialogs/sheets include import, Bluetooth permission, workout menu, player controls/stop, FTP acceptance/push, zones/About, and FIT sharing. The four removed areas—builder, standalone free ride, GPX route riding, live coaching—stay removed. No proposal adds a replacement entry point for them.

Home currently gives a large trainer/“choose workout” hero priority over a planned workout; rider FTP and weight occupy the header. Recent activity disappears when there is a plan. This makes the same rider choose between a general launch invitation and a specific session. My design judgment is to give the known session the strongest action, keep connection status compact, move infrequently changed athlete settings into Profile, and always keep the previous ride available.

Important behavior: today's planned Quick Start resolves/downloads the workout and opens an idle Player directly. Opening Player does **not** start recording; Play does. Library selection uses Preview before Player. Trainer absence does not block navigation. “Connected” means equipment connectivity, not physiological readiness. Current app data does not support a recovery score, adaptive workout advice, future training calendar, streak target, or fitness/fatigue model. None is displayed here. Historical TSS and IF use current FTP, so these concepts avoid putting historical stress figures on Home.

## Research: what established products teach us

Research checked on 11 October 2026. These are established products with documented workflows, not proof that their designs improve this app's retention. Findings below distinguish sourced behavior from our interpretation. This review used official product/support documentation and published design research; it did not include hands-on accounts or a controlled usability study of competitors.

| Reference and observed behavior | What it implies for TrainerLoop | What we deliberately leave out |
|---|---|---|
| [TrainerRoad TrainNow overview](https://support.trainerroad.com/hc/trainerroad-support/articles/360057075531-trainnow-overview) separates workout choice by duration/type and provides a Load Workout action. Its recommendations depend on its own training model. | Make the known workout and its time commitment easy to scan. Keep an explicit alternative-workout path. Console emphasizes duration and interval structure. | No “recommended for you” badge or AI suggestions: our app has no equivalent model. |
| [Wahoo SYSTM quick-start guide](https://support.wahoofitness.com/hc/en-us/articles/4405908390674-A-Quick-Start-Guide-to-the-SYSTM-App) separates Library, trainer/sensor setup, and interval targets in the player. | Equipment setup is a supporting task; actual effort controls belong in Player. Focus shrinks equipment status; Console deliberately gives it more prominence. | Video channels, subscriptions, yoga, strength, and media libraries would re-expand the user's requested narrow scope. |
| [Wahoo daily recommended workouts](https://support.wahoofitness.com/hc/en-us/articles/22483546617618-Daily-Recommended-Workouts-for-Wahoo-app-SYSTM) places daily suggestions on Today/Home, informed by history and upcoming workouts. | A daily workout can be the anchor of Home. For us the anchor is the imported plan, clearly attributed to intervals.icu. | Recommendation engine and future-calendar awareness are different product capabilities, not a visual restyling. |
| [TrainingPeaks athlete Home](https://help.trainingpeaks.com/hc/en-us/articles/218339768-Home-view-for-Athletes) combines upcoming work, goals, zones, and progress. | Home can connect what is planned with what was completed. Journal adopts this temporal context using today's plan and the existing latest session only. | Events, tomorrow's workouts, and goal countdowns lack current data support. |
| [TrainingPeaks performance insights](https://help.trainingpeaks.com/hc/en-us/articles/41077784209165-Athlete-Home-Performance-Insights) exposes fitness/form/fatigue trends and subjective effort insights. | History merits its own destination and detailed analysis. Home should not become a wall of unexplained physiological numbers. | No CTL/ATL/form graphs or RPE analysis; our measurements and persistence do not provide their prerequisites. |
| [Polar Flow training targets](https://support.polar.com/us-en/create-a-training-target-in-the-polar-flow-app) adds targets into a training diary for today. | “Today” and “Previous entry” are useful contextual labels. Journal borrows the chronological mental model, not Polar's calendar machinery. | Target creation, calendar editing, route targets, or training notes would introduce unrequested scope. |
| [Garmin Connect home customization](https://support.garmin.com/en-AU/?faq=35qboQSvKC2WpKkTaDaRP7) documents prioritizing the data you care about. Search-index text supported this observation; the opened dynamic page exposed little content. | Separate primary information from secondary utilities; Console surfaces the small set relevant before indoor training. This is an inference from the documented approach, not a pixel-level review of Garmin's UI. | No customizable dashboard builder or wearable health tiles. A fixed, focused hierarchy is cheaper to maintain. |
| [Google Material 3 Expressive research](https://design.google/library/expressive-material-design-google-research?pubDate=20250521) studies the use of size, color, shape, and containment to direct attention, and warns that unfamiliar patterns/unlabeled actions can hurt usability. | Give the main action strong size/color contrast, retain labeled navigation, group related information. Focus uses calm containment; Console uses contrast; Journal uses typography and chronology. | Do not claim Google's measured improvements transfer to these proposals. Avoid decorative motion or icon-only navigation that obscures function. |

The common pattern is **a legible session → visible preparation → deliberate launch**, with deeper analysis behind a secondary destination. Our three designs intentionally disagree about how much preparation and history belong on Home. Research supports the principles; which balance suits you needs your feedback and later usability testing.

## Shared design contract

- One filled primary launch action per Home. “Open”/“Load” means entering idle Player. Do not label this “Start riding” unless recording behavior changes explicitly.
- Workout name may wrap; duration is mandatory. Power ranges come from the workout and FTP, not a fixed threshold assumption. For general workouts use a truthful segment summary, not “3 efforts” by default.
- Plan label requires validated date/source metadata. Do not infer “today” from a stale download. Date uses local timezone and locale; cached data shows its age.
- Profile includes name, weight, FTP, zones, intervals.icu settings, simulation settings, and About. Removing the Profile tab does not remove those functions.
- Workouts retains built-ins, supported imports (ZWO/MRC/ERG/JSON), synced workouts, and FTP ramp test. Import stays in Workouts; sensor setup stays in Devices. No nested menu on Home for these infrequent tasks.
- Preserve Player/Completion semantics for this first redesign. Save/discard/FIT share/FTP acceptance remain there. Done returns to the concept's Home tab. History rows still open Session Detail.
- 8 dp spacing grid with 4 dp text refinements; 24 dp phone gutters; 48 dp minimum actionable region; 56 dp primary button. Every compact textual link gets a full 48 dp interaction region when wired. Bottom navigation uses at least 52 dp touch regions and safe-area inset in the real scaffold.
- Text scales with system font settings. Use scrolling content with a fixed nav; avoid fixed card heights or truncating workout titles. On small phones and at large font sizes, secondary content can move below the fold while the launch remains reachable. Tablet/landscape: centered maximum 640 dp content; Player keeps its dedicated landscape experience.
- Normal text contrast ≥4.5:1; large text and functional boundaries ≥3:1. Status includes text, not color alone. Chart gets a textual equivalent describing intervals and duration; decorative brand/chart grid ignored by TalkBack. Nav selection announced. Controls have roles and distinct labels. These are implementation acceptance criteria, not a claim of completed accessibility validation.
- Motion: 150–200 ms fades/state transitions; never pulse a connected device or animate a workout chart endlessly. Respect reduced motion; avoid hiding state changes behind animation. No Home haptics needed.

## 1. Focus — one good ride

**Intention:** Reduce the decision cost of starting a planned session. A restrained editorial identity makes training feel deliberate rather than busy. The large serif headline and forest session card create two clear levels: daily context, then the actual work.

**Hierarchy:** Small brand + Profile avatar → date and short headline → dominant plan card (name, duration/efforts, interval chart, Open workout) → equipment status/Manage → alternative workout → previous ride → three tabs. Unlike current Home, the equipment invitation no longer outranks the planned session. FTP and weight move out of the header; the previous ride remains visible with a plan.

**Tokens:**

| Role | Value |
|---|---|
| Canvas / surface | `#F5F4ED` / `#FFFFFF` |
| Primary / text | `#163E32` / `#172B25` |
| Hero text / secondary hero text | `#FFFFFF` / `#D4E6DB` |
| Primary button fill / ink | `#DFEDC8` / `#163E32` |
| Chart effort / recovery | `#CCE4A5` / `#6E9A83` |
| Type | Native serif 38/46 headline, 32/38 workout; native sans 16/19 action, 14/17 body, 12/14 labels |
| Geometry | 24 dp hero radius; 28 dp pill action; 24 dp card padding; 16–28 dp section gaps |
| Elevation | Flat surfaces; containment and contrast, no floating shadows |

**Navigation:** Today = existing Home; Workouts = library/Preview; History = existing History. Avatar = existing Profile. Manage = Devices dialog. Open workout resolves planned workout then opens idle Player. Choose a different workout = library. View history = History. Latest ride can open Session Detail when wired as a whole row.

**States:** With no plan, replace the hero with “Choose your session”, a library CTA, and a short explanation that structured workouts and the FTP test live there. Don't invent a recommendation or automatically replay the last ride. Connecting/loading shows compact labeled status. Plan errors show Retry and Choose workout. A missing trainer changes the equipment row to “Connect trainer”; it does not disable workout browsing/loading.

**Tradeoff and effort:** Most direct match for scope reduction; moving Profile into an avatar makes settings one step less obvious. Low-to-medium implementation effort: reuse current Home state and routes; add persistent latest-session row and scaffold navigation change. No new storage or training computation. Choose this if you want Home to disappear from your attention once you know what to ride.

## 2. Console — prepare, then execute

**Intention:** Feel like an indoor training instrument. Dark surfaces, bright lime, monospaced figures, and a wide interval chart prioritize machine state and the work prescription. Less conversational, more explicit.

**Hierarchy:** Brand/Profile → Ride Console heading → three equipment/calibration modules → today's plan → large duration/effort figures → interval chart → Load Workout + Play explanation → Library/FTP test shortcuts → last ride → four tabs. This is the only concept that exposes FTP on Home and elevates the ramp test to a direct shortcut.

**Tokens:**

| Role | Value |
|---|---|
| Canvas / panel | `#101719` / `#1C282A` |
| Primary text / secondary text | `#F1F5F3` / `#ADBEB7` |
| Action/status accent | `#CCFA75` with `#101719` ink |
| Chart recovery / grid | `#517269` / `#31453E` |
| Type | Native sans 30/36 headline, 27/32 workout; monospace 48/58 duration, 28/34 intervals; 11/13 metadata |
| Geometry | 8 dp buttons; 12 dp device panel; 16 dp panel padding; 16–20 dp section gaps; 104 dp chart |
| Elevation | None; brighter panels distinguish utility from the workout canvas |

**Navigation:** Ride = Home; Library = Workouts; History = History; Setup = Profile (label change, same destination initially). Avatar also opens Profile. Trainer/HR panel regions open Devices; FTP opens Profile. Load Workout opens idle Player. FTP test resolves the existing ramp test and opens idle Player, following the current test route. Last ride opens Session Detail.

**States:** Each module independently shows Connecting, Connected, or Connect; HR shows an em dash when no current sample exists, not the last sample indefinitely. Connection without fresh telemetry is distinct from live data. Workout block replaces metrics with a library launch when no plan exists. Do not show “3 × 10” for unrelated workouts. Prescribed power is available in workout summary; current FTP is setup data, not a readiness measurement.

**Tradeoff and effort:** Fast to inspect from a trainer stand, but more numbers and stronger contrast can feel demanding. The most compact modules need care at large font sizes: stack them vertically rather than shrinking text. Medium implementation effort: direct FTP-test callback and navigation labeling, freshness handling for HR, split-module semantics. No new physiological calculation or device type required. Choose this if setup checks and test access matter more than a calm dashboard.

## 3. Journal — connect the next ride to the last

**Intention:** Present training as a sequence of real sessions. Warm paper, cobalt session card, apricot chart, date stamp, and explicit “Today/Previous entry” labels make it easy to move between doing and reviewing.

**Hierarchy:** Brand/Profile → training headline/date → Today label → planned workout card → device strip → previous saved entry → browse alternative → three tabs. The last ride is a first-class entry rather than a section that disappears behind a plan. There is no calendar strip suggesting unsupported future scheduling.

**Tokens:**

| Role | Value |
|---|---|
| Canvas / history surface | `#F6E7DB` / `#FFF9F3` |
| Text / secondary text | `#25243A` / `#655B66` |
| Primary hero / action ink | `#243CB5` |
| Hero text / secondary hero text | `#FFFFFF` / `#DFE4FF` |
| Chart effort / recovery | `#F6BA8C` / `#8191D6` |
| Type | Native sans 32/38 headline and workout, 23/28 duration, 19/23 entry; monospace 24/29 date |
| Geometry | 6 dp cards/buttons, 22 dp plan padding, 18 dp history padding; generous 20–24 dp sections |
| Elevation | None; chronology and warm/cool surface contrast distinguish entries |

**Navigation:** Journal = existing Home, not a new calendar; Workouts = library; History = full history. Avatar = Profile. Today CTA = idle planned Player. Devices = current dialog. Review = last Session Detail. Browse workouts = library. History remains separate because the Home journal only contains today's plan and latest saved ride.

**States:** No saved rides shows “Your first saved ride will appear here” with no fake completion markers. No plan makes the Today card a library invitation. Dates respect local timezone. A finished but unsaved workout does not appear as a previous entry. Plan and history refresh independently. Offline local history remains usable; a plan download failure doesn't hide it.

**Tradeoff and effort:** Best for alternating training and reflection, but less efficient for users who never review history. “Journal” could imply notes or a full calendar; onboarding/help must describe it as a daily overview, and do not add those capabilities silently. Low-to-medium effort with existing latest-session state and navigation; no weekly totals, streak logic, targets, or migrations. Choose this if reviewing the previous ride helps you stay engaged.

## State and route acceptance matrix

| Condition/action | Required result in all three concepts |
|---|---|
| Plan successfully loaded | Correct name/segments/duration/source; only one primary open action |
| Plan metadata exists, file still downloading | Labeled loading; prevent duplicate resolve requests; library remains usable |
| Plan missing or sync not configured | Honest choose-workout state; integrations setup accessible in Profile |
| Plan download fails | Visible error + retry; no placeholder power prescription; library still accessible |
| Cached plan after date changes | Show cached date or refresh; never silently call yesterday's plan “today” |
| BLE permission denied / trainer absent | Explain setup issue in Devices path; no fake Connected; preserve navigation |
| HR connected but sample stale | Connected status can remain; bpm becomes unavailable with freshness policy |
| Press Open/Load | Resolve workout → idle Player; Play is the recording start |
| Press alternate workout | Workouts → Preview → idle Player |
| End and save ride | Completion save; Done → Home; refresh previous entry; do not count discarded sessions |
| Press History / latest saved session | History / Session Detail respectively |
| FTP changes | Future prescription reflects actual model; historical stress limitation remains visible in detail |

## Implementation boundary and next review

These artifacts intentionally stop at Home. Tokens describe a future coherent system, but the rest of the app has not been restyled. Extending the selected direction should follow the important path: Workouts → Preview → Player → Completion, then History/Session Detail, then Profile/Devices. Preserve the functional inventory while changing presentation; any later feature removal is a separate product decision.

Before selecting, compare: Can you identify the planned session immediately? Does the main action accurately predict opening Player? Can you find the library, equipment, last ride, and FTP settings without hunting? Do you prefer a calm launch screen, a technical preparation screen, or a daily training record? After selection, implement real callbacks/state-driven components and verify no-plan/loading/error/disconnected states, font scaling, TalkBack, both theme needs, and smaller viewports. Current renders alone cannot establish usability or physical BLE behavior.

## Reproduction and validation

Source `/workspace/toolchains/activate.sh`, then run from repository root:

```bash
./scripts/compose-screenshots.sh record --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.compiler.execution.strategy=in-process
./scripts/compose-screenshots.sh verify --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.compiler.execution.strategy=in-process
```

Record intentionally updates screenshot references; use verify for routine checks. Copy the three `proposal-*.png` files from `android/app/src/test/screenshots/` to this directory after inspecting them. The self-contained report generator embeds the PNGs and specification for offline review. The existing screenshot guide remains the renderer/setup source of truth.

## Measured color checks

Calculated sRGB relative-luminance contrast for sampled rendered token pairs: Focus body 13.52:1, primary button 9.66:1, secondary hero text 9.12:1; Console secondary text on panel 7.81:1, primary button 15.10:1; Journal secondary body 5.36:1, links 7.27:1, secondary hero text 6.98:1. These checks cover selected text pairs, not every graphical element or runtime state.

## Completed verification

All three final native PNGs were opened and visually inspected. Console spacing was adjusted once to expose the last ride in the reference viewport, then its final render was reopened. Roborazzi verification passed all five fixtures (three proposals plus existing production Home light/dark), with zero failures or skips. Existing production Home PNG hashes are unchanged. `assembleDebug` and `lintDebug` passed; lint reports no errors.

The HTML was rendered using the installed Chromium through Playwright: all concept PNGs loaded, single-concept filtering worked, the enlargement dialog opened/closed, and the 390 px mobile layout had no horizontal overflow. No JavaScript page errors were reported. The desktop report capture was also opened and inspected. Local file navigation was blocked by Chromium policy, so validation used the exact HTML document via `setContent`; no external assets were required. No real device, BLE connection, account sync, or future-state accessibility behavior was tested.
