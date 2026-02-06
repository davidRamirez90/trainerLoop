# Strava Integration Implementation Summary

## Overview

Phase 3.5 of the Trainer Loop implementation is now complete. This adds seamless Strava export functionality with OAuth authentication via Cloudflare Workers (free tier), auto-generated workout names, and descriptions with training metrics.

## What Was Created

### 1. Cloudflare Worker Backend (`strava-auth-worker/`)

**Files Created:**
- `package.json` - Worker dependencies
- `tsconfig.json` - TypeScript configuration
- `wrangler.toml` - Cloudflare deployment config
- `src/types.ts` - TypeScript type definitions
- `src/oauth.ts` - OAuth 2.0 + PKCE implementation
- `src/strava.ts` - Strava API client
- `src/index.ts` - Main HTTP request handler

**Features:**
- OAuth 2.0 with PKCE for secure authentication
- Cloudflare KV for token storage
- Activity upload endpoint
- Automatic token refresh
- CORS support for your domain

### 2. Frontend Integration

**Files Created:**
- `src/config/strava.ts` - Configuration
- `src/hooks/useStravaAuth.ts` - Authentication hook
- `src/utils/stravaApi.ts` - API client

**Features:**
- React hook for auth state management
- Popup-based OAuth flow
- Automatic user ID generation
- Upload status tracking

### 3. Workout Utilities

**Files Created:**
- `src/utils/workoutNaming.ts` - Auto-generate technical workout names
- `src/utils/workoutDescription.ts` - Generate goal + metrics descriptions
- `src/utils/trainingMetrics.ts` - TSS, NP, and power calculations

**Features:**
- Smart workout type detection (Sweet Spot, Threshold, VO2max, etc.)
- Technical naming: "Sweet Spot Intervals - 4x3min @ 91% FTP"
- Description generation with adherence and TSS
- Simplified TSS calculation using normalized power

### 4. Enhanced FIT Export

**Updated:**
- `src/utils/fit.ts` - Added `workoutName`, `description`, `deviceName` fields

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Trainer Loop   │────►│  Cloudflare      │────►│     Strava      │
│   (Frontend)    │     │   Worker         │     │     API         │
│                 │     │   (OAuth Proxy)  │     │                 │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                      │
        ▼                      ▼
┌─────────────────┐     ┌──────────────────┐
│  LocalStorage   │     │   Cloudflare KV  │
│  (Auth Status)  │     │   (Tokens)       │
└─────────────────┘     └──────────────────┘
```

## Setup Instructions

### Step 1: Create Strava API Application

1. Go to https://www.strava.com/settings/api
2. Click "Create App"
3. Fill in:
   - **Name:** Trainer Loop
   - **Category:** Training
   - **Website:** Your domain (e.g., https://trainer-loop.example.com)
   - **Authorization Callback Domain:** `strava-auth-worker.YOUR_SUBDOMAIN.workers.dev`
4. Save **Client ID** and **Client Secret**

### Step 2: Set Up Cloudflare Worker

```bash
cd strava-auth-worker
npm install
```

### Step 3: Configure Environment Variables

Edit `wrangler.toml`:
```toml
[vars]
ALLOWED_ORIGIN = "https://your-trainer-loop-domain.com"
STRAVA_CLIENT_ID = "your_strava_client_id"

[[kv_namespaces]]
binding = "STRAVA_TOKENS"
id = "your_kv_namespace_id"
```

Set the secret:
```bash
wrangler secret put STRAVA_CLIENT_SECRET
# Enter your Strava Client Secret when prompted
```

### Step 4: Deploy Worker

```bash
wrangler deploy
```

Note the Worker URL (e.g., `https://strava-auth-worker.youraccount.workers.dev`)

### Step 5: Configure Frontend

Create `.env.local` in project root:
```env
VITE_STRAVA_WORKER_URL=https://strava-auth-worker.youraccount.workers.dev
```

### Step 6: Update Strava Callback URL

1. Go back to https://www.strava.com/settings/api
2. Update "Authorization Callback Domain" to match your Worker domain
3. Save changes

## Usage

### In Your React Components

```typescript
import { useStravaAuth } from './hooks/useStravaAuth';
import { uploadActivityToStrava, fitFileToBase64 } from './utils/stravaApi';
import { generateWorkoutName } from './utils/workoutNaming';
import { generateWorkoutDescription } from './utils/workoutDescription';

function WorkoutCompletionModal({ plan, segments, samples, fitData }) {
  const { authenticated, athlete, initiateAuth, logout } = useStravaAuth();

  const handleExport = async () => {
    const workoutName = generateWorkoutName(plan, segments);
    const description = generateWorkoutDescription(plan, segments, samples, 94);
    const fileData = fitFileToBase64(fitData);

    const result = await uploadActivityToStrava({
      fileData,
      name: workoutName,
      description,
      sportType: 'Ride',
      deviceName: 'Trainer Loop',
    });

    console.log('Upload ID:', result.id);
  };

  return (
    <div>
      {authenticated ? (
        <button onClick={handleExport}>Export to Strava</button>
      ) : (
        <button onClick={initiateAuth}>Connect to Strava</button>
      )}
    </div>
  );
}
```

## API Endpoints

The Cloudflare Worker exposes these endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/auth/initiate` | GET | Start OAuth flow |
| `/auth/callback` | GET | OAuth callback handler |
| `/auth/status` | GET | Check if authenticated |
| `/auth/logout` | POST | Revoke tokens |
| `/upload` | POST | Upload FIT file |
| `/upload/status/:id` | GET | Check upload status |
| `/athlete` | GET | Get athlete info |

## Auto-Generated Workout Names

Names follow the format: `{Type} - {Structure} @ {Intensity}`

**Examples:**
- "Sweet Spot Intervals - 4x3min @ 91% FTP"
- "Threshold Intervals - 2x20min @ 100% FTP"
- "VO2max Intervals - 5x3min @ 115% FTP"
- "Recovery Ride - 45min @ 55% FTP"
- "Endurance Ride - 1h30 @ 75% FTP"

**Workout Type Detection:**
- Recovery: <70% FTP
- Endurance: 70-80% FTP
- Tempo: 80-88% FTP
- Sweet Spot: 88-94% FTP
- Threshold: 95-105% FTP
- VO2max: >105% FTP, <5min intervals
- Anaerobic: >110% FTP, <2min intervals
- Neuromuscular: >120% FTP, <1min intervals

## Workout Descriptions

Descriptions include the workout goal and key metrics:

**Format:**
```
{Goal Statement} Adherence: {X}% | Avg Power: {Y}W | TSS: {Z} | NP: {W}W
```

**Examples:**
- "Accumulate 20 minutes in threshold range. Adherence: 94% | Avg Power: 285W | TSS: 65 | NP: 290W"
- "Recovery ride as part of training block. Adherence: 98% | Avg Power: 145W | TSS: 28 | NP: 148W"
- "Build aerobic capacity with 24min sweet spot work. Adherence: 91% | Avg Power: 265W | TSS: 82 | NP: 270W"

## Training Metrics

The implementation includes simplified calculations for:

### TSS (Training Stress Score)
```typescript
TSS = (duration_hours × NP × IF) / (FTP × 36)
```

### Normalized Power (NP)
Uses average power with variability adjustment. True 30-second rolling average can be added later.

### Intensity Factor (IF)
```typescript
IF = NP / FTP
```

### Variability Index (VI)
```typescript
VI = NP / Average_Power
```
Higher VI = more variable effort (harder on body)

## Security

1. **Client Secret Protection**
   - Never exposed to frontend
   - Stored only in Cloudflare Worker secrets
   - Used only server-side for token exchange

2. **Token Storage**
   - Access tokens: Cloudflare KV (encrypted at rest)
   - Refresh tokens: Cloudflare KV
   - Auth status only: LocalStorage (boolean)
   - No tokens in LocalStorage

3. **CORS Protection**
   - Restricted to your domain only
   - Set via `ALLOWED_ORIGIN` environment variable
   - Preflight requests handled properly

4. **PKCE Implementation**
   - Code verifier generated client-side
   - Code challenge (SHA256 hash) sent to Strava
   - Prevents authorization code interception attacks

## Rate Limits

### Cloudflare Workers (Free Tier)
- 100,000 requests/day
- 10ms CPU time per request
- Sufficient for ~100 users uploading 3 workouts/day each

### Strava API
- 100 requests per 15 minutes
- 1,000 requests per day
- Activity upload: 1 request per upload
- Limits: ~33 uploads per hour, 1,000 per day

## Next Steps

1. **Create React Components** (not yet implemented):
   - `StravaAuthButton.tsx` - Connect/Export button
   - `StravaUploadModal.tsx` - Post-workout export UI
   - Integration with existing completion flow in `App.tsx`

2. **Test the Integration**:
   - Deploy Worker to Cloudflare
   - Run frontend locally
   - Complete test workout
   - Verify OAuth flow
   - Verify upload to Strava

3. **Enhancements** (Future):
   - Add loading states and progress indicators
   - Implement upload retry logic
   - Add offline queue for failed uploads
   - Enhance TSS calculation with proper 30s rolling average
   - Add power zone distribution charts

## Files Summary

```
trainer-loop/
├── strava-auth-worker/          # NEW
│   ├── src/
│   │   ├── index.ts            # Worker entry point
│   │   ├── oauth.ts            # OAuth implementation
│   │   ├── strava.ts           # Strava API client
│   │   └── types.ts            # TypeScript types
│   ├── package.json
│   ├── tsconfig.json
│   └── wrangler.toml
├── src/
│   ├── config/
│   │   └── strava.ts           # NEW - Configuration
│   ├── hooks/
│   │   └── useStravaAuth.ts    # NEW - Auth hook
│   └── utils/
│       ├── fit.ts              # MODIFIED - Added metadata fields
│       ├── stravaApi.ts        # NEW - API client
│       ├── workoutNaming.ts    # NEW - Auto naming
│       ├── workoutDescription.ts # NEW - Description generator
│       └── trainingMetrics.ts  # NEW - TSS/NP calculations
└── docs/
    └── STRAVA_IMPLEMENTATION.md # This file
```

## Troubleshooting

### "Invalid redirect_uri" Error
- Check Strava callback domain matches Worker URL exactly
- No protocol (https://) in callback domain field
- Redeploy Worker after domain changes

### CORS Errors
- Verify `ALLOWED_ORIGIN` in wrangler.toml matches your frontend
- Include port for localhost (e.g., `http://localhost:5173`)
- Redeploy after changing CORS settings

### "Not authenticated" Error
- User hasn't connected Strava yet
- Tokens expired and couldn't refresh
- Check browser console for detailed error

### Upload Fails
- Verify FIT file is valid
- Check Strava rate limits
- Verify file size (<25MB for Strava)
- Check Worker logs in Cloudflare dashboard

## Resources

- [Strava API Documentation](https://developers.strava.com/docs/)
- [Cloudflare Workers Documentation](https://developers.cloudflare.com/workers/)
- [OAuth 2.0 Simplified](https://aaronparecki.com/oauth-2-simplified/)
- [FIT File Format](https://developer.garmin.com/fit/protocol/)

---

## Ready to Test!

All backend infrastructure is in place. To complete the integration:

1. Set up Strava API app and Cloudflare Worker
2. Deploy the Worker
3. Create the React UI components
4. Test end-to-end flow

The foundation is solid and ready for UI integration!
