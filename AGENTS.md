# Working on 길동무

Notes for an AI coding assistant (or a person) picking this repository up. Read this first, then
`README.md` (Korean) for what the app does and what has and has not been verified.

## What this is

길동무 (Gildongmu) is a road-trip host for a car's infotainment screen: it reads the route, trip
time, battery and navigation prompts, and leads the trip by voice with stories about the places
being passed, quizzes, and rest-stop suggestions that become navigation waypoints. The target is
Pleos Connect (Android Automotive OS, API 34). It is an entry for the Hyundai Motor Group in-car
app contest; the judged qualities are fit for the car, originality, use of the Pleos SDK, and
business sense.

## Where things stand

The code was written without access to an Android build environment. On 2026-11-12, on a
Windows PC, Gradle was run for the first time (it built `mockDebug` and ran the unit tests) and the
app was run on an emulator for the first time; the list says what that and later runs covered.
Be precise about what has and has not been verified when you report results, and update
`README.md` ("지금 상태") when any of it changes.

- `app.gildongmu.core` (host logic, pure Kotlin): unit-tested, including a full scripted
  Seoul → Gangneung drive. All 133 tests also pass through Gradle (`testMockDebugUnitTest`). This
  is the part to trust.
- Android code (`app.gildongmu.host`, the `mock` source set): run on 2026-11-12 as `mockDebug` on
  the Pleos Connect emulator (Android 14, API 34, image `release_connect_v2.1.0`), in a
  1682×1210 dp window with the demo panel open. The whole scripted drive played to Gangneung; the
  app never crashed, and its own log (tag `Gildongmu`) had no warnings or errors. In the seven
  screenshots taken after **주행 시작**, the card and the parked panel drew without clipping, and
  every caption in them fit on one line. Seven moments were compared with `docs/demo-script.md`
  (the captions at about 10, 20, 30 and 40 s, and the switches to the parked panel, back and to
  arrival) and all came within two seconds of it. That proves little, because without a voice
  each sentence lasts its estimated time, the same estimate the script was made with. The app had
  no voice: Android's `TextToSpeech` finds no engine on that image (`README.md`, "문제가 생기면"),
  so the run was captions only and no sentence was ever handed to an engine. The speaking path
  (playback, its progress reports, audio focus) has therefore never run on a device. On
  2026-11-14, on the waiting screen before **주행 시작**, **패널 숨기기** hid the panel, and a tap
  on the card or the status row, or a long press on the destination, brought it back. That day
  the emulator gave the app a 1682×1172 dp window lower on the screen, its bottom 128 dp or so
  under the Pleos bottom bar. Not tried yet: the stock Automotive emulator; the microphone and
  speech recognition (**자동 응답** in the side panel stayed on); any other button. The card alone
  at full width with the panel hidden (the only layout the pleos build uses) has been seen only
  with the waiting card, not during a drive; the layouts for narrow or low windows have been
  tested only as rules and never seen on a screen. On 2026-11-14 a toast the app showed was not in
  a screenshot taken about a second later, and the image's system UI logged "Attempt to hide
  non-current toast"; no other toast was tried, so put messages in the app's own views. If
  something on screen or in speech misbehaves, suspect this layer first, not the core.
- Gradle build files: on 2026-11-12 `assembleMockDebug` and `testMockDebugUnitTest` succeeded on
  Windows (Gradle 9.3.1, AGP 9.1.1). The Gradle daemon ran on the JetBrains Runtime 25 that comes
  with Android Studio, not on the JDK 17 in `JAVA_HOME`. The daemon's JDK is set by
  `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=25`), a file that appeared when Android
  Studio first opened the project and is committed, so every checkout has it. The command-line
  builds of 2026-11-12 reused the daemon Android Studio had started. On 2026-11-14 that daemon was
  gone, so a command-line build started one. Android Studio's JetBrains Runtime 25 was on the PC
  and `JAVA_HOME` was JDK 17, but the build used neither: it downloaded Java 25 (Eclipse Adoptium
  25.0.3, a 141 MB zip; with the unpacked copy the folder holds over 400 MB) into
  `%USERPROFILE%\.gradle\jdks` from the address in that file, ran the daemon on it, and the 133
  tests passed there. Whether to keep the file is not decided; until it is, leave it alone like
  the other build tool versions. The build has not been tried with JDK 17 alone.
  `assembleMockRelease` and the pleos build have not been run.
- `pleos` source set (the Pleos Connect SDK adapters): never compiled against the real SDK. Off
  by default. `docs/pleos-integration.md` lists every assumption to check, file by file.
- `pipeline/` (Python): tested with stand-ins for Wikipedia, TourAPI and Claude; never run live.

## Layout

```
app/src/main/java/app/gildongmu/core/    host engine and its helpers; no Android imports
    HostEngine.kt      state machine: handle(signal, nowMs) -> actions
    Signals.kt         Signal (car -> host), Action (host -> car), HostSnapshot (for the screen)
    SpeechGuard.kt     decides when a spoken sentence is over, whatever the engine does
    AudioFocusKeeper.kt, ScreenFit.kt, ParkedDetector.kt, Korean.kt, Understanding.kt ...
    sim/               MockDrive (scripted car), Scenario, DemoSession
app/src/main/java/app/gildongmu/host/    Activity, screen (views built in code), speech wrappers
app/src/mock/                            mock build: demo panel, scripted drive, Android speech
app/src/pleos/                           pleos build: Pleos Connect SDK adapters
app/src/main/assets/routes/              route package (stories, quizzes, stops)
app/src/test/                            JUnit 4 tests for core
pipeline/                                Python tool that builds route packages
docs/                                    demo script, Pleos integration notes
```

The host never sees SDK types. A platform turns SDK callbacks into `Signal`s and carries out the
`Action`s the host returns. Keep it that way: it is why the logic can be tested without a car.

## Build, run, test (Windows)

```
gradlew.bat testMockDebugUnitTest        unit tests
gradlew.bat assembleMockDebug            debug APK, mock build
gradlew.bat assembleMockRelease          release APK (see apk/README.md)
```

The default variant is `mockDebug`. It needs no SDK beyond Android's. It is meant for the
Automotive emulator that `README.md` ("실행하기") names, but so far it has only run on the Pleos
Connect emulator, where the demo is captions only. In the app, press **주행 시작** in the side
panel to start the scripted drive.

To look at a running emulator from a terminal:

```
adb logcat -d -s Gildongmu AndroidRuntime        app log and crashes so far
adb shell screencap -p /sdcard/screen.png        screenshot, then:
adb pull /sdcard/screen.png
adb shell am start -n app.gildongmu.host/.MainActivity
```

From Git Bash, put `MSYS_NO_PATHCONV=1` in front of these: otherwise Git Bash rewrites
`/sdcard/screen.png` into a Windows path and `screencap` fails. With the prefix, give local paths
in Windows form (`cygpath -w` converts one).

The mock build writes a line starting `진단:` to the log (tag `Gildongmu`) and shows the same
facts at the bottom of the side panel: window size in dp and the arrangement chosen for it, the
speech engine and how it has behaved, whether speech recognition exists, device and build. Read
that line before guessing why something is silent or invisible.

`pipeline/`: `pip install -e ".[dev]"`, then `python -m pytest -q`. See `pipeline/README.md`.

## Rules for changes

- Decisions go in `core` with a test; Android classes only wire them up. `SpeechGuard`,
  `AudioFocusKeeper` and `ScreenFit` exist because timing, focus and layout bugs cannot be seen
  from a desk, and each has tests that fail when its protection is removed. Follow that pattern.
- Run the unit tests before saying something works, and say plainly what you could not run.
- The host must never be left waiting: every sentence handed to a speaker ends, every
  "audio unavailable" is followed by "available", every question times out.
- While the car moves the screen is one card and one button. No text entry, no lists, no video.
  Anything else belongs on the parked panel.
- No new library dependencies without a reason; the app has none besides JUnit for tests.
- Story and quiz text must be speakable (no brackets, hanja or symbols) and must not open as if
  the car were on the spot ("지금 지나는…", "여기는…"): the host also tells stories ahead of a
  place and at rest stops. `RoutePackageParserTest` and `pipeline` check both.
- Facts in stories come from the sources listed in the route package. Do not add facts from
  memory.
- Never commit API keys or keystores. `pipeline` reads keys from environment variables.
- Leave the build tool versions alone unless a build error names them.

## What needs doing next

In this order. Each item says how to tell it is done.

1. **See the demo run on the emulator.** Done on 2026-11-12, on the Pleos Connect emulator: the
   card's title and caption were readable and the drive reached Gangneung about six minutes after
   주행 시작, with captions only. To repeat it: build `mockDebug`, start it, press 주행 시작, take
   a screenshot and read the `진단:` line.
2. **Get a voice.** The mock build speaks through Android's `TextToSpeech`, which needs an engine
   on the image. On the Pleos Connect image used on 2026-11-12 (`release_connect_v2.1.0`) it finds
   none (`Android 엔진 0개` in the diagnostics): the image's speech service (`SpeechService` in
   `ai.pleos.playground.caas`) has no DEFAULT category in its intent filter, and `TextToSpeech`
   only finds engines that have one. From the Android 14 source, naming the engine would not help
   and the app's manifest cannot fix it; neither has been tried on the device. Whether the stock
   Automotive image has an engine is still unknown. Without one the demo is captions only, by
   design. On the Pleos emulator the voice is meant to come from the Pleos TextToSpeech SDK, which
   has not been tried yet: set `gildongmu.pleos=true` in `gradle.properties`, build `pleosDebug`,
   and fix the compile errors against the real SDK using `docs/pleos-integration.md`. Start with
   `PleosSpeaker.kt`; it is small. In the pleos build, press and hold the destination at the top
   left to start the same scripted drive in the car's voice (`PleosPlatform.startDemoDrive`). Done
   when the briefing is heard; an AI assistant cannot hear the emulator, so ask the user.
3. **Record the demo and build the APK.** `docs/demo-script.md` has the transcript with timings
   and a suggested short cut. The release APK goes in `apk/` under the name the contest asks for.
   Until item 2 is done, a recording on the Pleos Connect emulator is captions only. So is the
   release APK on that emulator, because it is the mock build (see `apk/README.md`).
4. **Wire up what the pleos build still lacks**: turn-by-turn prompts (`onTBTInfo`), a real
   parked signal, the cabin light. `docs/pleos-integration.md` has the list.
