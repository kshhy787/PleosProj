# 길동무

가족 장거리 이동을 진행하는 차량용 로드트립 호스트. 차가 알려 주는 경로, 주행 시간, 배터리, 내비 안내를 읽고, 지금 지나는 곳의 이야기와 퀴즈, 쉬어 갈 곳 제안을 음성으로 이끈다. 제안을 받아들이면 내비에 경유지가 추가된다.

현대자동차그룹 차량용 앱 개발 공모전 출품작. 대상 플랫폼은 Pleos Connect(Android Automotive OS 기반)이다.

## 지금 상태

코드는 두 가지 빌드로 나뉜다. `mock`은 차 없이 대본대로 주행을 흉내 내는 빌드이고, `pleos`는 Pleos Connect SDK에 연결하는 빌드다. 호스트의 판단 로직(`app.gildongmu.core`)은 두 빌드가 똑같이 쓴다.

| 부분 | 상태 |
| --- | --- |
| 호스트 엔진과 데모 주행 (`core`) | 단위 테스트 133개 통과. Kotlin 2.2.10으로 컴파일해 JVM에서 실행했고, 2026-11-12에 Gradle(`testMockDebugUnitTest`)로도 133개 모두 통과했다. 서울 → 강릉 데모를 처음부터 끝까지 돌리는 테스트 포함. |
| 앱 화면, 음성 래퍼, `mock` 플랫폼 | 2026-11-12에 Pleos Connect 에뮬레이터(Android 14, API 34)에서 `mockDebug`로 데모 주행을 출발부터 도착까지 돌렸다. 앱 창 1682×1210dp에서 주행 카드와 정차 화면(점수, 경로 그림)이 잘리지 않고 그려졌고, 장면은 [데모 대본](docs/demo-script.md)의 시각과 1~2초 안에서 맞았다. 강제 종료는 없었고 앱이 남긴 경고나 오류 로그도 없었다. **소리는 나지 않았다.** 이 이미지에서는 Android 표준 음성 API가 엔진을 찾지 못해 자막으로만 진행했다(아래 "문제가 생기면"). **표준 Automotive 에뮬레이터에서는 실행해 보지 못했고, 마이크와 음성 인식도 써 보지 않았다**(자동 응답을 켠 채 돌렸다). |
| Gradle 빌드 설정 | 2026-11-12에 Windows의 JDK 17에서 `gradlew.bat assembleMockDebug`와 `gradlew.bat testMockDebugUnitTest`가 성공했다(Gradle 9.3.1, AGP 9.1.1). 버전은 공식 호환표로 맞췄다(아래 "빌드 구성"). **`assembleMockRelease`와 `pleos` 빌드는 아직 돌려 보지 않았다.** |
| `pleos` 플랫폼 (SDK 어댑터) | 공개 가이드 문서를 보고 작성했고 **SDK에 대해 컴파일해 보지 못했다.** 기본값으로 꺼져 있다. 확인할 항목은 [docs/pleos-integration.md](docs/pleos-integration.md). |
| 콘텐츠 파이프라인 (`pipeline`) | 테스트 49개 통과(위키백과·TourAPI·Claude를 대역으로 바꿔 실행). **실제 API로는 돌려 보지 못했다.** |
| 번들 콘텐츠 (서울 → 강릉) | 이야기와 퀴즈는 손으로 쓰고 위키백과 문서와 대조했다. **경로선과 장소·휴게소 좌표, 충전기 유무는 데모용 근사값이다.** |

## 실행하기

Android Studio와 Automotive 에뮬레이터(API 34, "Android Automotive with Google APIs x86_64", 기기 프로필 "Automotive (1408p landscape)")가 필요하다. Pleos Connect 에뮬레이터에서도 실행되지만, 그 이미지에서는 소리 없이 자막으로만 진행된다(아래 "문제가 생기면").

1. 이 폴더를 한글과 공백이 없는 짧은 경로에 둔다. 예: `C:\dev\gildongmu`
2. Android Studio에서 **Open**으로 이 폴더를 연다. 첫 Gradle sync는 Gradle 9.3.1, Android Gradle Plugin 9.1.1, SDK Platform 36, Build-Tools를 내려받는다.
3. 기기 목록에서 Automotive 에뮬레이터를 고르고 **Run**을 누른다. Build Variant는 기본값 `mockDebug`다.
4. 앱이 뜨면 오른쪽 패널의 **주행 시작**을 누른다.

명령줄에서는 다음과 같다. Android Studio에서 한 번 연 뒤에는 SDK 위치가 `local.properties`에 적혀 있어서 그대로 된다. JDK 17 이상이 필요한데, 따로 설치하지 않았다면 `JAVA_HOME`을 Android Studio에 든 JDK로 잡는다(Windows 기본 위치는 `C:\Program Files\Android\Android Studio\jbr`).

```
gradlew.bat assembleMockDebug          디버그 APK
gradlew.bat testMockDebugUnitTest      단위 테스트
gradlew.bat assembleMockRelease        제출용 APK (apk/README.md 참고)
```

## 데모 주행

서울 양재에서 강릉 경포해변까지 약 205km를 6분 남짓으로 압축해 보여 준다. 위치, 주행 시간, 배터리, 정차, 내비 안내는 대본(`app/src/mock/assets/scenarios/seoul-gangneung-demo.json`)이 만들고, 호스트는 그것을 실제 차의 신호와 구별하지 못한다.

순서는 이렇다. 출발 브리핑 → 여주에서 세종대왕릉 이야기 → 아이가 "퀴즈 하자" → 퀴즈 두 문제 → 원주에서 치악산 이야기 도중 내비 안내가 나와 말을 멈췄다가 이어 감 → 주행 2시간째에 횡성휴게소 제안 → "응" → 경유지 추가 → 정차와 충전, 정차 화면 열림 → 봉평 이야기 긴 버전과 퀴즈 결승 → 대관령 → 오죽헌 → 도착, 오늘의 기록. 전체 대본은 [docs/demo-script.md](docs/demo-script.md)에 있다.

오른쪽 패널로 할 수 있는 일:

- **자동 응답**: 켜 두면 탑승자 대답이 대본대로 나와서 손을 대지 않고 녹화할 수 있다. 켜져 있는 동안에는 마이크를 열지 않는다. 방 안의 소리가 대본보다 먼저 대답해 버리면 매번 다른 데모가 되기 때문이다. 끄면 마이크 권한을 묻고, 그때부터 직접 말하거나 버튼으로 답한다.
- **자막**: 호스트가 말하는 문장을 카드에 보여 준다. 실제 주행 화면에서는 꺼져 있는 기능이고, 녹화용으로 켠다.
- **말하기 버튼**: 마이크 대신 눌러서 말한다. 음성 인식이 없는 에뮬레이터에서도 데모가 된다.
- **패널 숨기기**: 카드만 남긴다. 왼쪽 위 목적지 글자를 길게 누르면 다시 나온다.
- **진단**: 패널 맨 아래에 창 크기와 배치, 음성 엔진의 상태, 음성 인식 유무, 기기와 빌드 날짜가 작은 글씨로 나온다. 무언가 안 될 때 이 부분이 보이게 화면을 찍으면 원인을 바로 알 수 있다. 같은 내용이 Logcat에 `진단:`으로 시작하는 줄로도 남는다.

화면은 주어진 창 크기에 맞춰 배치를 바꾼다. 넓은 화면에서는 카드와 패널이 나란히 놓이고, 분할 화면처럼 좁은 창에서는 카드가 위, 패널이 아래로 간다. 낮거나 좁은 창에서는 카드 글씨가 작아지고 자막 줄 수가 늘어난다.

에뮬레이터에 한국어 음성이 없으면 호스트는 소리 없이 자막만으로 진행하고, 패널의 "말하기" 줄에 이유가 표시된다. 음성 엔진이 있어도 실제로 소리를 내지 못하고 문장을 바로 끝냈다고 알려 오는 경우가 있는데, 그때도 문장을 읽을 시간만큼 자막을 띄워 둔다. 음성을 들으려면 에뮬레이터의 설정 → 시스템 → 언어 및 입력 → 텍스트 음성 변환 출력에서 한국어 음성 데이터를 설치한다. Automotive 이미지에 음성 엔진이 들어 있는지는 확인하지 못했다. 그 메뉴에 엔진이 없으면 이 이미지에서는 자막으로만 진행된다.

음성 엔진이 예상보다 느려 이야기가 다음 장면에 밀리면, 대본의 해당 구간 `timeScale`을 낮춘다.

## 호스트가 판단하는 방식

우선순위는 고정이다.

1. 안전: 차량 경고가 뜨거나 내비 음성 안내가 임박하면 말을 멈춘다. 안내가 끝나면 끊긴 문장부터 다시 말한다.
2. 정차 제안: 주행 2시간이 지났거나 배터리가 20% 이하이고, 휴게소가 2~15km 앞에 있을 때. 대답이 "응"이면 경유지를 추가한다.
3. 장소 이야기: 장소 4km 전부터. 다음에 말해야 할 일까지 남은 시간에 맞는 가장 긴 버전을 고르고, 짧은 버전도 못 끝낼 상황이면 건너뛴다.
4. 퀴즈: 탑승자가 청하거나 3분쯤 조용할 때. 차례를 돌아가며 묻는다.
5. 그 밖에는 조용히 있는다.

"조용히"라고 하면 다음 정차까지 정차 제안만 남기고 말하지 않는다. 정차하면 다음 이야기를 바로 시작하지 않고 들을지 묻는다. 휴게소에서는 차에 아무도 없을 수 있어서다. 대답이 없으면 조용히 기다리고, 정차 화면의 버튼이나 "이야기해 줘"로 다시 청할 수 있다.

주행 중 화면은 카드 한 장과 건너뛰기 버튼 하나뿐이다. 점수판과 경로 그림, 바로가기 버튼은 정차 중에만 열린다.

## 구조

```
app/src/main/java/app/gildongmu/core/     호스트 엔진. Android에 의존하지 않는 순수 Kotlin
    HostEngine.kt                         신호를 받아 할 일을 정하는 상태 기계
    Signals.kt                            차 → 호스트(Signal), 호스트 → 차(Action)
    HostRuntime.kt                        엔진과 플랫폼을 잇는 층
    SpeechGuard.kt                        음성 엔진이 어떻게 굴든 문장이 제때 끝나게 하는 부분
    AudioFocusKeeper.kt, ScreenFit.kt     오디오 채널 점유, 창 크기에 맞춘 화면 배치
    sim/                                  대본 주행(MockDrive)과 데모 세션
app/src/main/java/app/gildongmu/host/     화면, Android 음성 래퍼, 액티비티
app/src/mock/                             mock 빌드: 대본 주행, 데모 패널, 데모 대본
app/src/pleos/                            pleos 빌드: Pleos Connect SDK 어댑터
app/src/main/assets/routes/               경로 패키지 (서울 → 강릉)
app/src/test/                             단위 테스트
pipeline/                                 경로 패키지를 만드는 Python 도구
docs/                                     데모 대본, Pleos 연동 메모
```

호스트는 SDK 타입을 직접 보지 않는다. 플랫폼이 SDK 콜백을 `Signal`로 바꿔 넣고, 호스트가 내놓는 `Action`을 SDK 호출로 실행한다.

| 호스트가 받는 신호 | Pleos SDK |
| --- | --- |
| 경로 안내 시작, 목적지 도착, 회전 안내 | NaviHelper |
| 위치 | FusedLocation |
| 주행 시간과 거리, 배터리와 충전 | Vehicle (Odometer, EvBattery) |
| 탑승자가 한 말 | SpeechToText |

| 호스트가 시키는 일 | Pleos SDK |
| --- | --- |
| 말하기, 말 멈추기 | TextToSpeech |
| 듣기 | SpeechToText |
| 경유지 추가 | NaviHelper |
| 정답·도착 조명 연출 | Vehicle (Light) |

## 빌드 구성

- Android Gradle Plugin 9.1.1, Gradle 9.3.1. Kotlin은 AGP 9의 내장 Kotlin을 쓰므로 Kotlin 플러그인을 따로 적용하지 않는다.
- compileSdk 36, targetSdk 35, minSdk 28(Pleos Connect 요구 사항), Java 17.
- 외부 라이브러리 의존성은 테스트용 JUnit 4 하나다. 화면은 레이아웃 XML 없이 코드로 만든다.
- 버전은 Android 개발자 문서의 호환표를 따랐다. AGP 9.1은 Gradle 9.3.1 이상을 요구하고, Android Studio 2026.2.2는 AGP 7.1~9.5를 지원하므로 9.1.1을 그대로 연다. 업그레이드를 권하는 알림은 무시해도 된다.

`pleos` 빌드를 켜려면 `gradle.properties`에서 `gildongmu.pleos=true`로 바꾼다. SDK 저장소(nexus-playground.pleos.ai)에 접근할 수 있어야 한다.

## 테스트

```
gradlew.bat testMockDebugUnitTest
```

`DemoDriveTest`는 데모 전체를 소리와 화면 없이 돌려서 장면이 순서대로 다 나오는지, 잘린 이야기가 없는지 확인하고 대본을 출력한다. 음성이 25% 느리거나 20% 빠른 경우도 같이 돌린다.

## 콘텐츠 파이프라인

경로를 주면 그 길의 장소를 찾고, 장소마다 이야기 두 가지 길이와 퀴즈를 써서 앱이 읽는 경로 패키지를 만든다. 사용법은 [pipeline/README.md](pipeline/README.md).

```
cd pipeline
pip install -e ".[dev]"
python -m gildongmu_pipeline check ../app/src/main/assets/routes/seoul-gangneung.json
```

## 문제가 생기면

- **Gradle sync 실패**: 메시지 전문이 필요하다. AGP 버전을 문제 삼으면 최상위 `build.gradle.kts`의 버전을, Gradle 버전을 문제 삼으면 `gradle/wrapper/gradle-wrapper.properties`의 버전을 메시지가 권하는 값으로 바꾼다.
- **앱이 바로 꺼짐**: Android Studio의 Logcat 창에서 `AndroidRuntime` 또는 `Gildongmu`로 걸러 나오는 오류 전문이 필요하다.
- **카드나 자막이 안 보임**: 패널 맨 아래 "진단"의 "화면" 줄이 보이게 화면을 찍는다. 창 크기와 배치가 적혀 있다.
- **Pleos 에뮬레이터에서 소리가 안 남**: `mock` 빌드는 Android의 표준 음성 API(`TextToSpeech`)로 말한다. 진단의 "말하기" 줄에 "Android 엔진 0개"라고 나오면 그 API가 쓸 수 있는 엔진이 없다는 뜻이고, `mock` 빌드로는 소리를 낼 수 없다. Pleos Connect 에뮬레이터(API 34)가 그렇다. 음성 서비스(`ai.pleos.playground.caas`의 `SpeechService`)가 들어 있기는 하지만 인텐트 필터에 DEFAULT 카테고리가 없어서, 엔진을 `MATCH_DEFAULT_ONLY`로 찾는 `TextToSpeech`에는 보이지 않는다. 엔진 이름을 직접 지정해도 마찬가지이고, 앱 매니페스트로는 바꿀 수 없다. Pleos 에뮬레이터에서 소리를 내려면 Pleos TextToSpeech SDK를 쓰는 `pleos` 빌드가 필요하다. `pleos` 빌드에는 같은 대본 주행을 차량 음성으로 돌리는 기능이 있다([docs/pleos-integration.md](docs/pleos-integration.md)).
- **소리가 안 남**: 위 "데모 주행"의 한국어 음성 설치를 본다. 에뮬레이터 볼륨도 확인한다. Automotive 이미지에 음성 엔진이 없어서 소리를 낼 수 없다면, Google 음성 엔진이 든 일반 태블릿 에뮬레이터(가로 화면)에 같은 앱을 실행하는 방법이 있다. 이 방법은 시험해 보지 못했다.
- **말을 못 알아들음**: 먼저 패널에서 자동 응답을 끈다. 켜져 있으면 마이크를 쓰지 않는다. 그다음 에뮬레이터 확장 컨트롤의 Microphone에서 호스트 마이크 사용을 켜고, 앱에 마이크 권한을 준다. 패널의 "듣기" 줄이 "음성 인식 없음"이면 그 이미지에는 인식 서비스가 없는 것이니 버튼을 쓴다. 마이크는 호스트가 질문한 뒤에만 열린다(아래 "알려진 한계").

## 알려진 한계

- **탑승자가 먼저 거는 말은 음성으로 받지 못한다.** 호스트는 질문을 한 뒤에만 마이크를 연다. "퀴즈 하자", "조용히", "이야기해 줘"처럼 탑승자가 먼저 꺼내는 말은 `mock` 빌드에서 패널 버튼과 대본으로만 들어간다. 호스트 엔진은 이런 말을 언제든 처리하지만, 차에서 그 말을 받아 올 진입점(호출어, 스티어링 휠 버튼, 차량 음성 비서 연동)을 만들지 않았다. 주행 중에 말을 멈추게 하는 방법은 지금은 카드의 건너뛰기 버튼이다.
- **경로가 바뀌는 경우를 처리하지 않는다.** 위치는 패키지에 든 경로선을 기준으로 잰다. 내비가 경로를 다시 잡거나 다른 길로 가면 이야기 위치가 맞지 않는다.
- **경로가 하나다.** 서울 → 강릉 한 방향의 패키지만 들어 있다. 출발할 때 경로에 맞는 패키지를 받아 오는 서버는 만들지 않았다.
- **`pleos` 빌드에는 회전 안내 연동, 주차 상태 읽기, 조명 연출이 빠져 있다.** [docs/pleos-integration.md](docs/pleos-integration.md)에 적었다.
- **화면 배치는 창 크기 하나에서만 눈으로 확인했다.** Pleos Connect 에뮬레이터의 1682×1210dp 창에서 주행 중(카드와 패널 나란히, 큰 카드)과 정차 중(작은 카드, 점수와 경로 그림) 화면을 봤다. 분할 화면처럼 좁거나 낮은 창에서 쓰는 배치(위아래로 쌓기, 좁은 카드)는 규칙만 테스트했고 그려진 모습은 보지 못했다.

## 데이터 출처

- 이야기와 퀴즈의 사실은 한국어 위키백과 문서에 근거했고, 장소마다 `sources`에 문서를 적었다. 위키백과 본문은 CC BY-SA 4.0이다.
- TourAPI(한국관광공사 국문 관광정보 서비스)는 파이프라인에서 키가 있을 때 쓴다.
- 번들 패키지의 좌표는 데모용 근사값이다. 실제 서비스에서는 내비 경로와 TourAPI 좌표로 바뀐다.
