# 화면 전송 기반과 필기 중복 방지 조사

확인일: 2026-10-16. 이 문서는 설계 자료이며, 앱 구현·빌드·장치 테스트 결과가 아니다. 공식 문서와 공개 소스만 확인했다.

## 결론

EternalMonitor는 Windows 화면 캡처, 하드웨어 영상 인코딩, USB 연결, iPad 영상 재생을 재사용할 수 있는 실제 공개 프로젝트다. 하지만 발표용 화면 전체 필기, iPad 자체 즉시 필기, 독립 확대, 필기 저장 기능을 이미 제공하는 제품으로 보아서는 안 된다.

가장 먼저 검증할 설계는 **Windows.Graphics.Capture(WGC)의 세션별 창 제외 기능으로 우리 전송 영상에서만 필기창을 빼고, iPad와 Windows가 같은 필기 데이터를 각각 그리는 방식**이다. Windows 필기창은 일반적인 모니터 공유에 계속 나타나게 둔다. 해당 신규 API가 두 PC에서 실제 사용 가능한지, Zoom 수신 화면에는 필기가 보이는지가 선행 조건이다.

`SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)`만으로는 이 분리가 성립하지 않는다. 이 속성은 창에 적용되며 특정 캡처 프로그램을 지정하는 인자가 없다. 이 속성을 지키는 Zoom 캡처에서도 필기가 제외될 수 있으므로, “iPad 영상에서만 제외하고 Zoom에는 포함된다”고 단정하면 안 된다. [Microsoft API 문서](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowdisplayaffinity)

## 1. 확인된 저장소 상태와 빌드 조건

- 저장소: [sidebandstudio/EternalMonitor](https://github.com/sidebandstudio/EternalMonitor)
- 기본 브랜치: `main`
- 조사 기준 커밋: [`9888f37a5a457c76ec1d960dc9d9f1115304e6a9`](https://github.com/sidebandstudio/EternalMonitor/commit/9888f37a5a457c76ec1d960dc9d9f1115304e6a9), 커밋 시각 `2026-10-11T22:50:29Z`. GitHub API와 해당 SHA의 raw 파일 접근 성공.
- 루트 라이선스: MIT, 저작권자 Ali Younes. 수정·배포 허용 및 저작권/허가문 보존 조건이 있다. 외부 라이브러리와 배포 자산의 조건은 각각 별도 확인해야 한다. [고정 버전 LICENSE](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/LICENSE)

| 구성 | 소스에서 확인한 조건 |
| --- | --- |
| Rust | `rust-toolchain.toml`은 `1.98.0`, rustfmt/clippy 지정. 루트·host·proto Cargo.toml에 `rust-version` 명시가 없어, 별도 최소 지원 Rust 버전은 확인하지 못함 |
| Windows 호스트 | README 기준 Windows 10 1809 이상/11, MSVC, FFmpeg 7.1 shared SDK, LLVM/libclang. `FFMPEG_DIR`로 SDK 경로 지정 |
| 의존성 | `ffmpeg-next = 7`, `windows = 0.58`, `eframe = 0.36`; 현재 호스트는 DXGI 캡처. 신규 WGC 인터페이스 바인딩은 별도 확보 필요 |
| iPad | `ios/project.yml` 기준 iOS/iPadOS 17.0, Swift 5.9, XcodeGen. Xcode에서 본인 서명 팀과 앱 식별자로 구성해야 함 |
| USB 실행 | Windows의 Apple Devices 및 Apple 장치 서비스, 데이터 전송 가능한 케이블, 실제 iPad 신뢰/연결 절차 필요 |

출처: [Rust 도구체인](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/rust-toolchain.toml), [host/Cargo.toml](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/Cargo.toml), [iOS 프로젝트](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/ios/project.yml), [README 빌드/실행 조건](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/README.md#build-from-source).

프로젝트의 Windows 10 지원과 우리가 새로 선택할 세션별 캡처 제외 기능의 지원 범위는 다르다. 최신 Windows SDK를 설치하는 것만으로 구형 OS에 기능이 생기지는 않는다.

## 2. 실제 확장 지점

아래 위치는 위 SHA의 소스 기준이다. 상위 프로젝트 전체를 그대로 포크해야 한다는 뜻은 아니다.

| 영역 | 실제 파일/구조 | 우리가 추가하거나 바꿀 부분 |
| --- | --- | --- |
| 화면 캡처 | [`capture/mod.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/src/capture/mod.rs)의 `RawFrame`, 최신 프레임 `FrameSlot`, `run_capture_stage`; [`capture/dxgi.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/src/capture/dxgi.rs)의 `DuplicateOutput` | 선택 모니터의 WGC 백엔드를 추가하고, 필기창 제외 여부를 기능 탐지 결과에 따라 결정 |
| 인코딩 | [`encoder/mod.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/src/encoder/mod.rs)의 `run_encode_stage`, `NALUnit`, GPU별 low-delay 설정 | 기존 경로를 출발점으로 사용하되 캡처 변경 후 복사 횟수·큐 체류시간 측정 |
| USB | [`usbmuxd.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/src/transport/usbmuxd.rs), [`link.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/src/transport/link.rs), [`usb.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/host/src/transport/usb.rs) | 장치 서비스 `127.0.0.1:27015`에서 iPad 앱 포트 `9877`로 터널. `EMLINK` 프리앰블과 길이 프레임을 유지하면서 필기 메시지 추가 |
| 프로토콜 | [`proto/src/v2/control.rs`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/proto/src/v2/control.rs), Swift `WireProtocol.swift`/`ControlChannel.swift` | 기존 `INPUT_EVENT`는 원격 OS 입력용. 발표 필기는 별도 협상 capability와 stroke/tool/undo/clear/snapshot 메시지로 모델링 |
| iPad 입력 | [`PencilRelay.swift`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/ios/EternalMonitor/Input/PencilRelay.swift), [`TouchRelayView.swift`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/ios/EternalMonitor/Input/TouchRelayView.swift) | 정밀 좌표·coalesced samples 수집을 활용. PC 펜 주입 대신 자체 필기 모델에 전달하고 손가락 확대/이동 및 Pencil 더블탭을 추가 |
| iPad 영상 | [`VideoDecoder.swift`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/ios/EternalMonitor/Decode/VideoDecoder.swift)의 VideoToolbox callback, [`MetalView.swift`](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/ios/EternalMonitor/Render/MetalView.swift)의 NV12/aspect-fit 렌더러 | 영상과 필기에 같은 확대/이동 변환 적용. 펜 좌표에는 역변환 적용. 현재 Metal 뷰는 새 영상 프레임 도착 때 그리므로 필기 갱신은 영상 도착과 독립적으로 예약해야 함 |

기존 프로토콜은 기능 협상, session/epoch, 단편 프레임, 순서/중복 방지에 대한 설계가 있다. 새 필기 명령에는 PC ID·모니터 ID·필기 세대·stroke ID·샘플 순서를 두어 다른 PC의 늦은 메시지나 전체 삭제 전의 데이터가 복원되지 않게 해야 한다. 이는 **우리 추가 설계**이며 기존 기능이라는 주장이 아니다. [기존 아키텍처](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/ARCHITECTURE.md)

사용자는 PC 조작을 마우스로 한다. 따라서 원격 마우스/키보드/Windows Ink 주입과 가상 확장 모니터(VDD)는 첫 버전에서 필요하지 않다. 선택 모니터 미러링과 자체 필기창에 범위를 좁히는 것이 적합하다. 기존 저장소의 가상 모니터 드라이버 설치 작업을 우리 앱의 필수 작업으로 가져올 이유도 없다.

## 3. 화면 공유와 중복 필기 문제

### 확인된 Windows 기능

1. `SetWindowDisplayAffinity`는 창 단위 전역 속성이다. `WDA_EXCLUDEFROMCAPTURE`는 Windows 10 2004부터 지원하며, 이 속성을 따르는 캡처에는 창 자체가 보이지 않는다. 문서는 모든 캡처를 보장하는 보안 기능이라고 하지는 않는다. 따라서 Zoom에서의 실제 동작도 별도 확인해야 한다. [Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowdisplayaffinity)
2. 최신 문서에는 `IDisplayGraphicsCaptureSession.SetWindowExclusionList`가 실제로 존재한다. 공식 SDK 10.0.26100.8249 릴리스노트는 이 인터페이스와 제외 목록 메서드, capture frame/session의 `ConfigurationIteration` 추가를 기록한다. API 페이지에는 여전히 prerelease 안내가 있어 문서만으로 특정 PC 지원을 확정하지 않는다. [API](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.idisplaygraphicscapturesession.setwindowexclusionlist?view=winrt-28000), [SDK 릴리스노트](https://learn.microsoft.com/en-us/windows/apps/windows-sdk/release-notes)
3. WGC 담당자의 공개 샘플 저장소 답변은 display 대상 `GraphicsCaptureSession`에서 위 인터페이스를 QueryInterface하고, HWND를 WindowId로 변환해 제외 목록을 지정하는 사용법을 설명한다. 업데이트/기능 활성 상태가 맞지 않으면 QueryInterface가 실패할 수 있다고 명시한다. [공개 구현 담당자 답변](https://github.com/robmikh/Win32CaptureSample/issues/106)

### 추천 경로 — 세션별 제외가 실제 동작할 때

```text
Windows 데스크톱 ── 우리 WGC 세션(필기창 제외) ── 영상 ── iPad 배경
        │                                                  + 즉시 필기
        └── 일반 모니터 표시 + Windows 필기창 ← 필기 데이터 ──┘
                      │
                사용자가 Zoom에서 모니터 공유
```

- Windows 필기창은 일반 top-level 투명창으로 유지하고 글로벌 캡처 제외 속성을 켜지 않는다.
- 우리 WGC 세션의 제외 목록에 필기창을 넣는다. iPad로 가는 영상에 필기가 없으므로 iPad가 모든 필기를 한 번만 그린다. 반투명 형광펜·지우개·실행 취소도 같은 원리로 처리한다.
- 실제 필기 데이터는 양쪽에서 같은 좌표계로 렌더링한다. iPad 확대/이동은 보기 상태이며 Windows 원본 좌표나 선 굵기를 확대하지 않는다.
- 제외 설정 전 프레임이나 모니터 전환 전 프레임이 섞이지 않게 세션/스트림 세대를 바꾼다. `ConfigurationIteration`의 정확한 적용 시점과 반환값 의미는 이번 문서만으로 충분히 확인되지 않았으므로, 초기 구현에서 이를 검증 없이 동기화 보증으로 사용하지 않는다.
- Zoom의 실제 수신 장치에서 선·형광펜·커서가 보이는지 검증한다. “Windows에서 보인다”만으로 합격 처리하지 않는다.

이 구조는 공개 API에 근거한 **제안 설계**다. 이번 조사에서는 해당 API를 이 PC에서 호출하거나 Zoom 테스트를 수행하지 않았다.

### 세션별 제외를 사용할 수 없는 PC

일반 데스크톱 영상에 Windows 필기가 들어 있고 그 위에 iPad 필기를 다시 그리면 중복이 생긴다. 완료 확인을 받고 iPad 선을 제거하는 방식은 지속적인 중복을 줄일 수 있지만, 수신 확인은 화면 표시 확인과 다르다. 특히 반투명 형광펜은 겹친 동안 진해지고, 지우개/실행 취소는 영상에 이미 들어간 과거 선을 즉시 제거할 수 없다.

따라서 “ACK를 받으면 로컬 선을 지운다”는 방식만으로 모든 도구의 중복 문제를 해결했다고 보지 않는다. 코드상 프레임 번호와 표시된 필기 revision을 연결하더라도 압축된 배경과 필기를 정확히 분리하는 별도 문제가 남는다.

호환 경로는 선택한 앱 창을 별도 캡처해 그 위에 필기하는 방식이나, 명시적인 정지 화면 필기 모드 등으로 범위를 제한할 수 있다. 그러나 이는 사용자가 요구한 임의 데스크톱의 실시간 전체 화면 필기와 사용 흐름이 다르다. 기능을 조용히 바꾸지 말고, 두 PC에서 신규 API를 먼저 시험한 뒤 지원 기준을 정한다. 캡처 순간 필기창을 숨겼다 다시 보이는 방식은 Zoom 누락·깜빡임·동기화 문제가 있으므로 기본 설계로 추천하지 않는다.

## 4. 지연에 대해 확인된 것과 확인되지 않은 것

기존 프로젝트 문서는 실제 iPad Pro M2/Windows RTX 5080 기준 USB와 Wi-Fi에서 약 58 FPS, 30분 실행 결과를 공개한다. USB 확장 화면 2732×2048 실행은 58.42 FPS로 기록되어 있다. 다만 원시 결과는 비공개 evidence 경로를 참조하며, 우리 장치에서 재현한 결과가 아니다. FPS는 입력부터 픽셀까지의 지연과 다른 지표다. [하드웨어 검증 기록](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/docs/hardware-verification.md)

기존 Pencil 경로는 정밀한 실제 샘플을 영상 FPS와 무관하게 보내고, USB `TCP_NODELAY`와 묶음 전송을 사용한다. 문서 자체가 이러한 최적화로 Pencil-to-pixel 지연을 측정했다고 볼 수 없다고 명시한다. [Pencil 문서](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/docs/pencil-drawing.md)

Apple은 coalesced touches를 수집하고, predicted touches를 별도 보관했다가 실제 샘플 도착 때 교체하는 로컬 그리기 방식을 제공한다. 우리 앱은 실제 샘플을 영구 필기로 전송·저장하고, 예측 샘플은 iPad의 일시적인 표시로만 사용하는 것이 적합하다. 예측을 최종 선으로 전송해 굳히지 않는다. [Apple 그리기 예제](https://developer.apple.com/documentation/uikit/leveraging-touch-input-for-drawing-apps), [예측 입력 교체](https://developer.apple.com/documentation/uikit/incorporating-predicted-touches-into-an-app)

## 5. 최소 검증 순서 제안

아래는 앞으로 실행할 검증이며 아직 수행하지 않았다.

1. **캡처 분리 검증:** 두 Windows PC의 OS/API 지원을 탐지한다. 임시 필기창이 PC와 Zoom 수신에는 나타나고 우리 전송용 WGC 프레임에서는 빠지는지 확인한다. 창 이동·모니터 전환·DPI 배율도 포함한다. 이 검증이 실패하면 전체 화면 동작의 지원 경로를 먼저 재설계한다.
2. **iPad 로컬 선 한 줄:** 영상 전송 없이 실제 Pencil 입력에 즉시 선을 그린다. 압력·빠른 곡선·손바닥·더블탭 지우개를 확인한다. 입력 수집/렌더링 대기 시간이 영상 수신 작업에 막히지 않아야 한다.
3. **USB 실제 샘플 전송:** 선 한 줄을 Windows 필기창에 표시한다. 펜을 뗌/취소/케이블 분리 때 마지막 선이 무한히 이어지지 않는지 확인한다. 명령 순서와 재전송을 검증한다.
4. **영상+필기 결합:** 깨끗한 배경 영상과 로컬 선을 겹친다. 반투명 형광펜을 포함해 이중 합성이 없는지, 확대·이동 후 PC의 대응 위치가 맞는지 확인한다.
5. **지연 측정:** iPad 로컬 표시, PC 표시, PC 배경의 iPad 도착을 각각 측정한다. 소프트웨어 timestamp와 고속 촬영을 함께 사용하고 p50/p95 및 최악 구간을 남긴다. 장치 간 시각 차이를 보정하지 않은 timestamp 차이를 그대로 지연이라고 부르지 않는다.
6. **USB/이더넷 비교:** 같은 해상도·FPS·코덱·전원 상태로 짧은 동일 시나리오를 비교한다. 평균 FPS뿐 아니라 입력 지연 분포, 큐 길이, 재연결, 발열을 본다. Zoom 수신 지연은 별도 항목으로 기록한다.

첫 구현 범위는 모니터 한 대의 미러링, 자체 펜/지우개, iPad 독립 확대, PC별 필기 상태로 좁힌다. 추가 도구는 이 경로를 통과한 뒤 같은 필기 모델에 확장하는 것이 적합하다. 목표 지연 수치를 아직 달성 수치로 제시하지 않는다.

## 남은 불확실성

- 두 PC의 WGC 세션별 제외 실제 지원 여부와 Zoom 캡처 경로의 동작.
- 신규 WGC 경로의 성능 및 Rust 바인딩/Windows SDK 구성.
- 실제 사용 iPad Air의 모델·OS·화면 주사율·케이블/포트 조건에 따른 지연.
- 기존 프로젝트의 네트워크 전송은 암호화되지 않는다. 인증과 암호화는 별개이며, 향후 LAN/이더넷 배포에서는 기존 페어링만으로 전송 기밀성이 확보된다고 설명하면 안 된다. [기존 보안 설명](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/SECURITY.md)

