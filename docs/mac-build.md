# Mac에서 첫 iPad 빌드

Windows에서 iPadOS SDK를 컴파일하거나 Apple 개발 서명을 완료할 수 없으므로 이 단계는 Mac의 Xcode가 필요하다. 아직 이 프로젝트의 Xcode 빌드는 실행하지 않았다.

실제 설치 이후 USB·필기·지연을 검사하는 구체적인 순서는 [iPad 첫 검증 안내](ipad-first-check.md)에 있다.

1. 프로젝트 소스를 Mac으로 복사한다. 제공한 `artifacts/local/DeskInk-source.zip`에는 Git에 기록한 소스만 들어 있다. `.tools/`, `target/`, `.reference/`는 Windows 의존성과 빌드 결과이므로 필요 없다.
2. Xcode를 설치하고 한 번 실행해 초기 구성을 완료한다. XcodeGen이 없다면 `brew install xcodegen`으로 준비한다.
3. 프로젝트 루트에서 `bash scripts/build-ipad.sh`를 실행한다. 이 스크립트는 프로젝트 생성과 서명 없는 시뮬레이터 빌드를 먼저 수행한다. 성공이 실제 Pencil/USB 검증을 뜻하지 않는다.
   이어 `bash scripts/test-ipad.sh`로 기존 단위 테스트와 `DeskInkWireTests`를 실행한다. 설치된 첫 iPad 시뮬레이터를 사용하며, 없으면 Xcode에서 iPad 시뮬레이터를 먼저 준비하도록 안내한다. 이 테스트도 아직 Windows에서 실행한 것으로 간주하지 않는다.
4. 생성된 `ios/DeskInk.xcodeproj`를 Xcode로 연다. 앱 타깃은 기반 모듈 호환성을 위해 `EternalMonitor`라는 이름을 유지하고 앱 표시 이름은 `DeskInk`다. Signing & Capabilities에서 자신의 Team을 선택한다. 원본 저자의 Team 값은 제거했다.
5. iPad를 Mac에 연결해 개발 기기로 선택하고 Run한다. 개인 Team 사용 시 설치 유효기간·사용 가능한 권한은 자신의 계정 조건을 확인한다.
6. 설치한 iPad를 Windows PC에 USB로 연결한다. Windows에서 Apple Devices와 DeskInk를 실행하고 iPad에서 DeskInk를 연다. 화면에 나타난 페어링 절차를 진행한다.

이 빌드는 첫 연결 검증용이다. 기본 펜 입력과 영상, 중복 표시, 연결 끊김을 먼저 확인한 다음 도구와 확대 기능을 개발한다. 앱 종료 시 PC 필기가 영구 저장되는 단계는 아직 아니다.

Mac에서 Codex로 이어서 작업한다면 `docs/requirements.md`, `docs/implementation-plan.md`, `docs/first-stroke-status.md`와 작업 01 티켓을 읽고 Xcode 오류부터 해결하면 된다. 기존 요구사항과 테스트 범위는 이미 승인되어 있으므로 재질문할 필요가 없다.

복사한 폴더를 Mac의 Codex에서 열고 다음처럼 요청하면 기존 작업을 이어갈 수 있다.

> DeskInk 개발을 이어가줘. docs/mac-build.md와 docs/first-stroke-status.md를 읽고 scripts/build-ipad.sh와 scripts/test-ipad.sh를 실행해 Xcode 오류부터 해결해줘. 기존 요구사항과 테스트 범위는 승인되어 있어. Apple 계정 로그인과 기기 서명에 사용자 조작이 필요한 경우에만 알려줘.
