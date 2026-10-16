# 첫 연결 구현 상태 — 2026-10-16

## 직접 확인한 결과

- Rust/MSVC Windows 라이브러리와 별도 `deskink.exe` 빌드 성공.
- 현재 PC: 1920×1080 모니터 두 대를 열거. 기본 모니터에 실제 투명 필기창을 생성.
- WGC 세션에서 신규 제외 인터페이스를 실제 호출함. 이어 픽셀 검사에서 일반 캡처에는 빨간 선이 있고, 우리 세션에서만 제외한 뒤 PC 화면에는 그 선이 남는 것을 확인함.
- AMD AMF 인코더로 FFmpeg 1080p 6프레임 인코딩 성공. 이 시험은 앱 전체의 지연 수치가 아님.
- 개발용 Windows 연결 대기 화면 실행 확인. 네트워크 소켓은 실제로 127.0.0.1:9876에만 바인딩됨.
- 핵심 필기/수신 세션 테스트: 시작·이동·종료, 중복, 순서 누락, 이전 연결, 범위 밖 좌표, 잘린 입력과 과도한 메시지, 재연결 후 PC 필기 유지.

## 연결한 코드

`DeskInkCanvas`는 UIKit의 정확한/병합 터치를 읽고 로컬 Core Animation 선을 먼저 갱신한다. 별도 큐로 보낸 `DI01` 입력은 기존 USB 프레임 전송 경로를 통과해 연결된 USB peer 확인 → `InkSession` → Windows 필기창으로 전달된다. SwiftUI 전체 화면은 점마다 갱신하지 않는다.

Windows의 `deskink` 진입점은 가상 화면 드라이버 변경, 원격 마우스/키보드 입력, 오디오, 자동 시작 등록, 자동 업데이트를 호출하지 않는다. 기반 소스의 기능은 남아 있지만 이 실행 경로에서 활성화하지 않는다.

## 아직 검증/구현하지 않은 사항

- Xcode 컴파일·서명·iPad 설치, 실제 USB 영상/입력 왕복, Pencil 지연과 손바닥 처리.
- Zoom 실제 수신 화면. 글로벌 캡처 제외는 사용하지 않지만 Zoom 성공으로 간주하지 않는다.
- iPad만 확대/이동, Pencil 더블탭 지우개, 여러 도구와 포인터, 지우기/숨기기/실행 취소.
- PC 두 대 선택 UI, 영구 저장 및 재시작 복원, 재연결 시 iPad로 전체 필기 복원.
- LAN/이더넷 필기 경로. 첫 연결 구현은 USB만 받는다.
- Windows 필기 렌더러의 긴 필기 성능. 현재 전체 장면 CPU 래스터 방식이며 최종 저지연 렌더러가 아님.
- 디스플레이 크기 변경 시 현재 첫 구현은 명확한 오류로 종료하며 자동 재배치는 후속 작업이다.

현재 코드를 완성된 발표용 앱이나 측정된 초저지연 제품으로 표시하지 않는다. 작업 01은 실제 iPad 검증 뒤 완료하고 02–06을 이어간다.

## 재현 명령

```powershell
. .\scripts\windows-env.ps1
cargo test -p deskink-core
cargo run -p deskink-windows --example capture_probe
cargo run -p deskink-windows --example exclusion_pixels
cargo build -p eternal-host --bin deskink
```

캡처 픽셀 시험은 잠깐 빨간 선을 띄웠다가 제거한다. 사진이나 화면을 외부로 전송하지 않는다.
