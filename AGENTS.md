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

The code was written without access to an Android build environment. Be precise about this when
you report results, and update `README.md` ("지금 상태") when any of it changes.

- `app.gildongmu.core` (host logic, pure Kotlin): unit-tested, including a full scripted
  Seoul → Gangneung drive. This is the part to trust.
- Android code (`app.gildongmu.host`, the `mock` source set): typechecked only. If something on
  screen or in speech misbehaves, suspect this layer first, not the core.
- Gradle build files: written from the official compatibility tables, not executed by the author.
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

The default variant is `mockDebug`. It runs on any Android Automotive emulator and needs no SDK
beyond Android's. In the app, press **주행 시작** in the side panel to start the scripted drive.

To look at a running emulator from a terminal:

```
adb logcat -d -s Gildongmu AndroidRuntime        app log and crashes so far
adb shell screencap -p /sdcard/screen.png        screenshot, then:
adb pull /sdcard/screen.png
adb shell am start -n app.gildongmu.host/.MainActivity
```

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

1. **See the demo run on the emulator.** Build `mockDebug`, start it, press 주행 시작, take a
   screenshot and read the `진단:` line. Done when the card with the title and the caption is
   readable and the drive reaches Gangneung (about six and a half minutes).
2. **Get a voice.** The mock build speaks through Android's own text-to-speech engine. The stock
   Automotive image and the Pleos image may not have one (`Android 엔진 0개` in the diagnostics);
   then the demo is captions only, by design. On the Pleos emulator the voice comes from the
   Pleos TextToSpeech SDK: set `gildongmu.pleos=true` in `gradle.properties`, build `pleosDebug`,
   and fix the compile errors against the real SDK using `docs/pleos-integration.md`. Start with
   `PleosSpeaker.kt`; it is small. In the pleos build, press and hold the destination at the
   top left to start the same scripted drive in the car's voice (`PleosPlatform.startDemoDrive`).
   Done when the briefing is heard.
3. **Record the demo and build the APK.** `docs/demo-script.md` has the transcript with timings
   and a suggested short cut. The release APK goes in `apk/` under the name the contest asks for.
4. **Wire up what the pleos build still lacks**: turn-by-turn prompts (`onTBTInfo`), a real
   parked signal, the cabin light. `docs/pleos-integration.md` has the list.
