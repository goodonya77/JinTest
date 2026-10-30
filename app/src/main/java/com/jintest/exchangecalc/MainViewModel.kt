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
    val country: String = "",
    /** 사용자가 직접 기준 통화를 고른 경우 true (자동 설정 대신 그 선택을 유지). */
    val baseChosenManually: Boolean = false,
    val amountText: String = "1",
    val rates: Rates? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RateRepository(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val country = CountryDetector.detectCountry(app)
        val manual = Currency.fromCode(prefs.getString(KEY_MANUAL_BASE, null))
        _state.value = UiState(
            base = manual ?: CountryDetector.currencyForCountry(country),
            country = country,
            baseChosenManually = manual != null,
            rates = repo.loadCached(),
        )
        refresh()
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

    /** 목록에서 다른 통화를 누르면 그 통화를 기준으로 바꾸고, 금액도 환산해서 이어간다. */
    fun selectBase(currency: Currency) {
        val s = _state.value
        if (currency == s.base) return
        val converted = s.amountText.toDoubleOrNull()
            ?.let { s.rates?.convert(it, s.base, currency) }
        prefs.edit().putString(KEY_MANUAL_BASE, currency.code).apply()
        _state.update {
            it.copy(
                base = currency,
                baseChosenManually = true,
                amountText = converted?.let { v -> Formatter.plain(v, currency) } ?: it.amountText,
            )
        }
    }

    /** 수동 선택을 지우고 거주 국가 기준으로 되돌린다. */
    fun resetToAutoBase() {
        prefs.edit().remove(KEY_MANUAL_BASE).apply()
        _state.update {
            it.copy(base = CountryDetector.currencyForCountry(it.country), baseChosenManually = false)
        }
    }

    private companion object {
        const val KEY_MANUAL_BASE = "manual_base"
    }
}
