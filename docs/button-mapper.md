# Button Mapper setup and troubleshooting

RingLight provides an exported `android.intent.action.CREATE_SHORTCUT` activity
named **RingLight actions**. The picker returns a named, explicit activity intent
for Button Mapper to save. Launcher shortcuts are also available, but they are not
required for the picker to work.

## The shortcut does not appear

1. Open RingLight once after installing it.
2. In Button Mapper, choose a button, enable Customize, select a press action and
   switch the action category to Shortcuts.
3. Find RingLight actions and choose an action inside it. The actions are choices
   inside this picker, not necessarily separate entries in Button Mapper's list.
4. If Button Mapper was open during the update, close and reopen it to refresh
   its shortcut list.

With ADB, check whether Android can discover the picker:

```sh
adb shell cmd package query-activities --brief -a android.intent.action.CREATE_SHORTCUT -p com.ringlight.tv
```

The result should include `com.ringlight.tv/.ShortcutActivity`. If it does not,
confirm the installed APK is version 2.0 and that RingLight is enabled for the
current TV user profile.

## The assignment exists but does nothing

First open RingLight, grant display permission and use Turn light on. Then open
Remote setup and use Test actions. This test sends a command through the same
exported activity as a mapper assignment.

Replace any old assignment in Button Mapper using the new picker. Press the mapped
button while another TV app is visible, then return to RingLight's Remote setup.

| What you see | Next check |
| --- | --- |
| No new received timestamp | The command did not reach RingLight. Check the selected press type, the saved assignment and Button Mapper's accessibility access. |
| New timestamp, permission prompt | Allow display over other apps, then press the button again. |
| New timestamp, light stays off after colour or brightness | Those actions change the saved appearance. Use Toggle RingLight to turn it on. |
| New timestamp, Toggle still fails | Try the power control in RingLight and capture the logs below. This points to display permission or foreground service startup. |
| Test actions work, physical remote does not | Check Button Mapper's receipt of the button and any competing mapper. RingLight cannot receive a command the mapper never sends. |

A received timestamp confirms dispatch into RingLight. It does not prove that the
overlay was accepted by the TV window manager.

## Explicit intents and diagnostics

These commands use the same public entry point as the returned shortcut:

```sh
adb shell am start -W -n com.ringlight.tv/.TrampolineActivity -a com.ringlight.tv.TOGGLE
adb shell am start -W -n com.ringlight.tv/.TrampolineActivity -a com.ringlight.tv.CYCLE_COLOR
adb shell am start -W -n com.ringlight.tv/.TrampolineActivity -a com.ringlight.tv.CYCLE_INTENSITY
adb shell am start -W -n com.ringlight.tv/.TrampolineActivity -a com.ringlight.tv.TURN_OFF
```

The final command is safe to repeat. It will not turn an inactive light on.
Unknown external actions are ignored. The service itself is not exported.

```sh
adb logcat -s RingLightShortcut RingLightService RingLightBoot
adb shell dumpsys activity services com.ringlight.tv
```

When the light is off, there should be no running RingLight foreground service.
For a failure report, record the TV model, Android version, Button Mapper version,
APK checksum, selected remote press and whether the received timestamp changed.

## Implementation references

The [Android shortcut creation contract](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts)
explains `ACTION_CREATE_SHORTCUT` and the returned shortcut intent. The
[Button Mapper developer site](https://buttonmapper.app/) describes mapping
buttons to apps, shortcuts and actions. Physical TV compatibility is recorded
separately in [device validation](device-validation.md).
