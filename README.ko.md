# DeskInk

**아이패드·갤럭시탭의 펜으로 Windows 화면을 칠판처럼 사용합니다.**

[English](README.md) · **한국어** · [캡처로 보는 설치·사용 안내](https://zkdlwuthwo1-hash.github.io/deskink/README.html)

USB로 연결한 태블릿에 Windows 화면을 띄우고, 펜으로 쓰면 PC 화면 위에도 필기가 나타납니다. 태블릿에서만 확대할 수 있으며, PC 조작은 평소처럼 마우스와 키보드로 합니다.

## 다운로드

[**연구실 공유용 미리보기 버전 받기**](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.4.0-lab.1)

| 파일 | 사용 방법 |
| --- | --- |
| `DeskInk-Windows-x64.zip` | Windows에서 전체 압축을 풀고 `DeskInk.exe` 실행 |
| `DeskInk-iPad-unsigned.ipa` | iloader에서 본인 Apple 계정으로 아이패드에 설치 |
| `DeskInk-Android.apk` | 갤럭시탭 등 Android 8 이상 태블릿에서 열어 설치 |
| `README.html` | 내려받아 브라우저로 여는 캡처 포함 안내서 |
| `SHA256SUMS.txt` | 다운로드 파일의 무결성 확인값 |

**사용자는 개발 도구나 Mac이 필요 없습니다.** 아직 미리보기 버전입니다. Windows 11 빌드 26200·Radeon 740M·1080p 모니터 두 대에서 검증했으며, 다른 PC/GPU는 추가 확인이 필요합니다. 필기가 영상에 중복되지 않도록 사용하는 캡처 기능 때문에 최신 Windows가 필요합니다.

## 아이패드에 처음 설치하기

1. **PC와 아이패드를 준비합니다.** Windows x64 PC, iPadOS 17 이상, 호환되는 Pencil, 데이터 전송용 USB 케이블을 준비합니다. Microsoft Store에서 **Apple Devices**를 설치하고 엽니다. 아이패드의 잠금을 풀어 USB로 연결하고 **이 컴퓨터를 신뢰**를 누릅니다. DeskInk를 사용하는 동안 Apple Devices를 열어 두세요.
2. **Windows DeskInk를 실행합니다.** ZIP을 문서 폴더 등 쓰기 가능한 위치에 **전체 압축 해제**하고 그 안의 `DeskInk.exe`를 엽니다. 첫 실행에서 영상 구성요소 약 60MB를 공식 배포처에서 받아 검사하므로 인터넷이 필요합니다. 이후에는 내려받은 파일을 재사용합니다. 폴더 안의 파일을 따로 옮기지 마세요.
3. **iloader로 아이패드에 설치합니다.** [iloader 공식 사이트](https://iloader.app/)에서 Windows 버전을 받습니다. 공식 Windows 안내는 iTunes를 요구하므로 Apple 구성요소가 없다는 안내가 나오면 해당 설치 안내를 따릅니다. 연결한 아이패드를 선택하고 **본인 Apple 계정**으로 로그인·이중 인증합니다. **Import IPA / IPA 가져오기**에서 `DeskInk-iPad-unsigned.ipa`를 선택하고 **Sign & Install App**을 눌러 완료될 때까지 기다립니다. 버튼 이름은 버전·언어에 따라 조금 다를 수 있습니다.
4. **개발자를 신뢰합니다.** 아이패드에서 신뢰되지 않은 개발자라고 나오면 **설정 → 일반 → VPN 및 기기 관리 → 개발자 앱 → 본인 Apple 계정 → 신뢰**로 이동합니다. 추가 확인이나 재시동 안내가 나오면 따릅니다. 제작자의 계정이나 비밀번호를 공유받을 필요는 없습니다.
5. **개발자 모드를 켭니다 — 필수입니다.** **설정 → 개인정보 보호 및 보안 → 개발자 모드 → 켜기 → 재시동** 순서로 진행합니다. 재시동 후 잠금을 풀고, 확인창에서 **켜기**를 한 번 더 누릅니다. 기기 암호를 요청하면 입력하고 DeskInk를 엽니다. 메뉴가 없다면 **iloader로 IPA를 먼저 설치하고 DeskInk 실행을 한 번 시도한 뒤 설정을 다시 여세요.** 학교·회사에서 관리하는 기기는 설정이 제한될 수 있습니다. [Apple 공식 안내](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)

## 갤럭시탭·Android에 처음 설치하기

1. **태블릿에서 `DeskInk-Android.apk`를 열어 설치**합니다. 설치에 사용한 브라우저나 내 파일 앱의 **이 출처 허용**을 요청하면 해당 앱에 허용합니다. Apple 계정이나 iloader는 필요 없으며, 7일마다 재설치할 필요도 없습니다.
2. **설정 → 태블릿 정보 → 소프트웨어 정보 → 빌드번호를 7번 탭**하고 기기 암호를 입력합니다. 설정에 생긴 **개발자 옵션 → USB 디버깅**을 켭니다. 메뉴 이름은 기종에 따라 다를 수 있습니다. [삼성 공식 안내](https://www.samsung.com/uk/support/mobile-devices/how-do-i-turn-on-the-developer-options-menu-on-my-samsung-galaxy-device/)
3. PC에 Windows ZIP을 **전체 압축 해제**한 뒤 **`DeskInk-Android.exe`를 처음 한 번 실행**합니다. Google 공식 Android USB 구성요소 약 8MB와 영상 구성요소 약 60MB를 받아 검사합니다. 이때 인터넷이 필요합니다.
4. 데이터 USB 케이블로 연결하고, 태블릿의 **USB 디버깅 허용** 창에서 본인 PC를 허용합니다. 태블릿 DeskInk를 열어 PC에 표시된 6자리 코드를 입력합니다. Android 태블릿은 한 번에 한 대만 USB로 연결하세요.
5. **S펜 버튼을 누르는 동안 지우개, 놓으면 이전 도구**로 돌아옵니다. 버튼 없는 펜은 도구막대의 지우개를 사용합니다. 기기가 인식되지 않으면 [삼성 공식 Windows USB 드라이버](https://developer.samsung.com/android-usb-driver)를 확인하세요.

이후에는 바탕화면의 **DeskInk - Screen 1 / Screen 2** 중 하나를 열면 됩니다. Android용은 Apple Devices를 사용하지 않습니다. USB 디버깅에는 신뢰하는 PC만 허용하고, 더 이상 사용하지 않을 때는 끌 수 있습니다. 업데이트는 같은 서명의 새 APK를 기존 앱 위에 설치합니다.

Android판은 자동 검사와 에뮬레이터로 검증하며, **실제 갤럭시탭의 S펜 버튼 전달·필기 감각·전체 지연은 실기기 확인이 필요**합니다.

## 매번 사용하기

1. 태블릿을 **USB로 연결**하고 잠금을 풉니다. PC에서 DeskInk를 엽니다. 아이패드 사용자는 Apple Devices도 열어 둡니다.
2. 바탕화면의 **DeskInk - Screen 1 / Screen 2** 아이콘이나 PC 앱에서 사용할 화면을 고릅니다. 태블릿 DeskInk를 열고 **PC에 표시된 페어링 코드**를 입력합니다. PC DeskInk를 재시작하면 다시 페어링합니다.
3. 펜으로 쓰고, **아래 도구막대**에서 도구를 바꿉니다.

| 도구·동작 | 사용법 |
| --- | --- |
| 펜·형광펜 | 필기를 남깁니다. 색상·굵기를 고를 수 있습니다. |
| 지우개 | 닿은 선을 통째로 지웁니다. 도구막대에서 선택한 지우개는 펜을 떼면 이전 도구로 복귀합니다. S펜은 버튼을 누르는 동안 지우며, 놓으면 복귀합니다. 지원 Pencil은 두 번 탭으로도 전환합니다. |
| 레이저·사라지는 펜 | 위치를 가리키거나 약 2초 뒤 사라지는 필기를 남깁니다. 화면에 대기 전 포인터 표시는 호버 지원 기기가 필요합니다. |
| 취소·다시 | 현재 모니터의 변경을 실행 취소·다시 실행합니다. |
| 눈·휴지통 | 필기 숨기기/보이기·현재 모니터 전체 지우기입니다. 전체 지우기도 실행 취소할 수 있습니다. |
| 두 손가락 벌리기·끌기 | **태블릿에서만** 1~4배 확대·이동합니다. **화면 맞춤**으로 돌아옵니다. 손가락은 필기하지 않습니다. |

**바탕화면에서 화면 전환:** Windows DeskInk를 처음 실행하면 **DeskInk - Screen 1 / Screen 2** 아이콘이 자동으로 만들어집니다. 누구나 원하는 아이콘을 **더블클릭**하면 터미널 창 없이 해당 화면으로 전환됩니다. Stream Deck 사용자는 이 바로가기를 열기 동작에 등록해도 됩니다. 영상은 잠시 다시 연결됩니다. DeskInk 폴더를 같은 위치에 두고, 아이콘을 다시 만들 때는 `DeskInk-Shortcuts.exe`를 실행하세요.

실행 중에는 화면마다 필기가 따로 유지됩니다. **Windows DeskInk를 종료하면 모든 필기가 지워지며, 영구 저장 기능은 없습니다.** iloader는 설치 후 꺼도 됩니다. 발표 중에는 DeskInk를 켜 두고 USB 연결을 유지하세요.

Zoom에서는 직접 **필기하는 모니터 전체**를 공유하고 다른 참가자 화면에서도 필기와 포인터가 보이는지 확인하세요. 특정 앱 창만 공유하면 필기가 빠질 수 있습니다.

## 7일마다 아이패드 설치 갱신하기

무료 Apple 계정으로 설치한 개발용 앱의 서명은 **7일 후 만료**됩니다. **Apple 계정이 7일짜리이거나 iloader 유료 체험인 것은 아닙니다.** 만료 전에 USB로 연결해 iloader에서 **같은 계정·같은 IPA로 Sign & Install App을 다시 실행**하면 됩니다. 이 방식은 구독 없이 갱신합니다. [Apple 무료 계정 제한](https://developer.apple.com/help/account/basics/about-your-developer-account)

## 막힐 때

- **iPad가 안 보임:** 잠금 해제 → 컴퓨터 신뢰 → Apple Devices 실행 → 데이터 케이블·다른 USB 포트를 확인합니다.
- **Android가 안 보임:** `DeskInk-Android.exe` 실행 → USB 디버깅 켜기 → 태블릿에서 PC 허용 → 데이터 케이블·삼성 USB 드라이버를 확인합니다. 앱을 태블릿 화면에 열어 두세요.
- **개발자 앱을 사용할 수 없음:** 위 4·5단계를 확인합니다. 개발자 모드는 재시동 후 **켜기** 확인까지 필요합니다.
- **일주일 뒤 iPad 앱이 안 열림:** iloader에서 다시 서명·설치합니다.
- **다른 화면이 나옴:** PC의 Screen 1/2를 바꿉니다. DeskInk 번호는 Windows 디스플레이 설정 번호와 다를 수 있습니다.
- **Windows 실행 실패:** 전체 압축 해제 여부와 첫 실행 인터넷 연결을 확인합니다. 로그는 `%LOCALAPPDATA%/DeskInk/logs`에 있습니다. 오류를 공유할 때 페어링 코드와 개인 화면 내용은 빼 주세요.

현재 Bluetooth·Wi-Fi·이더넷 필기 연결은 구현되어 있지 않습니다. 실제 전체 입력 지연, 모든 태블릿 모델, Zoom 상대방 화면은 자동 검사만으로 검증되지 않습니다.

[문제 제보](https://github.com/zkdlwuthwo1-hash/deskink/issues) · [소스 빌드·테스트](docs/development.md) · [iPad 클라우드 빌드](docs/windows-only-ipad.md)

## 기반 프로젝트와 라이선스

[EternalMonitor](https://github.com/sidebandstudio/EternalMonitor) 커밋 `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`를 기반으로 합니다. 원본 [MIT 고지](third-party/EternalMonitor-LICENSE)를 보존합니다. ZIP의 `licenses/`와 [iPad 고지](ios/EternalMonitor/Resources/ThirdPartyNotices.txt)에 의존성 라이선스가 있습니다. 영상 구성요소는 첫 실행에서 [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds)로부터 별도로 내려받습니다. 각 외부 구성요소는 자체 라이선스를 따르며, 새로 추가된 DeskInk 코드에 대한 저장소 전체의 재사용 라이선스는 아직 지정하지 않았습니다.

Android APK에는 [구성요소 고지](android/app/src/main/assets/ThirdPartyNotices.txt)가 포함되어 있습니다. Windows Android 연결 도구는 Google [Platform Tools](https://developer.android.com/tools/releases/platform-tools)를 별도로 받고 원본 NOTICE.txt를 보관합니다.
