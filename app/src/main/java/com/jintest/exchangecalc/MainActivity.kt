package com.jintest.exchangecalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                ExchangeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val amount = state.amountText.toDoubleOrNull() ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("환율계산기") },
                actions = {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                BaseHeader(
                    state = state,
                    onAmountChange = vm::onAmountChange,
                    onAmountFocused = vm::onAmountFocused,
                    onResetAuto = vm::resetToAutoBase,
                )
            }
            items(Currency.entries.filter { it != state.base }, key = { it.code }) { c ->
                CurrencyRow(c, state.base, amount, state.rates, onClick = { vm.selectBase(c) })
            }
            item { Footer(state) }
        }
    }
}

@Composable
private fun BaseHeader(
    state: UiState,
    onAmountChange: (String) -> Unit,
    onAmountFocused: () -> Unit,
    onResetAuto: () -> Unit,
) {
    val base = state.base
    val focusManager = LocalFocusManager.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${base.flag}  ${base.code} · ${base.koreanName}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.baseChosenManually) "직접 선택"
                    else "자동: ${state.country.code.ifEmpty { "?" }} (${state.country.source.label})",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                singleLine = true,
                placeholder = { Text("0") },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                suffix = { Text(base.code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .onFocusChanged { if (it.isFocused) onAmountFocused() },
            )
            if (state.baseChosenManually) {
                TextButton(onClick = onResetAuto) { Text("거주 국가 통화로 되돌리기") }
            }
        }
    }
    Text(
        "다른 통화를 누르면 그 통화가 기준이 돼요 · ▲빨강: 기준통화 대비 강세, ▼파랑: 약세",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

/**
 * 한 통화의 줄 (2줄 구성)
 *  1줄: 국기 · 코드 · 이름 ........ 환산 금액
 *  2줄: 1 USD = 1,380.50 ..... 전일 ▲5.20  작년말 ▼12.30
 * 환율은 "이 통화 1단위가 기준통화로 얼마인지". 오르면 이 통화가 강해진 것(빨강), 내리면 약해진 것(파랑).
 */
@Composable
private fun CurrencyRow(currency: Currency, base: Currency, amount: Double, rates: RateSet?, onClick: () -> Unit) {
    val converted = rates?.latest?.convert(amount, base, currency)
    val rate = rates?.latest?.convert(1.0, currency, base)
    val unit = rate?.let { Formatter.quoteUnit(it) } ?: 1
    val prev = rates?.prevDay?.convert(1.0, currency, base)
    val yearEnd = rates?.yearEnd?.convert(1.0, currency, base)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.flag, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(currency.code, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    currency.koreanName,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    converted?.let { Formatter.display(it, currency) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rate?.let { "$unit ${currency.code} = ${Formatter.rate(it * unit)}" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ChangeText("전일", rate, prev, unit)
                Spacer(Modifier.width(10.dp))
                ChangeText("작년말", rate, yearEnd, unit)
            }
        }
    }
}

@Composable
private fun ChangeText(label: String, now: Double?, before: Double?, unit: Int) {
    val dark = isSystemInDarkTheme()
    val red = if (dark) Color(0xFFEF5350) else Color(0xFFD32F2F)
    val blue = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val diff = if (now != null && before != null) (now - before) * unit else null
    val (value, color) = when {
        diff == null -> "—" to muted
        abs(diff) < 0.005 -> "0.00" to muted
        diff > 0 -> "▲${Formatter.rate(diff)}" to red
        else -> "▼${Formatter.rate(-diff)}" to blue
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append("$label ") }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(value) }
        },
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

@Composable
private fun Footer(state: UiState) {
    Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        val r = state.rates
        Text(
            if (r != null) {
                "기준일 ${r.latest.date} · 전일 ${r.prevDay?.date ?: "없음"} · 작년말 ${r.yearEnd?.date ?: "없음"}\n출처: fawazahmed0/currency-api"
            } else {
                "환율 데이터 없음 — 인터넷 연결 후 새로고침해 주세요"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
