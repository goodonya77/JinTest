package com.jintest.exchangecalc

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Formatter {
    /** 화면 표시용: 천 단위 쉼표 포함 (예: 1,234,567.89). */
    fun display(value: Double, currency: Currency): String = grouped(value, currency.decimals)

    /** 환율·증감 표시용: 소수점 2자리 (예: 1,380.50). */
    fun rate(value: Double): String = grouped(value, 2)

    /** 입력창용: 쉼표 없이, 불필요한 0 제거 (예: 1234.5). */
    fun plain(value: Double, currency: Currency): String =
        BigDecimal(value).setScale(currency.decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()

    /** 환율이 1보다 작으면 10, 100, 1000… 단위로 묶어 보여준다 (예: 100 JPY = 912.30). */
    fun quoteUnit(rate: Double): Int {
        var unit = 1
        while (rate * unit < 1 && unit < 1_000_000) unit *= 10
        return unit
    }

    private fun grouped(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)
}
