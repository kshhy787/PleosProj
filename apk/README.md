# 제출용 APK

공모전 제출 규정에 따라 이 폴더에 `김상현_gildongmu_release.apk`를 둔다.

```
gradlew.bat assembleMockRelease
copy app\build\outputs\apk\mock\release\app-mock-release.apk apk\김상현_gildongmu_release.apk
```

Android Studio에서는 Build Variants 창에서 `mockRelease`를 고른 뒤 Build > Build APK(s)를 실행한다.

이 APK는 차 없이 대본대로 주행을 흉내 내는 `mock` 빌드다. 심사자가 Automotive 에뮬레이터에 설치해 데모를 볼 수 있도록 만들었다. 2026-11-12에 Pleos Connect 에뮬레이터(Android 14, API 34, 이미지 release_connect_v2.1.0)에서 같은 `mock` 빌드의 디버그 APK로 데모 주행을 출발부터 도착까지 돌렸다. 이 이미지에서는 Android 표준 음성 API가 엔진을 찾지 못해 소리 없이 자막으로만 진행했다(최상위 `README.md`의 "문제가 생기면"). 릴리스 APK도 코드가 같으므로 이 이미지에서는 마찬가지일 것이다.

릴리스 빌드지만 디버그 키로 서명하도록 설정되어 있다. 키스토어 없이 설치 가능한 APK를 만들기 위한 설정이며, 앱 마켓에 올릴 때는 `app/build.gradle.kts`의 `signingConfig`를 바꿔야 한다.

에뮬레이터에 설치:

```
adb install apk\김상현_gildongmu_release.apk
```

`adb`는 Android SDK의 `platform-tools` 폴더에 있다. 명령을 찾지 못한다고 나오면 전체 경로로 부른다. Android Studio가 SDK를 기본 위치에 설치했다면 Windows에서 그 경로는 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`이다. SDK를 다른 곳에 두었다면 그 SDK 위치 아래의 `platform-tools\adb.exe`를 부른다. SDK 위치는 Android Studio의 SDK Manager에 Android SDK Location으로 나오고, 이 프로젝트를 Android Studio로 연 적이 있으면 `local.properties`의 `sdk.dir`에도 적혀 있다.
