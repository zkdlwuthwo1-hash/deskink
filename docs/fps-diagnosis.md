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
