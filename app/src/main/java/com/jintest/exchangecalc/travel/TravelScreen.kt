package com.jintest.exchangecalc.travel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jintest.exchangecalc.Currencies
import com.jintest.exchangecalc.Formatter
import com.jintest.exchangecalc.Rates
import com.jintest.exchangecalc.Trend
import com.jintest.exchangecalc.flagOf
import com.jintest.exchangecalc.trendColor
import kotlin.math.abs

/** 거주국 정보 (환율 화면에서 넘겨받음) */
data class Home(val iso2: String, val currency: String)

/** 금액 + 통화 코드 (예: "76,000 VND"). 통화별 소수점 자리수를 따른다 */
fun money(value: Double, currency: String): String {
    val decimals = Currencies.of(currency)?.decimals ?: if (abs(value) >= 100) 0 else 2
    return Formatter.number(value, decimals) + " " + currency
}

/** 거주국 = 100일 때 각 나라 물가 수준. 거주국 값이 없으면 미국 기준 */
class LevelTable(data: TravelData, home: Home) {
    private val homeCountry = data.country(home.iso2)?.takeIf { data.priceLevel[it.iso3] != null }
    val baseName: String = homeCountry?.name ?: "미국"
    private val base: Double = data.priceLevel[homeCountry?.iso3 ?: "USA"]?.value ?: 1.0
    private val levels = data.countries.associate { c -> c.iso2 to data.priceLevel[c.iso3]?.let { it.value / base * 100 } }

    fun of(iso2: String): Double? = levels[iso2]
    fun average(countries: List<Country>): Double? =
        countries.mapNotNull { of(it.iso2) }.takeIf { it.isNotEmpty() }?.average()
}

@Composable
fun TravelScreen(vm: TravelViewModel, home: Home, rates: Rates?, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data = ui.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (ui.error != null) Text(ui.error!!) else CircularProgressIndicator()
        }
        return
    }
    // 처음 열 때 대륙·지역 평균을 한 번에 계산해 둔다
    val levels = remember(data, home) { LevelTable(data, home) }

    val detail = ui.detail
    if (detail != null) {
        BackHandler { vm.closeDetail() }
        TravelDetail(vm, ui, data, levels, home, rates, detail, modifier)
        return
    }

    val continent = data.continents.firstOrNull { it.name == ui.continent } ?: data.continents.first()
    val region = continent.regions.firstOrNull { it.name == ui.region } ?: continent.regions.first()
    val countries = region.countries.sortedWith(compareBy(nullsLast()) { levels.of(it.iso2) })

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "picker") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("어디로 여행 가세요?", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${flagOf(home.iso2)} ${levels.baseName} = 100", style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.continents.forEach { c ->
                            LevelChip("${c.icon} ${c.name}", levels.average(c.regions.flatMap { it.countries }), c == continent) {
                                vm.selectContinent(c.name)
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        continent.regions.forEach { r ->
                            LevelChip(r.name, levels.average(r.countries), r == region) { vm.selectRegion(r.name) }
                        }
                    }
                    levels.average(region.countries)?.let {
                        Text(
                            "${region.name} 평균 물가: ${levels.baseName}의 약 ${it.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        item(key = "title") {
            Text(
                "${region.name} 국가별 물가 · 저렴한 순",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
        items(countries, key = { it.iso2 }) { c ->
            CountryRow(vm, ui, data, levels, home, rates, c)
        }
        item(key = "legend") {
            Text(
                "🔴 성수기 🟡 보통 🟢 비수기 (이번 달) · 물가: 세계은행·IMF 구매력 기준\n" +
                    "날씨: MET Norway (CC BY 4.0) · 가격 자료는 1차 조사값으로 실제와 다를 수 있어요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun LevelChip(label: String, level: Double?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label)
            if (level != null) {
                Text(
                    " ${level.toInt()}",
                    color = if (selected) Color.Unspecified else trendColor(if (level < 100) Trend.DOWN else Trend.UP),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountryRow(
    vm: TravelViewModel, ui: TravelUi, data: TravelData, levels: LevelTable, home: Home, rates: Rates?, c: Country,
) {
    val expanded = ui.expanded == c.iso2
    val cities = data.cities[c.iso2].orEmpty()
    val level = levels.of(c.iso2)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { vm.toggleCountry(c.iso2) },
        border = if (expanded) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flagOf(c.iso2), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                countrySeason(cities)?.let { Pill("${it.icon} ${it.label}", MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(6.dp))
                if (level != null) {
                    val cheaper = level < 100
                    Pill(
                        "${if (cheaper) "▼" else "▲"} ${abs(100 - level).toInt()}% ${if (cheaper) "저렴" else "비쌈"}",
                        trendColor(if (cheaper) Trend.DOWN else Trend.UP),
                    )
                }
                Text(if (expanded) " ▲" else " ▼", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            LevelBar(level, Modifier.padding(start = 30.dp, top = 6.dp))
            val bigMac = data.bigMac[c.iso3]?.let { b -> rates?.convert(b.localPrice, b.currency, home.currency) }
            Text(
                if (level == null) "물가 정보 없음" else "🍔 빅맥 " + (bigMac?.let { money(it, home.currency) } ?: "없음"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp),
            )

            if (expanded) {
                LaunchedEffect(c.iso2) { vm.loadWeather(c.iso2, cities) }
                Column(Modifier.padding(top = 10.dp)) {
                    FilterChip(selected = true, onClick = { vm.openDetail(c.iso2, null) }, label = { Text("🗺️ 국가 전체") })
                    listOf(CityType.MAJOR to "🏙️ 대표 도시", CityType.TOURIST to "🏝️ 작지만 유명한 관광 도시·섬").forEach { (type, title) ->
                        val list = cities.filter { it.type == type }
                        if (list.isNotEmpty()) {
                            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { city ->
                                    CityCard(city, vm.weather[vm.weatherKey(c.iso2, city)], type == CityType.TOURIST) {
                                        vm.openDetail(c.iso2, city.name)
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = { vm.openDetail(c.iso2, null) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text("${c.name} 물가 자세히 보기 →")
                    }
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** 물가 막대: 0~180 범위, 100(거주국) 위치에 기준선 */
@Composable
fun LevelBar(level: Double?, modifier: Modifier = Modifier) {
    val color = trendColor(if ((level ?: 100.0) < 100) Trend.DOWN else Trend.UP)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (level != null) {
                Box(Modifier.fillMaxWidth((level / 180).toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            }
            Box(Modifier.fillMaxWidth(100f / 180f).height(8.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
        Text(
            level?.toInt()?.toString() ?: "—",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp).padding(start = 6.dp),
        )
    }
}

@Composable
fun CityCard(city: City, weather: Weather?, tourist: Boolean, onClick: () -> Unit) {
    val season = city.seasonNow()
    Column(
        Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (tourist) Color(0x22F3B13C) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (tourist) Color(0x66F3B13C) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
    ) {
        Text(city.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            weather?.let { "${it.sky.icon} ${it.sky.label} ${it.temperature.toInt()}° ${it.feel.label}" } ?: "날씨 불러오는 중…",
            fontSize = 11.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${season.icon} ${season.label}", fontSize = 11.sp)
    }
}
