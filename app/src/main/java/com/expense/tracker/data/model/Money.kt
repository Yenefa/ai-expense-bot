package com.expense.tracker.data.model

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    private val decimalYuanPattern = Regex("^\\d+(?:\\.\\d+)?$")

    fun parseYuanToCents(text: String): Long {
        val normalized = text.trim()
        require(decimalYuanPattern.matches(normalized)) { "金额格式无效" }
        val cents = try {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("金额超出范围", error)
        }
        require(cents > 0L) { "金额必须大于 0" }
        return cents
    }

    fun centsToYuan(cents: Long): Double = BigDecimal.valueOf(cents, 2).toDouble()

    fun formatYuan(cents: Long): String = BigDecimal.valueOf(cents, 2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString()
}
