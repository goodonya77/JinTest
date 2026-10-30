package com.jintest.exchangecalc

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** 거주 국가를 추정해서 기준 통화를 정한다. */
object CountryDetector {

    private val EURO_COUNTRIES = setOf(
        "AT", "BE", "HR", "CY", "EE", "FI", "FR", "DE", "GR", "IE", "IT",
        "LV", "LT", "LU", "MT", "NL", "PT", "SK", "SI", "ES",
    )

    /** 국가 코드(ISO 3166, 예: "KR")를 찾는다. 통신망 → SIM → 시스템 언어 설정 순. */
    fun detectCountry(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOf(
            tm?.networkCountryIso,
            tm?.simCountryIso,
            Locale.getDefault().country,
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT) ?: ""
    }

    /** 국가 코드에 맞는 기준 통화. 목록에 없는 나라는 USD. */
    fun currencyForCountry(country: String): Currency = when (country) {
        "KR" -> Currency.KRW
        "US" -> Currency.USD
        "JP" -> Currency.JPY
        "VN" -> Currency.VND
        "ID" -> Currency.IDR
        "IN" -> Currency.INR
        "CN" -> Currency.CNY
        in EURO_COUNTRIES -> Currency.EUR
        else -> Currency.USD
    }
}
