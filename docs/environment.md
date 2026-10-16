# 확인한 개발 환경

2026-10-16 현재 Windows PC에서 읽기 전용으로 확인한 결과입니다. 다른 Windows PC의 환경은 아직 확인하지 않았습니다.

| 항목 | 확인 결과 |
|---|---|
| 운영체제 | Windows 11 Pro x64, 빌드 26200 |
| CPU / GPU | AMD Ryzen 5 220 / Radeon 740M |
| 메모리 | 운영체제 보고값 약 15.2 GiB |
| 화면 | 활성 모니터 2대, 각각 1920×1080 |
| 주사율 | GPU WMI 보고값 59Hz. 모니터별 실제 주사율 미확정 |
| 기존 도구 | Git 2.54.0, Node.js 24.16.0, Python 3.14.3 |
| Windows C++ 기반 | Visual Studio Build Tools 2022 17.14.33, MSVC 14.44.35207, Windows SDK 10.0.22621.0 |
| Apple 연결 프로그램 | Apple Devices 설치, AppleMobileDeviceProcess 실행 확인 |
| iPad 연결 | 조사 시 연결된 PnP 장치에서 iPad를 발견하지 못함 |

Rust/Cargo, .NET SDK, CMake, Clang은 조사 당시 PATH와 일반 설치 위치에서 발견되지 않았습니다. 추가 준비 여부와 실제 빌드 결과는 이후 검증 기록에 남깁니다.

## 추가 준비 완료

- Rust 1.98.0 / Cargo 1.98.0 MSVC x64, rustfmt, clippy 설치 완료.
- 공식 rustup 설치 파일과 게시된 SHA-256 일치 확인 후 설치했습니다.
- 기존 사용자 PATH를 보존하고 `.cargo/bin`을 추가했습니다.
- 임시 확인 프로그램을 rustc로 컴파일·링크·실행해 Windows 네이티브 빌드 기반을 확인했습니다. 이는 앱 동작 검증이 아닙니다.

macOS/Xcode에서 해야 하는 iPad 앱 빌드·서명·설치는 이 Windows 환경에서 검증한 것으로 간주하지 않습니다. GPU 모델 확인도 실제 영상 인코딩 성공을 의미하지 않습니다.

## 개발 도구 준비 결과

같은 날 설계 준비 중 Rust 1.98.0과 Cargo 1.98.0의 `x86_64-pc-windows-msvc` 도구체인을 설치했습니다. rustfmt와 clippy도 설치했고, 기존 사용자 PATH를 보존하며 `.cargo/bin` 경로를 추가했습니다. 새 터미널에서 PATH 변경을 사용할 수 있습니다.

- 설치 프로그램은 Rust 공식 다운로드와 공개 SHA-256을 대조했습니다. 확인한 값: `6f4bef66261261fcb43131be8720bab817d403a09edec7455c371974b90bdb7e`.
- `rustc --version`, `cargo --version`, 설치된 구성 요소 목록을 확인했습니다.
- 임시 폴더의 자체 작성 프로그램을 네이티브 Windows 실행 파일로 컴파일·링크·실행했습니다. 출력: `DeskInk Windows toolchain OK`.
- 이 검사는 컴파일러 설치 검사입니다. 제품 기능 테스트나 Windows 호스트 전체 빌드 결과가 아닙니다.
- FFmpeg shared SDK, LLVM/libclang, 최신 캡처 기능용 Windows SDK는 아직 별도 준비·검증이 필요합니다.

공식 설치 근거: [Rust 설치](https://rust-lang.org/tools/install/), [Windows MSVC 도구체인](https://rust-lang.github.io/rustup/installation/windows.html).
