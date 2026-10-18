# DeskInk — 아이패드로 쓰는 윈도우 화면 칠판

DeskInk는 개발 중 사용할 임시 이름입니다. iPad에서 Windows 모니터를 보며 필기하고, Windows 화면 위에 같은 필기와 포인터를 표시하는 개인 발표용 앱입니다.

현재 **USB 발표 도구와 iPad 전용 확대 기능**을 실기기로 검증 중입니다. 기존 버전은 iloader로 iPad에 설치했고 실제 USB 영상 수신과 도구막대 터치를 확인했습니다. 펜·형광펜·선 단위 지우개·레이저·사라지는 펜, 색·굵기, 실행 취소·다시 실행·전체 지우기·숨기기와 Pencil 더블탭을 구현했습니다. 지운 뒤 펜을 떼면 직전 도구로 돌아옵니다.

두 손가락으로 1–4배 확대·축소하고 확대된 화면을 이동할 수 있습니다. Windows 배율은 유지되고 필기는 원래 모니터 좌표로 전달됩니다. 화면 맞춤으로 전체 보기에 돌아가며 도구막대는 아래 검은 영역에 고정해 영상을 가리지 않습니다. 화면별 필기·실행 취소 기록을 분리하는 iPad 전체 단위 테스트 163개와 시뮬레이터 UI 검사 2개가 통과했습니다. 이 PC의 바탕화면에 `DeskInk-monitors-build11.ipa`를 준비했으며 iloader로 기존 앱 위에 업데이트합니다. 새 버전의 실제 USB 전환과 Pencil 검증은 남아 있습니다.

Windows 캡처의 FPS 저하도 수정했고, 해당 PC의 캡처→인코딩 진단에서 약 55–59fps를 확인했습니다. 실제 iPad 수신 성능과 물리적인 펜 지연은 별도로 검증합니다. 영구 저장·PC 두 대 전환은 후속 작업입니다.

Windows에서는 `scripts/build-windows.ps1`로 빌드합니다. 실행할 터미널에서 `scripts/windows-env.ps1`을 dot-source한 뒤 `target/debug/deskink.exe`를 실행합니다. `--list-monitors`, `--monitor 0`으로 모니터를 지정할 수 있습니다. 원본 프로젝트 실행 파일 `eternal-host.exe`는 DeskInk 실행 경로가 아닙니다.

이 PC의 개발용 앱을 바로 실행하려면 `powershell -File scripts/run-windows.ps1`을 사용합니다. 두 번째 모니터는 `-Monitor 1`로 선택합니다. Mac으로 옮길 소스와 빌드·테스트 절차는 아래 안내에 있습니다.

최적화한 Windows 실행물은 `powershell -File scripts/build-windows.ps1 -Release`로 `target/monitor-switch/release/deskink.exe`에 빌드하고 `powershell -File scripts/run-windows.ps1 -Release`로 실행합니다. 업데이트한 앱이 이미 실행 중이면 `-Monitor 0` 또는 `-Monitor 1` 명령이 기존 앱의 화면을 전환합니다.

이 PC의 바탕화면에는 `DeskInk - 화면 1`, `DeskInk - 화면 2` 바로가기가 있습니다. 앱이 꺼져 있으면 실행하고, 켜져 있으면 해당 화면으로 전환합니다. Stream Deck의 파일 열기 동작에 각각의 `.lnk`를 지정해도 됩니다. Windows 상태창의 `Screen 1`·`Screen 2`도 같은 동작입니다. 화면 1과 화면 2의 필기·실행 취소 기록은 각각 유지되며, 전체 지우기는 현재 선택한 화면에만 적용됩니다. USB 스트림을 다시 연결하므로 전환 중 영상이 잠깐 멈출 수 있습니다.

처음 적용할 때는 iPad를 빌드 11로 업데이트한 다음, 필요한 기존 필기를 캡처하고 이전 Windows DeskInk를 한 번 닫아 바로가기로 다시 엽니다. **이 한 번의 앱 종료 때 기존 필기는 사라집니다.** 이후 화면만 바꿀 때는 앱을 종료하지 않습니다. 영구 저장은 아직 없어서 앱 자체를 종료하면 모든 화면의 필기가 지워집니다. 바로가기는 `scripts/start-deskink.ps1`에서 Release 앱과 로컬 DLL을 연결하고 로그를 `artifacts/local`에 남깁니다. 프로젝트 폴더를 옮긴 경우 바로가기를 다시 연결해야 합니다.

- [현재 구현과 실제 검증 결과](docs/first-stroke-status.md)
- [Windows에서 계속 개발하고 iPad에 설치하기](docs/windows-only-ipad.md)
- [Mac에서 iPad 빌드 이어가기](docs/mac-build.md)

- [확정한 요구사항](docs/requirements.md)
- [구조와 개발 순서](docs/implementation-plan.md)
- [테스트 범위와 실기기 검증](docs/verification-plan.md)
- [개발 환경 확인 결과](docs/environment.md)
- [작업 관리 방식](docs/agents/issue-tracker.md)

최우선 목표는 펜 반응과 발표 중 사용성입니다. 실제 지연을 측정하기 전에는 성능을 달성했다고 표시하지 않습니다.
