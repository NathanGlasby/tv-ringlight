# RingLight

RingLight adds a soft band of light around your Android TV screen while you keep
watching. Adjust the colour, brightness and width with your remote, or assign a
button to switch the light without opening the app.

Version 2 rebuilds the editor, overlay service, timer and remote integration.
It keeps the original `com.ringlight.tv` app ID and shortcut action names.

## Use it

Requires Android TV 8.0 or newer.

1. Install the debug APK from a successful [Android workflow run](../../actions/workflows/android.yml),
   or build it using the instructions below.
2. Open **RingLight** on your TV.
3. Choose **Open settings** and allow RingLight to display over other apps.
4. Return and choose **Turn light on**.

The live preview shows your chosen colour, brightness and width even when the
screen light is off. Settings save automatically. The light stays over other apps
and does not take remote focus.

| Control | Choices |
| --- | --- |
| Colour | White, warm white, yellow, red, green, blue, purple, pink |
| Brightness | 25%, 50%, 75%, 100% |
| Width | 5% to 40% of the screen's shorter side, in 5% steps |
| Sleep timer | 5 minutes to 8 hours, or disabled |

The default is white, full brightness, 15% width and a three-hour timer.
**Restore defaults** resets the controls and preserves the current on or off state.

## Set up Button Mapper

The app now has a **RingLight actions** shortcut picker. It supports the Android
`CREATE_SHORTCUT` contract used by shortcut pickers, independently of the TV
launcher's support for static shortcuts.

In Button Mapper:

1. Choose a remote button and enable **Customize**.
2. Choose the press action you want to assign.
3. Select **Shortcuts**, then **RingLight actions**.
4. Choose **Toggle RingLight**, **Cycle colour**, **Cycle brightness**, or
   **Turn off RingLight**.
5. Replace any old RingLight assignment with this new one and try it over another TV app.

Button Mapper's accessibility service must be enabled for it to receive your
remote presses. Menu wording can vary by version.

Open **Remote setup** in RingLight to test the same action entry point and see the
last command received. If its timestamp changes after a remote press, the command
reached RingLight. If it stays unchanged, check the mapping and Button Mapper's
accessibility access.

See [Button Mapper troubleshooting](docs/button-mapper.md) for both missing
shortcuts and shortcuts that do nothing. The Android contracts have automated
coverage; the rebuilt app still needs a test with a physical TV and Button Mapper.

## Timer and background behaviour

The foreground service runs only while the light is on. Colour and brightness
shortcuts can change the saved setting while it is off without starting a service.
The notification's **Turn off** action always turns the light off.

Changing timer settings starts the newly selected duration immediately. Changing
colour, width or brightness leaves the running timer alone. A process restart
keeps the remaining time. The timer uses elapsed time on the same boot, and falls
back to a saved clock deadline after a reboot. It checks for expiry when the screen
wakes, so time spent asleep counts toward the timer.

After a reboot, RingLight restores an active light only if display permission is
still available and the timer has not expired. Some TV firmware blocks background
starts. Opening RingLight can restore an unexpired session in that case. A force
stop requires opening the app again before Android allows its receivers to run.

The overlay shifts by two pixels every 30 seconds. Display behaviour, HDR
composition and remote button interception depend on the TV and need device testing.

## Build and test

Use JDK 17 and an Android SDK with **Android 34** and **Build Tools 34.0.0** installed.
Set `ANDROID_HOME` to your SDK, or set `sdk.dir` in an untracked `local.properties`.
The checked-in Gradle wrapper includes its JAR and a distribution SHA-256 checksum.

```sh
git clone https://github.com/NathanGlasby/tv-ringlight.git
cd tv-ringlight
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, run `gradlew.bat` with the same tasks. The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. Test and lint reports are in
`app/build/reports/`.

```sh
adb connect YOUR_TV_IP:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android only accepts an in-place update when the APK uses the same signing key as
the installed app. Local and CI debug builds may have different keys. For an
update that preserves installed data, build with your original signing key.
Release signing is not configured in this repository.

CI runs the regression suite, Android lint and the debug build for pushes and pull
requests, and saves the APK and reports as artifacts. The tests use JVM crypto
providers so the behavioural suite also runs on Linux ARM64. Native graphics and
physical remote behaviour are outside that suite.

## Project structure

| File | Responsibility |
| --- | --- |
| `MainActivity` | TV editor, live preview, display permission and remote diagnostics |
| `RingLightView` | Cached edge and corner gradients shared by preview and overlay |
| `RingLightService` | Overlay window, foreground notification and lifecycle cleanup |
| `AutoOffTimer` | Timer deadlines across clock changes, process restarts and reboots |
| `Prefs` / `RingLightSettings` | Validated saved settings and shared control ranges |
| `ShortcutActivity` | Mapper discovery, shortcut results and the in-app action test |
| `TrampolineActivity` | Validated command receipt, permission recovery and dispatch |
| `BootReceiver` | Conditional restoration after reboot |

The app has no network permission, accounts or analytics. Settings and the latest
shortcut receipt stay on the TV. Display permission is used for the light overlay;
foreground service permission keeps it running while it is visible.

Before calling a build device-tested, complete [device validation](docs/device-validation.md).
