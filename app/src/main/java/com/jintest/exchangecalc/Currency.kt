package com.jintest.exchangecalc

import java.util.Locale

/**
 * 통화 하나. country는 국기와 거주국 판단에 쓰는 ISO 국가 코드 (유로는 "EU").
 * decimals는 환산 금액을 보여줄 소수점 자리수.
 */
data class Currency(
    val code: String,
    val koreanName: String,
    val country: String,
    val decimals: Int = 2,
) {
    /** 국가 코드 두 글자를 국기 이모지로 바꾼다 (예: "KR" → 🇰🇷). */
    val flag: String
        get() = country.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || code.contains(q, ignoreCase = true) || koreanName.contains(q) ||
            country.equals(q, ignoreCase = true)
    }
}

/** 추가할 수 있는 통화 목록과 처음 설치했을 때의 기본 목록. */
object Currencies {
    val ALL = listOf(
        Currency("USD", "미국 달러", "US"),
        Currency("KRW", "대한민국 원", "KR", 0),
        Currency("JPY", "일본 엔", "JP", 0),
        Currency("EUR", "유로", "EU"),
        Currency("CNY", "중국 위안", "CN"),
        Currency("AUD", "호주 달러", "AU"),
        Currency("IDR", "인도네시아 루피아", "ID", 0),
        Currency("INR", "인도 루피", "IN"),
        Currency("THB", "태국 바트", "TH"),
        Currency("VND", "베트남 동", "VN", 0),
        Currency("GBP", "영국 파운드", "GB"),
        Currency("CAD", "캐나다 달러", "CA"),
        Currency("CHF", "스위스 프랑", "CH"),
        Currency("HKD", "홍콩 달러", "HK"),
        Currency("TWD", "대만 달러", "TW"),
        Currency("SGD", "싱가포르 달러", "SG"),
        Currency("PHP", "필리핀 페소", "PH"),
        Currency("MYR", "말레이시아 링깃", "MY"),
        Currency("NZD", "뉴질랜드 달러", "NZ"),
        Currency("MNT", "몽골 투그릭", "MN", 0),
        Currency("KHR", "캄보디아 리엘", "KH", 0),
        Currency("LAK", "라오스 킵", "LA", 0),
        Currency("MMK", "미얀마 짯", "MM", 0),
        Currency("NPR", "네팔 루피", "NP"),
        Currency("PKR", "파키스탄 루피", "PK"),
        Currency("BDT", "방글라데시 타카", "BD"),
        Currency("LKR", "스리랑카 루피", "LK"),
        Currency("KZT", "카자흐스탄 텡게", "KZ"),
        Currency("UZS", "우즈베키스탄 숨", "UZ", 0),
        Currency("AED", "아랍에미리트 디르함", "AE"),
        Currency("SAR", "사우디 리얄", "SA"),
        Currency("QAR", "카타르 리얄", "QA"),
        Currency("KWD", "쿠웨이트 디나르", "KW"),
        Currency("ILS", "이스라엘 셰켈", "IL"),
        Currency("TRY", "튀르키예 리라", "TR"),
        Currency("RUB", "러시아 루블", "RU"),
        Currency("PLN", "폴란드 즐로티", "PL"),
        Currency("CZK", "체코 코루나", "CZ"),
        Currency("HUF", "헝가리 포린트", "HU", 0),
        Currency("SEK", "스웨덴 크로나", "SE"),
        Currency("NOK", "노르웨이 크로네", "NO"),
        Currency("DKK", "덴마크 크로네", "DK"),
        Currency("ZAR", "남아프리카 랜드", "ZA"),
        Currency("EGP", "이집트 파운드", "EG"),
        Currency("MXN", "멕시코 페소", "MX"),
        Currency("BRL", "브라질 헤알", "BR"),
        Currency("ARS", "아르헨티나 페소", "AR"),
        Currency("CLP", "칠레 페소", "CL", 0),
        Currency("COP", "콜롬비아 페소", "CO", 0),
        Currency("PEN", "페루 솔", "PE"),
    )

    val DEFAULT_LIST = listOf("USD", "KRW", "JPY", "EUR", "CNY", "AUD", "IDR", "INR", "THB")

    val USD: Currency = ALL.first { it.code == "USD" }
    val EUR: Currency = ALL.first { it.code == "EUR" }

    fun of(code: String?): Currency? = ALL.firstOrNull { it.code == code }
}
