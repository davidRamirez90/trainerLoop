# Token & usage specification — full-image color revamp

Status: final, as implemented on `feature/full-image-revamp` (commits
`108f0eb..6dae581`). Source of truth for values: `app/src/main/java/com/trainerloop/ui/theme/Color.kt`,
`Theme.kt`, `TrainerLoopColors.kt`. Regenerate this table from those files if
they change — do not hand-edit hex values here without checking the source.

## 1. Two-layer system

```
Color.kt              → foundation ramps (Sun*, Coral*, Sky*, Sand*, Ocean*, Kelp*, Amber*, Red*, Neutral*)
        │
        ├──> Theme.kt              → Material 3 colorScheme roles (MaterialTheme.colorScheme.*)
        └──> TrainerLoopColors.kt  → app-specific semantic roles (MaterialTheme.trainerLoopColors.*)
```

Foundation ramps in `Color.kt` are never imported outside `Theme.kt` and
`TrainerLoopColors.kt`. Screens and `ui/components/` primitives consume only
`MaterialTheme.colorScheme.*` or `MaterialTheme.trainerLoopColors.*`.

## 2. Material 3 roles (`MaterialTheme.colorScheme.*`)

Defined in `LightColorScheme` / `DarkColorScheme`, `Theme.kt`.

| Role | Light hex | Dark hex | Responsibility |
|---|---|---|---|
| `primary` | `#006782` (Ocean40) | `#82C8E5` (Sky80) | Brand / primary interactive accent (buttons, active icons, selection). |
| `onPrimary` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Content drawn on `primary`. |
| `primaryContainer` | `#82C8E5` (Sky80\*) | `#003544` (Ocean20) | Emphasized container tied to primary (e.g. info inline messages). |
| `onPrimaryContainer` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Content on `primaryContainer`. |
| `inversePrimary` | `#82C8E5` (Sky80\*) | `#006782` (Ocean40) | Primary accent on inverse surfaces. |
| `secondary` | `#786956` (Sand40) | `#E6D8C4` (Sand80) | Secondary structural accent. |
| `onSecondary` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Content on `secondary`. |
| `secondaryContainer` | `#82C8E5` (Sky80\*) | `#82C8E5` (Sky80) | Selected-tab indicator container (bottom nav). |
| `onSecondaryContainer` | `#081417` (DarkBackground) | `#081417` (DarkBackground) | Content on `secondaryContainer` (selected nav icon). |
| `tertiary` | `#9B3F3B` (Coral40) | `#F88379` (Coral80) | Tertiary accent. |
| `onTertiary` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Content on `tertiary`. |
| `tertiaryContainer` | `#FFD8D3` (Coral90) | `#5D201E` (Coral20) | Tertiary emphasized container. |
| `onTertiaryContainer` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Content on `tertiaryContainer`. |
| `background` | `#FAF7F1` (WarmOffWhite) | `#081417` (DarkBackground) | Screen/scaffold background. |
| `onBackground` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Content on `background`. |
| `surface` | `#FFFCF8` (Foam) | `#101E22` (DarkCard) | Card / raised-content surface. |
| `onSurface` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Content on `surface`. |
| `surfaceVariant` | `#F2EADF` (PaleSand) | `#17282D` (DarkGrouped) | Emphasized card fill (e.g. `MetricTile`). |
| `onSurfaceVariant` | `#3A494E` (Neutral30) | `#D7D0C5` (Neutral80) | Secondary/label text (metric labels, unselected nav). |
| `surfaceTint` | `#006782` (Ocean40) | `#82C8E5` (Sky80) | Elevation tint overlay. |
| `inverseSurface` | `#203238` (DarkRaised) | `#FFFCF8` (Foam) | Inverse surface (snackbars). |
| `inverseOnSurface` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Content on `inverseSurface`. |
| `error` | `#B3261E` (Red40) | `#FFB4AB` (Red80) | Destructive / error accent. |
| `onError` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Content on `error`. |
| `errorContainer` | `#FFDAD6` (Red90) | `#601410` (Red20) | Error inline-message container. |
| `onErrorContainer` | `#601410` (Red20) | `#FFDAD6` (Red90) | Content on `errorContainer`. |
| `outline` | `#59666A` (Neutral40) | `#A9A197` (Neutral60) | Borders / dividers. |
| `outlineVariant` | `#C6B398` (Sand60) | `#59666A` (Neutral40) | Subtler borders / dividers. |
| `scrim` | `#081417` (DarkBackground) | `#081417` (DarkBackground) | Modal scrim. |
| `surfaceBright` | `#FFFCF8` (Foam) | `#294047` (DarkRaisedHigh) | Brightest surface step. |
| `surfaceContainer` | `#F2EADF` (PaleSand) | `#17282D` (DarkGrouped) | Standard container step. |
| `surfaceContainerHigh` | `#E6D8C4` (Sand80) | `#203238` (DarkRaised) | Higher container step. |
| `surfaceContainerHighest` | `#C6B398` (Sand60) | `#294047` (DarkRaisedHigh) | Highest container step. |
| `surfaceContainerLow` | `#FAF5ED` (Sand95) | `#101E22` (DarkCard) | Lower container step. |
| `surfaceContainerLowest` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Lowest container step. |
| `surfaceDim` | `#F2EADF` (Sand90) | `#081417` (DarkBackground) | Dimmed surface step. |

\* `Sky80` intentionally does double duty as `primaryContainer`/`secondaryContainer`
in light mode — both roles land on the same brand-tinted container tone; they
diverge in dark mode (`Ocean20` vs `Sky80`).

## 3. `TrainerLoopColors` semantic roles (`MaterialTheme.trainerLoopColors.*`)

Defined in `LightTrainerLoopColors` / `DarkTrainerLoopColors`, `TrainerLoopColors.kt`.
Each role has exactly **one** responsibility; do not reuse a role for a second purpose.

| Role | Light hex | Dark hex | Single responsibility |
|---|---|---|---|
| `ready` | `#FFEB3B` (Sun80) | `#3D3600` (Sun20) | Readiness hero only (e.g. Home readiness card). Never used as a generic accent or warning. |
| `onReady` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Content on `ready`. |
| `coach` | `#F88379` (Coral80) | `#5D201E` (Coral20) | Coaching / assessment surfaces only (coach messaging, athlete-model callouts). Never used for errors. |
| `onCoach` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Content on `coach`. |
| `connected` | `#28704B` (Kelp40) | `#123D29` (Kelp20) | Connected / success device state (`StatusPill` Connected & Success). |
| `onConnected` | `#FFFCF8` (Foam) | `#FFFCF8` (Foam) | Content on `connected`. |
| `warning` | `#A65300` (Amber40) | `#573000` (Amber20) | Warning state only (`StatusPill` Warning/Reconnecting, `InlineMessage` Warning). **Yellow (`Sun*`) is never used for warning** — that's reserved for `ready`. |
| `onWarning` | `#FFFCF8` (Foam) | `#FFFCF8` (Foam) | Content on `warning`. |
| `stale` | `#59666A` (Neutral40) | `#3A494E` (Neutral30) | Stale/unavailable metric or device state (`MetricTile` Stale/Unavailable, `StatusPill` Unavailable). **Coral is never used for this** — coral is reserved for coaching. |
| `onStale` | `#FFFCF8` (Foam) | `#FFFCF8` (Foam) | Content on `stale`. |
| `heroAction` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Primary hero call-to-action (e.g. Start Ride). Ink-on-light / paper-on-dark, not tied to `primary`. |
| `onHeroAction` | `#FFFCF8` (Foam) | `#081417` (DarkBackground) | Content on `heroAction`. |
| `chartPower` | `#006782` (Ocean40) | `#82C8E5` (Sky80) | Power series line/fill in charts. |
| `chartHeartRate` | `#B3261E` (Red40) | `#FFB4AB` (Red80) | Heart-rate series only. |
| `chartCadence` | `#28704B` (Kelp40) | `#8AD0A4` (Kelp80) | Cadence series only. |
| `chartElevation` | `#9B3F3B` (Coral40) | `#F88379` (Coral80) | Elevation-profile series only. |
| `chartGrid` | `#C6B398` (Sand60) | `#59666A` (Neutral40) | Chart gridlines/axes. |
| `chartCursor` | `#081417` (DarkBackground) | `#FFFCF8` (Foam) | Chart scrub cursor. |
| `chartPlanOutline` | `#786956` (Sand40) | `#A9A197` (Neutral60) | Stepped outline of the planned-effort profile in workout charts. Monochrome — zone colors are never used for plan geometry. |
| `chartPlanFill` | `#C6B398` (Sand60) | `#59666A` (Neutral40) | Faint tint under the plan outline; renderers apply ~8% alpha. |

**Never-rules enforced by usage (see `WCAG` contrast tests in
`app/src/test/java/com/trainerloop/ui/theme/ThemeContrastTest.kt`):**
- Yellow (`ready` / `Sun*`) is never used for warnings — `warning` (`Amber*`) owns that.
- Coral (`coach` / `Coral*`) is never used for errors — `error`/`errorContainer` (`Red*`) own that.
- White (`Foam`) is never placed as text/icon directly on a pastel anchor color
  (e.g. `Sun80`, `Coral80`, `Sky80`) without going through the paired `on*` role,
  which is chosen per-mode for contrast (e.g. `onReady` is dark ink in light mode,
  not white).

## 4. Zone colors — independent data system

`ZoneColors.kt` is **not** part of the semantic role system above. It maps a
power-zone index (1–6, derived from `%FTP` via `PowerZoneMath.zoneIndex`) to a
`ZoneColorSet(fill, onFill, line)`, with separate light/dark arrays. Consumers
call `zoneColorSet(targetWatts, ftp)` (theme-aware Composable helper) or
`ZoneColors.forZone`/`forTarget` directly — never a hardcoded zone hex. This
system exists because zone coloring is workout *data* visualization (variable
per-workout, per-interval), not a fixed UI role, so it is intentionally kept
out of `TrainerLoopColors`.

## 5. Component inventory (`ui/components/`)

| Component | File | Purpose |
|---|---|---|
| `PrimaryActionButton`, `SecondaryActionButton` | `ActionButtons.kt` | Primary/secondary CTA buttons using `heroAction`/`primary` roles. |
| `AnimatedMetricValue` | `AnimatedMetricValue.kt` | Animated numeric transition for live metric values. |
| `Messaging` (`EmptyState`, `InlineMessage`) | `Messaging.kt` | Empty-state placeholder (icon/title/body/action) and inline severity banners (Info/Warning/Error). |
| `MetricBadge` | `MetricBadge.kt` | Compact inline metric chip. |
| `MetricCard` | `MetricCard.kt` | Larger metric display card (distinct from `MetricTile`). |
| `MetricTile` | `MetricTile.kt` | Labeled metric tile with `Available`/`Stale`/`Unavailable` states; drives `stale` role and `stateDescription` semantics. |
| `PagerDots` | `PagerDots.kt` | Page-indicator dots for pager/carousel UIs. |
| `Modifier.pressable` | `Pressable.kt` | Shared press-feedback modifier used by `TrainerLoopCard` and buttons. |
| `RouteProfileChart` | `RouteProfileChart.kt` | GPX elevation-profile chart (uses `chartElevation`, `chartGrid`). |
| `SampleChart` | `SampleChart.kt` | Generic telemetry sample chart primitive. |
| `SectionHeader` | `SectionHeader.kt` | Section title row used across list/detail screens. |
| `StatusPill` | `StatusPill.kt` | Device/connection status chip; states `Connected/Scanning/Warning/Reconnecting/Unavailable/Success` map to `connected`/`primary`/`warning`/`stale` roles, always paired with a text label (never hue-only). |
| `TrainerLoopCard` | `TrainerLoopCard.kt` | Base card scaffold (`surface`/`surfaceVariant`, rounded 16dp, optional press/click). |
| `TrainerLoopTopBar` | `TrainerLoopTopBar.kt` | App bar variants. |
| `WorkoutChart` | `WorkoutChart.kt` | Structured-workout interval + power chart (zone-colored blocks over elevation). |
| `WorkoutMiniChart` | `WorkoutMiniChart.kt` | Small interval preview chart (library cards). |
| `FitShareHelper` | `FitShareHelper.kt` | Non-visual: FIT file share-intent helper (not a themed component). |

## 6. Do / don't rules for future screens

**Do**
- Read colors only via `MaterialTheme.colorScheme.*` or `MaterialTheme.trainerLoopColors.*`.
- Reuse an existing `ui/components/` primitive before writing a new
  `Row`/`Column` + manual color lookup.
- Pick the semantic role by *responsibility*, not by hue that "looks right"
  (e.g. a new "device syncing" indicator uses `warning` or `connected`
  depending on state — not a new one-off color).
- Add a new `TrainerLoopColors` role (in both light/dark maps) if no existing
  role's responsibility fits, and add/extend a contrast test for it in
  `ThemeContrastTest.kt`.
- Use `zoneColorSet(...)`/`ZoneColors` for anything that colors by power zone.

**Don't**
- Don't import `Sun*`, `Coral*`, `Sky*`, `Sand*`, `Ocean*`, `Kelp*`, `Amber*`,
  `Red*`, or `Neutral*` from `Color.kt` in a screen or component file.
- Don't write `Color(0x...)` hex literals in screens/components — if a value
  isn't available as a role, add the role instead of hardcoding.
- Don't repurpose `ready` (Sun) for warnings, or `coach` (Coral) for errors —
  those roles are reserved (see §3 never-rules).
- Don't put light text/icons on `ready`, `coach`, `connected`, `warning`, or
  `stale` without going through the paired `on*` color — the pairing is what
  keeps contrast correct in both themes.
- Don't hand-pick a zone/series color for a new chart — extend `ZoneColors` or
  add a new `chart*` role instead.

## 7. Validation

```bash
# Full unit suite, including WCAG contrast tests for every role pairing
./gradlew testDebugUnitTest

# Lint
./gradlew lint

# Compose UI tests (compile-check without a device)
./gradlew compileDebugAndroidTestKotlin
```
