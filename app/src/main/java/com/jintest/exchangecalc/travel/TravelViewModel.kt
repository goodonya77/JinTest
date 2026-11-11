package com.jintest.exchangecalc.travel

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TravelUi(
    val data: TravelData? = null,
    val continent: String = "아시아",
    val region: String? = null,          // null이면 대륙 첫 지역
    val expanded: String? = null,        // 펼친 나라 iso2
    val detail: String? = null,          // 상세 화면 나라 iso2
    val destCity: String? = null,        // null = 국가 전체
    val homeCity: String? = null,        // null = 국가 전체
    val budgetText: String = "",
    val error: String? = null,
)

class TravelViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(TravelUi())
    val ui: StateFlow<TravelUi> = _ui

    /** 도시 이름(나라 코드 포함) → 현재 날씨. 화면이 바로 다시 그려지도록 상태 맵 */
    val weather = mutableStateMapOf<String, Weather>()

    init {
        viewModelScope.launch {
            // 앱 안의 데이터로 바로 화면을 그리고, 최신 데이터는 뒤에서 받아 다음 실행부터 쓴다
            runCatching { TravelRepository.load(getApplication()) }
                .onSuccess { d -> _ui.update { it.copy(data = d) } }
                .onFailure { e -> _ui.update { it.copy(error = "여행 데이터를 읽지 못했어요 (${e.message})") } }
            runCatching { TravelRepository.refresh(getApplication()) }
        }
    }

    fun selectContinent(name: String) = _ui.update { it.copy(continent = name, region = null, expanded = null) }
    fun selectRegion(name: String) = _ui.update { it.copy(region = name, expanded = null) }
    fun toggleCountry(iso2: String) = _ui.update { it.copy(expanded = if (it.expanded == iso2) null else iso2) }

    fun openDetail(iso2: String, city: String?) =
        _ui.update { it.copy(detail = iso2, destCity = city, homeCity = null, budgetText = "") }

    fun closeDetail() = _ui.update { it.copy(detail = null) }
    fun selectDestCity(name: String?) = _ui.update { it.copy(destCity = name) }
    fun selectHomeCity(name: String?) = _ui.update { it.copy(homeCity = name) }

    fun onBudgetChange(text: String) {
        val cleaned = text.replace(",", "")
        if (cleaned.isEmpty() || cleaned.matches(Regex("""\d*\.?\d*"""))) _ui.update { it.copy(budgetText = cleaned) }
    }

    fun weatherKey(iso2: String, city: City) = "$iso2/${city.name}"

    fun loadWeather(iso2: String, cities: List<City>) {
        cities.filter { weatherKey(iso2, it) !in weather }.forEach { city ->
            viewModelScope.launch {
                WeatherService.current(city.lat, city.lon)?.let { weather[weatherKey(iso2, city)] = it }
            }
        }
    }
}
