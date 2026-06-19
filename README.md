# TV RingLight

An Android TV app that draws a soft glowing ring around the edges of your TV screen — like a Snapchat ringlight — so you can take flattering photos at night without interrupting what you're watching.

## Features

- Soft glow overlay around all four screen edges (blends into the background — not a harsh border)
- **8 colours:** White, Warm White, Yellow, Red, Green, Blue, Purple, Pink
- **4 intensity levels:** 25 / 50 / 75 / 100%
- **Adjustable ring thickness:** 5–40% of screen height (default 15%)
- Passes all remote input through — the overlay is completely non-interactive
- Auto-off timer (default 3 hours, configurable, can be disabled)
- Burn-in protection: overlay shifts ±2 px every 30 seconds
- Restores previous state after TV reboot

## Button Mapper integration

The app publishes three Android static shortcuts that appear in Button Mapper's Shortcuts picker (free tier):

| Action | Intent |
|---|---|
| Toggle ring on/off | `com.ringlight.tv.TOGGLE` |
| Cycle colour | `com.ringlight.tv.CYCLE_COLOR` |
| Cycle intensity | `com.ringlight.tv.CYCLE_INTENSITY` |

Suggested mappings (works on Hisense and most Android TV remotes):
- **APPS long-press** → Toggle
- **APPS double-click** → Cycle Intensity
- **YouTube double-click** → Cycle Colour

## Installation

1. Enable **Developer Options** and **USB Debugging** on your Android TV.
2. Build the APK in Android Studio (`Build → Build APK`) or download a release APK.
3. Sideload it to your TV:
   ```
   adb connect <TV-IP-address>
   adb install app-debug.apk
   ```
4. Open **TV RingLight** from the launcher to grant overlay permission.
5. Set up your button shortcuts in **Button Mapper**.

## Building from source

Requirements: Android Studio, JDK 17+

```bash
git clone https://github.com/nathanglasby/tv-ringlight.git
cd tv-ringlight
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

| Component | Purpose |
|---|---|
| `RingLightService` | Foreground service; owns the overlay window via `WindowManager` |
| `RingLightView` | Custom `View`; draws glow with 4 `LinearGradient` strips + 4 `RadialGradient` corners |
| `TrampolineActivity` | Transparent activity; forwards Button Mapper shortcut intents to the service instantly |
| `BootReceiver` | `BOOT_COMPLETED` receiver; restarts service and restores ring state |
| `MainActivity` | TV-friendly D-pad-navigable settings screen |
| `Prefs` | `SharedPreferences` wrapper |

## Notes

- Min SDK 26 (Android 8.0), Target SDK 34
- Package: `com.ringlight.tv`
- HDR / Dolby Vision content is unaffected — the overlay renders in the SDR compositor layer
- Uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` so the overlay never captures remote input
