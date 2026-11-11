package com.jintest.exchangecalc.travel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class Sky(val label: String, val icon: String) {
    CLEAR("맑음", "☀️"),
    CLOUDY("흐림", "⛅"),
    RAIN("비", "🌧️"),
    SNOW("눈", "🌨️"),
}

enum class Feel(val label: String) { HOT("더움"), WARM("따뜻"), COOL("선선"), CHILLY("쌀쌀"), COLD("추움") }

data class Weather(val sky: Sky, val temperature: Double) {
    val feel: Feel
        get() = when {
            temperature >= 28 -> Feel.HOT
            temperature >= 20 -> Feel.WARM
            temperature >= 12 -> Feel.COOL
            temperature >= 5 -> Feel.CHILLY
            else -> Feel.COLD
        }
}

/**
 * 현재 날씨: 노르웨이 기상청(MET Norway) Locationforecast API.
 * 무료·상업 이용 가능 (CC BY 4.0, 출처 표기 필요), 앱을 알 수 있는 User-Agent 필수.
 * 같은 도시는 30분 동안 다시 묻지 않는다.
 */
object WeatherService {
    private const val UA = "ExchangeCalc/2.0 github.com/goodonya77/JinTest"
    private const val TTL = 30 * 60 * 1000L
    private val cache = mutableMapOf<String, Pair<Long, Weather>>()

    suspend fun current(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        // MET은 좌표를 소수 4자리까지만 받는다
        val key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon)
        synchronized(cache) {
            cache[key]?.let { (at, w) -> if (System.currentTimeMillis() - at < TTL) return@withContext w }
        }
        runCatching { fetch(lat, lon) }.getOrNull()?.also {
            synchronized(cache) { cache[key] = System.currentTimeMillis() to it }
        }
    }

    private fun fetch(lat: Double, lon: Double): Weather? {
        val url = String.format(
            Locale.ROOT,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon,
        )
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", UA)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val now = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
            val temp = now.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
            val symbol = listOf("next_1_hours", "next_6_hours", "next_12_hours")
                .firstNotNullOfOrNull { now.optJSONObject(it)?.optJSONObject("summary")?.optString("symbol_code") }
                ?: ""
            return Weather(skyOf(symbol), temp)
        } finally {
            conn.disconnect()
        }
    }

    /** MET 날씨 기호(clearsky_day, lightrain, heavysnowshowers …)를 4가지로 묶는다 */
    private fun skyOf(symbol: String): Sky = when {
        "snow" in symbol || "sleet" in symbol -> Sky.SNOW
        "rain" in symbol || "thunder" in symbol -> Sky.RAIN
        symbol.startsWith("clearsky") || symbol.startsWith("fair") -> Sky.CLEAR
        else -> Sky.CLOUDY
    }
}
