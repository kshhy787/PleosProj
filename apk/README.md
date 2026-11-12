# 제출용 APK

공모전 제출 규정에 따라 이 폴더에 `팀명_길동무_release.apk`를 둔다.

```
gradlew.bat assembleMockRelease
copy app\build\outputs\apk\mock\release\app-mock-release.apk apk\팀명_길동무_release.apk
```

Android Studio에서는 Build Variants 창에서 `mockRelease`를 고른 뒤 Build > Build APK(s)를 실행한다.

이 APK는 `mock` 빌드다. 차 없이 대본 주행으로 동작하므로 심사자가 Automotive 에뮬레이터에 설치해 바로 볼 수 있다. 릴리스 빌드지만 디버그 키로 서명되어 있다. 키스토어 없이 설치 가능한 APK를 만들기 위한 설정이며, 앱 마켓에 올릴 때는 `app/build.gradle.kts`의 `signingConfig`를 바꿔야 한다.

에뮬레이터에 설치:

```
adb install apk\팀명_길동무_release.apk
```

`adb`는 Android SDK의 `platform-tools` 폴더에 있다. 명령을 찾지 못한다고 나오면 전체 경로로 부른다. Windows의 기본 위치는 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`이다.
