package com.expense.tracker.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test fun yuanTextUsesDecimalHalfUpRounding() {
        assertThat(Money.parseYuanToCents("12.345")).isEqualTo(1_235L)
        assertThat(Money.parseYuanToCents("0.30")).isEqualTo(30L)
    }

    @Test fun centsRoundTripAndFormattingAreExact() {
        assertThat(Money.centsToYuan(1_250L)).isEqualTo(12.5)
        assertThat(Money.formatYuan(1_250L)).isEqualTo("12.50")
        assertThat(Money.formatYuan(1L)).isEqualTo("0.01")
    }

    @Test fun decimalTextConvertsDirectlyToExactCents() {
        assertThat(Money.parseYuanToCents("12.345")).isEqualTo(1_235L)
        assertThat(Money.parseYuanToCents("0.01")).isEqualTo(1L)
        assertThat(Money.parseYuanToCents("00012.30")).isEqualTo(1_230L)
        assertThat(Money.parseYuanToCents("92233720368547758.07")).isEqualTo(Long.MAX_VALUE)
    }

    @Test fun invalidNonPositiveExponentAndOverflowAmountsAreRejected() {
        listOf("", "abc", "0", "0.00", "-1", "1e2", "92233720368547758.08").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                Money.parseYuanToCents(value)
            }
        }
    }
}
