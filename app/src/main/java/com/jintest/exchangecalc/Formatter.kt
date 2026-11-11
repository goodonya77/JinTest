package com.jintest.exchangecalc

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

enum class Trend { UP, DOWN, FLAT, NONE }

object Formatter {
    /** 환산 금액 표시용: 천 단위 쉼표, 통화별 소수점 (예: 1,234,567.89). */
    fun display(value: Double, currency: Currency): String = number(value, currency.decimals)

    /** 입력창용: 쉼표 없이, 불필요한 0 제거 (예: 1234.5). */
    fun plain(value: Double, currency: Currency): String =
        BigDecimal(value).setScale(currency.decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()

    /** 환율이 1만 이상이면 소수점 없이, 그보다 작으면 소수점 2자리. */
    fun rateDecimals(rate: Double): Int = if (abs(rate) >= 10_000) 0 else 2

    /** 환율이 1보다 작으면 10, 100, 1000… 단위로 묶어 보여준다 (예: 100 IDR = 8.30 KRW). */
    fun quoteUnit(rate: Double): Int {
        var unit = 1
        while (rate * unit < 1 && unit < 1_000_000) unit *= 10
        return unit
    }

    /** 증감 표시: ▲1.23 / ▼4.56 / 0.00 / — */
    fun change(diff: Double?, decimals: Int): Pair<String, Trend> = when {
        diff == null -> "—" to Trend.NONE
        abs(diff) < 0.5 * 10.0.pow(-decimals) -> number(0.0, decimals) to Trend.FLAT
        diff > 0 -> "▲" + number(diff, decimals) to Trend.UP
        else -> "▼" + number(-diff, decimals) to Trend.DOWN
    }

    fun number(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)
}
