# Repository Guidelines

## Project Structure & Module Organization
- `src/` holds the React + TypeScript app. Key areas include `src/components/` (UI building blocks), `src/hooks/` (state/telemetry logic), `src/data/` (mock workout data), and `src/utils/` (formatting helpers).
- `public/` contains static assets served by Vite.
- `docs/` tracks product and delivery notes (implementation plan, data model, UX flow).
- `profiles/` stores coach profile JSON files.
- `UI concepts/` contains UI references and wireframes.

## Build, Test, and Development Commands
- `npm install` installs dependencies.
- `npm run dev` starts the Vite dev server with HMR.
- `npm run build` runs TypeScript build + Vite production build.
- `npm run preview` serves the production build locally.
- `npm run lint` runs ESLint across the repo.

## Coding Style & Naming Conventions
- TypeScript + React function components; keep logic in hooks when possible.
- Use 2-space indentation, semicolons, and single quotes to match current files.
- Component files use PascalCase (e.g., `src/components/WorkoutChart.tsx`).
- Hooks use `useX` naming (e.g., `src/hooks/useTelemetrySimulation.ts`).
- Prefer CSS variables in `src/index.css` and component styles in `src/App.css`.

## Testing Guidelines
- No test framework is configured yet. When adding tests, introduce a test runner, document the command in this file, and keep tests close to source (for example in `src/__tests__/`).
- Define any coverage expectations when tests are introduced.

## Commit & Pull Request Guidelines
- Recent commits use short prefixes like `feat:`, `chore:`, and `chg:` with concise summaries. Follow this pattern.
- PRs should include a clear description, linked issue (if available), and screenshots for UI changes (workout screen updates in particular).
- If UX or MVP scope shifts, update `docs/implementation-plan.md` progress notes.

## Architecture Overview
- The app is front-end only. Workout definitions live in `src/data/workout.ts` and simulated telemetry is generated in `src/hooks/useTelemetrySimulation.ts`.
