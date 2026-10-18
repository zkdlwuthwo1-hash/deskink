# 로컬 Mac 없이 iPad 앱 빌드·설치

[한국어 README](../README.ko.md) · [English README](../README.md)

Windows를 개발 환경으로 사용하면서 iPad 코드는 GitHub의 macOS 서버에서 빌드할 수 있습니다. 생성된 IPA는 Windows에서 본인 Apple 계정으로 서명하여 설치합니다. 빌드 서버는 발표 중의 영상이나 필기 전송에 참여하지 않습니다.

## GitHub에서 빌드

1. [DeskInk 저장소](https://github.com/zkdlwuthwo1-hash/deskink)를 본인 계정으로 Fork합니다.
2. Fork의 **Actions → Build iPad app → Run workflow**를 선택합니다. Actions가 꺼져 있다면 먼저 활성화합니다.
3. 기본값은 전체 단위 테스트입니다. 화면 UI 검사도 포함하려면 `test_filter`에 `EternalMonitorTests,EternalMonitorUITests/DeskInkViewportUITests`를 입력합니다.
4. 성공한 실행의 Artifacts에서 `DeskInk-iPad-…`를 내려받아 압축을 풉니다. `DeskInk-unsigned.ipa`가 기기용 앱이며 SHA-256 파일과 빌드 정보도 함께 제공됩니다.
5. 실패했다면 `tests.log` 또는 `build.log`의 첫 오류를 확인합니다. 실패한 실행에는 IPA 없이 로그만 있을 수 있습니다.

워크플로는 수동 실행 전용이며 최대 실행 시간은 35분, 결과 보관 기간은 3일입니다. Apple 인증 정보를 CI에 넣지 않습니다. 현재 Xcode 16.4를 사용하며 TestFlight용 서명·업로드는 하지 않습니다.

[빌드 12 실행](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39118670760)은 iPad 단위 검사 164개, UI 검사 2개, 기기용 Release 빌드를 통과한 기록입니다. 결과 파일이 만료됐다면 새 실행으로 다시 빌드합니다.

## Windows에서 서명·설치

1. Apple Devices에서 USB iPad가 인식되는지 확인합니다. 잠금 해제 후 **이 컴퓨터를 신뢰**를 허용합니다.
2. iPad 설치 도구(개발 과정에서는 iloader 사용)를 공식 배포처에서 준비합니다. 로그인과 이중 인증은 본인이 설치 도구에서 직접 진행합니다.
3. 연결한 iPad와 내려받은 `DeskInk-unsigned.ipa`를 선택하여 서명·설치합니다.
4. iPad에서 **설정 → 일반 → VPN 및 기기 관리 → 개발자 앱 → 본인 Apple 계정 → 신뢰**를 확인합니다. **설정 → 개인정보 보호 및 보안 → 개발자 모드**를 켜고 재시동합니다. 재시동 후 잠금을 풀고 확인창의 **켜기**를 누릅니다. 메뉴가 없다면 IPA를 먼저 설치하고 실행을 시도한 뒤 설정을 다시 엽니다.
5. Windows DeskInk와 iPad DeskInk를 열고 Windows에 표시된 페어링 코드를 입력합니다.

설치가 끝나면 설치 도구를 종료할 수 있습니다. 발표 중에는 Windows DeskInk, iPad DeskInk, USB 연결과 Apple 기기 연결 서비스가 필요합니다. Apple Devices는 기기 연결을 제공하며 앱 컴파일러는 아닙니다.

무료 Personal Team의 설치 프로파일은 7일 후 만료됩니다. 각 테스터가 자신의 계정으로 갱신하거나 다시 서명·설치해야 합니다. [Apple 계정 제한](https://developer.apple.com/help/account/basics/about-your-developer-account)

## 설치 이후 확인

펜·지우개·실행 취소, 지우개를 뗐을 때 이전 도구로 복귀, 두 손가락 확대·이동, 화면별 필기 보존을 확인합니다. Zoom에서는 전체 모니터를 공유하고 다른 참가자 화면에서 필기·포인터가 보이는지 확인합니다.

서버 HTTP 502/503만으로 비밀번호 오류라고 단정하지 않습니다. 공개 문제 제보에는 앱 버전과 오류 문구를 우선 첨부하고 계정 정보·페어링 코드·토큰을 제거합니다.

직접 빌드하지 않고 설치하려면 [설치·사용 안내](https://zkdlwuthwo1-hash.github.io/deskink/README.html)에서 Windows ZIP·iPad IPA·iloader를 내려받으세요. 개발자 모드, 도구 사용법과 갱신 방법도 캡처와 함께 안내합니다.
