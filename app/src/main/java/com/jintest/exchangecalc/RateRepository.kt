package com.jintest.exchangecalc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * USD 1달러 기준 환율표. 다른 통화끼리는 USD를 거쳐 교차 계산한다.
 * updatedAt은 데이터 제공처가 환율을 갱신한 시각(epoch 초).
 */
data class Rates(val perUsd: Map<Currency, Double>, val updatedAt: Long) {
    fun convert(amount: Double, from: Currency, to: Currency): Double? {
        val fromRate = perUsd[from] ?: return null
        val toRate = perUsd[to] ?: return null
        return amount / fromRate * toRate
    }
}

/** 무료 환율 API(open.er-api.com, API 키 불필요)에서 환율을 받고, 마지막 결과를 저장해 둔다. */
class RateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)

    fun loadCached(): Rates? = prefs.getString(KEY_JSON, null)?.let { parse(it) }

    suspend fun fetch(): Rates = withContext(Dispatchers.IO) {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val rates = parse(body) ?: error("응답 형식 오류")
            prefs.edit().putString(KEY_JSON, body).apply()
            rates
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): Rates? = runCatching {
        val root = JSONObject(json)
        if (root.optString("result") != "success") return null
        val table = root.getJSONObject("rates")
        val perUsd = Currency.entries
            .filter { table.has(it.code) }
            .associateWith { table.getDouble(it.code) }
        Rates(perUsd, root.optLong("time_last_update_unix"))
    }.getOrNull()

    private companion object {
        const val API_URL = "https://open.er-api.com/v6/latest/USD"
        const val KEY_JSON = "latest_json"
    }
}
