package com.jintest.exchangecalc

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Formatter {
    /** 화면 표시용: 천 단위 쉼표 포함 (예: 1,234,567.89). */
    fun display(value: Double, currency: Currency): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = currency.decimals
            maximumFractionDigits = currency.decimals
        }.format(value)

    /** 입력창용: 쉼표 없이, 불필요한 0 제거 (예: 1234.5). */
    fun plain(value: Double, currency: Currency): String =
        BigDecimal(value).setScale(currency.decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
}
