# Screenshot state matrix

| Area | Base | Interaction / overlay | Dark mode | Data-dependent states not captured |
| --- | --- | --- | --- | --- |
| Home | Captured | Device management entry represented through Devices captures | Captured | Connected trainer variant |
| Workouts | Captured | Detail and player controls captured | Player captured | Empty library, sync error |
| History | Captured | Session detail requires history data | Captured | Completed-session list/detail |
| Profile | Captured | Power zones, HR zones, coach picker, About, advanced simulation captured | Captured | API key visible intentionally skipped |
| Devices | Empty, scanning, connected | Device details captured | Not separately captured | Permission/Bluetooth/location errors |
| Routes | Library entry captured | Route detail/free ride require route fixture | Not separately captured | Empty routes, route detail |
| Completion | Not captured | Requires completing a real session | Not captured | Workout complete summary |

## Capture conventions

- Images are full-device screenshots at the connected phone's native resolution.
- Names describe the visible destination and state.
- Dialogs and sheets are separate captures when they materially change navigation or available actions.
- API credentials are not exposed in screenshots.
