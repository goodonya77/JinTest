package com.jintest.exchangecalc

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

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

/**
 * 세로 화면: 위 입력 칸 + 아래 2열 카드 (함께 스크롤)
 * 가로 화면/태블릿: 왼쪽에 입력 칸 고정 + 오른쪽 3열 이상 카드
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val config = LocalConfiguration.current
    val wide = config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 600
    val gridState = rememberLazyGridState()
    val reorder = remember(gridState) { ReorderState(gridState) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val dragging = reorder.draggingKey != null

    Scaffold(
        topBar = {
            if (!wide) {
                TopAppBar(
                    title = { Text("환율계산기") },
                    actions = { RefreshButton(state.loading, vm::refresh) },
                )
            }
        },
    ) { padding ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("환율계산기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        RefreshButton(state.loading, vm::refresh)
                    }
                    BaseHeader(state, vm)
                    Hint(dragging)
                    Footer(state)
                }
                Spacer(Modifier.width(12.dp))
                CardGrid(
                    state, vm, reorder,
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    onAdd = { showAdd = true },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            CardGrid(
                state, vm, reorder,
                columns = GridCells.Fixed(2),
                onAdd = { showAdd = true },
                header = {
                    Column(Modifier.padding(top = 4.dp)) {
                        BaseHeader(state, vm)
                        Hint(dragging)
                    }
                },
                footer = { Footer(state) },
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            )
        }
    }

    if (showAdd) {
        AddCurrencySheet(
            options = state.addable,
            onPick = { vm.addCurrency(it); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    if (loading) {
        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = onClick) { Icon(Icons.Filled.Refresh, contentDescription = "새로고침") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardGrid(
    state: UiState,
    vm: MainViewModel,
    reorder: ReorderState,
    columns: GridCells,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val trashHeightPx = with(LocalDensity.current) { (TrashHeight + 24.dp).toPx() }
    val amount = state.amountText.toDoubleOrNull() ?: 0.0

    Box(modifier.onSizeChanged { reorder.trashTop = it.height - trashHeightPx }) {
        LazyVerticalGrid(
            columns = columns,
            state = reorder.gridState,
            // 끄는 중에는 목록이 같이 스크롤되지 않게
            userScrollEnabled = reorder.draggingKey == null,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(reorder) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (reorder.start(it)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            if (reorder.draggingKey != null) change.consume()
                            reorder.drag(dragAmount, vm::moveCurrency)
                        },
                        onDragEnd = {
                            if (reorder.draggingKey != null) {
                                reorder.end(vm::removeCurrency)
                                vm.onReorderFinished()
                            }
                        },
                        onDragCancel = { reorder.cancel() },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }

            items(state.cards, key = { cardKey(it, state) }) { c ->
                val key = cardKey(c, state)
                val isHome = c == state.home
                val lifted = reorder.draggingKey == key
                val placement = if (lifted) {
                    Modifier.zIndex(1f).graphicsLayer {
                        val t = reorder.translation(key)
                        translationX = t.x
                        translationY = t.y
                        scaleX = 1.05f
                        scaleY = 1.05f
                        rotationZ = 2f
                    }
                } else {
                    Modifier.animateItem()
                }
                CurrencyCard(
                    currency = c,
                    isHome = isHome,
                    // 거주국 카드는 거주국 통화 자신과 비교할 수 없으니 지금 기준 통화와 비교
                    against = if (isHome) state.base else state.home,
                    converted = state.rates?.latest?.convert(amount, state.base, c),
                    quote = state.rates?.quote(c, if (isHome) state.base else state.home),
                    lifted = lifted,
                    modifier = placement
                        .clip(CardDefaults.shape)
                        // 길게 누르기는 그리드의 끌기 동작이 처리하므로 여기서는 아무것도 안 함 (클릭만 막음)
                        .combinedClickable(onClick = { vm.selectBase(c) }, onLongClick = {}),
                )
            }

            item(key = "add") { AddCard(onClick = onAdd, modifier = Modifier.animateItem()) }

            if (footer != null) item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { footer() }
        }

        if (reorder.draggingKey != null) {
            TrashZone(active = reorder.overTrash, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

private fun cardKey(c: Currency, state: UiState) =
    if (c == state.home) "home:${c.code}" else ReorderState.MOVABLE + c.code

private val TrashHeight = 60.dp

@Composable
private fun TrashZone(active: Boolean, modifier: Modifier = Modifier) {
    val red = Color(0xFFD32F2F)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) red.copy(alpha = 0.85f) else red.copy(alpha = 0.12f))
            .border(2.dp, red, RoundedCornerShape(14.dp))
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (active) "🗑 놓으면 삭제돼요" else "🗑 여기로 끌어 놓으면 목록에서 삭제",
            color = if (active) Color.White else red,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BaseHeader(state: UiState, vm: MainViewModel) {
    val base = state.base
    val focusManager = LocalFocusManager.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${base.flag}  ${base.code} · ${base.koreanName}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (state.baseChosenManually) "직접 선택"
                    else "자동: ${state.country.code.ifEmpty { "?" }} (${state.country.source.label})",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = vm::onAmountChange,
                singleLine = true,
                placeholder = { Text("0") },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                suffix = { Text(base.code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .onFocusChanged { if (it.isFocused) vm.onAmountFocused() },
            )
            if (state.baseChosenManually) {
                TextButton(onClick = vm::resetToAutoBase) { Text("↺ 거주 국가 통화로 되돌리기") }
            }
        }
    }
}

@Composable
private fun Hint(dragging: Boolean) {
    if (dragging) {
        Text(
            "✋ 끌어서 원하는 위치에 놓으세요 · 거주국 통화는 맨 앞 고정",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    } else {
        Text(
            buildAnnotatedString {
                append("카드를 누르면 기준 통화 변경 · ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("길게 누르면 순서 변경") }
                append(" · ")
                withStyle(SpanStyle(color = trendColor(Trend.UP))) { append("▲강세 ") }
                withStyle(SpanStyle(color = trendColor(Trend.DOWN))) { append("▼약세") }
                append(" (거주국 통화 대비)")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Footer(state: UiState) {
    Column(Modifier.padding(top = 8.dp, start = 4.dp, bottom = 16.dp)) {
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
