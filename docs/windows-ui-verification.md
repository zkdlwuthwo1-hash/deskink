# Windows 0.5.2 UI 검증

2026-10-21 기준. Windows 컨트롤러의 표시와 조작을 개선한 릴리스이며,
iPad·Android 앱은 기존 0.5.1 파일을 그대로 사용한다.

## 변경 범위

- 기존 native egui 창에 Geist 글꼴, 다크 테마, 모니터 선택 카드와 연결 상태를 적용했다.
- 페어링 코드를 크게 표시하고 복사 기능과 등록 해제 확인창을 추가했다.
- Wi-Fi / LAN 카드에서 IP·포트·QR을 확인하고, 부가 도움말·업데이트·진단을 펼쳐 본다.
- Windows에 설치된 Malgun Gothic을 시작 시 보조 글꼴로 읽어 한글 기기 이름을 표시한다.
  이 글꼴 파일을 배포하지 않으며, 읽기에 실패해도 앱 시작은 계속한다.
- 공유 상태는 잠금 안에서 복사한 뒤 잠금을 해제하고 화면을 그린다.
  필기·전송·캡처·압축·Windows 필기층의 구현은 변경하지 않았다.

## 실행한 검사

- `cargo check -p eternal-host --bin deskink --target-dir target/intel-fix-v044` 통과.
- 같은 target의 release `deskink`와 `deskink_ui_preview` 빌드 통과.
- `deskink_ui_preview --verify`: 실제 대시보드 renderer에 포인터·Tab·Enter 입력을
  보내 모니터 선택, 페어링 코드 클립보드, 등록 해제 취소·확인, QR 열기, 한글 glyph를 확인했다.
  화면에 쓰는 샘플 값으로 실행하며 실제 태블릿 등록·네트워크 수신·필기층은 시작하지 않는다.
- native renderer가 출력한 640×760, 480×760 캡처에서 모니터·페어링 코드·IP·포트·QR
  버튼의 배치와 한글 기기 이름을 확인했다. 최소 높이에서는 세로 스크롤을 사용한다.
- 480×560에서는 QR 팝업 전체와 오류 표시를 확인했다. 연결 안내 문장은 명시적으로
  줄바꿈하며, QR 팝업 제목은 `Connect your iPad`로 표시한다.
- 기존 Windows update descriptor 검사 2개 통과. 접힌 업데이트 영역 밖에서 결과 메시지를
  처리하는 것과 설치 완료 시에만 창을 닫는 흐름도 별도 코드 검토했다.
- HTML 안내서는 320·390·820·1440px에서 기기별 다운로드, 키보드 이동, 그림 확대·닫기,
  내부 링크, 오프라인 자원, 인쇄 표시를 검사했다. 업데이트 설명 링크도 3개 폭에서 확인했다.

## 검증하지 못한 부분

Orca의 창 목록은 실제 앱의 필기층만 반환하고 컨트롤러를 누락했다. 임시 계측으로
실제 앱의 `logic`·`ui`·상태 복사·dashboard 반환이 연속 완료되는 것과, Windows 기준
컨트롤러 창의 `visible=true`, `minimized=false`를 확인했다. 이를 앱 창 미생성이나
첫 렌더 교착으로 오인하여 제품의 표시·GPU 경로를 바꾸지 않았다. 임시 계측은 제거했다.

`accepted_ink_reaches_desktop_without_a_controller_ui_pass`는 이번 실행 환경에서
화면 픽셀 확인 시간 초과로 실패했다. UI 수정 전 빌드한 `annotation_layers_probe.exe`도
같은 시점에 검은 픽셀(`0`)을 읽고 실패했다. 당시 실제 설치·실행 중인 앱은 여전히 0.5.1이었다.
따라서 이를 새 UI의 필기 회귀 또는 특정 환경 원인으로 단정하지 않으며, native 필기 검사가
통과했다고 기록하지 않는다. 테스트 제한 시간이나 제품의 필기층을 바꾸지 않았다.
실행 중인 DeskInk를 모두 종료한 상태에서도 두 픽셀 검사의 실패가 동일했다.

별도로 보고된 무선 전용 필기 가림 증상은 원인 미확정 상태다.
이 UI 릴리스에서 수정했다고 주장하지 않는다. 관측 내용은
[무선 검증 기록](wireless-verification.md)에 남겼다.

## 배포 파일

- Windows 0.5.2 ZIP은 clean source `dc4ff60`에서 만들어 566개 manifest 파일을 검증했다.
- 공개 v0.5.2의 Windows ZIP·IPA·APK·HTML·APK 서명 안내·체크섬 파일을 다시 다운로드하여
  로컬 원본과 바이트 해시가 같음을 확인했다. IPA·APK는 공개 0.5.1과 같은 파일이다.
- Windows ZIP SHA256: `6393c17cacbdc2cdef3d000a12e18750072a535630591e33d07e98e437431f16`.
- Windows 실행 파일 SHA256: `906875ff2e1b40bce163653f930a0d0402b79fc7447bd15b0781d64447ea608d`.
- 기존 다운로드 별칭 `v0.4.0-lab.1`도 갱신했다. GitHub API에서 두 릴리스의 6개 자산
  파일명·크기·SHA256 digest가 모두 같음을 확인했다.
- 공개 GitHub Pages의 `README.html`과 기존 `?guide=f335f2d` 주소 모두 새 HTML과
  바이트 해시가 같았다. Windows 링크는 0.5.2, 태블릿 링크는 0.5.1을 가리킨다.

## 실제 설치 확인

- 설치된 updater의 Stage로 공개 GitHub 패키지를 다운로드·검증하고 Apply로
  기존 설치 폴더에 적용했다. 결과는 `installed`, 버전은 `v0.5.2`였으며 시작 확인도 받았다.
- 실행 중인 프로세스가 기존 바탕화면 DeskInk 폴더의 새 바이너리임을 확인했다.
  설치 manifest의 버전·소스 commit과 실제 실행 파일 해시는 위 배포 값과 일치했다.
- 실제 설정 경로인 `%LOCALAPPDATA%/DeskInk`의 `lan-setting.txt`와
  `paired-tablets.bin`은 업데이트 전후 해시가 같았다.
- 바탕화면 화면 1·2 바로가기의 대상은 기존 launcher이며 인자는 각각
  `--monitor 0`, `--monitor 1`로 유지됐다.
- 설치 후 LAN의 인증 전 HELLO 요청에 ACK(status 4)가 도착했다. 이 확인은
  페어링하거나 영상을 시작하지 않으며, 실물 태블릿의 영상·필기 지연 검증을 대체하지 않는다.
