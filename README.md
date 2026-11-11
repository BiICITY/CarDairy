# 우리 차 일기

가족이 이름 지어준 차가 실제 차량 신호로 쓴 일기를 들려주는 Pleos Connect 앱입니다.

## 왜 차량 앱인가

| 기능 | 필요한 차량 신호 | 폰 앱으로는 |
|---|---|---|
| 아이 이름 부르기 | 좌석별 탑승 | 누가 어느 자리에 탔는지 모름 |
| 벨트 포옹 | 벨트 버클 | 버클을 채운 순간을 모름 |
| 비 온 날 일기 | 와이퍼 | 지역 날씨만 알고, 우리 차가 맞은 비는 모름 |
| 돌봄 알림 | EV 배터리 잔량 | 차량 데이터를 직접 읽기 어려움 |
| 주행 중 침묵 | 주행 제한(UX Restrictions) | 운전 중 사용을 시스템 차원에서 막을 수 없음 |
| 차가 기다린 시간 | 주차와 탑승 | 블루투스 연결로 짐작할 뿐 |
| 차가 말하기 | 차 화면과 스피커 | 뒷좌석 아이에게는 폰이 없음 |


## 심사용 APK와 실행 방법

제출용 APK: [`apk/BiICITY_우리차일기_release.apk`](apk/BiICITY_우리차일기_release.apk)

Pleos Connect Emulator v2.1.0(Android 14)에 APK를 설치한 뒤 앱 목록에서 **우리 차 일기**를 엽니다. 첫 화면에서 차 이름, 아이 이름과 좌석을 설정하고 보호자 동의를 선택합니다. 화면의 **데모** 메뉴는 차량 신호를 직접 보내기 어려울 때 탑승·주행·충전 장면을 재현하는 용도입니다. 실제 차량 신호는 Pleos Vehicle SDK를 통해 읽습니다.

검증 환경: Pleos Connect Emulator v2.1.0, Android SDK 35, Java 21. `:app:testDebugUnitTest`와 `:app:assembleDebug`가 통과했고, release APK는 빌드와 서명 검증을 마쳤습니다. 신규 설치·전체 시나리오 동작은 에뮬레이터에서 추가 확인이 필요합니다. 에뮬레이터의 Virtual Console 신호와 실차 동작은 다를 수 있습니다.

## 구조

| 파일 | 역할 |
|---|---|
| `core/JourneyMachine.kt` | 탑승, 주행, 주차, 기다림 상태 머신. 주행 중에는 말하지 않고 주차 후 전달 |
| `core/DiaryEngine.kt` | 기록된 사실을 템플릿 문장으로 변환. 차 상태를 지어내지 않음 |
| `VehicleSource.kt` | Vehicle SDK 신호를 앱 이벤트로 변환. SDK 값 해석은 이 파일에만 둠 |
| `CarVoice.kt` | Pleos TTS `ON_DEVICE` 음성. 실패해도 화면 문장은 유지 |
| `DiaryController.kt` | 이벤트를 음성과 화면으로 연결 |
| `DiaryStore.kt` | 차 안에만 저장하는 JSON 기록 |
| `service/DiaryService.kt` | 화면이 가려져도 신호를 기록하는 포그라운드 서비스 |
| `core/PhotoSelector.kt` | 시간 기록만으로 나들이 사진 7장 추천. 운전 중 운전자 폰 사진 제외 |
| `PhotoServer.kt` | 폰 사진 페이지를 여는 차량 내 로컬 서버. 토큰 필요, 화면이 열린 동안만 실행 |
| `assets/companion.html` | 보호자 폰 화면. 사진 메타데이터만 먼저 보내고 승인한 사진만 전송 |
| `ui/Screens.kt` | 첫 설정, 홈, QR, 사진 표시, 데모 패널 |

## 빌드와 테스트

JDK 17, Android SDK 35가 필요합니다.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## 에뮬레이터 실행

Pleos Connect v2.1.0 AVD를 사용합니다. 실행 중 데이터 파일이 약 9GB까지 늘어나므로 C 드라이브 여유가 12GB 이상일 때 실행하고, `scripts/disk_guard.ps1`을 함께 켜 두세요. 그래픽 카드가 권장 사양보다 낮으면 창 없이 실행하세요.

```bash
emulator -avd Pleos_Connect_v2.1 -no-window -no-snapshot -no-boot-anim -netdelay none -netspeed full
```

부팅 직후 Virtual Console의 DDS가 오프라인이면 `adb reboot`로 한 번 재부팅합니다. 그 뒤 콘솔을 엽니다.

```bash
adb forward tcp:8088 tcp:8088
```

스크립트는 PATH의 `adb`를 쓰며, 다른 위치는 `ADB` 환경 변수로 지정합니다. 결과는 `out/` 아래에 저장됩니다.

`scripts/e2e.sh`는 콘솔 HTTP API로 차량 신호를 보내며 장면별 스크린샷을 저장합니다. 콘솔은 브라우저 세션이 활성 상태일 때만 값을 받습니다.

## 시연 영상

`docs/우리차일기_시연영상.mp4`는 다음 순서로 만듭니다. 에뮬레이터 소리는 녹음되지 않아 Windows 한국어 음성으로 내레이션을 넣습니다.

1. `scripts/record_scenes.sh <폴더> part1`, 폰 페이지 승인, `part2`로 차 화면 녹화
2. `scripts/phone_capture.py <폴더>/phone`으로 폰 화면 캡처
3. `scripts/build_video.py <폴더> <출력 파일>`로 합치기

## 확인된 제약

- 좌석 탑승 값은 에뮬레이터에서 콘솔 설정과 반대로 수신됩니다. `VehicleSource.interpretOccupancy`에서 처리하며 실제 차량 확인이 필요합니다.
- 벨트 값 변화는 콘솔로 재현할 수 없어 "벨트 맸어요" 버튼과 데모 패널로 시연합니다.
- 타이어 공기압과 경고등 API는 권한이 거부되어 돌봄 알림은 배터리 잔량을 사용합니다.
- 폰이 차량의 로컬 주소에 접속할 수 있는지는 실제 차량에서 미확인입니다. 안 되면 중계 서버가 필요합니다.
- 에뮬레이터에서는 `adb forward tcp:8765 tcp:8765` 뒤 QR에 표시된 주소의 IP를 `127.0.0.1`로 바꿔 PC 브라우저로 엽니다.
- 포토북 주문은 아직 구현하지 않았습니다.
