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

## 6. 추가 조사: WGC 확장 인터페이스의 정확한 ABI

2026-10-16 추가 확인. Microsoft `windows-rs` 기준 커밋은 `d3c0d1e528f02edec11c5c2902eb2aa8565219c1`이다. 다음 값은 이 커밋의 생성 바인딩에서 확인했으며 추정한 GUID가 아니다. [고정 소스](https://github.com/microsoft/windows-rs/blob/d3c0d1e528f02edec11c5c2902eb2aa8565219c1/crates/libs/windows/src/Windows/Graphics/Capture/mod.rs)

| 항목 | 확인 값 |
| --- | --- |
| 인터페이스 | `Windows.Graphics.Capture.IDisplayGraphicsCaptureSession` |
| IID | `bb91f61b-218a-587d-8580-2701a74c0525` |
| 상속 | `IUnknown` → `IInspectable` → 해당 인터페이스 |
| 첫 추가 메서드 | `SetWindowExclusionList(this: *mut c_void, excludedWindows: *mut c_void, result: *mut u64) -> HRESULT` |
| 둘째 추가 메서드 | `GetWindowExclusionList(this: *mut c_void, result: *mut *mut c_void) -> HRESULT` |
| 호출 규약 | `unsafe extern "system"` |
| 제외 목록 타입 | 첫 포인터는 WinRT `IIterable<Windows.UI.WindowId>` 인터페이스 포인터. `HWND[]`나 `u64[]`의 시작 주소를 직접 전달하는 API가 아님 |
| 반환값 | Rust 투영은 `Result<u64>`. ABI는 `HRESULT`와 `u64` out parameter를 분리하므로 함수 반환값 자체를 `u64`로 선언하면 틀림 |

0부터 센 vtable 슬롯은 `QueryInterface=0`, `AddRef=1`, `Release=2`, `GetIids=3`, `GetRuntimeClassName=4`, `GetTrustLevel=5`, **Set=6**, **Get=7**이다. 이는 현재 생성 인터페이스 순서와 0.58.0의 실제 [IUnknown 정의](https://github.com/microsoft/windows-rs/blob/0.58.0/crates/libs/core/src/unknown.rs), [IInspectable 정의](https://github.com/microsoft/windows-rs/blob/0.58.0/crates/libs/core/src/inspectable.rs)를 대조한 결과다. 숫자 슬롯을 흩어 쓰기보다 `#[repr(C)]` vtable과 타입을 한 모듈에 선언하는 편이 검토하기 쉽다.

**`windows = 0.58` 유지 가능성:** 0.58.0에는 `IInspectable_Vtbl`, `IIterable<T>`, `Windows.UI.WindowId`와 WGC 기본 인터페이스가 이미 있다. 따라서 새 IID/두 메서드의 수동 선언만 추가하고 기존 `GraphicsCaptureSession`에서 QueryInterface하는 방식은 ABI 차원에서 가능하다. 이는 소스에 근거한 구현 가능성 판단이며, 이 조사에서 컴파일하거나 OS 호출을 시험하지 않았다. master의 새 `windows_collections` 크레이트 타입을 그대로 복사하지 말고 0.58의 `windows::Foundation::Collections::IIterable`을 사용해야 한다. [0.58 Collections](https://github.com/microsoft/windows-rs/blob/0.58.0/crates/libs/windows/src/Windows/Foundation/Collections/mod.rs), [0.58 UI](https://github.com/microsoft/windows-rs/blob/0.58.0/crates/libs/windows/src/Windows/UI/mod.rs)

**배포 버전 확인:** crates.io API가 반환한 최신 stable은 `windows 0.62.2`였다. 실제 [0.62.2 배포 tarball](https://static.crates.io/crates/windows/windows-0.62.2.crate)을 메모리에서 열어 `src/Windows/Graphics/Capture/mod.rs`를 확인했으나 `IDisplayGraphicsCaptureSession`/`SetWindowExclusionList`가 없었다. 0.58.0에도 없다. 따라서 현재 확인한 최신 배포본으로 단순 업그레이드하면 해결된다고 안내하지 않는다. master에서는 발견되지만 master 전체 의존성을 가져와야 한다는 뜻도 아니다. [배포 버전 API](https://crates.io/api/v1/crates/windows)

### WindowId 변환

필요한 값은 `Windows.UI.WindowId { Value: u64 }`다. WinUI의 `Microsoft.UI.WindowId`와 이름이 비슷하지만 WinRT 제네릭 타입 서명이 다르므로 같은 `IIterable` 타입으로 간주하면 안 된다. 0.58 생성 코드의 RuntimeType 서명은 `struct(Windows.UI.WindowId;u8)`이다. [0.58 실제 정의](https://github.com/microsoft/windows-rs/blob/0.58.0/crates/libs/windows/src/Windows/UI/mod.rs)

공식 Windows SDK 변환 함수는 `windows.ui.interop.h`의 `HRESULT GetWindowIdFromWindow(HWND hwnd, ABI::Windows::UI::WindowId* id)`다. [Microsoft 함수 문서](https://learn.microsoft.com/en-us/windows/win32/api/windows.ui.interop/nf-windows-ui-interop-getwindowidfromwindow), [Microsoft SDK 헤더 고정 소스](https://github.com/microsoft/win32metadata/blob/0f012441d654d3bb7de898fdc664b5fcad52bdcc/generation/WinSDK/RecompiledIdlHeaders/um/windows.ui.interop.h)

별도 WinUI 런타임을 도입하지 않는 경로도 있다. API 담당자는 HWND의 정수값을 WindowId로 캐스팅하는 방식을 허용한다고 직접 설명했다. 유효한 자기 필기창의 HWND를 포인터 폭의 unsigned 정수로 바꾼 후 `u64 Value`에 담는 작은 변환 함수를 둘 수 있다. 창이 파괴되거나 다시 만들어지면 목록을 갱신해야 한다. [담당자 사용 안내](https://github.com/robmikh/Win32CaptureSample/issues/106)

## 7. 추가 조사: 기존 CI와 동일한 FFmpeg SDK

EternalMonitor 고정 커밋의 Windows CI는 **Gyan FFmpeg 7.1.1 full shared**를 사용한다. `FFMPEG_DIR`는 압축을 푼 최상위 디렉터리, DLL 검색 경로는 그 아래 `bin`이다. [CI의 실제 다운로드 단계](https://github.com/sidebandstudio/EternalMonitor/blob/9888f37a5a457c76ec1d960dc9d9f1115304e6a9/.github/workflows/ci.yml)

- CI가 사용하는 고정 자산: [ffmpeg-7.1.1-full_build-shared.7z](https://github.com/GyanD/codexffmpeg/releases/download/7.1.1/ffmpeg-7.1.1-full_build-shared.7z).
- 직접 검사한 같은 릴리스의 ZIP: [ffmpeg-7.1.1-full_build-shared.zip](https://github.com/GyanD/codexffmpeg/releases/download/7.1.1/ffmpeg-7.1.1-full_build-shared.zip).
- ZIP 크기: `71,806,900` bytes. 조사 중 받은 바이트의 SHA-256: `9f28727e8b472a04c1d2e520aaa425dca82721b995139b35710091130ea6e699`. 이는 직접 계산한 고정 식별값이며 독립적인 배포자 서명을 검증했다는 뜻은 아니다. 해당 과거 자산의 별도 Gyan checksum URL은 찾지 못했다.
- ZIP 내부에서 `include/libavcodec/avcodec.h`, `lib/{avcodec,avdevice,avfilter,avformat,avutil,postproc,swresample,swscale}.lib`, 대응 `.def`, `bin/avcodec-61.dll` 등 8개 DLL을 확인했다.
- ZIP의 `README.txt`는 **GPL v3** 및 FFmpeg source commit `db69d06eee`를 명시하고, `LICENSE`도 GPLv3다. 이 SDK를 MIT 라이브러리나 LGPL 빌드라고 표시하면 안 된다. Gyan의 현재 설명도 release full shared를 개발 파일 포함 GPLv3 빌드로 안내한다. [배포자 설명](https://www.gyan.dev/ffmpeg/builds/)

FFmpeg 공식 프로젝트는 소스만 직접 배포하고 Windows 바이너리 제공처로 Gyan과 BtbN을 연결한다. 따라서 위 자산은 **FFmpeg 공식 사이트가 안내하는 외부 빌드**이지 FFmpeg 프로젝트 자체가 제작·서명한 Windows SDK라는 뜻은 아니다. [FFmpeg 다운로드](https://ffmpeg.org/download.html)

## 8. 향후 배포를 위한 LGPL shared SDK 후보

기존 CI의 GPL SDK를 자동 채택하지 않기 위해 BtbN의 **고정된 7.1.5 LGPL shared x64 자산**을 추가 확인했다. 현재 `latest` 릴리스에는 7.1 자산이 없었으므로 `latest` URL을 사용하지 않는다.

| 항목 | 확인 값 |
| --- | --- |
| 고정 릴리스 | `autobuild-2026-07-31-14-10` |
| 자산 | `ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1.zip` |
| 크기 | `62,425,578` bytes |
| SHA-256 | `0f376f96fb38554ccefb1b2ae9c7c6a7b351f0e60a372b38262c320e8392c5d0` |
| 검증 | 메모리에서 ZIP 전체의 SHA-256을 계산했고 같은 고정 릴리스의 `checksums.sha256` 값과 일치 |
| FFmpeg 구성 | x86_64, mingw32, `--enable-version3 --enable-shared --disable-static`; `--enable-gpl` 및 `--enable-nonfree` 없음 |
| 라이선스 | 압축 내부 `LICENSE.txt`는 **GNU LGPL version 3** |

출처: [고정 릴리스](https://github.com/BtbN/FFmpeg-Builds/releases/tag/autobuild-2026-07-31-14-10), [검사한 ZIP](https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-07-31-14-10/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1.zip), [배포자 checksum](https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-07-31-14-10/checksums.sha256).

ZIP은 디스크에 설치·추출하거나 실행하지 않고 메모리에서 읽었다. 내부 파일 확인 결과:

- `include/libavcodec/avcodec.h` 존재. `include/libavcodec/version_major.h`의 `LIBAVCODEC_VERSION_MAJOR`는 **61**.
- `lib/avcodec.lib`, `avdevice.lib`, `avfilter.lib`, `avformat.lib`, `avutil.lib`, `swresample.lib`, `swscale.lib`와 대응 `.def`가 존재.
- `bin/avcodec-61.dll`, `avdevice-61.dll`, `avfilter-10.dll`, `avformat-61.dll`, `avutil-59.dll`, `swresample-5.dll`, `swscale-8.dll`이 존재. Gyan GPL SDK의 `postproc`와 똑같은 라이브러리 구성은 아니다.
- `avcodec.lib`는 `!<arch>` archive이며 COFF 멤버 machine은 AMD64(`0x8664`)다. **MSVC 링크용 `.lib` 자산이 실제 존재한다**는 것을 확인했지만 실제 MSVC 링크/실행 성공을 검증한 것은 아니다.
- 실행 없이 `avutil-59.dll`의 ASCII 구성 문자열을 읽었다. `--enable-amf`, `--enable-ffnvcodec`, `--enable-libvpl`, `--enable-libopus`, `--enable-libopenh264`가 있고, `--disable-libx264`, `--disable-libx265`가 있다. 따라서 기존 소프트웨어 `libx264` fallback을 그대로 기대하면 안 된다. 하드웨어 NVENC/AMF/QSV 실제 사용 가능 여부는 해당 PC 드라이버로 확인해야 한다.

BtbN 패키징 소스도 `.lib`, `.def`, `.dll.a`, DLL 및 include를 함께 복사하며, LGPL shared 변형은 `COPYING.LGPLv3`와 `--enable-version3`, `--enable-shared --disable-static`을 선택한다. [패키징 스크립트](https://github.com/BtbN/FFmpeg-Builds/blob/63ac3265b0789ea16c7b1f8351cd2ad195a28722/variants/windows-install-shared.sh), [LGPL 설정](https://github.com/BtbN/FFmpeg-Builds/blob/63ac3265b0789ea16c7b1f8351cd2ad195a28722/variants/defaults-lgpl.sh), [shared 설정](https://github.com/BtbN/FFmpeg-Builds/blob/63ac3265b0789ea16c7b1f8351cd2ad195a28722/variants/defaults-lgpl-shared.sh)

**MSVC import library 주의점:** FFmpeg 공식 플랫폼 문서는 `dlltool`로 만든 import library를 `/OPT:REF`와 사용할 때의 문제와, 제공 `.def`에서 `lib.exe`로 import library를 재생성하는 방법을 안내한다. 이 SDK는 MinGW 계열 구성이다. `.lib`가 있다는 이유만으로 Release 링크/실행까지 보장하지 않는다. 필요한 경우 원본 SDK를 보존하고 별도 staging에서 `lib.exe /machine:x64 /def:avcodec-61.def /out:avcodec.lib`와 같은 방식으로 생성한 뒤 확인할 수 있다. 이는 앞으로의 구현/검증 절차이며 이번 조사에서는 수행하지 않았다. [FFmpeg MSVC 링크 안내](https://ffmpeg.org/platform.html#Linking-to-FFmpeg-with-Microsoft-Visual-C_002b_002b)

**선택 제안:** 우리 첫 빌드 후보는 위 BtbN LGPL shared 자산을 우선 검토하고, 기존 호스트의 사용 라이브러리/하드웨어 인코더와 실제로 링크되는지 검증한다. GPL `libx264` fallback을 유지하려고 SDK 전체를 자동으로 GPL 빌드로 바꾸지 않는다. LGPL도 고지·대응 소스 제공 등 배포 조건이 있으며, MIT인 호스트 소스만으로 바이너리 배포 조건이 끝나지 않는다. 배포 시 실제 구성에 맞는 고지와 소스 자료를 준비한다. [FFmpeg 라이선스 설명](https://ffmpeg.org/legal.html)
