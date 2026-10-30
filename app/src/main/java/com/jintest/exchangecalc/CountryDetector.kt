package com.jintest.exchangecalc

import android.content.Context
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** 국가를 어디서 알아냈는지 (화면에 표시). */
enum class CountrySource(val label: String) {
    NETWORK("통신망"),
    IP("인터넷 위치"),
    SAVED("지난번 위치"),
    SIM("SIM"),
    LOCALE("언어 설정"),
    NONE("확인 불가"),
}

data class DetectedCountry(val code: String, val source: CountrySource)

/**
 * 현재 있는 국가를 추정해서 기준 통화를 정한다.
 * 정확한 순서: 통신망(기지국) → 인터넷(IP) 위치 → SIM → 언어 설정.
 * 해외 로밍 중 Wi-Fi만 쓰면 통신망 정보가 없고 SIM은 고국을 가리키므로, IP 위치를 SIM보다 먼저 본다.
 */
object CountryDetector {

    private val EURO_COUNTRIES = setOf(
        "AT", "BE", "HR", "CY", "EE", "FI", "FR", "DE", "GR", "IE", "IT",
        "LV", "LT", "LU", "MT", "NL", "PT", "SK", "SI", "ES",
    )

    /** 기지국 기준 국가. 셀룰러 신호가 없으면(Wi-Fi 전용, 비행기 모드) null. */
    fun fromNetwork(context: Context): DetectedCountry? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.networkCountryIso.toCountry()?.let { DetectedCountry(it, CountrySource.NETWORK) }
    }

    /** 네트워크 없이 바로 알 수 있는 마지막 수단: SIM → 언어 설정. */
    fun fromSimOrLocale(context: Context): DetectedCountry {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        tm?.simCountryIso.toCountry()?.let { return DetectedCountry(it, CountrySource.SIM) }
        Locale.getDefault().country.toCountry()?.let { return DetectedCountry(it, CountrySource.LOCALE) }
        return DetectedCountry("", CountrySource.NONE)
    }

    /** 공용 IP로 국가를 조회한다 (무료, 키 불필요). 실패하면 null. */
    suspend fun fromIp(): DetectedCountry? = withContext(Dispatchers.IO) {
        val code = lookup("https://api.country.is/", "country")
            ?: lookup("https://ipwho.is/", "country_code")
        code?.let { DetectedCountry(it, CountrySource.IP) }
    }

    private fun lookup(url: String, field: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString(field).toCountry()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun String?.toCountry(): String? =
        this?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }

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
