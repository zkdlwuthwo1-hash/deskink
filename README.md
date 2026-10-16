# DeskInk — 아이패드로 쓰는 윈도우 화면 칠판

DeskInk는 개발 중 사용할 임시 이름입니다. iPad에서 Windows 모니터를 보며 필기하고, Windows 화면 위에 같은 필기와 포인터를 표시하는 개인 발표용 앱입니다.

현재 단계는 **작업 01: USB로 화면을 보면서 첫 선 그리기**입니다. Windows 개발용 실행 파일과 iPad 소스가 있으며, iPad 빌드·설치와 실제 Pencil 검증은 아직 수행하지 않았습니다. 전체 도구·확대·저장 기능이 완성된 버전은 아닙니다.

Windows에서는 `scripts/build-windows.ps1`로 빌드합니다. 실행할 터미널에서 `scripts/windows-env.ps1`을 dot-source한 뒤 `target/debug/deskink.exe`를 실행합니다. `--list-monitors`, `--monitor 0`으로 모니터를 지정할 수 있습니다. 원본 프로젝트 실행 파일 `eternal-host.exe`는 DeskInk 실행 경로가 아닙니다.

이 PC의 개발용 앱을 바로 실행하려면 `powershell -File scripts/run-windows.ps1`을 사용합니다. 두 번째 모니터는 `-Monitor 1`로 선택합니다. Mac으로 옮길 소스와 빌드·테스트 절차는 아래 안내에 있습니다.

- [현재 구현과 실제 검증 결과](docs/first-stroke-status.md)
- [Mac에서 iPad 빌드 이어가기](docs/mac-build.md)

- [확정한 요구사항](docs/requirements.md)
- [구조와 개발 순서](docs/implementation-plan.md)
- [테스트 범위와 실기기 검증](docs/verification-plan.md)
- [개발 환경 확인 결과](docs/environment.md)
- [작업 관리 방식](docs/agents/issue-tracker.md)

최우선 목표는 펜 반응과 발표 중 사용성입니다. 실제 지연을 측정하기 전에는 성능을 달성했다고 표시하지 않습니다.
