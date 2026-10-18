# DeskInk

**아이패드와 Apple Pencil로 윈도우 화면을 칠판처럼 사용합니다.**

[English](README.md) · **한국어**

DeskInk는 USB로 연결한 아이패드에 Windows 모니터 화면을 보여줍니다. 아이패드에 쓰면 PC 화면 위에도 필기가 나타나므로 슬라이드, 논문, 코드, 그림을 설명할 때 사용할 수 있습니다. PC 조작은 평소처럼 마우스와 키보드로 합니다.

> **개발 중인 미리보기 버전입니다.** 이 저장소에는 소스와 빌드 방법이 있습니다. 바로 설치하는 Windows 배포판과 DeskInk TestFlight 초대 링크는 아직 공개하지 않았습니다. 현재 iPad 빌드는 개인 계정으로 서명한 뒤 설치해야 합니다.

## 주요 기능

| 기능 | 현재 동작 |
| --- | --- |
| 필기 도구 | 펜, 형광펜, 획 단위 지우개, 레이저 포인터, 잠시 후 사라지는 펜 |
| 편집 | 색·굵기 선택, 실행 취소/다시 실행, 현재 화면 전체 지우기, 필기 숨기기/보이기 |
| Pencil 단축 동작 | 지원 모델에서 두 번 탭, 지우개를 화면에서 떼면 이전 도구로 복귀 |
| 아이패드 화면 | 두 손가락으로 1~4배 확대·이동, Windows 화면 배율은 그대로 유지 |
| 도구막대 | 영상 아래 별도 제어 영역에 배치하여 필기 화면을 가리지 않음 |
| 여러 모니터 | Windows 버튼·바탕화면 바로가기·Stream Deck으로 전환, 화면마다 필기와 실행 취소 기록을 따로 유지 |
| 발표 | Zoom 등의 전체 모니터 공유에 필기와 포인터를 함께 보여주는 용도, 화면 공유는 사용자가 직접 선택 |

PC의 필기 창은 마우스 클릭을 아래 프로그램으로 통과시킵니다. 손가락 필기는 기본적으로 꺼져 있고 두 손가락은 화면 확대·이동에 사용합니다. 펜을 대기 전 위치를 보여주는 호버 기능은 아이패드와 Pencil의 하드웨어 지원이 필요합니다.

## 준비물과 현재 제한

- **Windows x64:** Windows 11 빌드 26200, Radeon 740M, 1920×1080 모니터 두 대에서 검증했습니다. 다른 PC·GPU는 추가 확인이 필요합니다. 필기 창을 자체 화면 캡처에서만 제외하는 Windows 기능이 필요하므로 이전 Windows 버전의 동작을 보장하지 않습니다.
- **iPadOS 17 이상**, 해당 아이패드와 호환되는 Pencil, 데이터 전송용 USB 케이블, Windows의 **Apple Devices** 프로그램이 필요합니다. 아이패드에서 **이 컴퓨터를 신뢰**를 허용합니다.
- 현재 DeskInk 필기 연결은 **USB**입니다. Bluetooth·Wi-Fi·이더넷 필기 연결은 구현하지 않았습니다.
- 모니터 전환 시 영상 연결이 잠시 다시 시작됩니다. Windows 앱을 켜 둔 동안에는 화면마다 필기가 유지됩니다.
- 필기는 메모리에만 저장됩니다. **Windows 앱을 종료하면 지워집니다.** 영구 저장, 서로 다른 PC의 연결 정보 선택, iPad 앱 종료 후 필기 복원은 구현하지 않았습니다.
- Windows에 표시된 코드로 USB 연결을 페어링합니다. 호스트를 다시 시작하면 다시 페어링해야 합니다.
- 실제 Pencil 입력부터 PC 화면 표시까지의 지연, 다양한 iPad 호환성, Zoom 상대방에게 보이는 결과는 실기기 확인이 필요합니다. FPS와 내부 렌더링 시간은 전체 입력 지연과 다릅니다.

## Windows 앱 빌드

저장소를 내려받고 프로젝트 루트에서 실행합니다.

```powershell
git clone https://github.com/zkdlwuthwo1-hash/deskink.git
cd deskink
```

[rust-toolchain.toml](rust-toolchain.toml)에 지정된 Rust MSVC 도구체인, Windows SDK가 포함된 Visual Studio C++ Build Tools, FFmpeg 7.1 **LGPL shared** 헤더·라이브러리·DLL, libclang을 준비합니다. 현재 개발 스크립트는 다음 경로를 사용합니다.

```text
.tools/
  ffmpeg/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1/
    include/  lib/  bin/
  libclang/clang/native/libclang.dll
```

이 의존성은 **Git에 포함되지 않으며**, 자동 설치 스크립트는 아직 없습니다. 개발에 사용한 FFmpeg SDK의 배포처는 [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds)입니다. 환경을 준비할 때 [scripts/windows-env.ps1](scripts/windows-env.ps1)을 확인합니다. 빌드·실행·바로가기 스크립트는 동일한 고정 FFmpeg 경로를 사용합니다.

```powershell
./scripts/build-windows.ps1 -Release
./scripts/run-windows.ps1 -Release -Monitor 0
```

결과는 `target/monitor-switch/release/deskink.exe`와 `deskink-launcher.exe`입니다. 필기 앱의 실행 파일은 **deskink**입니다. 함께 남아 있는 원본 `eternal-host` 앱은 기능 구성이 다릅니다.

모니터 두 대의 바탕화면 바로가기를 만들려면 다음을 실행합니다.

```powershell
./scripts/install-desktop-shortcuts.ps1
```

현재 사용자의 바탕화면에 `DeskInk - 화면 1`, `DeskInk - 화면 2`가 생깁니다. Stream Deck의 열기 동작에 이 바로가기를 등록하면 터미널 창 없이 실행 중인 앱의 화면을 전환할 수 있습니다. 모니터 인수는 `0`, `1`부터 시작하므로 실제 선택되는 화면을 확인합니다.

## iPad 앱 빌드·설치

**Mac이 있다면:** Xcode, iPad 시뮬레이터 런타임, XcodeGen을 준비하고 다음을 실행합니다.

```bash
brew install xcodegen
bash scripts/build-ipad.sh
bash scripts/test-ipad.sh
```

생성된 `ios/DeskInk.xcodeproj`를 열고 `EternalMonitor` 타깃의 Signing & Capabilities에서 본인의 Team을 선택합니다. 계정에서 사용할 수 있는 번들 ID로 설정한 뒤 연결한 iPad에서 실행합니다. 앱에 표시되는 이름은 **DeskInk**이며 일부 내부 타깃 이름은 기반 프로젝트와의 호환성을 위해 유지했습니다.

**로컬 Mac 없이 진행한다면:** 저장소를 Fork하고 **Actions → Build iPad app → Run workflow**를 수동 실행합니다. macOS 서버가 테스트와 빌드를 수행하고 `DeskInk-iPad-…` 결과 파일 안에 **서명되지 않은** `DeskInk-unsigned.ipa`를 만듭니다. Windows의 iPad 설치 도구에서 본인 계정으로 서명·설치합니다. IPA를 내려받는 것만으로 설치되지는 않습니다. 결과 파일 보관 기간은 3일이며 이 워크플로에는 Apple 인증 정보를 넣지 않습니다.

현재 워크플로는 Xcode 16.4로 검증한 개발용 빌드입니다. TestFlight 업로드 절차는 아직 구성하지 않았습니다. 자세한 과정은 [Windows 중심 설치 안내](docs/windows-only-ipad.md)와 [Mac 빌드 안내](docs/mac-build.md)에 있습니다.

## 처음 연결하기

1. Windows에서 Apple Devices를 열고 잠금 해제한 iPad를 데이터용 USB 케이블로 연결합니다. 신뢰 안내가 뜨면 허용합니다.
2. Windows DeskInk를 실행하고 필기할 모니터를 선택합니다.
3. 아이패드의 DeskInk를 열고 Windows에 표시된 페어링 코드를 입력합니다.
4. 선을 쓰고 지운 뒤 실행 취소를 확인합니다. 두 손가락으로 확대했을 때 Windows 화면 배율은 유지되는지 확인합니다.
5. 발표할 때는 원하는 **전체 모니터**를 공유하고, 다른 참가자의 화면에서 필기와 포인터가 함께 보이는지 확인합니다.

## 연구실·팀에 배포하기

권장하는 배포 구성은 **GitHub Releases의 Windows ZIP + iPad TestFlight 초대 링크**입니다. Windows 패키지는 실행에 필요한 DLL과 라이선스 안내를 포함하고, 개발 도구가 없는 다른 PC에서도 실행되는지 확인해야 합니다. 이 패키징 작업은 아직 남아 있습니다.

처음 소수에게 직접 설치해 주며 테스트할 때는 각자 자신의 Apple 계정으로 IPA를 서명할 수 있습니다. 무료 Personal Team의 설치 프로파일은 **7일 후 만료**되므로 갱신이 필요합니다. [Apple 무료 계정 제한](https://developer.apple.com/help/account/basics/about-your-developer-account)

설치와 업데이트를 편하게 하려면 배포자가 **TestFlight**를 구성합니다. 친구들은 무료 TestFlight 앱을 설치하고 초대를 수락하면 됩니다. 배포자의 서명 정보를 공유할 필요가 없습니다. 첫 외부 빌드는 Apple의 베타 심사가 필요하고, 각 빌드는 최대 **90일** 사용할 수 있습니다. App Store Connect 등록, 배포 서명, 현재 제출 SDK 요구사항에 맞춘 빌드 도구 구성이 필요합니다. [TestFlight 안내](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)

배포자의 Apple Developer Program 가입비는 **연 US$99**이며 지역별 현지 가격은 다를 수 있습니다. TestFlight는 베타 테스트용이고, 장기적인 일반 배포는 이후 App Store 등록으로 진행할 수 있습니다. [개발자 프로그램 가입 안내](https://developer.apple.com/help/account/membership/program-enrollment/)

## 테스트와 코드 구성

```powershell
# Windows: 필기 모델·프로토콜·USB·호스트 검사
./scripts/test-windows.ps1
```

```bash
# Mac: 단위 검사와 외부 호스트 없이 실행하는 화면 UI 검사
DESKINK_TEST_FILTER=EternalMonitorTests,EternalMonitorUITests/DeskInkViewportUITests \
  bash scripts/test-ipad.sh
```

[빌드 12 검증](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39118670760)에서 iPad 단위 검사 164개, UI 검사 2개, 서명 없는 기기용 빌드가 통과했습니다. Windows에서는 필기 편집, 오버레이 픽셀, 모니터 전환, 입력 처리 대기를 검사했습니다. 자동 검사 통과와 실제 기기에서의 사용성 확인은 구분합니다.

| 경로 | 역할 |
| --- | --- |
| `crates/ink-core/` | 필기 상태, 도구, 실행 취소/다시 실행, 입력 프로토콜 |
| `crates/windows-platform/` | Windows 필기 창, 화면 캡처, 모니터 전환, 실행기 |
| `host/` | Rust 영상 호스트와 DeskInk 실행 진입점 |
| `ios/` | Swift/SwiftUI iPad 앱, Pencil 입력, Metal 영상 표시 |
| `proto/` | 공통 전송 프로토콜과 테스트 데이터 |
| `scripts/` | 빌드·테스트·실행·바로가기 도우미 |

[요구사항](docs/requirements.md) · [검증 계획](docs/verification-plan.md) · [개발 이력](docs/first-stroke-status.md) · [문제 제보](https://github.com/zkdlwuthwo1-hash/deskink/issues)

상세 개발 기록에는 초기 개발 PC에서 확인한 날짜별 결과가 포함되어 있습니다. 문제를 제보할 때 앱 버전, Windows/iPadOS 버전, GPU, 모니터 해상도, 재현 순서를 알려주세요. 첨부 자료에서는 페어링 코드·토큰·개인 화면 내용을 제거해 주세요.

## 기반 프로젝트와 라이선스

DeskInk는 [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor)의 커밋 `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`를 기반으로 개발했습니다. 원본의 [MIT 저작권·허가문](third-party/EternalMonitor-LICENSE)을 보존합니다. [외부 소스 안내](third-party/README.md), [Geist 글꼴 라이선스](host/assets/fonts/OFL.txt), [iPad 의존성 고지](ios/EternalMonitor/Resources/ThirdPartyNotices.txt)를 참고하세요.

외부 의존성은 각각의 라이선스를 따릅니다. 원본의 MIT 허가가 모든 배포 구성 요소에 적용되는 것은 아닙니다. DeskInk에서 새로 추가한 코드에 대한 저장소 전체의 재사용 라이선스는 아직 지정하지 않았습니다.
