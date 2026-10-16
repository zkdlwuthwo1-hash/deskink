# 01: 연결해서 화면 보고 선 하나 그리기

**What to build:** 선택한 Windows 모니터를 iPad에 표시하고, iPad 입력을 즉시 표시하면서 Windows 필기층에도 같은 선을 그린다.

**Blocked by:** None
**Status:** in-progress

- [x] 메시지 입력이 중복 없이 필기 상태에 반영된다(자동 테스트). 실제 Pencil 입력은 아래 실기기 검증에서 확인한다.
- [ ] Windows 필기층이 표시되고 마우스 입력을 통과시킨다.
- [x] 캡처에서 필기층을 분리할 수 있는지 실제 기능을 탐지한다. API 호출과 실제 픽셀 제외 모두 통과.
- [ ] USB 영상과 iPad 입력 경로가 실기기에서 연결된다.
- [ ] 초기 지연과 중복 합성 여부를 기록한다.

Windows 개발용 실행 파일 및 iPad 입력/로컬 선 소스 구현. `docs/first-stroke-status.md` 참조. iPad 빌드/설치·Pencil·Zoom 검증 전이므로 작업 01은 완료가 아니다.
