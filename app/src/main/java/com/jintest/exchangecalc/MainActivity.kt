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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                BaseHeader(
                    state = state,
                    onAmountChange = vm::onAmountChange,
                    onResetAuto = vm::resetToAutoBase,
                )
            }
            items(Currency.entries.filter { it != state.base }, key = { it.code }) { c ->
                val converted = state.rates?.convert(amount, state.base, c)
                CurrencyRow(c, converted, onClick = { vm.selectBase(c) })
            }
            item { Footer(state) }
        }
    }
}

@Composable
private fun BaseHeader(state: UiState, onAmountChange: (String) -> Unit, onResetAuto: () -> Unit) {
    val base = state.base
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${base.flag}  ${base.code} · ${base.koreanName}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.baseChosenManually) "직접 선택" else "거주국 자동 (${state.country.ifEmpty { "?" }})",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(base.code) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (state.baseChosenManually) {
                TextButton(onClick = onResetAuto) { Text("거주 국가 통화로 되돌리기") }
            }
        }
    }
    Text(
        "다른 통화를 누르면 그 통화가 기준이 돼요",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
private fun CurrencyRow(currency: Currency, value: Double?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(currency.flag, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(currency.code, fontWeight = FontWeight.Bold)
                Text(currency.koreanName, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                value?.let { Formatter.display(it, currency) } ?: "—",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun Footer(state: UiState) {
    Column(Modifier.padding(top = 8.dp)) {
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        val updated = state.rates?.updatedAt?.takeIf { it > 0 }?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(it * 1000))
        }
        Text(
            if (updated != null) "환율 기준 시각: $updated (출처: open.er-api.com)" else "환율 데이터 없음 — 인터넷 연결 후 새로고침해 주세요",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
