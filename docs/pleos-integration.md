# Pleos Connect SDK 연동 메모

`pleos` 빌드(`app/src/pleos/`)는 Pleos Connect SDK로 차와 이야기한다. 이 문서는 그 코드가 어디까지 확인됐고 무엇을 더 확인해야 하는지 적는다.

## 상태

어댑터는 공개 가이드 문서(document.pleos.ai)의 설명과 예제를 보고 작성했다. **SDK에 대해 컴파일해 본 적이 없다.** 작성한 환경에서 SDK 저장소(nexus-playground.pleos.ai)에 접근할 수 없었고, 정확한 시그니처가 실린 API 레퍼런스 페이지는 Playground 밖에서 내용이 표시되지 않았다.

그래서 `pleos` 빌드는 기본값으로 꺼져 있다(`gradle.properties`의 `gildongmu.pleos=false`). 꺼져 있는 동안에는 Gradle이 SDK를 내려받지도, `app/src/pleos/`를 컴파일하지도 않으므로 `mock` 빌드에 영향이 없다.

확인된 것은 어댑터 코드가 가이드에 적힌 모양의 SDK에는 맞는다는 점까지다. 가이드의 호출 형태대로 만든 대역 클래스에 대해 타입 검사를 통과시켰다. 실제 SDK와 다른 부분은 첫 빌드에서 컴파일 오류로 드러난다.

## 켜는 방법

1. SDK 저장소에 접근 가능한 PC에서 `gradle.properties`의 `gildongmu.pleos`를 `true`로 바꾼다.
2. Gradle sync 후 Build Variants에서 `pleosDebug`를 고른다.
3. 컴파일 오류를 아래 표의 "확인할 것"과 대조해 고친다. Android Studio의 자동 완성이 실제 시그니처를 보여 준다.
4. Pleos Connect 에뮬레이터(Android Studio AVD, API 34의 Pleos Connect 이미지)에서 실행한다. Pleos 가이드는 AVD의 부팅 방식을 Cold boot로, RAM을 4GB로 두라고 한다. 차량 신호는 Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`)로 넣는다.

에뮬레이터는 외장 그래픽 카드가 필수다(최소 RTX 3060 Laptop 6GB).

## 에뮬레이터에서 먼저 알아낼 것

공개 가이드에서 답을 찾지 못한 두 가지가 있다. 둘 다 호스트가 움직이는 데 꼭 필요하다.

- **내비 경로 안내를 어떻게 시작하는가.** 호스트는 NaviHelper의 경로 시작 이벤트를 받아야 출발한다. 에뮬레이터의 내비에서 목적지를 잡고 안내를 시작할 수 있는지, 그때 `onRouteStarted`가 오는지 확인해야 한다. 가이드가 설명하는 Virtual Console의 영역은 차량 신호(Vehicle, Motion, ADAS, Convenience, Safety, Energy, Climate)이고 내비는 없다.
- **위치를 어떻게 움직이는가.** 호스트는 FusedLocation의 위치를 경로선에 대어 어디쯤인지 잰다. 에뮬레이터에서 서울 → 강릉 경로를 따라 위치를 넣는 방법을 확인해야 한다.

경로 안내를 시작할 방법이 없을 때를 대비해, `pleos` 빌드에서는 화면 왼쪽 위의 목적지 글자를 길게 누르면 출발 신호를 손으로 넣을 수 있다. 시험용이고, 이미 출발한 뒤에는 아무 일도 하지 않는다. 실제 차에 올리기 전에 `PleosPlatform.kt`에서 지운다.

## 어댑터별 근거와 확인할 것

| 파일 | SDK (좌표) | 가이드에서 확인한 것 | 확인할 것 |
| --- | --- | --- | --- |
| `PleosSpeaker.kt` | TextToSpeech `ai.pleos.playground:TextToSpeech:3.3.1` | 생성자 `TextToSpeech(context, Mode)`, `initialize(OnInitializeListener)`, `addEventListener`, `setLocale(Locale)`, `speak(text)`, `stop()`, `release()`. `EventListener`의 콜백 6개. ON_DEVICE 모드는 앱 등록이 필요 없음 | `ErrorType`의 패키지. `EventListener`에 다른 추상 메서드가 있는지 |
| `PleosListener.kt` | SpeechToText `ai.pleos.playground:SpeechToText:3.3.1` | `addListener(ResultListener)`, `request()`, `stop()`. `ResultListener`의 콜백 7개. 인식 결과는 `onUpdated(stt, completed)`의 `stt` | `ErrorType`의 패키지. `initialize`가 `OnInitializeListener`를 받는지 |
| `PleosNavigation.kt` | NaviHelper `ai.pleos.playground:NaviHelper:3.0.5` | `NaviHelper(context)`, `initialize(onConnected, onFailed)`, `addListener`, `onRouteStarted(RouteStartInfo)`, `onError(NaviErrorCode)`, `addWaypoint(RequestWaypointInfo(latitude, longitude, waypointIndex, ...))` | `onDestinationArrived`의 매개변수. `RequestWaypointInfo`의 필수 인자. **`onTBTInfo`는 아직 연결하지 않았다**(매개변수 타입 미확인) |
| `PleosVehicle.kt` | Vehicle `ai.pleos.playground:Vehicle:2.3.2` | `Vehicle(context)`, `initialize()`, 도메인 객체 `getXxx()`, 읽기 `getSomething(onComplete, onFailed)`. Odometer와 EvBattery의 메서드 이름 | `getOdometer()`, `getEvBattery()` 접근자 이름. 네 값의 타입과 **단위**(코드는 분, km, %, 참/거짓으로 가정). 앰비언트 조명 설정 메서드 |
| `PleosLocation.kt` | FusedLocation `ai.pleos.playground:FusedLocation:1.1.3` | `FusedLocation(context)`, `isSupported()`, `initialize()`, `registerFusedLocationCallback(FusedLocationListener)`, `onChanged(LocationData?)`, `onError(Exception)` | `LocationData`의 프로퍼티 이름(코드는 `latitude`, `longitude`로 가정) |

## 아직 없는 것

- **회전 안내 연동.** 호스트는 `Signal.TurnGuidance(남은 거리 m)`를 받으면 말을 멈추고, `null`을 받으면 이어 간다. 이 로직은 `mock` 빌드에서 동작하고 테스트되어 있다. `pleos` 빌드에서는 `onTBTInfo`의 데이터 타입을 확인한 뒤 `PleosNavigation`에서 신호를 보내야 한다.
- **정차 판정.** 공개 가이드에서 기어나 주차 브레이크를 읽는 방법을 찾지 못했다(Brake 도메인 문서에는 브레이크 페달 상태만 있다). 지금은 `ParkedDetector`가 위치로 판정한다. 같은 자리에 2분 머물거나 충전이 시작되면 정차로 본다. SDK에서 기어나 주차 브레이크를 읽을 수 있으면 그것으로 바꾸는 편이 낫다.
- **조명 연출.** `PleosVehicle.light()`는 로그만 남긴다. Light 도메인의 설정 메서드와 `CONTROL_CAR_LIGHTS` 권한 승인이 필요하다. 없어도 호스트는 동작한다.
- **구독.** 주행 시간과 배터리는 5초마다 읽는다. 해당 값의 리스너 인터페이스 이름을 확인하면 `register...`로 바꿀 수 있다.
- **콘텐츠 서버.** 지금은 앱에 든 경로 패키지를 읽는다. 출발 시 경로를 서버에 보내 패키지를 받아 오는 부분은 구현하지 않았다.

## 권한

`app/src/pleos/AndroidManifest.xml`에 선언했다. 모두 Pleos App Market에서 사전 승인을 받아야 한다.

| 권한 | 쓰는 곳 |
| --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | NaviHelper 정보 조회 |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | NaviHelper `addWaypoint` |
| `pleos.car.permission.CAR_DRIVE` | Vehicle Odometer |
| `pleos.car.permission.CAR_ENERGY` | Vehicle EvBattery |
| `pleos.car.permission.FUSED_LOCATION` | FusedLocation |

## 주의 분산 관련

Pleos 심사 가이드는 영상, 게임, 브라우저 기능이 있는 앱을 주행 중 사용 금지로 분류한다. 이 앱은 그 기능이 없는 오디오 앱이다. 퀴즈는 음성으로만 진행되고 게임 화면이 없다. 주행 중 화면은 카드 한 장이고 터치는 건너뛰기 하나, 글자 입력과 목록 스크롤이 없다. 점수판과 경로 그림은 정차 중에만 보인다. 알림을 보내지 않는다.
