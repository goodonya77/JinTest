package com.jintest.exchangecalc

/** 앱에서 다루는 8개 통화. decimals는 화면에 표시할 소수점 자리수. */
enum class Currency(
    val code: String,
    val koreanName: String,
    val flag: String,
    val decimals: Int,
) {
    KRW("KRW", "대한민국 원", "🇰🇷", 0),
    USD("USD", "미국 달러", "🇺🇸", 2),
    JPY("JPY", "일본 엔", "🇯🇵", 0),
    VND("VND", "베트남 동", "🇻🇳", 0),
    EUR("EUR", "유로", "🇪🇺", 2),
    IDR("IDR", "인도네시아 루피아", "🇮🇩", 0),
    INR("INR", "인도 루피", "🇮🇳", 2),
    CNY("CNY", "중국 위안", "🇨🇳", 2);

    companion object {
        fun fromCode(code: String?): Currency? = entries.firstOrNull { it.code == code }
    }
}
