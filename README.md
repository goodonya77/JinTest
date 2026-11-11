# 환율계산기 (Android)

거주 국가 통화를 기준으로 여러 통화를 한 화면에서 환산해 주는 안드로이드 앱 + 홈 화면 위젯입니다.

## 기능
- **거주 국가 통화 자동 설정**: 통신망(기지국) → 인터넷(IP) 위치 → SIM → 언어 설정 순으로 판단합니다.
  해외 로밍 중 Wi-Fi만 써도 IP 위치로 현지 통화가 잡힙니다.
- **화면 크기에 맞춘 카드 배치** (돌리거나 접고 펼 때마다 자동 변경)
  - 폰 세로·폴드 커버: 2열 / 폰 가로: 4열
  - 폴드 펼친 세로: 3열 / 폴드 펼친 가로: 5열 × 2줄 (한 화면에 전부)
  - 카드마다: 환산 금액, 선택된 통화 1단위의 환율(예: USD 선택 시 KRW 카드에 `1 USD = 1,334 KRW`),
    그 환율의 전일·작년말 대비 증감 (전일 1,337 → ▼3, 작년말 1,400 → ▼66)
  - 환율 숫자가 오르면 ▲빨강, 내리면 ▼파랑 (처음엔 선택 통화 = 거주국 통화)
- 기본 통화 순서: **거주국 통화(맨 앞 고정)** → USD, KRW, JPY, EUR, CNY, AUD, IDR, INR, THB
- 카드를 누르면 그 통화가 입력 기준이 됩니다. "거주 국가 통화로 되돌리기"로 복귀.
- 입력창을 누르면 값이 비워져 바로 새 금액을 칠 수 있습니다.
- **길게 눌러 끌기**로 순서 변경, 끄는 중 화면 아래 삭제 영역에 놓으면 삭제
- 맨 끝 **＋ 카드**로 통화 추가 (50개 통화, 검색 가능). 순서·목록은 저장됩니다.
- **홈 화면 위젯 (4×2)**: 거주국 통화 기준으로 목록 앞 4개 통화의 환율과 전일 대비 증감.
  3시간마다 자동 갱신, ⟳로 즉시 갱신, 누르면 앱 열기
- 환율은 [fawazahmed0/currency-api](https://github.com/fawazahmed0/exchange-api) (무료, API 키 불필요,
  하루 1회 갱신, 날짜별 과거 환율 제공)에서 받으며, 저장해 두어 오프라인에서도 계산됩니다.
- 다크 모드 자동 지원

## 폰에 바로 설치하기 (APK)
GitHub에 코드를 올릴 때마다 APK가 자동으로 빌드되어 아래 주소에 올라갑니다.

**휴대폰 브라우저로 열기:** https://github.com/goodonya77/JinTest/releases/latest/download/ExchangeCalc.apk

받은 파일을 열고 → "출처를 알 수 없는 앱 설치" 허용 → 설치

## Android Studio로 실행하기
1. [Android Studio](https://developer.android.com/studio) 설치
2. `File > Open` 으로 이 폴더 열기 (Gradle 동기화가 자동으로 진행됩니다)
3. 휴대폰을 USB로 연결(개발자 옵션 → USB 디버깅 켜기)하거나 에뮬레이터를 만든 뒤 ▶ Run

## 위젯 추가하기
홈 화면 빈 곳을 길게 누름 → 위젯 → "환율계산기" → 4×2 위젯을 끌어다 놓기

## 코드 구조 (`app/src/main/java/com/jintest/exchangecalc/`)
- `Currency.kt` — 통화 목록 (이름, 국가, 소수점 자리수)과 기본 목록
- `CountryDetector.kt` — 거주 국가 판단 및 국가 → 통화 매핑
- `RateRepository.kt` — 환율 API 호출, 저장, 통화 간 환산, 전일·작년말 증감 계산
- `Settings.kt` — 통화 순서, 마지막 거주 국가 저장 (앱·위젯 공용)
- `MainViewModel.kt` — 화면 상태 관리
- `MainActivity.kt` — 화면 배치 (세로/가로), 입력 칸, 카드 그리드
- `CurrencyCard.kt` — 통화 카드, ＋ 카드
- `ReorderState.kt` — 길게 눌러 끌어서 순서 바꾸기·삭제
- `AddCurrencySheet.kt` — 통화 추가 목록
- `Formatter.kt` — 숫자 표시 형식
- `widget/ExchangeWidget.kt` — 홈 화면 위젯
