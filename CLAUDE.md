@AGENTS.md

## Claude Code

- This is a Windows checkout: use `gradlew.bat` (`.\gradlew.bat` from PowerShell, `./gradlew.bat`
  from Git Bash). If `adb` is not on the PATH, run it from the `platform-tools` folder of the
  Android SDK that `sdk.dir` in `local.properties` names.
- After a change to anything under `app/src`, run `gradlew.bat testMockDebugUnitTest` before
  reporting on it.
- To see what the app is doing, read the log (`adb logcat -d -s Gildongmu AndroidRuntime`) and
  take a screenshot (`adb shell screencap -p /sdcard/screen.png`, then
  `adb pull /sdcard/screen.png`), then look at the image. Do this rather than reasoning about
  what the screen probably shows. The Bash tool here is Git Bash: put `MSYS_NO_PATHCONV=1` in
  front of `adb` commands that name a device path such as `/sdcard/screen.png`, or Git Bash
  rewrites the path into a Windows one and the command fails. With that prefix, give local paths
  in Windows form (`cygpath -w` converts one).
