# Windows 캡처 FPS 저하 진단 — 2026-10-16

사용자가 iPad 설치·실행 후 펜이 나타나지만 FPS가 낮다고 보고했다. 기존 앱의 USB 수신 보고에서 정지 화면은 약 2fps였고, 움직이는 테스트 창에서는 37–43fps 수준을 재현했다. 정지 화면 2fps는 변경 없는 화면의 재전송 주기이며 그 자체로 성능 실패가 아니다.

## 재현과 원인 분리

- 처음 WinForms 애니메이션 타이머를 16ms로 설정한 검사에서 수신 중앙값 37.1/36.7fps였다. 자극 자체의 갱신 빈도를 확인하기 위해 타이머를 1ms로 바꾸고 실제 tick 수를 계측했다. 이후 자극은 초당 약 62회, 기존 iPad 수신은 41–43fps였다. 두 자극 조건을 같은 측정으로 섞지 않는다.
- 1080p BGRA 입력을 60fps로 직접 넣은 실제 인코더는 60fps를 유지했다. 입력 시각→인코딩 완료 p50 8.2ms, p95 9.7ms. 단색 입력 진단이며 복잡한 장면의 처리 보장이나 Pencil 지연 수치가 아니다.
- 실제 WGC→인코더 경로는 중앙값 43.5fps로 `--minimum-fps=50` 검사를 실패했다. USB 없이도 재현되어 앞단을 우선 수정했다.
- WGC의 `MinUpdateInterval` 실측 기본값은 16,000µs. 이를 0으로 둔 비교에서 원본 캡처가 약 43.8→58.3fps로 증가했다. [Microsoft API](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscapturesession.minupdateinterval), ABI는 Microsoft windows 0.62.2의 `IGraphicsCaptureSession5`와 대조했다.
- 이 변경만으로 앱 경로는 44.6fps에 머물렀다. 앱의 상대적 `recv_timeout` 대기를 기존 정밀 타이머와 절대 프레임 시각으로 변경한 뒤 54.5–58.5fps 범위, 중앙값 약 58fps로 통과했다. Windows 캡처 최소 간격과 앱 대기의 두 원인을 구분했다.
- 정지 화면 재전송이 기존 프레임의 오래된 timestamp를 유지하는 별도 결함도 확인했다. 실제 경로의 `--maximum-frame-age-ms=100` 검사가 p95 538.6ms로 실패했고, 재전송 샘플만 새 시각으로 기록한 뒤 p95 12.9ms로 통과했다. 이 값은 화면 내용의 나이가 아니라 전송 샘플 처리 시간이며 물리적인 표시 지연이 아니다.
- 도구 구현 이후 같은 8초 자극에서 인코딩은 54.8–58.6fps였다. Windows PowerShell이 이미 끝난 프로세스의 ExitCode를 null로 읽는 진단 스크립트 결함을 발견해, 시작 직후 프로세스 핸들을 유지하도록 수정하고 재검사했다. 성능 통과 여부와 스크립트 종료 상태를 혼동하지 않는다.

## 재현 명령

승인된 Windows 캡처→영상 경로를 검사하는 하드웨어 진단이다. 화면이나 계정 정보는 저장하지 않고 시간과 개수만 출력한다. 일반 CI에서 GPU 없는 단위 테스트인 것처럼 실행하지 않는다.

```powershell
. ./scripts/windows-env.ps1
cargo build -p eternal-host --example stream_fps_probe
powershell -File scripts/probe-stream-fps.ps1 -HostProbe
./target/debug/examples/stream_fps_probe.exe --maximum-frame-age-ms=100
./target/debug/examples/stream_fps_probe.exe --encode-only --minimum-fps=50
```

첫 검사는 작은 움직이는 창을 8초간 표시하고 실제 WGC→인코더 경로의 FPS를 판정한다. 둘째는 바탕 화면을 정지한 상태에서 재전송 시각을 검사한다. 임계값 50fps/100ms는 이 PC의 회귀 검사용이며 모든 하드웨어의 제품 성능 약속이 아니다.

실제 iPad 연결을 검사할 때는 실행 중인 DeskInk의 stdout 로그를 지정한다.

```powershell
powershell -File scripts/probe-stream-fps.ps1 -LogPath artifacts/local/deskink-fps-stdout.log
```

## 아직 남은 검증

- 수정본을 실제 Windows 앱에 적용한 뒤 iPad 수신 FPS 재측정.
- Windows Release 실행물, 동일 해상도와 전원 조건에서 재확인.
- Pencil→양쪽 화면의 물리적 표시 지연, 장시간 필기 렌더링, Zoom 실제 수신.
- 앱을 배경으로 보냈다 다시 여는 과정에서 관찰한 watchdog 재시작은 이번 움직이는 화면 FPS 재현과 별개다. 추가 검증 대상으로 남긴다.

## 2026-10-18: PC에 나타나는 필기의 버벅임

사용자는 iPad의 로컬 필기는 자연스럽고 PC의 선만 버벅인다고 확인했다. 영상 FPS를 올리는 것으로 해결되지 않는 별도 경로다. 실제 iPad 영상 검사 명령은 이번 실행에서 연결된 수신 보고를 얻지 못했으므로 영상 성능 수치로 사용하지 않았다.

Release 하드웨어 진단 `ink_latency_probe`는 실제 1080p 투명창에 각각 100점을 가진 1·20·100개 선을 그린 뒤 포인터를 120번 갱신한다. 매번 표시 시간을 측정하며 p95 16.7ms를 넘으면 실패한다. 100개 선에서 p50 13.58ms, p95 18.92ms, 최댓값 29.34ms로 실패했다. 원인은 변경 없는 완료 선을 매번 다시 래스터화하는 비용이었다. 완료한 선의 이미지를 보관하고 새 선·포인터만 합성하자 같은 검사에서 p50 6.14ms, p95 8.52ms로 통과했다. 지우기·취소·숨기기나 선 내용이 바뀌면 캐시를 무효화한다.

그 다음 실제 `DeskInk::logic` 표시와 `AnnotationState::receive` 입력을 동시에 실행했다. 화면 그리기가 입력 세션의 잠금을 붙잡아, 입력 p95 5.009ms·최댓값 8.270ms로 4ms 검사에 실패했다. 표시할 선만 복사하고 잠금을 해제한 뒤 Windows에 그리도록 변경한 결과 p95 0.081ms·최댓값 0.234ms로 통과했다. 이 검사는 100개 완료 선 위에 240Hz 입력을 보내며 60Hz 표시를 2.6초간 실행한다. 두 수치는 각각 렌더링 호출 시간과 입력 함수 처리 시간이며 USB·실제 디스플레이 스캔·Pencil 전체 지연을 포함하지 않는다.

기존 렌더러와 모니터 전환 추가 직후 렌더러의 소스는 동일했다. 이번 검사는 누적 필기에서 드러나는 병목과 그리기 중 입력 대기를 확인한 것이며, 사용자 기기의 모든 체감 변화 원인을 단정하지 않는다. 수정본에서 실제 Pencil로 다시 확인한다.

```powershell
. ./scripts/windows-env.ps1
cargo run -p deskink-windows --example ink_latency_probe --release --target-dir target/monitor-switch
cargo test -p eternal-host --bin deskink --release --target-dir target/monitor-switch pen_input_remains_responsive -- --ignored --nocapture --test-threads=1
```

두 진단은 임시 필기창을 표시한 뒤 제거한다. 기존 사용자 필기나 화면 파일을 저장하거나 전송하지 않는다. 임계값은 이 PC의 회귀 검사 기준이며 모든 기기의 성능 보장은 아니다.

## 2026-10-19: 장시간 연결 후 재발하는 지연 — 원인 미확정

사용자는 오래 켜 두면 PC와 iPad 양쪽이 다소 느려지며, USB를 뽑았다 꽂거나 iPad의 X로 연결을 종료한 뒤 다시 연결하면 회복된다고 보고했다. 이후 iPad 업데이트 전 상태에서 다시 느려졌다고 알렸다. 자동 페어링을 개선한 0.4.2는 이 증상을 해결했다고 검증한 버전이 아니다.

### 현재 실행물에서 관측한 사실

- 사용자 Windows 앱은 13:43부터 같은 프로세스로 실행 중이었다. 검사 중 앱을 종료하거나 사용자 필기를 지우지 않았다.
- 움직이는 8초 검사에서 iPad 수신 FPS는 첫 측정 중앙값 57.8, 최솟값 55.8, 최댓값 59.0이었다. 재발 보고 후 17:33~17:34 측정에서는 중앙값 58.7, 최솟값 56.3, 최댓값 59.8이었다. 각 12개 보고이며 움직임 자극은 약 63회/초였다. 이 결과는 검사 구간의 디코딩 처리량이고, 펜 입력·화면 표시 지연이 없다는 뜻은 아니다.
- 로그에는 17:31:21 연결 종료, 17:31:22 새 연결이 기록되어 있다. 이때 Windows 프로세스와 스트림 epoch는 유지됐지만 연결과 인코더 세션이 새로 만들어졌다. 따라서 재연결 회복만으로 iPad 디코더 또는 USB 드라이버 중 하나를 원인으로 단정할 수 없다.
- 해당 재연결 직전 약 2분의 보고에서는 디코딩 대기 깊이가 최대 1, 수신 간격 변동은 최대 약 1.23ms였다. 대부분 정지 화면이므로 낮은 FPS를 성능 실패로 판정하지 않았다.
- 로그의 일부 `encoder silent` 경고는 연결이 없었던 긴 구간을 포함해 새 연결 직후 발생했다. 이를 모두 실제 인코더 정지 시간으로 해석하지 않는다.
- 짧은 프로세스 표본에서 메모리와 핸들 수의 지속적인 증가를 입증하지 못했다. 이 관측만으로 장시간 메모리 누수를 배제하지 않는다.

### 독립적인 양방향 전송 검사

```powershell
. ./scripts/windows-env.ps1
cargo run -p eternal-host --example usb_latency_probe --release --target-dir target/monitor-switch
```

`usb_latency_probe`는 실제 호스트 전송 루프, EMLink 프레이밍, 세션 인증, 필기 수신 상태를 사용한다. 가상 태블릿은 루프백 TCP로 연결하며 합성 영상 패킷과 포인터 명령만 보낸다. 실제 Apple 장치 서비스, ADB, 화면 캡처, 저장된 페어링 정보, 사용자 앱과는 연결하지 않는다. 첫 연결의 인증 토큰으로 재연결하며 인증 정보는 출력하지 않는다.

| 구간 | 입력 반영 관측 p95 | 영상 패킷 나이 p95 |
|---|---:|---:|
| 초기 연결 | 16.07ms | 0.92ms |
| 영상 수신을 반복해서 100ms 정지 | 16.09ms | 93.67ms |
| 같은 연결에서 수신 재개 | 16.09ms | 0.97ms |
| 앱 재연결에 해당하는 새 연결 | 16.11ms | 0.99ms |

명령은 종료 코드 0으로 완료했다. 입력 관측에는 Windows 타이머로 상태를 확인하는 대기 시간이 포함된다. 각 이벤트가 반영된 뒤 다음 이벤트를 전송하므로 실제 전송률은 240Hz보다 낮으며, 이 수치를 USB/Pencil의 실제 지연 또는 240Hz 부하 시험 결과로 사용하지 않는다. 영상 수치는 합성 패킷의 생성→수신 완료 시간이고 H.264 디코딩과 표시를 포함하지 않는다. 이 검사에서는 수신을 재개하면 재연결 없이 회복했고, 사용자가 보고한 지속적인 버벅임은 재현하지 못했다.

### 다음 재발을 위한 관측 보강

호스트 `Receiver report` 로그에 기존 iPad가 이미 전송하는 `assembler_depth`, `e2e_latency_ms_x10`, `rtt_ms_x10`을 추가했다. FPS가 유지돼도 오래된 프레임이 도착하는지 구분하기 위한 변경이며 성능 수정은 아니다. 두 시간 값은 10으로 나누면 ms이고, 영상 지연 0은 시계 동기화 전에도 나올 수 있다. 영상 지연은 캡처 샘플→디코딩 완료의 시계 보정 추정치로 실제 화면 발광이나 펜의 전체 지연을 나타내지 않는다.

이 로그 변경은 소스에만 있으며 현재 실행 중인 앱과 공개 배포 파일에는 아직 적용하지 않았다. 원인 확정과 성능 수정 완료를 표시하지 않는다. 다음 구분에 필요한 실제 관측은 느린 상태에서 iPad의 새 선 자체가 펜끝을 늦게 따라오는지, 아니면 로컬 선은 즉시 표시되고 PC 필기·전송 영상이 늦는지다.

로그 보강 후 `cargo check -p eternal-host --lib --examples --release --target-dir target/monitor-switch`가 통과했다. 진단 예제의 `rustfmt --check`와 변경 파일의 공백 검사도 통과했다. 성능 문제의 재현·수정 테스트가 통과한 것으로 해석하지 않는다.

### 추가 조사: 실제 Windows 설정 창을 최소화할 때의 지연

사용자는 iPad 로컬 선도 함께 늦는 경우와 Windows만 늦는 경우가 모두 있다고 확인했다. 완료 선 1,000개/30만 점을 넣은 `ink_latency_probe --long-session`은 표시용 복사를 포함해 p95 11.10ms로 통과했다. 단순히 완료 필기가 쌓인 조건에서는 지속적 버벅임을 재현하지 못했다.

다음 검사는 실제 eframe 창과 `DeskInk` 앱, 입력 수신, Windows 투명창을 실행한다. 64px 임시 필기창 중심에 60Hz로 바뀌는 불투명 색을 표시하고, 다른 스레드에서 실제 바탕 화면 픽셀을 읽어 현재 보이는 샘플이 얼마나 오래됐는지 측정한다. 6초 후 자동으로 닫히며 실제 USB·페어링·사용자 앱은 사용하지 않는다.

```powershell
. ./scripts/windows-env.ps1
cargo test -p eternal-host --bin deskink --release --target-dir target/monitor-switch real_controller_window_keeps_ink_fresh_when_minimized -- --ignored --nocapture --test-threads=1
```

| 조건 | 보이는 설정 창 p95 | 최소화된 설정 창 p95 | 판정(50ms 이내) |
|---|---:|---:|---|
| 수정 전 첫 실행 | 37.5ms | 121.3ms | 실패 |
| 수정 전 반복 | 39.8ms | 121.0ms | 실패 |
| 표시 스레드 분리 후 | 18.8ms | 21.2ms | 통과 |

표시 샘플의 나이에는 픽셀 관측 스레드의 스케줄링 지연도 포함된다. Pencil 전체 지연이나 디스플레이 발광 시간은 아니다. 관측 지점을 바꾸지 않고 같은 입력/픽셀 검사에서 최소화 시 지연을 제거했다.

원인은 eframe 0.36.2 `native/run.rs`의 `INVISIBLE_WINDOW_REPAINT_INTERVAL = 100ms`였다. 기존 앱은 설정 창의 `App::logic`에서 필기까지 표시해 이 제한을 받았다. 작은 점 하나로도 실패해 완료 선 수와 래스터화량을 최소 재현에서 제거했다. 실제 사용자 프로세스의 창을 PID로 한정해 열거했을 때 DeskInk 설정 창은 visible=true/minimized=true였다. `Get-Process.MainWindowHandle`만 보면 별도 필기창을 가리킬 수 있어 설정 창 상태 판별에 사용하지 않았다.

`AnnotationSurface`가 모니터별 창과 표시 스레드를 소유하도록 수정했다. 입력은 기존 인증/순서 검사를 거쳐 같은 보드에 반영한다. 표시는 최대 한 개의 깨우기 신호를 유지하고 최신 보드를 복사한 뒤 입력 잠금을 해제한다. 설정 창과 무관하게 임시 필기/포인터도 만료되며 종료 시 해당 스레드가 창을 파괴한다. 설정 창은 화면 선택과 상태 표시만 담당한다.

수정 후 실제 픽셀을 통한 지우개·취소·숨기기 검사와 동시 입력 검사가 통과했다. 동시 입력 처리 p95는 0.034ms였다. iPad 로컬 필기까지 함께 느려지는 경우는 별도 검증 대상으로 남긴다. 이 항목의 Windows 재현 결과를 그 경우까지 해결한 근거로 사용하지 않는다.
