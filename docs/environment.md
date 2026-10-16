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
- 이 기록 당시 FFmpeg shared SDK와 LLVM/libclang은 미설치 상태였으며 아래 구현 단계에서 준비했습니다.

공식 설치 근거: [Rust 설치](https://rust-lang.org/tools/install/), [Windows MSVC 도구체인](https://rust-lang.github.io/rustup/installation/windows.html).

## 첫 구현에서 추가 검증

- 프로젝트 내부 `.tools`에 고정 FFmpeg 7.1.5 LGPL shared SDK와 libclang 18.1.1을 준비하고 실제 MSVC 링크·DeskInk 실행을 확인했습니다. 시스템 전체 DLL 검색 경로는 바꾸지 않았습니다.
- 신규 WGC 제외 기능은 별도 Windows SDK 전체 교체 없이 격리한 인터페이스 코드로 호출하고 실제 캡처 픽셀까지 확인했습니다.
- 현재 AMD GPU에서 AMF 인코더 테스트가 통과했습니다. 실기기 전체 지연 측정과는 구분합니다.
- 기반 소스의 x264 테스트는 별도 test-only GPL SDK로 실행했습니다. 제품 실행·링크용 SDK는 LGPL 경로를 유지합니다.
- 메모리 부족으로 병렬 링크가 실패한 뒤 `.cargo/config.toml`의 `jobs = 1`로 직렬 빌드를 사용합니다.
