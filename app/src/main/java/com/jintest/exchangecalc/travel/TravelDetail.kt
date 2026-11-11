package com.jintest.exchangecalc.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jintest.exchangecalc.Formatter
import com.jintest.exchangecalc.Rates
import com.jintest.exchangecalc.Trend
import com.jintest.exchangecalc.flagOf
import com.jintest.exchangecalc.trendColor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 나라 상세: 여행지(국가 전체/도시)와 비교 기준(거주국 전체/도시)을 고르고,
 * 내 돈으로 살 수 있는 것 · 전체 물가 수준 · 품목별 가격(현지가 → 거주국 통화 → 비교)을 보여준다.
 */
@Composable
fun TravelDetail(
    vm: TravelViewModel, ui: TravelUi, data: TravelData, levels: LevelTable, home: Home, rates: Rates?,
    iso2: String, modifier: Modifier = Modifier,
) {
    val country = data.country(iso2) ?: return
    val homeCountry = data.country(home.iso2)
    val destCities = data.cities[iso2].orEmpty()
    val homeCities = data.cities[home.iso2].orEmpty()
    val destName = ui.destCity ?: country.name
    val homeName = ui.homeCity ?: (homeCountry?.name ?: home.iso2)
    fun toHome(v: ItemValue) = rates?.convert(v.value, v.currency, home.currency)

    LaunchedEffect(iso2) { vm.loadWeather(iso2, destCities) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeDetail) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                Text("${flagOf(iso2)} ${country.name} 여행 물가", style = MaterialTheme.typography.titleLarge)
            }
        }
        item(key = "route") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${flagOf(home.iso2)} ${homeCountry?.name ?: ""} → ${flagOf(iso2)} ${country.name}", fontWeight = FontWeight.Bold)
                    rateLine(rates, country.currency, home.currency)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    PlaceChips("여행지", destCities, ui.destCity, vm::selectDestCity)
                    if (homeCities.isNotEmpty()) PlaceChips("비교", homeCities, ui.homeCity, vm::selectHomeCity)
                    destCities.firstOrNull { it.name == ui.destCity }?.let { city ->
                        val w = vm.weather[vm.weatherKey(iso2, city)]
                        val s = city.seasonNow()
                        Text(
                            "${city.name} 지금: " + (w?.let { "${it.sky.icon} ${it.sky.label} ${it.temperature.toInt()}° (${it.feel.label})" } ?: "날씨 불러오는 중…") +
                                " · ${s.icon} 이번 달 ${s.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        item(key = "budget") { BudgetCard(vm, ui, data, home, rates, iso2, destName, homeName) }
        item(key = "level") { LevelCard(data, levels, home, rates, country, destName, ui.destCity != null) }
        item(key = "itemsTitle") {
            Text(
                "🧾 품목별 가격 · 현지가 → ${home.currency} 환산 → $homeName 과 비교",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
        items(ITEMS, key = { it.key }) { item ->
            val dest = data.itemValue(iso2, item, ui.destCity)
            val cmp = if (home.iso2 == iso2) null else data.itemValue(home.iso2, item, ui.homeCity)
            ItemCard(item, dest, cmp, dest?.let(::toHome), cmp?.let(::toHome), home.currency, ui.destCity, ui.homeCity)
        }
        item(key = "foot") {
            Text(
                "※ 가격은 공개 자료와 검색으로 모은 1차 조사값으로 실제와 다를 수 있어요. 도시·매장마다 차이가 있어요.\n" +
                    "물가 수준: 세계은행·IMF · 빅맥: The Economist · 날씨: MET Norway (CC BY 4.0)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/** "1,000 VND = 51.71 KRW · 10,000 KRW = 193,375 VND" */
private fun rateLine(rates: Rates?, dest: String, home: String): String? {
    if (rates == null || dest == home) return null
    val r = rates.convert(1.0, dest, home) ?: return null
    val unit = Formatter.quoteUnit(r)
    // 거꾸로 보는 금액: 거주국 통화로 딱 떨어지는 금액 (예: 10,000 KRW)
    val back = 10.0.pow((log10(r * unit).roundToInt() + 2).coerceAtLeast(0))
    val backValue = rates.convert(back, home, dest) ?: return null
    return "${Formatter.number(unit.toDouble(), 0)} $dest = ${Formatter.number(r * unit, 2)} $home · " +
        "${Formatter.number(back, 0)} $home = ${money(backValue, dest)}"
}

@Composable
private fun PlaceChips(label: String, cities: List<City>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("국가 전체") })
        cities.forEach { c ->
            FilterChip(
                selected = selected == c.name,
                onClick = { onSelect(c.name) },
                label = { Text((if (c.type == CityType.TOURIST) "🏝️ " else "") + c.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetCard(
    vm: TravelViewModel, ui: TravelUi, data: TravelData, home: Home, rates: Rates?,
    iso2: String, destName: String, homeName: String,
) {
    val country = data.country(iso2) ?: return
    // 기본 예산: 미화 100달러쯤 되는 딱 떨어지는 금액
    val default = rates?.convert(100.0, "USD", home.currency)?.let { 10.0.pow(log10(it).roundToInt()) } ?: 100.0
    val budget = ui.budgetText.toDoubleOrNull() ?: default
    val inDest = rates?.convert(budget, home.currency, country.currency)
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("💰 내 돈으로 살 수 있는 것", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = ui.budgetText,
                onValueChange = vm::onBudgetChange,
                placeholder = { Text(Formatter.number(default, 0)) },
                suffix = { Text(home.currency) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            if (inDest != null && country.currency != home.currency) {
                Text("= ${money(inDest, country.currency)} 이면 ${destName}에서…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BUY_ITEMS.mapNotNull { key -> ITEMS.firstOrNull { it.key == key } }.forEach { item ->
                    val dest = data.itemValue(iso2, item, ui.destCity)
                    val destHome = dest?.let { rates?.convert(it.value, it.currency, home.currency) }
                    val cmp = if (home.iso2 == iso2) null else data.itemValue(home.iso2, item, ui.homeCity)
                    val cmpHome = cmp?.let { rates?.convert(it.value, it.currency, home.currency) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.width(76.dp).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(item.icon, fontSize = 22.sp)
                            Text(item.label.substringBefore(" ("), fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
                            Text(
                                destHome?.takeIf { it > 0 }?.let { "${floor(budget / it).toInt()}개" } ?: "없음",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            cmpHome?.takeIf { it > 0 }?.let {
                                Text("$homeName ${floor(budget / it).toInt()}개", fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelCard(
    data: TravelData, levels: LevelTable, home: Home, rates: Rates?, country: Country, destName: String, isCity: Boolean,
) {
    val level = levels.of(country.iso2)
    val pl = data.priceLevel[country.iso3]
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("📊 전체 물가 수준", fontWeight = FontWeight.Bold)
            Text(
                if (level == null) "물가 수준 자료 없음"
                else "${country.name} 물가는 ${levels.baseName}의 약 ${level.toInt()}%" + if (isCity) " (국가 기준)" else "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            LevelBar(level, Modifier.padding(top = 6.dp))
            pl?.let {
                Text(
                    "출처: ${if (it.source == "IMF") "IMF 세계경제전망" else "세계은행"} ${it.year}년 구매력평가 기준",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val bm = data.bigMac[country.iso3]
            val homeIso3 = data.country(home.iso2)?.iso3
            val homeBm = homeIso3?.let { data.bigMac[it] }
            if (bm != null && rates != null) {
                val destPrice = rates.convert(bm.localPrice, bm.currency, home.currency)
                val homePrice = homeBm?.let { rates.convert(it.localPrice, it.currency, home.currency) }
                Text(
                    "🍔 빅맥 지수 (${bm.date.take(7)}): ${money(bm.localPrice, bm.currency)}" +
                        (destPrice?.let { " = ${money(it, home.currency)}" } ?: "") +
                        (if (homePrice != null && homeIso3 != country.iso3) " · ${levels.baseName} ${money(homePrice, home.currency)}" else "") +
                        (if (bm.euroAverage) " (유로존 평균)" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (isCity) {
                Text(
                    "$destName 의 도시별 종합 물가 지수는 아직 없어 국가 값으로 보여드려요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: Item, dest: ItemValue?, cmp: ItemValue?, destHome: Double?, cmpHome: Double?, homeCurrency: String,
    destCity: String?, homeCity: String?,
) {
    val compared = destHome != null && cmpHome != null && cmpHome > 0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.icon, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label, fontWeight = FontWeight.Bold)
                    Text(
                        item.source + placeTag(item, dest, destCity),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (compared) {
                    val pct = (destHome!! / cmpHome!! - 1) * 100
                    val cheaper = pct < 0
                    Pill(
                        "${if (cheaper) "▼" else "▲"} ${abs(pct).roundToInt()}% ${if (cheaper) "저렴" else "비쌈"}",
                        trendColor(if (cheaper) Trend.DOWN else Trend.UP),
                    )
                } else {
                    Pill("비교 불가", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (dest == null) {
                Text("없음", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 30.dp, top = 6.dp))
            } else {
                Row(Modifier.padding(start = 30.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(money(dest.value, dest.currency), style = MaterialTheme.typography.bodyMedium)
                    if (destHome != null && dest.currency != homeCurrency) {
                        Text("  →  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(destHome, homeCurrency), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            Text(
                "비교: " + (cmpHome?.let { money(it, homeCurrency) } ?: "없음") + (cmp?.place?.takeIf { homeCity == null }?.let { " ($it 기준)" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp),
            )
            if (compared) {
                val max = maxOf(destHome!!, cmpHome!!)
                BarPair((destHome / max).toFloat(), (cmpHome / max).toFloat(), destHome < cmpHome)
            }
        }
    }
}

private fun placeTag(item: Item, dest: ItemValue?, destCity: String?): String = when {
    dest == null -> ""
    !item.city && destCity != null -> " · 국가 기준"
    item.city && dest.place != null && destCity == null -> " · ${dest.place} 기준"
    item.city -> " · 도시 기준"
    else -> ""
}

@Composable
private fun BarPair(dest: Float, cmp: Float, cheaper: Boolean) {
    val destColor = trendColor(if (cheaper) Trend.DOWN else Trend.UP)
    Column(Modifier.padding(start = 30.dp, top = 6.dp)) {
        listOf(dest to destColor, cmp to MaterialTheme.colorScheme.outline).forEach { (w, color) ->
            Box(
                Modifier.padding(vertical = 2.dp).fillMaxWidth(w.coerceIn(0.02f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(color),
            )
        }
    }
}
