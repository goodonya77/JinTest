package com.jintest.exchangecalc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 특정 날짜의 USD 1달러 기준 환율표 (통화 코드 → 환율). 다른 통화끼리는 USD를 거쳐 교차 계산한다.
 * date는 "yyyy-MM-dd".
 */
data class Rates(val perUsd: Map<String, Double>, val date: String) {
    fun convert(amount: Double, from: Currency, to: Currency): Double? = convert(amount, from.code, to.code)

    /** 통화 코드로 환산 (앱 통화 목록에 없는 통화도 가능, 예: FJD) */
    fun convert(amount: Double, from: String, to: String): Double? {
        val fromRate = perUsd[from] ?: return null
        val toRate = perUsd[to] ?: return null
        return amount / fromRate * toRate
    }
}

/**
 * 카드·위젯에 보여줄 환율 한 줄: "unit 통화 = value 상대통화"와 전일·작년말 대비 증감.
 * 값이 오르면 그 통화가 상대통화보다 강해진 것.
 */
data class Quote(val unit: Int, val value: Double, val decimals: Int, val prevDiff: Double?, val yearDiff: Double?)

/** 최신 환율과 비교용 환율(전일, 작년 말). 비교용은 못 받아오면 null. */
data class RateSet(val latest: Rates, val prevDay: Rates?, val yearEnd: Rates?) {
    fun quote(currency: Currency, against: Currency): Quote? {
        val rate = latest.convert(1.0, currency, against) ?: return null
        val unit = Formatter.quoteUnit(rate)
        val prev = prevDay?.convert(1.0, currency, against)
        val year = yearEnd?.convert(1.0, currency, against)
        return Quote(
            unit = unit,
            value = rate * unit,
            decimals = Formatter.rateDecimals(rate * unit),
            prevDiff = prev?.let { (rate - it) * unit },
            yearDiff = year?.let { (rate - it) * unit },
        )
    }
}

/**
 * 무료 환율 API(fawazahmed0/currency-api, API 키 불필요)에서 환율을 받는다.
 * 날짜별 과거 환율도 제공해서 전일·작년 말 대비 증감을 계산할 수 있다.
 * 받은 결과는 저장해 두어 오프라인에서도 쓴다.
 */
class RateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)

    fun loadCached(): RateSet? {
        val latest = prefs.getString(KEY_LATEST, null)?.let { parse(it) } ?: return null
        return RateSet(
            latest = latest,
            prevDay = prefs.getString(KEY_PREV, null)?.let { parse(it) },
            yearEnd = prefs.getString(KEY_YEAR_END, null)?.let { parse(it) },
        )
    }

    /** 마지막으로 받은 지 3시간이 지났으면 true (위젯 자동 갱신 판단용). */
    fun isStale(): Boolean =
        System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0) > 3 * 60 * 60 * 1000L

    suspend fun fetch(): RateSet = withContext(Dispatchers.IO) {
        val latestJson = download("latest") ?: error("서버 응답 없음")
        val latest = parse(latestJson) ?: error("응답 형식 오류")
        prefs.edit().putString(KEY_LATEST, latestJson).putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply()

        val day = dateFormat().parse(latest.date) ?: error("날짜 형식 오류")
        val prevDay = findDated(KEY_PREV, (1..4).map { shiftDays(day, -it) })
        val year = Calendar.getInstance(UTC).apply { time = day }.get(Calendar.YEAR) - 1
        val yearEnd = findDated(KEY_YEAR_END, (31 downTo 28).map { "$year-12-$it" })
        RateSet(latest, prevDay, yearEnd)
    }

    /** 후보 날짜를 순서대로 시도해서 처음 받아지는 환율을 쓴다. 이미 저장된 날짜면 다시 받지 않는다. */
    private fun findDated(key: String, dates: List<String>): Rates? {
        prefs.getString(key, null)?.let { parse(it) }?.takeIf { it.date in dates }?.let { return it }
        for (date in dates) {
            val json = runCatching { download(date) }.getOrNull() ?: continue
            val rates = parse(json) ?: continue
            prefs.edit().putString(key, json).apply()
            return rates
        }
        return null
    }

    /** tag는 "latest" 또는 "yyyy-MM-dd". 기본 CDN이 안 되면 예비 주소로. 해당 날짜가 없으면 null. */
    private fun download(tag: String): String? {
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$tag/v1/currencies/usd.min.json",
            "https://$tag.currency-api.pages.dev/v1/currencies/usd.min.json",
        )
        var lastError: Exception? = null
        for (url in urls) {
            try {
                return get(url)
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastError is FileNotFoundException) return null
        throw lastError ?: IllegalStateException("다운로드 실패")
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> return conn.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> throw FileNotFoundException(url)
                else -> error("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): Rates? = runCatching {
        val root = JSONObject(json)
        val table = root.getJSONObject("usd")
        val perUsd = table.keys().asSequence()
            .mapNotNull { key -> table.optDouble(key).takeIf { !it.isNaN() }?.let { key.uppercase(Locale.ROOT) to it } }
            .toMap()
        Rates(perUsd, root.getString("date"))
    }.getOrNull()

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = UTC }

    private fun shiftDays(date: java.util.Date, days: Int): String {
        val cal = Calendar.getInstance(UTC).apply { time = date; add(Calendar.DAY_OF_MONTH, days) }
        return dateFormat().format(cal.time)
    }

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        const val KEY_LATEST = "latest_json"
        const val KEY_PREV = "prev_day_json"
        const val KEY_YEAR_END = "year_end_json"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}
