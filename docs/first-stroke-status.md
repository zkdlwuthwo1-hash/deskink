# 첫 연결 구현 상태 — 2026-10-16

## 직접 확인한 결과

- Rust/MSVC Windows 라이브러리와 별도 `deskink.exe` 빌드 성공.
- 현재 PC: 1920×1080 모니터 두 대를 열거. 기본 모니터에 실제 투명 필기창을 생성.
- WGC 세션에서 신규 제외 인터페이스를 실제 호출함. 이어 픽셀 검사에서 일반 캡처에는 빨간 선이 있고, 우리 세션에서만 제외한 뒤 PC 화면에는 그 선이 남는 것을 확인함.
- AMD AMF 인코더로 FFmpeg 1080p 6프레임 인코딩 성공. 이 시험은 앱 전체의 지연 수치가 아님.
- 개발용 Windows 연결 대기 화면 실행 확인. 네트워크 소켓은 실제로 127.0.0.1:9876에만 바인딩됨.
- 마지막 수정 빌드의 실제 창을 다시 열어 밝은 배경의 글자 가독성, 모니터·페어링 코드·USB 대기 상태·인코더 표시를 확인했다. iPad 미연결 상태의 확인이며 연결 완료 화면을 검사한 것은 아니다.
- 핵심 필기/수신 세션 테스트: 시작·이동·종료, 중복, 순서 누락, 이전 연결, 범위 밖 좌표, 잘린 입력과 과도한 메시지, 재연결 후 PC 필기 유지.
- 기반 소스를 포함한 전체 Rust 검사 222개가 통과했다. 이 중 하드웨어 영상 검사는 현재 AMD PC에서 실행했으며, x264가 필요한 기존 테스트는 별도 test-only GPL DLL을 사용했다. 제품 실행은 LGPL SDK를 유지한다.
- 추가 검토에서 상태창 최소화 중 표시 중단을 픽셀 테스트로 재현하고 수정했다. 마지막 프레임 보류, 빈 캡처 풀 처리, USB 페어링 반복 실패 제한도 보강했다. [검토 기록](review-first-stroke.md)에 확인 범위를 구분했다.
- [GitHub 실행 38885470010](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38885470010)에서 iPad 시뮬레이터 단위 테스트 153개와 기기용 arm64 Release 빌드·IPA 생성이 모두 성공했다. Swift/Rust 필기 메시지 계약 검사도 포함된다. Xcode 16.4, 소스 `efe1c325454627b1b52fa28ab6ae1a4fa09cc9b2` 기준이다.
- `artifacts/local/DeskInk-unsigned.ipa`를 내려받아 SHA-256, ZIP CRC, Info.plist, Mach-O의 arm64·실기기 iOS 플랫폼을 검사했다. 크기 3,756,568바이트, SHA-256 `84e2817752748aa88b4a085bec9e0b00dc34ee25556cd8f93baafc70280ee916`. 개인 서명 전 파일이다.
- Sideloadly의 GSA HTTP 503과 iloader의 HTTP 502·계정 인증 오류를 거친 뒤, 사용자가 iloader 로그인을 완료했다. 사용자 화면에서 `Operation completed / Sign & Install App` 성공을 확인했다. 개인 서명과 iPad 설치는 완료됐다.
- 개발자 모드 안내 이후 사용자가 앱에서 펜이 나타난다고 보고했다. Windows 로그에서 실제 USB 세션 연결과 iPad의 영상 디코드 보고를 확인했다. 양쪽 필기 위치·손바닥 구분·Zoom 수신 검사는 아직 완료하지 않았다.
- 사용자의 FPS 저하 보고를 움직이는 테스트 창으로 재현했다. WGC 기본 간격과 앱의 대기 처리를 수정한 Windows 캡처→인코딩 경로는 같은 장치에서 약 55–59fps를 처리했다. 정지 화면 재전송의 오래된 시간값도 수정했다. 실제 iPad 수신에 수정본을 적용하는 검증은 별도로 남긴다. [FPS 진단 기록](fps-diagnosis.md)
- 발표 도구를 양쪽에 구현했다. 지우개는 선 단위로 지우며 한 제스처를 한 번에 실행 취소한다. 사용자 요청대로 지운 뒤 펜을 화면에서 떼면 직전 도구로 자동 복귀하고 기존 색·굵기를 유지한다. 형광펜, 2초 후 사라지는 펜, 레이저·펜 모양 포인터, 7색·3굵기, 취소·다시 실행·전체 지우기·숨기기, Pencil 더블탭 지우개를 포함한다. 손가락 필기는 기본 꺼짐이며 굵기 메뉴에서 선택할 수 있다. 호버는 지원 기기에서만 동작한다.
- 새 도구의 핵심 Rust 검사 20개가 통과했다. 실제 Windows 픽셀 검사에서 지우기→복원→숨기기→보이기를 확인했고, 캡처 관련 검사 13개도 통과했다. [iPad 전체 검사 38897105780](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38897105780)는 158개 단위 테스트와 Release IPA 빌드가 성공했다. 이 결과는 새 버전의 실기기 설치·더블탭·Zoom 검증을 대신하지 않는다.
- Windows 호스트 라이브러리는 134개 통과 후 x264 검사 한 개가 제품용 LGPL 런타임의 코덱 부재로 실패했다. 기존에 분리해 둔 test-only GPL 런타임에서 해당 검사도 통과했다. 제품 런타임은 LGPL을 유지한다. Windows Release 실행물도 빌드했다.
- [도구 계약·화면 검사 38898160968](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38898160968)에서 도구 검사 6개가 통과했다. XCTest 이미지 첨부를 추출해 가로·세로 iPad 도구막대의 아이콘·글자·배치가 잘리지 않는 것을 직접 확인했다. 실제 사용자 데스크톱이 아닌 앱의 미리보기 배경을 사용했다.
- 최종 [자동 복귀 포함 빌드 38898711423](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38898711423), 소스 `64375a0`, 앱 빌드 번호 8에서 전체 iPad 테스트 160개와 Release IPA 생성이 통과했다. 자동 복귀 테스트를 먼저 추가한 이전 실행은 해당 동작 부재로 실패한 것을 확인했다. `artifacts/local/DeskInk-tools-build8.ipa` 및 `%TEMP%\DeskInk-tools-build8.ipa`를 준비했다. ZIP CRC, arm64 실기기 플랫폼, 번들 ID·빌드 번호와 SHA-256을 검사했다. SHA-256: `da77c1f855b815093a343435d29a8af32244a92137e65fcc358e9e0dbfa4c742`. 아직 새 IPA를 실제 iPad에 설치하지 않았다.

- 사용자가 기존 도구막대의 지우개·휴지통을 누를 수 있다고 확인했다. Windows Release 실행과 화면별 바탕화면 바로가기도 확인했다. 이 확인은 모든 도구의 실기기 동작과 지연 측정을 대신하지 않는다.
- iPad 전용 확대 입력의 실패 테스트(뷰 미구현)를 먼저 확인한 뒤 UIKit의 같은 화면에 영상과 로컬 필기를 넣었다. 수정한 검사에서 2배 확대·이동 후 실제 입력이 Windows 좌표 `(720,405)`로 전송됨을 확인했고, 전체 [162개 단위 테스트 38912017205](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38912017205)가 통과했다. 화면 맞춤·회전 시 필기 보존과 여백 입력 거부를 포함한다.
- 사용자의 추가 배치 요청은 도구막대가 영상을 덮는 실패 검사 [38912817098](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38912817098)로 재현했다. 위·아래 제어 영역을 별도로 확보한 뒤 [38913462937](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38913462937)에서 전체 단위 테스트 162개와 실제 핀치·두 손가락 이동·도구 선택·화면 맞춤 및 배치 UI 검사 2개가 통과했다. 시뮬레이터의 영상은 PC 화면 대신 미리보기이며, 실제 UIKit 화면과 도구막대를 사용했다.
- 소스 `4a96451`, 앱 빌드 10의 Release IPA를 `artifacts/local/DeskInk-zoom-build10.ipa`와 `C:\Users\zkdlw\Desktop\DeskInk-zoom-build10.ipa`에 준비했다. 크기 3,855,846바이트, SHA-256 `24fe4c0d9cfbd15063b6813aa64968c96f67ebaca4fb0488548a5b57f80b70d0`. ZIP CRC·arm64 실기기 플랫폼·번들 ID·빌드 번호·두 사본 해시를 검사했다. 이 버전의 개인 서명·설치와 실제 확대 중 Pencil 검증은 사용자가 진행한다.
- [가로 회전 검사 38914118802](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38914118802)에서 실행 중인 앱을 회전한 후 영상 영역이 창 안에 들어오고 화면 맞춤·휴지통이 터치 가능한지 추가 확인했다. 제품 코드는 동일하며 UI 검사만 보강했다.
- 가로 방향의 `app.screenshot()` 첨부가 잘려 보여 [38914650202](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38914650202)에서 동일 순간의 전체 디스플레이 캡처를 함께 수집했다. 전체 캡처에는 16:9 영상 테두리 전체, 위쪽 버튼과 아래 검은 영역의 도구막대가 정상 표시되어 부분 캡처 문제로 구분했다. 가로 전체 화면과 세로 확대·이동·화면 맞춤 이미지를 직접 확인했다. 원본 첨부를 보존하고 로컬 확인용 사본을 `artifacts/local/deskink-build10-landscape.png`와 `artifacts/local/deskink-build10-portrait.png`에 두었다.

## 연결한 코드

`DeskInkViewport`는 영상과 `InkCanvasView`를 같은 UIKit 스크롤 화면 안에 두고 iPad에서만 확대·이동한다. UIKit의 좌표 역변환 후 필기 입력이 원래 모니터 좌표로 전달된다. `DeskInkWorkspace`는 위쪽 버튼, 영상, 아래 도구막대의 공간을 분리해 영상을 덮지 않도록 한다. 실제 연결 화면과 시뮬레이터 UI 검사에서 같은 배치를 사용한다.

`InkCanvasView`는 UIKit의 정확한/병합 터치를 읽고 로컬 Core Animation 선을 먼저 갱신한다. 별도 큐로 보낸 `DI01` 입력은 기존 USB 프레임 전송 경로를 통과해 연결된 USB peer 확인 → `InkSession` → Windows 필기창으로 전달된다. SwiftUI 전체 화면은 점마다 갱신하지 않는다.

Windows의 `deskink` 진입점은 가상 화면 드라이버 변경, 원격 마우스/키보드 입력, 오디오, 자동 시작 등록, 자동 업데이트를 호출하지 않는다. 기반 소스의 기능은 남아 있지만 이 실행 경로에서 활성화하지 않는다.

## 아직 검증/구현하지 않은 사항

- 수정본의 실제 USB 영상 FPS, 양쪽 필기 입력 왕복과 위치, Pencil 지연과 손바닥 처리.
- Zoom 실제 수신 화면. 글로벌 캡처 제외는 사용하지 않지만 Zoom 성공으로 간주하지 않는다.
- 새 도구의 실기기 양쪽 화면 일치, 실제 Pencil 더블탭·호버·손바닥 구분.
- iPad 전용 확대·이동 상태에서 실제 Pencil 정밀도와 손바닥 구분. 자동 좌표 검사와 시뮬레이터 제스처 검사는 통과했고 실제 기기 업데이트가 남았다.
- PC 두 대 선택 UI, 영구 저장 및 재시작 복원, 재연결 시 iPad로 전체 필기 복원.
- LAN/이더넷 필기 경로. 첫 연결 구현은 USB만 받는다.
- Windows 필기 렌더러의 긴 필기 성능. 현재 전체 장면 CPU 래스터 방식이며 최종 저지연 렌더러가 아님.
- 디스플레이 크기 변경 시 현재 첫 구현은 명확한 오류로 종료하며 자동 재배치는 후속 작업이다.

현재 코드를 완성된 발표용 앱이나 측정된 초저지연 제품으로 표시하지 않는다. 작업 01과 02는 자동 검증과 실제 iPad 검증을 구분해 기록하고, 나머지 저장·PC 전환 등을 이어간다.

사용자는 Windows에서 계속 진행하기로 정했다. [비공개 저장소](https://github.com/zkdlwuthwo1-hash/deskink)와 GitHub macOS 빌드를 연결했고 기존 IPA 개인 서명·설치를 마쳤다. Windows Release 앱 실행과 도구막대 터치 성공을 사용자와 확인했고, 화면별 바탕화면 실행 아이콘도 준비했다. 다음 작업은 확대 기능이 들어간 IPA로 iPad만 업데이트해 실제 필기 위치를 확인하는 것이다. 현재 Windows 앱을 닫으면 기존 필기가 사라지므로 이 업데이트를 위해 Windows를 재시작하지 않는다. [Windows 중심 진행 방법](windows-only-ipad.md)을 따른다. 직접 Mac을 쓰는 기존 절차는 대안으로 남긴다.

사용자 실행 순서와 통과 기준은 [iPad 첫 검증 안내](ipad-first-check.md)에 정리했다.

## 재현 명령

```powershell
. .\scripts\windows-env.ps1
cargo test -p deskink-core
cargo run -p deskink-windows --example capture_probe
cargo run -p deskink-windows --example exclusion_pixels
cargo build -p eternal-host --bin deskink
```

캡처 픽셀 시험은 잠깐 빨간 선을 띄웠다가 제거한다. 사진이나 화면을 외부로 전송하지 않는다.
