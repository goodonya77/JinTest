package com.jintest.exchangecalc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardMinHeight = 160.dp

/** 화면 크기에 따라 달라지는 카드 크기 */
data class CardLayout(val minHeight: Dp = CardMinHeight, val amountSize: TextUnit = 22.sp)

@Composable
fun trendColor(trend: Trend): Color {
    val dark = isSystemInDarkTheme()
    return when (trend) {
        Trend.UP -> if (dark) Color(0xFFEF5350) else Color(0xFFD32F2F)
        Trend.DOWN -> if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * 통화 카드
 *  🇺🇸 USD          (거주국이면 태그)
 *     미국 달러
 *                 38.71   ← 환산 금액
 *  ───────────────
 *  1 USD = 25,836 VND     ← 선택된(입력) 통화 기준 환율
 *  전일            ▲18
 *  작년말          ▼462
 */
@Composable
fun CurrencyCard(
    currency: Currency,
    isHome: Boolean,
    against: Currency,
    converted: Double?,
    quote: Quote?,
    lifted: Boolean,
    layout: CardLayout = CardLayout(),
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = layout.minHeight),
        elevation = CardDefaults.cardElevation(defaultElevation = if (lifted) 12.dp else 0.dp),
        colors = if (lifted) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.heightIn(min = layout.minHeight).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.flag, fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(currency.code, fontWeight = FontWeight.Bold)
                        if (isHome) {
                            Text(
                                "거주국",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        currency.koreanName,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 카드가 커지면 남는 공간은 금액 위에 둔다 (금액·환율·증감은 아래쪽에 모음)
            Spacer(Modifier.weight(1f))
            val amount = converted?.let { Formatter.display(it, currency) } ?: "—"
            Text(
                amount,
                fontSize = if (amount.length > 12) layout.amountSize * 0.78f else layout.amountSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            )
            HorizontalDivider()
            Text(
                quote?.let { "%,d %s = %s %s".format(it.unit, currency.code, Formatter.number(it.value, it.decimals), against.code) } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp, bottom = 3.dp),
            )
            ChangeRow("전일", quote?.prevDiff, quote?.decimals ?: 2)
            ChangeRow("작년말", quote?.yearDiff, quote?.decimals ?: 2)
        }
    }
}

@Composable
private fun ChangeRow(label: String, diff: Double?, decimals: Int) {
    val (text, trend) = Formatter.change(diff, decimals)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = trendColor(trend))
    }
}

/** 목록 맨 끝의 점선 ＋ 카드 */
@Composable
fun AddCard(onClick: () -> Unit, minHeight: Dp = CardMinHeight, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.5f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", fontSize = 36.sp, color = color)
            Text("통화 추가", fontWeight = FontWeight.Bold, color = color)
        }
    }
}
