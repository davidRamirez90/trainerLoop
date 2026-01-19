# Testing Plan for trainerLoop

This document outlines the testing strategy for enabling Ralph loop-based development on the trainerLoop repository.

## Current Status: ✅ Foundation Ready

Opencode has completed the initial setup:
- Vitest configured in `vite.config.ts`
- React Testing Library ready
- Sample tests created for component and hook
- Test scripts added to `package.json`

## Testing Stack

- **Test Runner**: Vitest
- **React Testing**: @testing-library/react
- **DOM Assertions**: @testing-library/jest-dom
- **Environment**: jsdom (browser simulation)
- **Coverage**: Built-in Vitest coverage

## Test Directory Structure

```
src/
├── __tests__/
│   ├── CoachPanel.test.tsx      # Component test example
│   ├── useWorkoutClock.test.ts  # Hook test example
│   └── ...
├── setupTests.ts                # Test configuration & imports
└── ...
```

## Implementation Steps

### Step 1: Install Dependencies
```bash
npm install
```
Required packages are already in `package.json`:
- vitest
- @testing-library/react
- @testing-library/jest-dom
- jsdom
- @types/jsdom

### Step 2: Verify Configuration
Check that `vite.config.ts` includes Vitest config and `tsconfig.app.json` has `"types": ["vitest/globals"]`.

### Step 3: Run Initial Tests
```bash
npm test
```
Should pass with sample tests.

### Step 4: Expand Test Coverage

#### Priority 1: Utility Functions (Deterministic)
- `src/data/` - Workout data models, parsing utilities
- `src/utils/` - Calculation helpers, formatters

#### Priority 2: Custom Hooks (Testable Logic)
- `useWorkoutClock.test.ts` ✅ Already created
- `useCoach.test.ts`
- `useBLEConnection.test.ts`
- `useWorkoutImporter.test.ts`

#### Priority 3: React Components (UI Testing)
- `CoachPanel.test.tsx` ✅ Already created
- `WorkoutTimeline.test.tsx`
- `DeviceSelector.test.tsx`
- `TelemetryDisplay.test.tsx`

#### Priority 4: Integration Tests
- Full workout flow (import → start → workout → export)
- BLE connection lifecycle
- Coach feedback loop

### Step 5: Mock External Dependencies
For reliable CI/CD:
- Mock Web Bluetooth API (`navigator.bluetooth`)
- Mock file system for import/export
- Mock timing functions for deterministic tests

### Step 6: CI/CD Integration
Add to `package.json`:
```json
{
  "scripts": {
    "test:ci": "vitest run --coverage",
    "test:watch": "vitest"
  }
}
```

## Ralph Loop Guidelines

When running Ralph loops with testing:

1. **Start with Utilities**: Test pure functions first (deterministic, no mocks needed)
2. **Then Hooks**: Test business logic with mocked dependencies
3. **Then Components**: Test UI rendering and user interactions
4. **Use Mocks Aggressively**: Don't depend on hardware or browser prompts
5. **Keep Tests Small**: One concept per test file
6. **Fast Feedback**: Aim for < 1s per test suite

## Example Test Patterns

### Component Test
```tsx
import { render, screen } from '@testing-library/react';
import { CoachPanel } from '../components/CoachPanel';

describe('CoachPanel', () => {
  it('renders coach profile and suggestions', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} />);
    expect(screen.getByText('Ari Mendoza')).toBeInTheDocument();
  });
});
```

### Hook Test
```ts
import { renderHook, act } from '@testing-library/react';
import { useWorkoutClock } from '../hooks/useWorkoutClock';

describe('useWorkoutClock', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });
  
  it('starts at zero', () => {
    const { result } = renderHook(() => useWorkoutClock());
    expect(result.current.elapsed).toBe(0);
  });
});
```

## Next Actions

1. [ ] Run `npm install`
2. [ ] Run `npm test` to verify setup
3. [ ] Add tests for `src/utils/workout.ts`
4. [ ] Add tests for `src/utils/workoutImport.ts`
5. [ ] Create mocks for Web Bluetooth API
6. [ ] Add CI test command

## References

- Vitest Docs: https://vitest.dev/
- React Testing Library: https://testing-library.com/docs/react-testing-library/
- Jest DOM Matchers: https://github.com/testing-library/jest-dom
