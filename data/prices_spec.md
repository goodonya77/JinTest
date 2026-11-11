# 여행 물가 품목 조사 기준

모든 값은 **현지 통화** 숫자로 기록한다 (`unit_currency`가 USD인 Cable.co.uk 항목 제외).
출처에서 직접 확인한 값만 쓰고, 2024년 1월 이전 자료이거나 확인하지 못하면 `value: null`.
추정·기억으로 채우지 않는다.

## 품목 (key)

| key | 품목 | 범위 | 기준 |
|---|---|---|---|
| starbucks_americano_tall | 스타벅스 카페 아메리카노 톨(Tall, 따뜻한) | 국가 | 공식 사이트·앱 메뉴가, 없으면 신뢰할 만한 언론 보도 |
| coke_500ml | 코카콜라 500ml 페트 | 국가 | 편의점·대형마트 온라인 판매가 |
| water_500ml | 생수 500ml | 국가 | 편의점·대형마트 온라인 판매가 |
| meal_cheap | 저렴한 식당 한 끼 | 도시 | 공신력 있는 여행·통계 출처 |
| beer_restaurant | 식당 맥주 한 잔·한 병 (국산, 약 500ml) | 도시 | 〃 |
| taxi_base | 택시 기본요금 | 도시 | 시·정부·택시회사 공식 요금표 |
| transit_single | 버스·지하철 1회 요금 (성인, 기본) | 도시 | 교통공사 공식 요금 |
| hotel_mid | 중급 호텔 1박 평균 | 도시 | 공신력 있는 조사 (없으면 null) |
| backpacker_daily_usd | 배낭여행자 하루 경비 (USD) | 도시 | Price of Travel Backpacker Index |
| iphone_base | 아이폰 최신 기본 모델 최저 용량 | 국가 | Apple 공식 스토어 (모델명·용량을 note에) |
| netflix_standard | 넷플릭스 스탠다드 월 요금 | 국가 | Netflix 공식 요금 페이지 |
| spotify_premium | 스포티파이 프리미엄 개인 월 요금 | 국가 | Spotify 공식 요금 페이지 |
| mobile_data_1gb_usd | 모바일 데이터 1GB 평균 (USD) | 국가 | Cable.co.uk 최신 조사 |
| broadband_month_usd | 초고속 인터넷 월 평균 (USD) | 국가 | Cable.co.uk 최신 조사 |
| petrol_1l | 휘발유 1L | 국가 | GlobalPetrolPrices.com 최신 |
| electricity_kwh | 가정용 전기 1kWh | 국가 | GlobalPetrolPrices.com 최신 |
| ikea_billy | 이케아 빌리 책장 80×28×202cm 흰색 | 국가 | 해당 나라 IKEA 공식 사이트 (매장 없으면 null) |

## 출력 형식

```json
{
  "iso2": "VN",
  "currency": "VND",
  "national": {
    "starbucks_americano_tall": {"value": 65000, "as_of": "2026-10", "source": "https://...", "note": ""}
  },
  "cities": {
    "호치민": {"taxi_base": {"value": 12000, "as_of": "2026-05", "source": "https://...", "note": "Vinasun 4인승"}}
  }
}
```
