package com.jintest.exchangecalc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.jintest.exchangecalc.CountryDetector
import com.jintest.exchangecalc.Currency
import com.jintest.exchangecalc.Formatter
import com.jintest.exchangecalc.MainActivity
import com.jintest.exchangecalc.Quote
import com.jintest.exchangecalc.RateRepository
import com.jintest.exchangecalc.Settings
import com.jintest.exchangecalc.Trend
import kotlinx.coroutines.withTimeoutOrNull

/** 위젯에 보여줄 내용: 거주국 통화 기준으로 앱 목록 순서의 앞 4개 통화. */
private data class WidgetData(val home: Currency, val rows: List<Pair<Currency, Quote?>>, val date: String?)

private fun loadData(context: Context): WidgetData {
    val home = CountryDetector.homeCurrencyQuick(context)
    val rates = RateRepository(context).loadCached()
    val rows = Settings(context).currencyList.filter { it != home }.take(4).map { it to rates?.quote(it, home) }
    return WidgetData(home, rows, rates?.latest?.date)
}

/**
 * 중간 위젯 (4×2): 거주국 통화 기준 환율 4개와 전일·작년말 대비 증감.
 * 누르면 앱이 열리고, ⟳를 누르면 바로 새 환율을 받는다.
 */
class ExchangeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = RateRepository(context)
        if (repo.isStale()) {
            withTimeoutOrNull(15_000) { runCatching { repo.fetch() } }
        }
        val data = loadData(context)
        provideContent { GlanceTheme { WidgetContent(data) } }
    }
}

class ExchangeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExchangeWidget()
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching { RateRepository(context).fetch() }
        ExchangeWidget().update(context, glanceId)
    }
}

private val Red = ColorProvider(day = Color(0xFFD32F2F), night = Color(0xFFEF5350))
private val Blue = ColorProvider(day = Color(0xFF1565C0), night = Color(0xFF64B5F6))

@Composable
private fun WidgetContent(data: WidgetData) {
    val muted = GlanceTheme.colors.onSurfaceVariant
    val text = GlanceTheme.colors.onSurface
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${data.home.flag} ${data.home.code} 기준 환율",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = text),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                (data.date?.substring(5)?.replace("-", "/")?.let { "$it · " } ?: "") + "전일 · 작년말",
                style = TextStyle(fontSize = 11.sp, color = muted),
            )
            Text(
                "  ⟳",
                style = TextStyle(fontSize = 16.sp, color = muted),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
            )
        }
        if (data.date == null) {
            Spacer(GlanceModifier.height(12.dp))
            Text("앱을 열어 환율을 불러와 주세요", style = TextStyle(fontSize = 13.sp, color = muted))
        }
        data.rows.forEach { (currency, quote) ->
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(currency.flag, style = TextStyle(fontSize = 17.sp))
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    if (quote != null && quote.unit > 1) "${quote.unit} ${currency.code}" else currency.code,
                    style = TextStyle(fontSize = 15.sp, color = text),
                )
                Text(
                    quote?.let { Formatter.number(it.value, it.decimals) } ?: "—",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = text, textAlign = TextAlign.End),
                    modifier = GlanceModifier.defaultWeight(),
                )
                ChangeText(quote?.prevDiff, quote?.decimals ?: 2, muted)
                ChangeText(quote?.yearDiff, quote?.decimals ?: 2, muted)
            }
        }
    }
}

/** 증감 한 칸 (▲빨강 / ▼파랑) */
@Composable
private fun ChangeText(diff: Double?, decimals: Int, muted: androidx.glance.unit.ColorProvider) {
    val (change, trend) = Formatter.change(diff, decimals)
    Text(
        change,
        style = TextStyle(
            fontSize = 11.sp,
            color = when (trend) {
                Trend.UP -> Red
                Trend.DOWN -> Blue
                else -> muted
            },
            textAlign = TextAlign.End,
        ),
        modifier = GlanceModifier.width(58.dp),
    )
}
