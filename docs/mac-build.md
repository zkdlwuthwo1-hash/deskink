# Mac에서 iPad 앱 빌드·설치

[한국어 README](../README.ko.md) · [English README](../README.md)

Windows 호스트는 Windows에서 실행하고, iPad 앱은 Mac의 Xcode로 빌드·서명합니다. 로컬 Mac이 없다면 [Windows 중심 안내](windows-only-ipad.md)를 사용합니다.

## 빌드와 설치

1. 저장소를 Mac으로 복제하고 Xcode, iPad 시뮬레이터 런타임, XcodeGen을 준비합니다.
2. 프로젝트 루트에서 아래 명령으로 시뮬레이터 빌드와 단위 테스트를 실행합니다.

```bash
brew install xcodegen
bash scripts/build-ipad.sh
bash scripts/test-ipad.sh
```

3. 생성된 `ios/DeskInk.xcodeproj`를 열고 `EternalMonitor` 앱 타깃의 Signing & Capabilities에서 본인의 Team을 선택합니다. 앱 표시 이름은 DeskInk입니다.
4. 필요하면 `ios/project.yml`의 `PRODUCT_BUNDLE_IDENTIFIER`를 본인 계정에서 사용할 수 있는 값으로 바꾸고 프로젝트를 다시 생성합니다. 생성된 프로젝트에서만 바꾼 설정은 다음 생성 때 덮어써질 수 있습니다.
5. iPad를 Mac에 연결하여 신뢰하고 실행 대상으로 선택한 다음 Run합니다. 개발자 모드를 요구하면 기기 안내에 따라 활성화합니다.
6. 설치한 iPad를 Windows PC에 USB로 연결합니다. Windows DeskInk와 iPad DeskInk를 열고 Windows에 표시된 코드로 페어링합니다. 기기가 인식되지 않을 때만 iTunes 또는 Apple Devices의 Apple USB 연결 구성요소를 확인합니다.

현재 CI는 Xcode 16.4로 검증했습니다. TestFlight에 제출하려면 별도로 현재 Apple의 SDK 제출 요구사항에 맞춰야 합니다. 시뮬레이터 검사는 실제 USB·Pencil 지연·Zoom 공유 검증을 대신하지 않습니다.

무료 Personal Team은 설치 프로파일이 7일 후 만료되어 재설치·갱신이 필요합니다. [Apple 계정 안내](https://developer.apple.com/help/account/basics/about-your-developer-account)

실제 기기에서 필기, 지우기 후 펜 복귀, 실행 취소, 두 손가락 확대·이동, 모니터 전환, Zoom 상대방 화면을 확인합니다. Windows 앱 종료 시 기존 필기는 지워집니다.
