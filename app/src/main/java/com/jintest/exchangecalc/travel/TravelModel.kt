package com.jintest.exchangecalc.travel

import java.util.Calendar

/** 비교 품목. usd=true면 값이 미국 달러 기준 (Cable.co.uk, 배낭여행 지수) */
data class Item(
    val key: String,
    val icon: String,
    val label: String,
    val city: Boolean,
    val source: String,
    val usd: Boolean = false,
)

val ITEMS = listOf(
    Item("bigmac", "🍔", "빅맥", false, "The Economist 빅맥 지수"),
    Item("starbucks_americano_tall", "☕", "스타벅스 아메리카노 (톨)", false, "스타벅스 메뉴가"),
    Item("coke_500ml", "🥤", "콜라 500ml", false, "편의점·마트 판매가"),
    Item("water_500ml", "💧", "생수 500ml", false, "편의점·마트 판매가"),
    Item("meal_cheap", "🍜", "저렴한 식당 한 끼", true, "통계·여행 자료"),
    Item("beer_restaurant", "🍺", "식당 맥주 한 잔", true, "통계·여행 자료"),
    Item("taxi_base", "🚕", "택시 기본요금", true, "공식 요금표"),
    Item("transit_single", "🚇", "버스·지하철 1회", true, "교통공사 요금"),
    Item("hotel_mid", "🏨", "호텔 1박 (평균)", true, "숙박 통계"),
    Item("backpacker_daily_usd", "🎒", "배낭여행 하루 경비", true, "Price of Travel 배낭여행자 지수", usd = true),
    Item("iphone_base", "📱", "아이폰 (최신 기본 모델)", false, "애플 정가"),
    Item("netflix_standard", "🎬", "넷플릭스 스탠다드 / 월", false, "넷플릭스 요금"),
    Item("spotify_premium", "🎵", "스포티파이 프리미엄 / 월", false, "스포티파이 요금"),
    Item("mobile_data_1gb_usd", "📶", "모바일 데이터 1GB", false, "Cable.co.uk", usd = true),
    Item("broadband_month_usd", "🌐", "인터넷 월 요금", false, "Cable.co.uk", usd = true),
    Item("petrol_1l", "⛽", "휘발유 1L", false, "GlobalPetrolPrices"),
    Item("electricity_kwh", "💡", "전기 1kWh (가정)", false, "GlobalPetrolPrices"),
    Item("ikea_billy", "🪑", "이케아 빌리 책장", false, "IKEA 판매가"),
)

/** "내 돈으로 살 수 있는 것"에 쓰는 품목 */
val BUY_ITEMS = listOf("bigmac", "starbucks_americano_tall", "beer_restaurant", "taxi_base")

enum class Season(val label: String, val icon: String) { PEAK("성수기", "🔴"), MID("보통", "🟡"), OFF("비수기", "🟢") }

fun seasonOf(code: Char): Season = when (code) {
    'P' -> Season.PEAK
    'O' -> Season.OFF
    else -> Season.MID
}

fun currentMonthIndex(): Int = Calendar.getInstance().get(Calendar.MONTH)

fun City.seasonNow(): Season = seasonOf(season[currentMonthIndex()])

/** 나라 전체 성수기: 이번 달 도시들 중 가장 많은 것 (동률이면 보통) */
fun countrySeason(cities: List<City>): Season? {
    if (cities.isEmpty()) return null
    val counts = cities.groupingBy { it.seasonNow() }.eachCount()
    val max = counts.values.max()
    val top = counts.filterValues { it == max }.keys
    return if (top.size == 1) top.first() else Season.MID
}

/** 품목 값 하나: 어디 기준인지(도시 이름 또는 null=국가) 함께 */
data class ItemValue(val value: Double, val currency: String, val place: String?, val entry: PriceEntry?)

/**
 * 나라(iso2)의 품목 값을 찾는다.
 * city가 정해져 있으면 그 도시 값, 없으면 국가 값. 도시 품목인데 국가 전체를 보면 대표 도시(첫 도시) 값을 쓴다.
 */
fun TravelData.itemValue(iso2: String, item: Item, city: String?): ItemValue? {
    val country = country(iso2) ?: return null
    if (item.key == "bigmac") {
        val b = bigMac[country.iso3] ?: return null
        return ItemValue(b.localPrice, b.currency, null, null)
    }
    val p = prices[iso2] ?: return null
    val currency = if (item.usd) "USD" else p.currency
    if (!item.city) {
        val e = p.national[item.key] ?: return null
        return e.value?.let { ItemValue(it, currency, null, e) }
    }
    val order = buildList {
        city?.let { add(it) }
        if (city == null) addAll(cities[iso2].orEmpty().filter { it.type == CityType.MAJOR }.map { it.name })
    }
    for (name in order) {
        val e = p.cities[name]?.get(item.key) ?: continue
        if (e.value != null) return ItemValue(e.value, currency, name, e)
    }
    return null
}
