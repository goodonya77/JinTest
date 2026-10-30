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
- **현재 있는 국가에 따라 기준 통화 자동 설정**: 통신망(기지국) → 인터넷(IP) 위치 → SIM → 언어 설정 순으로 판단합니다.
  해외 로밍 중 Wi-Fi만 써도 IP 위치로 현지 통화가 잡힙니다.
  (한국 → KRW, 베트남 → VND, 일본 → JPY, 유로존 국가 → EUR … 목록에 없는 나라는 USD)
- 기준 통화 금액을 입력하면 나머지 7개 통화가 바로 계산됩니다. 입력창을 누르면 값이 비워져 바로 새 금액을 칠 수 있습니다.
- 각 통화 줄에 표시되는 정보
  - 환산 금액
  - 환율 (예: `1 USD = 1,380.50`, 1보다 작으면 `100 JPY = 912.30`처럼 묶어서 표시)
  - **전일 대비**, **작년 말 대비** 환율 증감 금액 — 기준 통화 대비 강해지면 ▲빨강, 약해지면 ▼파랑
- 다른 통화를 누르면 그 통화가 기준이 되고, "거주 국가 통화로 되돌리기"로 자동 설정으로 돌아갈 수 있습니다.
  (직접 고른 통화는 앱을 다시 켜면 거주국 기준으로 초기화)
- 환율은 [fawazahmed0/currency-api](https://github.com/fawazahmed0/exchange-api) (무료, API 키 불필요, 날짜별 과거 환율 제공)에서
  받아오며, 마지막 환율을 저장해 두어 오프라인에서도 계산됩니다.
- 다크 모드 자동 지원

## 폰에 바로 설치하기 (APK)
GitHub에 코드를 올릴 때마다 APK가 자동으로 빌드됩니다.
1. GitHub 저장소의 **Actions** 탭 → 최신 **Build APK** 실행 클릭
2. 아래 **Artifacts**의 `ExchangeCalc-apk` 다운로드 → 압축을 풀면 `app-debug.apk`
3. 폰으로 APK 파일을 옮겨서 열기 → "출처를 알 수 없는 앱 설치" 허용 → 설치

## Android Studio로 실행하기
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
