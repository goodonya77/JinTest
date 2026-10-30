package com.jintest.exchangecalc

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val base: Currency = Currency.USD,
    val country: DetectedCountry = DetectedCountry("", CountrySource.NONE),
    /** 사용자가 이번 실행 중 기준 통화를 직접 고른 경우 true. 앱을 다시 켜면 거주국 기준으로 돌아간다. */
    val baseChosenManually: Boolean = false,
    val amountText: String = "1",
    val rates: RateSet? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RateRepository(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val network = CountryDetector.fromNetwork(app)
        val initial = network
            ?: prefs.getString(KEY_LAST_COUNTRY, null)?.let { DetectedCountry(it, CountrySource.SAVED) }
            ?: CountryDetector.fromSimOrLocale(app)
        _state.value = UiState(
            base = CountryDetector.currencyForCountry(initial.code),
            country = initial,
            rates = repo.loadCached(),
        )
        if (network != null) {
            remember(network)
        } else {
            // 통신망 정보가 없으면 인터넷 위치로 확인 (해외 로밍 + Wi-Fi 사용 시)
            viewModelScope.launch {
                val ip = CountryDetector.fromIp()
                if (ip != null) {
                    remember(ip)
                    applyCountry(ip)
                }
            }
        }
        refresh()
    }

    private fun remember(country: DetectedCountry) {
        prefs.edit().putString(KEY_LAST_COUNTRY, country.code).apply()
    }

    private fun applyCountry(country: DetectedCountry) {
        _state.update {
            if (it.baseChosenManually) it.copy(country = country)
            else it.copy(country = country, base = CountryDetector.currencyForCountry(country.code))
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val rates = repo.fetch()
                _state.update { it.copy(rates = rates, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "환율을 불러오지 못했어요 (${e.message})") }
            }
        }
    }

    fun onAmountChange(text: String) {
        val cleaned = text.replace(",", "")
        if (cleaned.isEmpty() || cleaned.matches(Regex("""\d*\.?\d*"""))) {
            _state.update { it.copy(amountText = cleaned) }
        }
    }

    /** 입력창을 누르면 바로 새 금액을 칠 수 있게 비운다. */
    fun onAmountFocused() {
        _state.update { it.copy(amountText = "") }
    }

    /** 목록에서 다른 통화를 누르면 그 통화를 기준으로 바꾸고, 금액도 환산해서 이어간다. */
    fun selectBase(currency: Currency) {
        val s = _state.value
        if (currency == s.base) return
        val converted = s.amountText.toDoubleOrNull()
            ?.let { s.rates?.latest?.convert(it, s.base, currency) }
        _state.update {
            it.copy(
                base = currency,
                baseChosenManually = true,
                amountText = converted?.let { v -> Formatter.plain(v, currency) } ?: it.amountText,
            )
        }
    }

    /** 직접 고른 통화를 취소하고 거주 국가 기준으로 되돌린다. */
    fun resetToAutoBase() {
        _state.update {
            it.copy(base = CountryDetector.currencyForCountry(it.country.code), baseChosenManually = false)
        }
    }

    private companion object {
        const val KEY_LAST_COUNTRY = "last_country"
    }
}
