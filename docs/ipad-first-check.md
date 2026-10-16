# iPad에서 첫 연결과 필기를 확인하는 순서

현재는 첫 연결 검증용 개발 버전이다. Mac에서 아직 컴파일하지 않았으므로, 아래 빌드·테스트가 먼저 통과해야 실제 iPad에 설치할 수 있다. 확대·여러 도구·더블탭 지우개·저장은 후속 작업이며 이번 검증의 통과 항목으로 넣지 않는다. 프로젝트의 최소 배포 대상은 iPadOS 17이다.

**Windows에서 계속 진행하는 경우:** 아래 1–2단계 대신 [클라우드 빌드 → Windows 설치 안내](windows-only-ipad.md)를 사용한다. 설치 후에는 이 문서의 3–5단계로 USB와 실제 필기를 확인한다.

## 1. Mac에서 빌드와 자동 테스트

`artifacts/local/DeskInk-source.zip`을 Mac으로 복사하고 압축을 푼다. 압축 안의 `DeskInk` 폴더를 Mac의 Codex에서 열면 작업을 이어갈 수 있다. 실행 파일이나 개인 Apple 서명 정보가 들어 있는 설치 패키지는 아니다.

Xcode를 설치하고 처음 실행할 때 필요한 iOS 구성 요소를 준비한다. XcodeGen이 없다면 Homebrew가 있는 환경에서 `brew install xcodegen`으로 설치한다. 이는 프로젝트 파일을 만들어 주는 도구이며 [XcodeGen의 공식 설치 방법](https://github.com/yonaskolb/XcodeGen#installing)이다.

압축을 푼 `DeskInk` 폴더의 터미널에서:

```bash
bash scripts/build-ipad.sh
bash scripts/test-ipad.sh
open ios/DeskInk.xcodeproj
```

첫 명령은 시뮬레이터용 컴파일, 둘째는 설치된 iPad 시뮬레이터의 단위 테스트다. 오류가 나면 설치 단계로 넘어가기 전에 Mac 쪽에서 수정한다. 이 과정만으로 실제 USB나 Pencil 지연이 검증되지는 않는다.

## 2. 실제 iPad에 설치

1. iPad를 Mac에 USB로 연결하고 잠금을 푼다.
2. Xcode의 앱 타깃 `EternalMonitor`를 선택한다. 내부 타깃 이름은 기존 모듈 호환 때문에 남아 있으며 설치되는 앱 이름은 `DeskInk`다.
3. `Signing & Capabilities`에서 본인 Apple 계정의 Team을 선택하고 자동 서명을 사용한다. 계정 로그인이 필요하면 본인이 진행한다. 번들 ID 중복 오류가 나면 본인 고유 ID로 바꾼다.
4. 실행 대상을 연결한 iPad로 선택하고 Run(▶)한다. 기기에서 신뢰 또는 개발자 모드 설정을 요구하면 해당 기기의 안내에 따라 직접 처리한다.
5. DeskInk가 설치되고 열리는지 확인한다. Mac에서 실행을 중지한 뒤 케이블을 빼고, iPad 홈 화면의 DeskInk를 직접 실행할 수 있는지도 확인한다.

계정·Team·기기 선택과 자동 서명은 [Apple의 실기기 실행 안내](https://developer.apple.com/documentation/Xcode/running-your-app-on-simulated-or-physical-devices)를 따른다. 개발자 모드는 [Apple 안내](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)를 참고한다. 코드 변경 후 XcodeGen을 다시 실행하면 프로젝트 설정이 재생성되므로 Team 선택을 다시 확인한다.

## 3. Windows로 옮겨 USB 연결

이제 iPad USB 케이블을 **Mac에서 빼서 Windows PC에 꽂는다**. Mac은 앱을 만드는 데 쓰고, 필기 검증은 Windows와 iPad 사이에서 한다.

- Windows에서 Apple Devices에 iPad가 나타나는지 확인한다. 본인 PC에서 처음 연결할 때 iPad가 ‘이 컴퓨터를 신뢰’할지 물으면 직접 선택한다. 충전 전용 케이블은 사용할 수 없다. [Apple의 Windows 연결 확인 안내](https://support.apple.com/en-ie/108643)
- 현재 열려 있는 DeskInk를 사용한다. 다시 실행하려면 Windows 프로젝트 폴더에서 아래 명령을 실행한다. 이미 열려 있으면 두 번째 인스턴스를 띄우지 않는다.

```powershell
powershell -File scripts/run-windows.ps1 -Monitor 0
```

- 현재 PC의 `0`은 첫 번째 모니터, `1`은 두 번째 모니터다. 다른 모니터를 시험할 때는 앱을 닫고 번호를 바꿔 다시 실행한다. 현재 버전은 앱을 닫으면 필기가 지워진다.
- iPad에서 DeskInk를 열고 앞 화면에 유지한다. `USB cable` 영역이 꺼져 있으면 `Turn on`, 일시 중단되어 있으면 `Connect USB`를 사용한다. 이 첫 버전은 IP 주소나 QR 입력 없이 USB 연결 경로를 시험한다.
- 페어링 창이 나타나면 Windows DeskInk에 표시된 6자리 코드를 입력한다. 연결 후 Windows는 `iPad connected`, iPad는 선택한 Windows 모니터 화면을 표시해야 한다.

Windows의 `Capture 0 fps / Encode 0 fps`는 연결 전에는 정상적인 대기 표시다. 연결되었다고 나오는데 화면이 검거나 움직이지 않으면 아래 검사에서 실패로 기록한다.

## 4. 5분 기능 확인

| 동작 | 확인할 결과 |
|---|---|
| PC 마우스로 창을 이동하거나 문서를 스크롤 | 선택한 모니터의 화면 변화가 iPad에 보임 |
| Pencil로 화면 가운데 점과 짧은 선을 그림 | iPad에 즉시 선이 나타나고 Windows의 같은 위치에도 표시됨 |
| 화면 중앙과 네 모서리 근처에 십자 표시 | 위치 오차가 없고 여백 부분에 잘못 그려지지 않음 |
| 선을 그린 뒤 2초간 멈춤 | iPad에 같은 선이 뒤늦게 두 겹으로 생기거나 깜박이지 않음 |
| Windows의 작은 DeskInk 상태창을 최소화하고 다시 씀 | Windows 필기가 계속 갱신됨 |
| 실제 마우스로 버튼 클릭·창 이동·스크롤 | 필기창이 마우스 조작을 막지 않음 |
| PPT 페이지를 넘기거나 문서를 스크롤 | 기존 필기가 화면의 같은 위치에 남음 |
| 사용자가 Zoom에서 해당 모니터 전체를 공유 | 별도 수신 기기에서도 필기가 보임. 송신자의 미리보기만으로 통과시키지 않음 |

끊김 복구는 별도로 기록한다. 현재 PC 프로세스가 살아 있으면 PC의 필기는 유지하도록 구현했지만, 재연결 때 iPad로 이전 필기를 모두 복원하는 기능은 아직 없다. 이 제한과 새로운 선을 더 이상 못 그리는 오류를 구분한다. 전체 지우기 UI도 아직 없으므로 첫 검증에서는 짧게 그리고, 다시 시작하려면 앱을 재실행한다.

## 5. 지연 확인

먼저 작은 글자, 빠른 원, 긴 곡선을 써 보고 펜 끝과 선 사이의 간격 및 끊김을 기록한다. 손맛 확인과 수치 측정은 구분한다.

가능하면 스마트폰의 120/240fps 촬영으로 Pencil 끝·iPad 화면·Windows 화면을 한 장면에 담는다. 같은 입력을 20회 이상 반복해 다음 두 시간을 따로 센다.

- 펜 접촉 → iPad에 첫 픽셀이 나타남.
- 펜 접촉 → Windows에 첫 픽셀이 나타남.

시간은 `차이 프레임 수 ÷ 실제 촬영 fps × 1000ms`로 근사한다. 240fps라면 프레임당 약 4.17ms 분해능이며 디스플레이 스캔과 카메라 촬영 방식에 따른 불확실성이 있다. PC와 iPad 시계의 단순 차이나 연결 왕복시간을 이 수치로 대신하지 않는다. Zoom 수신 지연은 별도로 관찰한다.

이 빌드는 Windows Debug 실행물이다. 첫 측정은 병목 확인에 쓰고 최종 성능으로 표시하지 않는다. 최종 비교는 Release 빌드, 동일 해상도·주사율·전원 조건에서 수행한다. 이더넷 필기 경로는 아직 미구현이므로 USB와의 우열을 지금 단정하지 않는다.

## 결과를 남길 내용

`Mac 빌드/테스트 성공 여부, iPad 모델·iPadOS, USB 연결 성공 여부, 영상 표시, 양쪽 필기, 위치 오차, 중복 표시, 최소화, 마우스 통과, Zoom 수신, 체감 또는 촬영 지연`을 각각 기록한다. 오류가 있으면 첫 오류 메시지와 발생 단계만으로 먼저 원인을 좁힌다. 화면 기록에는 페어링 코드나 발표 자료 등 불필요한 개인정보를 포함하지 않는다.
