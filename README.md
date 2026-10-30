# 환율계산기 (Android)

8개 통화를 한 화면에서 한꺼번에 환산해 주는 안드로이드 앱입니다.

| 코드 | 통화 |
|---|---|
| KRW | 대한민국 원 |
| USD | 미국 달러 |
| JPY | 일본 엔 |
| VND | 베트남 동 |
| EUR | 유로 |
| IDR | 인도네시아 루피아 |
| INR | 인도 루피 |
| CNY | 중국 위안 |

## 기능
- **거주 국가에 따라 기준 통화 자동 설정**: 통신망 국가 → SIM 국가 → 시스템 지역 설정 순으로 국가를 판단합니다.
  (한국 → KRW, 일본 → JPY, 유로존 국가 → EUR … 목록에 없는 나라는 USD)
- 기준 통화 금액을 입력하면 나머지 7개 통화가 바로 계산됩니다.
- 다른 통화를 누르면 그 통화가 기준이 되고(선택은 저장됨), "거주 국가 통화로 되돌리기"로 자동 설정으로 돌아갈 수 있습니다.
- 환율은 [open.er-api.com](https://www.exchangerate-api.com/docs/free) (무료, API 키 불필요)에서 받아오며,
  마지막 환율을 저장해 두어 오프라인에서도 계산됩니다.
- 다크 모드 자동 지원

## 실행 방법
1. [Android Studio](https://developer.android.com/studio) 설치
2. `File > Open` 으로 이 폴더 열기 (Gradle 동기화가 자동으로 진행됩니다)
3. 휴대폰을 USB로 연결(개발자 옵션 → USB 디버깅 켜기)하거나 에뮬레이터를 만든 뒤 ▶ Run

## 코드 구조 (`app/src/main/java/com/jintest/exchangecalc/`)
- `Currency.kt` — 8개 통화 정의 (이름, 국기, 소수점 자리수)
- `CountryDetector.kt` — 거주 국가 판단 및 국가 → 통화 매핑
- `RateRepository.kt` — 환율 API 호출, 저장, 통화 간 환산
- `MainViewModel.kt` — 화면 상태 관리 (기준 통화, 입력 금액 등)
- `MainActivity.kt` — 화면(UI, Jetpack Compose)
- `Formatter.kt` — 숫자 표시 형식
