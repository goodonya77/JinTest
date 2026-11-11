package com.jintest.exchangecalc

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jintest.exchangecalc.widget.ExchangeWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class UiState(
    /** 거주국 통화. 처음 기준 통화이자 카드 목록 맨 앞, 위젯의 기준. */
    val home: Currency = Currencies.USD,
    /** 입력 금액의 통화. 처음엔 거주국 통화, 카드를 누르면 그 통화로 바뀐다. */
    val base: Currency = Currencies.USD,
    val country: DetectedCountry = DetectedCountry("", CountrySource.NONE),
    /** 사용자가 정한 통화 순서 (거주국 통화는 이 순서와 무관하게 맨 앞). */
    val list: List<Currency> = emptyList(),
    val amountText: String = "",
    val rates: RateSet? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val baseChosenManually: Boolean get() = base != home

    /** 화면에 보여줄 카드 순서: 거주국 통화(기준이 아닐 때) → 사용자 목록. 입력 중인 기준 통화는 빠진다. */
    val cards: List<Currency>
        get() = buildList {
            if (base != home) add(home)
            addAll(list.filter { it != home && it != base })
        }

    /** ＋로 추가할 수 있는 통화 */
    val addable: List<Currency>
        get() = Currencies.ALL.filter { it != home && it !in list }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RateRepository(app)
    private val settings = Settings(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** 사용자가 금액을 직접 건드렸는지. 아니면 거주국 통화에 맞는 적당한 금액을 자동으로 넣는다. */
    private var amountTouched = false

    init {
        val network = CountryDetector.fromNetwork(app)
        val initial = network
            ?: settings.lastCountry?.let { DetectedCountry(it, CountrySource.SAVED) }
            ?: CountryDetector.fromSimOrLocale(app)
        val home = CountryDetector.currencyForCountry(initial.code)
        val rates = repo.loadCached()
        _state.value = UiState(
            home = home,
            base = home,
            country = initial,
            list = settings.currencyList,
            amountText = defaultAmount(home, rates),
            rates = rates,
        )
        if (network != null) {
            settings.lastCountry = network.code
        } else {
            // 통신망 정보가 없으면 인터넷 위치로 확인 (해외 로밍 + Wi-Fi 사용 시)
            viewModelScope.launch {
                val ip = CountryDetector.fromIp() ?: return@launch
                settings.lastCountry = ip.code
                applyCountry(ip)
            }
        }
        refresh()
    }

    private fun applyCountry(country: DetectedCountry) {
        val newHome = CountryDetector.currencyForCountry(country.code)
        _state.update {
            it.copy(
                country = country,
                home = newHome,
                base = if (it.baseChosenManually) it.base else newHome,
                amountText = if (amountTouched || it.baseChosenManually) it.amountText else defaultAmount(newHome, it.rates),
            )
        }
        updateWidget()
    }

    /** 미화 100달러쯤 되는 딱 떨어지는 금액 (VND 1,000,000 / KRW 100,000 / JPY 10,000 / USD 100). */
    private fun defaultAmount(home: Currency, rates: RateSet?): String {
        val hundredUsd = rates?.latest?.convert(100.0, Currencies.USD, home) ?: return "100"
        return Formatter.plain(10.0.pow(log10(hundredUsd).roundToInt()), home)
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val rates = repo.fetch()
                _state.update {
                    it.copy(
                        rates = rates,
                        loading = false,
                        amountText = if (amountTouched || it.baseChosenManually) it.amountText else defaultAmount(it.home, rates),
                    )
                }
                updateWidget()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "환율을 불러오지 못했어요 (${e.message})") }
            }
        }
    }

    fun onAmountChange(text: String) {
        val cleaned = text.replace(",", "")
        if (cleaned.isEmpty() || cleaned.matches(Regex("""\d*\.?\d*"""))) {
            amountTouched = true
            _state.update { it.copy(amountText = cleaned) }
        }
    }

    /** 입력창을 누르면 바로 새 금액을 칠 수 있게 비운다. */
    fun onAmountFocused() {
        amountTouched = true
        _state.update { it.copy(amountText = "") }
    }

    /** 카드를 누르면 그 통화를 기준으로 바꾸고, 금액도 환산해서 이어간다. */
    fun selectBase(currency: Currency) {
        val s = _state.value
        if (currency == s.base) return
        val converted = s.amountText.toDoubleOrNull()
            ?.let { s.rates?.latest?.convert(it, s.base, currency) }
        amountTouched = true
        _state.update {
            it.copy(
                base = currency,
                amountText = converted?.let { v -> Formatter.plain(v, currency) } ?: it.amountText,
            )
        }
    }

    /** 직접 고른 통화를 취소하고 거주 국가 통화로 되돌린다. */
    fun resetToAutoBase() = selectBase(_state.value.home)

    /** 길게 눌러 끄는 중: from 통화를 to 통화 자리로 옮긴다. */
    fun moveCurrency(fromCode: String, toCode: String) {
        _state.update { s ->
            val list = s.list.toMutableList()
            val from = list.indexOfFirst { it.code == fromCode }
            val to = list.indexOfFirst { it.code == toCode }
            if (from < 0 || to < 0 || from == to) return@update s
            list.add(to, list.removeAt(from))
            s.copy(list = list)
        }
    }

    /** 끌기가 끝나면 순서를 저장하고 위젯에도 반영한다. */
    fun onReorderFinished() {
        settings.currencyList = _state.value.list
        updateWidget()
    }

    fun removeCurrency(code: String) {
        _state.update { s -> s.copy(list = s.list.filter { it.code != code }) }
        onReorderFinished()
    }

    fun addCurrency(currency: Currency) {
        _state.update { s -> if (currency in s.list) s else s.copy(list = s.list + currency) }
        onReorderFinished()
    }

    private fun updateWidget() {
        viewModelScope.launch { runCatching { ExchangeWidget().updateAll(getApplication()) } }
    }
}
