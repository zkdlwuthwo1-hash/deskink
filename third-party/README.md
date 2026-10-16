# 재사용 소스

`host/`, `proto/`, `ios/`는 [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor)의 커밋 `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`에서 가져온 기반 소스입니다. 저작권과 MIT 허가문은 `EternalMonitor-LICENSE`에 보존합니다. 이 프로젝트에서 변경한 부분은 Git 변경 기록으로 구분합니다.

이 소스를 가져온 것만으로 DeskInk 기능이 완성되거나 검증된 것은 아닙니다. 기존 제품의 가상 모니터·원격 조작·오디오·업데이트 기능은 DeskInk 요구사항에 자동으로 포함되지 않습니다. 실제 실행 경로에 연결하기 전에 의도치 않은 시스템 변경과 네트워크 동작을 검토합니다.

FFmpeg 등 외부 바이너리의 라이선스는 빌드 구성별로 별도 확인합니다. MIT 소스의 재사용 허가를 전체 배포물의 라이선스로 확대 해석하지 않습니다.

앱 빌드 SDK는 BtbN `ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1`이며 LGPLv3이다. 별도 `.tools/ffmpeg-gpl-tests-only/`에는 기존 프로젝트의 x264 회귀 테스트용 GPLv3 DLL을 보관한다. 테스트용 자산 SHA-256은 `3e61e96b44bce30f0fad9fc31955be7fe4d6690d6a5a2b65c62494e262f8369e`로 같은 고정 릴리스의 배포 체크섬과 일치했다. 이 경로는 `test-windows.ps1 -IncludeUpstreamVideoTests`의 자식 테스트 프로세스에서만 사용하고 앱 패키지에 포함하지 않는다.
