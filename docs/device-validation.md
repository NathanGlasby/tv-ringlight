# Device validation

No physical TV test has been recorded for the version 2 rebuild. Automated checks
cover settings, timers, service state, shortcut discovery and returned intents,
permission recovery, preview controls and TV layout bounds. They do not emulate a
remote's firmware, Button Mapper's accessibility service, or the TV compositor.

## Record the setup

| Field | Value |
| --- | --- |
| Commit | Pending |
| APK SHA-256 | Pending |
| TV model | Pending |
| Android version | Pending |
| Button Mapper version | Pending |
| Remote button and press type | Pending |
| Tester and date | Pending |

## Complete one full pass

1. Fresh setup: install, open RingLight and grant display permission using only
   the D-pad. Confirm Back returns from settings without enabling the light.
2. Editor: reach all eight colours and every stepper. Check focus at the lowest
   and highest values. Confirm the preview changes while the light is off.
3. Overlay: turn on the light and open a video app. Check all four edge/corner
   joins, remote navigation, playback and the appearance of HDR content if used.
4. Shortcut discovery: create a new Button Mapper assignment through Shortcuts,
   RingLight actions. Record that the picker appears.
5. Shortcut execution: test Toggle, Cycle colour, Cycle brightness and Turn off
   over another app. Check the received timestamp. Repeat Turn off while already off.
6. Timer: select five minutes, wait two minutes, change only the colour and check
   that the light turns off at the original deadline. Repeat after changing the
   timer duration and after disabling it.
7. Lifecycle: verify no foreground service remains while off. Check restoration
   after a process restart and reboot, both with time remaining and after expiry.
8. Permission: remove display permission while the light is on. Check that the
   light and notification disappear. Use a mapped Toggle and confirm setup opens.
9. Screen sleep: leave an active five-minute session asleep past its deadline,
   then wake the TV. Confirm the expired light stays off.
10. Recovery: restore defaults and verify that the current power state is kept.

Record observed results and logs for failures. Only mark an item passed after
observing it on the named device and APK.
