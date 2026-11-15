# Pleos Connect SDK 연동 메모

`pleos` 빌드(`app/src/pleos/`)는 Pleos Connect SDK로 차와 이야기한다. 이 문서는 그 코드가 어디까지 확인됐고 무엇을 더 확인해야 하는지 적는다.

## 상태

어댑터는 처음에 공개 가이드 문서(document.pleos.ai)를 보고 작성했다. 2026-11-15에 SDK 저장소(nexus-playground.pleos.ai, 인증 없이 열린다)에서 실제 SDK를 받아 그 클래스 파일과 대조하고, 실제 SDK에 대해 컴파일했다.

| SDK | 버전 | 결과 |
| --- | --- | --- |
| TextToSpeech | 3.3.1 | 코드는 맞았다. 모든 호출에 `pleos.car.permission.TTS_SERVICE`가 필요해서 매니페스트에 넣었다 |
| SpeechToText | 3.3.1 | 코드는 맞았다. 모든 호출에 `pleos.car.permission.STT_SERVICE`가 필요해서 매니페스트에 넣었다 |
| NaviHelper | 3.0.5 | 컴파일 오류 두 군데를 고쳤다. `onDestinationArrived`는 `DestinationArrivedInfo`를 받고, `RequestWaypointInfo`는 `poiId`, `address`, `poiSubId`가 필수다 |
| FusedLocation | 1.1.3 | 코드는 맞았다. `FUSED_LOCATION`은 실행 중에 허락을 받아야 하는 권한이라 앱이 묻도록 했다 |
| Vehicle | 2.3.2 | 코드는 맞았다. 값의 실제 타입(분 `Int?`, 거리 `Float?`, 충전량 `Int?`, 충전 스위치 `Boolean?`)으로 바꾸고, 콜백을 메인 스레드로 옮겼다 |

빌드 설정도 하나 고쳤다. TextToSpeech와 SpeechToText가 `ai.pleos.playground.security:universal-access:0.6.0`을 끌어오는데, 이 그룹은 Pleos 저장소에만 있다. `settings.gradle.kts`의 Pleos 저장소 필터에 이 그룹을 추가했다.

같은 날 Pleos Connect 에뮬레이터(Android 14, 이미지 release_connect_v2.1.0)에서 `pleosDebug`를 돌렸다. 확인한 것:

- 설치할 때 `TTS_SERVICE`, `STT_SERVICE`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `CAR_DRIVE`, `CAR_ENERGY`와 SDK가 넣는 바인딩 권한이 모두 허용됐다. `FUSED_LOCATION`은 처음 실행할 때 대화상자로 묻는다. 이 이미지에서는 권한 이름이 없어서 대화상자에 "길동무에서 알 수 없는 작업 실행하도록 허용하시겠습니까?"라고 나온다.
- 음성 서비스(`ai.pleos.playground.caas` 4.2.10, SDK가 요구하는 4.1.29 이상)가 연결되고 한국어(`ko_KR`)를 받아들였다. 음성 인식 서비스도 연결됐다.
- 목적지를 길게 눌러 대본 주행을 시작하면, Pleos 음성으로 출발부터 도착까지 약 6분 20초에 진행됐다. 로그로 보면 문장마다 음성(화자 seoyoon)을 만들어 실제 길이만큼 재생한 뒤 끝났다고 알려 왔고, 호스트는 그때 다음 문장으로 넘어갔다. 건너뛴 문장은 없었다. **에뮬레이터를 소리 없이 띄워서 사람이 귀로 들어 보지는 않았다.**
- 배터리는 Vehicle SDK로 읽혔다(상태 줄의 배터리). 주행 시간과 거리는 에뮬레이터의 차량 서비스가 `ERROR_NOT_SUPPORTED_PROPERTY`로 거절한다. 어댑터는 값마다 한 번만 로그를 남기고 계속 묻는다.
- 앱이 꺼진 적은 없다.

아직 확인하지 않은 것: 실제 내비 경로 안내가 시작될 때 `onRouteStarted`가 오는지, 위치를 움직이는 방법, 음성 인식으로 대답을 받는 것, 내비가 POI 정보 없이 좌표와 이름만 있는 경유지를 받아들이는지, 회전 안내(`onTBTInfo`)의 거리 단위.

`pleos` 빌드는 여전히 기본값으로 꺼져 있다(`gradle.properties`의 `gildongmu.pleos=false`). 꺼져 있는 동안에는 Gradle이 SDK를 내려받지도, `app/src/pleos/`를 컴파일하지도 않으므로 `mock` 빌드에 영향이 없다. 제출용 APK는 `mock` 빌드다.

## 켜는 방법

1. 둘 중 하나로 켠다.
   - `gradle.properties`의 `gildongmu.pleos`를 `true`로 바꾸고 Gradle sync 후 Build Variants에서 `pleosDebug`를 고른다.
   - 파일은 그대로 두고 명령줄에 속성을 준다. 명령 프롬프트와 Git Bash에서는 `gradlew.bat assemblePleosDebug -Pgildongmu.pleos=true`, PowerShell에서는 점에서 인자가 갈라지지 않게 따옴표로 감싸서 `.\gradlew.bat assemblePleosDebug "-Pgildongmu.pleos=true"`라고 쓴다.
2. 결과는 `app/build/outputs/apk/pleos/debug/app-pleos-debug.apk`(약 30MB)다. SDK가 끌어오는 라이브러리(androidx, kotlinx-coroutines, gson, hilt, JNA 등)가 `pleos` 빌드에만 들어간다.
3. Pleos Connect 에뮬레이터(Android Studio AVD, API 34의 Pleos Connect 이미지)에 설치한다. `mock`과 같은 앱 ID라서 설치하면 서로 바뀐다. Pleos 가이드는 AVD의 부팅 방식을 Cold boot로, RAM을 4GB로 두라고 한다. 차량 신호는 Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`)로 넣는다.

에뮬레이터는 외장 그래픽 카드가 필수다(최소 RTX 3060 Laptop 6GB). 그래픽 카드 없이 창 없이 띄울 때는 `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`가 안정적이었다.

## 에뮬레이터에서 먼저 알아낼 것

공개 가이드에서 답을 찾지 못한 두 가지가 있다. 둘 다 호스트가 실제 차에서 움직이는 데 꼭 필요하다.

- **내비 경로 안내를 어떻게 시작하는가.** 호스트는 NaviHelper의 경로 시작 이벤트를 받아야 출발한다. 에뮬레이터 이미지에는 내비 서비스(`ai.pleos.playground.service.navi`)가 있다. 그 내비에서 목적지를 잡고 안내를 시작할 수 있는지, 그때 `onRouteStarted`가 오는지 확인해야 한다. 가이드가 설명하는 Virtual Console의 영역은 차량 신호(Vehicle, Motion, ADAS, Convenience, Safety, Energy, Climate)이고 내비는 없다.
- **위치를 어떻게 움직이는가.** 호스트는 FusedLocation의 위치를 경로선에 대어 어디쯤인지 잰다. 이미지에는 위치 서비스(`ai.pleos.playground.service.location.fused`)가 있다. `adb emu geo fix`가 이 서비스까지 가는지, 서울 → 강릉 경로를 따라 위치를 넣는 방법을 확인해야 한다.

## 대본 주행을 차량 음성으로 돌리기

에뮬레이터에서 경로 안내를 시작하거나 위치를 움직일 방법이 없어도 앱을 보여 줄 수 있게, `pleos` 빌드에 대본 주행을 넣었다. 화면 왼쪽 위의 목적지 글자를 길게 누르면 시작한다. 2026-11-15에 위에 적은 대로 끝까지 돌았다.

- 위치, 주행 시간, 배터리, 정차, 내비 안내는 `mock` 빌드와 같은 대본(`app/src/pleos/assets/scenarios/`)이 만든다. 그때부터 실제 차량 신호는 듣지 않는다.
- 말은 Pleos TextToSpeech SDK로 한다. 차량 음성으로 데모를 녹화하는 것이 이 기능의 목적이다.
- 탑승자 대답은 대본이 하므로 마이크는 열지 않는다. 경유지 추가도 실제 내비에 보내지 않고 화면에만 표시한다.
- 문장이 화면에도 나오도록 자막을 켠다.
- 실제 음성은 문장 길이를 추정한 시간과 조금씩 달라서, 도착지 추천처럼 빈 시간에 맞춰 넣는 말은 녹화마다 나오거나 빠질 수 있다.

사무실 PC의 에뮬레이터처럼 앱 화면이 창보다 128px 아래에 그려지는 경우, `uiautomator`가 알려 주는 좌표가 아니라 화면에 보이는 위치를 눌러야 한다.

실제 차에 올리기 전에 `PleosPlatform.kt`에서 이 기능을 지운다.

## 한 SDK가 안 될 때

에뮬레이터 이미지에 따라 SDK 뒤의 서비스가 없을 수 있다. `PleosPlatform`은 SDK를 하나씩 따로 준비하고, 실패한 것은 Logcat(`Gildongmu` 태그)에 남기고 빼 둔다. 하나가 안 된다고 앱이 꺼지지는 않는다. SDK 자체의 로그는 `[GAAS][SDK]`(음성), `[CAAS][SDK]`(음성 인식)로 시작하는 태그로 나온다.

음성은 예외로 반드시 대신할 것을 둔다. 호스트는 문장이 끝났다는 알림을 받아야 다음으로 넘어가기 때문이다. Pleos 음성 엔진이 시작되지 않거나, 문장을 끝냈다고 알려 주지 않거나, 말하지 않고 바로 끝냈다고 알려 오면, 문장을 읽을 시간만큼 기다렸다가 넘어가고 자막을 켠다. 이 판단은 `mock` 빌드와 같은 코드(`SpeechGuard`)가 하고, 단위 테스트가 있다.

## 어댑터별 근거와 확인할 것

| 파일 | SDK (좌표) | 실제 SDK에서 확인한 것 | 기기에서 확인할 것 |
| --- | --- | --- | --- |
| `PleosSpeaker.kt` | TextToSpeech `ai.pleos.playground:TextToSpeech:3.3.1` | `TextToSpeech(context, Mode)`(기본값은 HYBRID라 `Mode.ON_DEVICE`를 넘긴다), `initialize(OnInitializeListener)`(콜백은 한 번), `addEventListener`, `setLocale`, `speak(text, isFinal = true)`, `stop()`, `release()`. `EventListener`의 추상 메서드는 정확히 6개이고 콜백은 바인더 스레드로 온다(어댑터가 메인으로 옮긴다). ON_DEVICE는 앱 등록이 필요 없다 | 실제 소리. 2026-11-15에 에뮬레이터에서 문장마다 재생이 끝난 뒤 `onDone`이 왔다. SDK 로그에는 콜백이 두 번씩 찍히지만 건너뛴 문장은 없었다 |
| `PleosListener.kt` | SpeechToText `ai.pleos.playground:SpeechToText:3.3.1` | `addListener(ResultListener)`, `request()`, `stop()`, `setLocale`. `ResultListener`의 콜백은 정확히 7개이고 메인 스레드로 온다. 인식 결과는 `onUpdated(stt, completed)`의 `stt` | 인식 기본 언어, 요청마다 `completed = true`가 한 번 오는지, `RECORD_AUDIO`를 실행 중에 받아야 하는지 |
| `PleosNavigation.kt` | NaviHelper `ai.pleos.playground:NaviHelper:3.0.5` | `initialize(onConnected, onFailed)`, `addListener`, `onRouteStarted(RouteStartInfo)`, `onDestinationArrived(DestinationArrivedInfo)`, `onTBTInfo(List<TBTInfo>)`, `addWaypoint(RequestWaypointInfo(...))`. 경유지 추가는 `NAVI_ROUTE_SEARCH`가 없으면 그 자리에서 `SecurityException`을 던져서 잡도록 했다 | 경로 시작과 도착 이벤트가 오는지, POI 정보 없이 좌표와 이름만 넘긴 경유지를 받는지, `WaypointIndex.FIRST`가 기존 경유지를 바꾸는지 앞에 넣는지, `TBTInfo.distance`의 단위 |
| `PleosVehicle.kt` | Vehicle `ai.pleos.playground:Vehicle:2.3.2` | `getOdometer()`, `getEvBattery()`, 읽기 `getXxx(onComplete, onFailed)`와 값의 타입. 권한이 없으면 예외가 아니라 `onFailed`로 온다. 구독(`register...`)과 기어(`Gear`), 조명(`Light`) 메서드도 있다 | 주행 시간과 거리의 **단위**(코드는 분과 km로 가정). 충전 스위치가 충전 중에 켜지는지. 에뮬레이터는 주행 시간과 거리를 주지 않는다 |
| `PleosLocation.kt` | FusedLocation `ai.pleos.playground:FusedLocation:1.1.3` | `isSupported()`, `initialize()`(연결될 때까지 호출을 쌓아 둔다), `registerFusedLocationCallback`, `LocationData`의 `latitude`, `longitude` 등. 콜백은 바인더 스레드로 온다 | 위치가 실제로 들어오는지와 그 위치를 움직이는 방법 |

## 아직 없는 것

- **회전 안내 연동.** 호스트는 `Signal.TurnGuidance(남은 거리 m)`를 받으면 말을 멈추고, `null`을 받으면 이어 간다. 이 로직은 `mock` 빌드에서 동작하고 테스트되어 있다. SDK에서 `onTBTInfo(List<TBTInfo>)`의 모양은 확인했지만 `distance`의 단위를 몰라서 아직 신호를 보내지 않는다.
- **정차 판정.** 지금은 `ParkedDetector`가 위치로 판정한다. 같은 자리에 2분 머물거나 충전이 시작되면 정차로 본다. Vehicle SDK에 기어를 읽는 방법(`Gear.registerGearSelection`, `VehicleGear.GEAR_PARK`, `CAR_DRIVE` 권한)이 있으니 그것으로 바꾸는 편이 낫다.
- **조명 연출.** `PleosVehicle.light()`는 로그만 남긴다. Light 도메인의 설정 메서드(`setAmbientLightSwitch`, `setAmbientLightBrightnessColor` 등)는 확인했지만 `CONTROL_CAR_LIGHTS` 권한과 색 값의 형식이 필요하다. 없어도 호스트는 동작한다.
- **구독.** 주행 시간과 배터리는 5초마다 읽는다. 구독용 리스너(`registerTripCurrentDrivingTime` 등)도 있으니 바꿀 수 있다.
- **콘텐츠 서버.** 지금은 앱에 든 경로 패키지를 읽는다. 출발 시 경로를 서버에 보내 패키지를 받아 오는 부분은 구현하지 않았다.

## 권한

`app/src/pleos/AndroidManifest.xml`에 선언했다. 모두 Pleos App Market에서 사전 승인을 받아야 한다.

| 권한 | 쓰는 곳 | 에뮬레이터(2026-11-15) |
| --- | --- | --- |
| `pleos.car.permission.TTS_SERVICE` | TextToSpeech의 모든 호출 | 설치할 때 허용 |
| `pleos.car.permission.STT_SERVICE` | SpeechToText의 모든 호출 | 설치할 때 허용 |
| `pleos.car.permission.NAVI_ROUTE` | NaviHelper 정보 조회 | 설치할 때 허용 |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | NaviHelper `addWaypoint` | 설치할 때 허용 |
| `pleos.car.permission.CAR_DRIVE` | Vehicle Odometer | 설치할 때 허용 |
| `pleos.car.permission.CAR_ENERGY` | Vehicle EvBattery | 설치할 때 허용 |
| `pleos.car.permission.FUSED_LOCATION` | FusedLocation | 실행 중에 묻는다 |

SDK가 스스로 넣는 권한(`INTERNET`, 차량과 위치 서비스의 바인딩 권한)은 매니페스트가 합쳐질 때 들어간다.

## 주의 분산 관련

Pleos 심사 가이드는 영상, 게임, 브라우저 기능이 있는 앱을 주행 중 사용 금지로 분류한다. 이 앱은 그 기능이 없는 오디오 앱이다. 퀴즈, 수수께끼, 끝말잇기는 음성으로만 진행되고 게임 화면이 없다. 주행 중 화면은 카드 한 장이고 터치는 건너뛰기 하나, 글자 입력과 목록 스크롤이 없다. 먹거리·볼거리 추천도 말로 고른다. 점수판과 경로 그림은 정차 중에만 보인다. 알림을 보내지 않는다.
