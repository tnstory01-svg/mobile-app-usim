# mobile-app-usim

물리 USIM을 사용하는 Android 사용자가 선물받은 eSIM을 안전하게
설정하고 문제를 해결하도록 돕는 앱입니다.

## 현재 단계

제품 구현에 앞서 일반 Android 앱에서 공개 eSIM 다운로드 및 시스템
해결 절차가 실제로 동작하는지 검증하는 최소 프로브를 개발했습니다.

- 프로브: `activation-feasibility-probe/`
- 검증 절차: `docs/activation-feasibility-protocol.md`
- 기기 매트릭스: `docs/activation-feasibility-matrix.md`
- 결정 게이트: `docs/activation-decision-record.md`

물리 eSIM 기기 2종에서 검증이 통과하기 전에는 전체 제품 앱을
부트스트랩하지 않습니다.

## 프로브 빌드

Android SDK 35를 설정한 뒤 다음 명령을 실행합니다.

```powershell
cd activation-feasibility-probe
.\gradlew.bat testDebugUnitTest assembleDebug
```
