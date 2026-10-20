# Intel PC에서 페어링 직후 영상 연결 종료

- Status: in-progress
- Blocked by: none
- Review baseline: `2427cdac449e33c716ebfc639214e877bb7e9e80`

## 근거와 요구사항

친구의 Lightning iPad(iPadOS 26)는 USB 터널·페어링·세션 생성까지 성공한 뒤
`Connection lost — USB disconnected`로 돌아간다.
사용자가 제공한 비공개 로그에서 Intel UHD Graphics 730의 h264_qsv가
지원하지 않는 YUV420P를 거부한 뒤, 배포 런타임에 없는 libx264로
넘어가면서 인코더·영상 전송이 종료되는 것이 확인됐다.

1. 현재 런타임이 광고하는 입력 형식을 선택하고 BGRA 캡처를 정확히 변환한다.
2. 하드웨어 압축기 초기화가 실패하면 배포한 LGPL 구성에서 실제 사용 가능한
   소프트웨어 압축 경로로 영상 전송을 계속한다.
3. 기존 NVENC/AMF·직접 BGRA·x264 개발 테스트 경로는 유지한다.
4. 재접속 시 첫 영상에 디코더가 필요한 SPS/PPS/IDR이 있고 영상이 복원돼야 한다.
5. Windows ZIP만 업데이트한다. iPad 0.4.2는 재설치하지 않는다.
6. 한국어·영어 README 및 HTML에 업데이트·Lightning 연결 안내를 추가한다.
7. 친구 PC나 iPad 실기기에서 확인하지 않은 동작·지연 수치는 검증됐다고 하지 않는다.
8. 원본 로그, 개인 기기명, 주소, 세션 식별자는 공개 저장소에 추가하지 않는다.

## 검증

- 실제 LGPL FFmpeg의 QSV 입력 형식 검사: 수정 전 YUV420P 선택으로 실패.
- 공개 영상 처리 입구 `run_encode_stage`: BGRA 입력 → H.264 → 엄격한
  소프트웨어 디코더 출력의 색상·크기·프레임 검사.
- 대체 압축 경로 및 기존 Windows/프로토콜 회귀 검사.
- 실제 배포 ZIP과 포함 안내서 검증.
- Intel PC + Lightning iPad 실기기 재연결: 사용자 확인 필요.
