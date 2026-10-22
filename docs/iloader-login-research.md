# iloader Apple 계정 로그인 조사

확인일: **2026-10-22 (한국 시간)**. 공식 iloader 저장소·릴리스·소스, 의존 라이브러리 `isideload`, Apple 지원 문서와 HTTP 표준을 읽었다. 실제 Apple 계정으로 로그인하거나 인증 코드를 요청하지 않았다. 이 문서는 설치 안내의 근거이며, 특정 계정의 로그인 성공을 검증한 기록은 아니다.

## 안내에 반영할 결론

**Windows PC에서도 `Jkcoxson (Mac)`을 선택할 수 있다.** `Mac`과 `Windows`는 Anisette 서버 항목의 구분이며 사용자의 PC 운영체제를 맞추는 선택지가 아니다. 소스의 서버 목록은 PC 운영체제에 따라 걸러지지 않고, 선택한 주소가 동일한 원격 Anisette 공급자로 전달된다. 이는 코드에서 확인한 해석이다. [서버 목록·설정](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/pages/Settings.tsx#L37-L77), [선택 주소를 사용하는 로그인 구현](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src-tauri/src/account.rs#L195-L209)

더 직접적인 근거도 있다. **2026-10-22 공개된 v2.4.0은 안정성을 이유로 `Jkcoxson (Mac)`을 기본값으로 바꾸고 이전 Windows 서버들을 기본 목록에서 제거했다.** 따라서 Windows 서버를 반드시 선택하라는 기존 안내는 수정해야 한다. 다만 개발자의 안정성 판단과 사용자의 성공 사례가 모든 계정·오류의 해결을 보장하지는 않는다. [v2.4.0 공식 릴리스](https://github.com/nab138/iloader/releases/tag/v2.4.0)

권장 안내 문구:

> Settings의 Anisette Server에서 Jkcoxson (Mac)을 선택합니다. Windows PC에서도 사용할 수 있으며, 2.4.0에서는 기본값입니다. 이미 로그인이 정상적으로 된다면 바꿀 필요는 없습니다. 이전 Windows 서버에서 로그인이 실패했다면 이 서버로 바꾸어 다시 시도하세요. 계속 실패하면 오류 내용을 확인합니다.

이는 설치 안내를 위한 편집 권고다. 사용자에게서 전달받은 “Windows에서 Mac 서버로 바꾼 뒤 성공”은 개별 관찰로만 취급하며, 원인이나 성공률을 독립적으로 검증했다고 표현하지 않는다.

## 버전별 정확한 서버 이름과 기본값

검색엔진에 남은 v2.3.10 최신 버전 표시와 달리, 확인 당시 GitHub 최신 릴리스 API는 v2.4.0을 반환했다. 공개 시각은 `2026-10-22T02:26:10Z`다. [GitHub 최신 릴리스 API](https://api.github.com/repos/nab138/iloader/releases/latest), [v2.4.0 릴리스](https://github.com/nab138/iloader/releases/tag/v2.4.0)

| 버전 | 새 설정의 기본값 | 기본 목록의 표시 이름 |
| --- | --- | --- |
| 2.3.9 | `Jkcoxson (Mac)` | `Jkcoxson (Mac)`, `Jkcoxson (Windows)`, `StikStore (secondary)` |
| 2.3.10 | `SideStore (Mac, .io)` | `SideStore (Mac, .io)`, `Jkcoxson (Mac)`, `StikStore (Mac)`, `Jkcoxson (Windows)`, `StikStore (Windows)` |
| 2.4.0 | `Jkcoxson (Mac)` | `Jkcoxson (Mac)`, `SideStore (Mac, .io)`, `StikStore (Mac)` |

표의 근거는 각 릴리스의 고정 커밋이다. [2.3.9 목록·기본값](https://github.com/nab138/iloader/blob/aa9aa3779e15498180e0205158b7ff91536877c6/src/pages/Settings.tsx#L37-L55), [2.3.10 목록·기본값](https://github.com/nab138/iloader/blob/18907b1a801ed1d5aed3304c5477696c5d3ca411/src/pages/Settings.tsx#L37-L58), [2.4.0 목록·기본값](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/pages/Settings.tsx#L37-L58)

2.4.0의 주소는 각각 `mac-ani.jkcoxson.com`, `new.ani.sidestore.io`, `ani3.stikstore.app`이다. 이전의 `Jkcoxson (Windows)`는 `ani.jkcoxson.com`이었다. 이 주소들은 소스 확인용이며 초보자 안내에서는 앱의 기본 목록을 사용하면 된다. [2.4.0 서버 정의](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/pages/Settings.tsx#L37-L45)

2.3.10 소스에는 Windows 서버의 SMS 이중 인증 제약과 불안정성에 관한 주석이 있다. 주석의 대략적인 성공 비율을 측정된 통계로 인용해서는 안 된다. 2.4.0에서는 해당 Windows 항목이 주석 처리되어 목록에 나타나지 않는다. [2.3.10 주석](https://github.com/nab138/iloader/blob/18907b1a801ed1d5aed3304c5477696c5d3ca411/src/pages/Settings.tsx#L37-L45), [2.4.0 변경](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/pages/Settings.tsx#L37-L45)

## 실제 로그인 화면과 비밀번호

2.3.9의 영문 표시와 2.4.0의 로그인 컴포넌트를 확인했다. 처음 로그인할 때의 입력칸은 `Apple ID Email...`, `Apple ID Password...`이며 버튼은 `Login`이다. 저장된 계정의 버튼은 `Sign in`이라서 서로 구분해야 한다. 로그인 완료 화면은 `Logged in as`와 `Sign Out`을 표시한다. `Save Credentials`는 선택 항목이고 초기값은 꺼짐이다. [2.3.9 영문 문자열](https://github.com/nab138/iloader/blob/aa9aa3779e15498180e0205158b7ff91536877c6/src/locales/en.json), [2.4.0 로그인 UI](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/AppleID.tsx#L24-L36), [계정 화면·폼](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/AppleID.tsx#L100-L267)

2.3.9와 2.4.0의 `AppleID.tsx`를 직접 비교하면 서버 설정의 저장 키만 바뀌었고 로그인·인증 UI 코드는 동일하다. 두 버전의 영문 `apple_id` 문자열 묶음도 동일하다. [2.3.9 로그인 컴포넌트](https://github.com/nab138/iloader/blob/aa9aa3779e15498180e0205158b7ff91536877c6/src/AppleID.tsx), [2.4.0 로그인 컴포넌트](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/AppleID.tsx)

설치 안내는 **본인 Apple 계정 이메일과 평소 사용하는 계정 비밀번호**를 입력하도록 작성하는 것이 맞다. iloader는 그 값을 `AppleAccount.login`에 넘기며, 라이브러리는 Apple 계정 비밀번호를 사용하는 GrandSlam/SRP 로그인과 이중 인증을 수행한다. 이는 UI·문서·코드에 근거한 해석이다. 조사한 공식 iloader 문서에서 앱 암호(app-specific password)를 대체 입력하라는 안내나, 모든 앱 암호의 지원 여부를 명시한 문장은 찾지 못했다. 따라서 “앱 암호를 새로 만들어 입력하면 해결된다”는 절차는 추가하지 않는다. [iloader 동작 설명](https://iloader.app/), [로그인 호출](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src-tauri/src/account.rs#L201-L209), [isideload 비밀번호 계약](https://github.com/nab138/isideload/blob/fc7a81a6de678aa707e05399ffafb8be0ad56921/isideload/src/auth/apple_account.rs#L181-L206), [SRP 구현](https://github.com/nab138/isideload/blob/fc7a81a6de678aa707e05399ffafb8be0ad56921/isideload/src/auth/apple_account.rs#L751-L829)

Apple의 앱 암호 문서는 iCloud 메일·연락처·캘린더 같은 정보에 접근하는 타사 앱을 설명한다. 그 문서를 iloader 개발자 서명 로그인에 그대로 적용할 근거는 없다. [Apple 앱 암호 안내](https://support.apple.com/en-us/102654)

## 이중 인증에서 보여 줄 단계와 한계

일반적인 안내 흐름은 다음과 같다.

1. iloader에서 본인의 계정으로 `Login`을 누른다.
2. 같은 Apple 계정에 로그인된 신뢰하는 iPad·iPhone의 로그인 알림에서 **허용**을 눌러 Apple 인증 코드를 확인한다.
3. PC의 `Two-Factor Authentication` 창에서 `Verification Code...`에 **6자리 Apple 인증 코드**를 입력하고 `Submit`을 누른다.
4. `Logged in as` 표시를 확인한 뒤 IPA 설치로 넘어간다.

Apple은 신뢰하는 기기의 알림을 허용한 뒤 다른 기기의 로그인 화면에 코드를 입력하는 절차를 설명한다. iloader의 별도 인증 창은 6글자를 확인한 뒤 코드를 제출한다. 이 코드는 DeskInk 연결용 페어링 코드나 iPad 잠금 해제 암호와 구분하여 설명한다. [Apple 인증 코드 절차](https://support.apple.com/en-us/102606), [iloader 인증 창](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/AppleID.tsx#L269-L293)

`isideload`에는 신뢰하는 기기 인증과 SMS 인증 흐름이 모두 구현되어 있다. 하지만 2.4.0 iloader UI는 해당 라이브러리의 방법 선택·재전송 기능을 노출하지 않고 코드 제출만 한다. **iloader 화면에 없는 “코드를 받지 못했나요?”, SMS 전환, 재전송 버튼을 안내하면 안 된다.** Apple 지원 문서의 해당 버튼은 Apple의 로그인 화면 절차다. SMS가 진행되지 않는 경우에도 신뢰하는 기기의 알림이 실제로 표시되는지, 사용 버전과 서버가 무엇인지 확인해야 하며, SMS 코드와 기기 인증 코드가 언제나 서로 대체된다고 단정하지 않는다. [라이브러리 인증 상태](https://github.com/nab138/isideload/blob/fc7a81a6de678aa707e05399ffafb8be0ad56921/isideload/src/auth/apple_account.rs#L225-L298), [iloader 콜백 구현](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src-tauri/src/account.rs#L169-L193)

iloader 2.4.0의 오류 안내는 보안 키 방식의 이중 인증을 지원하지 않는다고 명시한다. 일반 설치 안내에서 계정 보안 기능을 끄도록 권하지 않는다. 인증 입력을 기다리는 앱의 제한 시간은 120초이며, 이것은 Apple 코드 자체의 유효 시간이라고 설명하면 안 된다. [인증 오류 안내 문자열](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/locales/en.json), [120초 콜백 대기](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src-tauri/src/account.rs#L179-L189)

## 실패 시 서버 변경을 안내할 범위

`Anisette`, `Failed to get anisette client info`, 프로비저닝 연결 실패가 보이면 인터넷 연결을 확인하고 앱의 다른 기본 서버로 바꾸어 재시도하는 것이 iloader가 실제로 제시하는 대응이다. 공식 오류 안내에도 서버 변경이 있으며, 개발자는 502 오류 신고와 프로비저닝 시간 초과 신고에서 다른 서버 사용을 제안했다. [앱의 Anisette 오류 안내](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/locales/en.json), [502에 대한 개발자 답변](https://github.com/nab138/iloader/issues/346#issuecomment-4187737211), [시간 초과에 대한 개발자 답변](https://github.com/nab138/iloader/issues/149#issuecomment-3941583623)

502는 게이트웨이·프록시가 상위 서버의 잘못된 응답을 받은 경우, 503은 서버가 당장 요청을 처리할 수 없는 경우를 나타내는 HTTP 서버 오류다. **502·503만 보고 비밀번호가 틀렸다고 판단하거나, 모두 Anisette 서버 탓이라고 단정할 수 없다.** 오류의 요청 주소가 Anisette 서버인지 Apple의 `gsa.apple.com`인지도 다를 수 있다. 실제 iloader 신고에도 Apple GrandSlam 주소의 503이 존재한다. [HTTP 502 정의](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.6.3), [HTTP 503 정의](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.6.4), [Apple 주소 503 신고](https://github.com/nab138/iloader/issues/927)

안내에서는 “잠시 후 다시 시도하고, 이전 버전이면 공식 최신 버전을 확인한다. Anisette 관련 실패가 계속되면 `Jkcoxson (Mac)` 또는 앱에 제공되는 다른 서버를 시도한다” 정도가 적절하다. 계정·비밀번호 오류나 계정 잠금이 명시되면 그 계정 안내를 따르고, 서버를 바꾸면 해결된다고 약속하지 않는다. 오류 전문은 민감한 계정 정보가 섞일 수 있으므로 공개 안내 이미지에는 실제 계정·인증 코드·토큰을 넣지 않는다. 이 문단은 위 근거에 따른 문서 작성 권고다.

## 화면 자료 작성 시 표시할 사항

### IPA 설치 단계의 버튼 구분

2.4.0의 `Import IPA` 버튼에서 파일을 고르면 앱은 바로 서명·설치 작업을
시작한다. **`Sign & Install App`은 추가로 눌러야 하는 버튼이 아니라 진행
단계명**이다. 안내서에는 “Import IPA에서 파일 선택 → Sign & Install App
단계가 끝날 때까지 대기”로 쓴다. 7일 갱신도 같은 계정으로 같은 IPA를
Import IPA에서 다시 선택하는 흐름이다.
[파일 선택 후 작업 시작](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/App.tsx#L375-L390),
[설치 단계 정의](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/src/components/operations.ts#L81-L89)

### 캡처의 표현

- 원본 화면이 2.3.9이면 캡션에 그 버전을 남기고, 2.4.0의 목록과 다를 수 있다고 설명한다.
- 로그인·인증 창을 실제 upstream 소스와 예시 값으로 렌더링했다면 사용한 버전에 맞추어 “공식 2.4.0 UI 소스 기반 설명용 화면, 예시 계정 사용”처럼 표기한다. 실제 Apple 인증을 완료한 캡처라고 표현하지 않는다.
- 서버·계정 입력·Apple 인증·로그인 완료·IPA 설치를 서로 다른 단계로 보여 준다. 단계의 버튼 이름은 위에서 확인한 실제 문자열을 쓴다.

위 항목은 검증한 버전 차이와 UI 흐름을 화면 안내에 적용하기 위한 편집 기준이다.
