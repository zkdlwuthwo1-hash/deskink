# Mac을 직접 사용하지 않고 iPad 앱 검증하기

Windows를 개발 환경으로 유지한다. iPad 코드는 GitHub의 macOS 빌드 서버에서 컴파일하고, 만들어진 IPA를 Windows에서 개인 계정으로 서명·설치한다. 클라우드는 빌드할 때만 사용하며 실제 발표의 화면·펜 데이터는 Windows와 iPad의 USB 연결을 사용한다.

## 현재 PC에서 확인한 것

- Apple Devices `1.1540.24088.0` 설치됨.
- Sideloadly 0.70.1을 공식 배포본으로 사용자 계정에 설치했다. 설치 파일에는 Authenticode 서명이 없으며 공식 HTTPS 배포 주소를 사용했다.
- Windows 장치 목록과 Sideloadly 화면에서 USB iPad 인식을 확인했다. Sideloadly가 표시한 기기 OS 버전은 27.0.1이며 실제 DeskInk 설치·동작 검증은 별도다.
- GitHub CLI 2.102.0을 `.tools/github-cli/bin/gh.exe`에 설치하고 공식 SHA-256 값과 대조했다. 사용자 브라우저 승인 후 Windows 자격 증명 저장소에 로그인이 완료됐다.
- [비공개 저장소](https://github.com/zkdlwuthwo1-hash/deskink)에 소스를 업로드하고 macOS 빌드를 실행했다.
- 웹 배포판 iTunes·iCloud 설치 파일은 `.tools/ipad-install`에 준비했고 Apple Inc.의 유효한 서명을 확인했다. 아직 설치하지 않았다. 현재 Sideloadly에서 기기를 인식하므로 기존 Apple Devices를 제거하지 않는다.

Apple Devices는 기기 인식·동기화·백업 등을 담당하며 iPad 앱 컴파일러가 아니다. [Apple Devices의 역할](https://support.apple.com/guide/devices-windows/syncing-overview-mchl923c1147/windows)

## 1. 준비된 클라우드 빌드 실행

`.github/workflows/ipad-build.yml`을 준비했다. 수동 실행 전용이며, Xcode 16.4에서 iPad 시뮬레이터 단위 테스트 후 실제 기기용 Release 앱을 서명 없이 빌드한다. Apple 계정·비밀번호·인증서·개인 서명 키를 CI에 넣지 않는다. 실패한 빌드에서도 진단 로그는 결과 파일로 남긴다.

현재 첫 전체 성공 실행은 [38885470010](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/38885470010)이다. 이 PC의 `artifacts/local/DeskInk-unsigned.ipa`가 검증한 기기용 파일이며, Sideloadly로 개인 서명·설치를 이어간다. 아래 저장소 생성·로그인 과정은 이 PC에서는 이미 완료했다.

Windows에서 진행할 순서:

현재 PC에서는 `artifacts/local/github-login.cmd`를 실행하면 공식 GitHub 로그인 절차가 열린다. 창에서 Enter를 누르고, 브라우저에서 본인 계정으로 로그인한 다음 클립보드에 복사된 일회용 코드를 붙여넣고 GitHub CLI 연결을 승인한다. 로그인 이후의 비공개 저장소 생성·업로드·빌드 실행은 Codex가 이어서 처리할 수 있다. 비밀번호나 토큰을 대화로 전달하지 않는다. 이 실행 파일과 도구는 로컬 전용이며 소스 업로드에서 제외된다.

1. GitHub에서 본인 계정의 **비공개 저장소**를 만든다. 이 단계는 소스를 GitHub에 올리는 선택이므로 공개 저장소로 만들지 않는다.
2. 이 프로젝트를 Git으로 업로드한다. `.tools`, `target`, 로컬 로그·서명 파일은 `.gitignore`로 제외된다. ZIP 자체를 저장소에 올리는 것만으로는 Actions가 실행되지 않는다. 압축을 이용한다면 압축을 푼 프로젝트 파일과 숨김 폴더 `.github`까지 저장소 루트에 있어야 한다.
3. 저장소의 `Actions` → `Build iPad app` → `Run workflow`를 선택한다.
4. 성공한 실행의 Artifacts에서 `DeskInk-iPad-실행번호`를 내려받는다. 안의 `DeskInk-unsigned.ipa`가 다음 설치 도구에 넣을 파일이다.
5. 실패했다면 내려받은 `tests.log` 또는 `build.log`의 첫 컴파일/테스트 오류를 수정한다. 빌드·실기기 검증의 최신 결과는 [첫 연결 구현 상태](first-stroke-status.md)에 기록한다.

GitHub에 로그인하고 비공개 저장소를 만든 뒤, 원격 주소를 알고 있다면 프로젝트 터미널에서 다음처럼 연결한다. 예시의 `사용자명/저장소`는 본인 주소로 바꾼다. 로그인은 Git Credential Manager의 본인 로그인 화면에서 처리하고 토큰을 대화나 명령에 붙여넣지 않는다.

```powershell
git remote add origin https://github.com/사용자명/저장소.git
git push -u origin main
```

이미 원격 주소가 있으면 위 `remote add`를 반복하지 않는다. 저장소를 생성할 때 README 등 초기 파일을 추가하지 않은 빈 저장소를 쓰면 현재 작업 기록을 그대로 올릴 수 있다.

GitHub는 macOS 실행 환경과 결과 파일 다운로드를 제공한다. 비공개 저장소는 계정의 포함 사용량을 소모하고 초과 시 과금 조건이 적용되므로 실행 전 Actions 사용량·예산을 확인한다. 이 워크플로는 자동 실행 없이 수동으로만 실행되고, 최대 35분·결과 보관 3일로 설정했다. [GitHub 실행 환경](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), [결과 파일](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts)

## 2. Windows에서 개인 iPad에 설치

설치 도구는 [Sideloadly 공식 사이트](https://sideloadly.io/)를 기준으로 한다. 공식 안내는 Windows에서 웹 배포판 iTunes와 iCloud를 요구한다. 이 PC에서는 Apple Devices와 Sideloadly만으로 기기 목록 표시까지 확인했다. 이는 Apple 계정 인증과 서명도 성공했다는 뜻은 아니다. 실제 서명에서 구성 요소 부족 오류가 발생하면 준비한 공식 Apple 설치 파일로 해결한다.

1. 공식 배포본의 Windows 64비트 Sideloadly와 안내된 Apple 구성 요소를 준비한다.
2. 데이터 전송이 되는 USB 케이블로 iPad를 Windows에 연결하고, iPad 잠금을 풀고 본인 PC 신뢰 절차를 진행한다.
3. Sideloadly에서 해당 iPad와 **우리가 빌드한** `DeskInk-unsigned.ipa`를 선택한다.
4. 본인의 Apple 계정 로그인·인증을 직접 진행하고 설치한다. 계정 비밀번호나 인증코드를 Codex 대화에 전달할 필요는 없다.
5. 기기에서 개발자 모드 또는 개발자 신뢰를 요구하면 안내에 따라 직접 설정한다. 홈 화면에서 DeskInk를 실행한다.

일반 무료 Apple 계정으로 설치한 앱은 7일마다 서명 갱신이 필요하다. Sideloadly는 USB 또는 Wi-Fi로 기기를 찾을 수 있을 때 자동 갱신하는 기능을 제공하지만, 이 PC에서 자동 갱신까지 검증된 상태는 아니다. 장기 사용과 배포 방식은 이 임시 개발 설치와 별도로 결정한다. [Sideloadly 공식 FAQ](https://sideloadly.io/faq.html)

## 3. 설치 후 검증

Windows DeskInk를 실행한 상태에서 iPad DeskInk를 연다. 페어링 창에 Windows의 6자리 코드를 입력하고 Windows 화면이 보이는지 확인한다. 다음으로 선이 양쪽의 같은 위치에 보이는지, 상태창을 최소화해도 필기되는지, PC 마우스 조작과 Zoom 수신 화면이 정상인지 확인한다. [구체적인 5분 검사와 지연 촬영 방법](ipad-first-check.md#4-5분-기능-확인)

현재 확대·여러 도구·더블탭 지우개·영구 저장은 후속 작업이다. 클라우드 빌드 설정 준비, IPA 빌드 성공, 실제 iPad 설치, USB 필기, 지연 측정을 각각 따로 기록한다.
