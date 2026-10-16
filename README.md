# DeskInk — 아이패드로 쓰는 윈도우 화면 칠판

DeskInk는 개발 중 사용할 임시 이름입니다. iPad에서 Windows 모니터를 보며 필기하고, Windows 화면 위에 같은 필기와 포인터를 표시하는 개인 발표용 앱입니다.

현재 **USB 첫 연결 검증과 발표 도구 구현**을 진행 중입니다. 기존 버전은 iloader로 iPad에 설치했고 실제 USB 영상 수신을 확인했습니다. 새 버전에는 펜·형광펜·선 단위 지우개·레이저·사라지는 펜, 색·굵기, 실행 취소·다시 실행·전체 지우기·숨기기와 Pencil 더블탭을 구현했습니다. 지운 뒤 펜을 떼면 직전 도구로 돌아옵니다. iPad 전체 단위 테스트 160개가 통과했으며 새 설치 파일의 실기기 검증은 남아 있습니다. Windows 캡처의 FPS 저하도 수정했고, 해당 PC의 캡처→인코딩 진단에서 약 55–59fps를 확인했습니다. 실제 iPad 수신 성능과 물리적인 펜 지연은 별도로 검증합니다. 확대·영구 저장·PC 두 대 전환은 후속 작업입니다.

Windows에서는 `scripts/build-windows.ps1`로 빌드합니다. 실행할 터미널에서 `scripts/windows-env.ps1`을 dot-source한 뒤 `target/debug/deskink.exe`를 실행합니다. `--list-monitors`, `--monitor 0`으로 모니터를 지정할 수 있습니다. 원본 프로젝트 실행 파일 `eternal-host.exe`는 DeskInk 실행 경로가 아닙니다.

이 PC의 개발용 앱을 바로 실행하려면 `powershell -File scripts/run-windows.ps1`을 사용합니다. 두 번째 모니터는 `-Monitor 1`로 선택합니다. Mac으로 옮길 소스와 빌드·테스트 절차는 아래 안내에 있습니다.

최적화한 Windows 실행물은 `powershell -File scripts/build-windows.ps1 -Release`로 빌드하고 `powershell -File scripts/run-windows.ps1 -Release`로 실행합니다. 이미 실행 중인 인스턴스와 동시에 열지 않습니다.

- [현재 구현과 실제 검증 결과](docs/first-stroke-status.md)
- [Windows에서 계속 개발하고 iPad에 설치하기](docs/windows-only-ipad.md)
- [Mac에서 iPad 빌드 이어가기](docs/mac-build.md)

- [확정한 요구사항](docs/requirements.md)
- [구조와 개발 순서](docs/implementation-plan.md)
- [테스트 범위와 실기기 검증](docs/verification-plan.md)
- [개발 환경 확인 결과](docs/environment.md)
- [작업 관리 방식](docs/agents/issue-tracker.md)

최우선 목표는 펜 반응과 발표 중 사용성입니다. 실제 지연을 측정하기 전에는 성능을 달성했다고 표시하지 않습니다.
