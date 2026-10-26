---
name: run-app
description: Build, install and launch the Receive Money Sound Android app on a connected device or emulator, then screenshot it and read its logcat. Use when asked to run, start, screenshot or try the app, or to confirm a change works on a device rather than only in unit tests.
---

# Run the app on a device

Package: `com.example.receivemoney`. Launcher activity: `.MainActivity`.

## 1. Check a device is connected

```bash
adb devices
```

No device listed: stop and ask the user to plug in a phone (USB debugging on) or start an
emulator (`emulator -list-avds`, then `emulator -avd <name> &`). Do not continue without one.
More than one device: pass `-s <serial>` to every `adb` command below.

## 2. Test, build and install

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

If the unit tests fail, report them and stop. Don't install a build with failing tests unless
the user asks.

## 3. Launch fresh and capture logs

```bash
adb shell am force-stop com.example.receivemoney
adb logcat -c
adb shell am start -n com.example.receivemoney/.MainActivity
```

Wait ~2 s, then:

```bash
adb logcat -d --pid=$(adb shell pidof com.example.receivemoney) | tail -200
adb logcat -d -b crash | tail -50
```

## 4. Screenshot

Save in the session scratchpad and read it with the Read tool to see the screen:

```bash
adb exec-out screencap -p > <scratchpad>/screen.png
```

## 5. Report

Say what you saw: launched or crashed (quote the shortest decisive stack line), what is on
screen, and any warnings about TTS engines or missing voices. Playing sound can't be verified
from logs alone; for audio, point to `docs/MANUAL_TEST_CHECKLIST.md` and ask the user to listen.
