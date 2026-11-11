package com.jintest.exchangecalc

import android.content.Context

/** 앱과 위젯이 함께 쓰는 설정 (통화 목록 순서, 마지막으로 확인한 거주 국가). */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var currencyList: List<Currency>
        get() = (prefs.getString(KEY_LIST, null)?.split(",") ?: Currencies.DEFAULT_LIST)
            .mapNotNull { Currencies.of(it) }
        set(value) = prefs.edit().putString(KEY_LIST, value.joinToString(",") { it.code }).apply()

    var lastCountry: String?
        get() = prefs.getString(KEY_LAST_COUNTRY, null)
        set(value) = prefs.edit().putString(KEY_LAST_COUNTRY, value).apply()

    private companion object {
        const val KEY_LIST = "currency_list"
        const val KEY_LAST_COUNTRY = "last_country"
    }
}
