---
name: deploy-app
description: Build the Android app and install it on the connected phone. Use after any change under packages/app, instead of only compiling.
---

After changing anything in `packages/app`, don't stop at compiling — install it on the device and launch it.

1. `adb` is not on PATH. Use `~/Android/Sdk/platform-tools/adb`.
2. Check a device is connected: `adb devices -l`. If none, tell the user instead of skipping.
3. From `packages/app`, build and install (gradlew isn't executable, so go through `sh`):
   `sh ./gradlew -q :app:installDebug`
4. Launch it: `adb shell am start -n com.tresabhi.gyrocursor/.MainActivity`
5. Report that it was installed and launched. Say plainly if you couldn't verify behavior on the device.
