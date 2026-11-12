@AGENTS.md

## Claude Code

- This is a Windows checkout: use `gradlew.bat`, and `adb` from the Android SDK's
  `platform-tools` folder (`%LOCALAPPDATA%\Android\Sdk\platform-tools`) if it is not on the PATH.
- After a change to anything under `app/src`, run `gradlew.bat testMockDebugUnitTest` before
  reporting on it.
- To see what the app is doing, read the log (`adb logcat -d -s Gildongmu AndroidRuntime`) and
  take a screenshot (`adb shell screencap -p /sdcard/screen.png`, then
  `adb pull /sdcard/screen.png`), then look at the image. Do this rather than reasoning about
  what the screen probably shows.
