package com.expense.tracker.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CsvExpenseImporterTest {

    @Test
    fun parsesBomQuotedCommaQuoteAndEmbeddedNewline() {
        val csv = """
            ﻿id,amount,categoryId,note,occurredAt,createdAt
            7,12.5,food,"面, ""双拼""
            大份",1781438400000,1781541152859
        """.trimIndent()

        val result = CsvExpenseImporter.parse(csv)

        assertThat(result.issues).isEmpty()
        assertThat(result.expenses).containsExactly(
            ImportedExpense(
                amountCents = 1_250L,
                categoryId = "food",
                note = "面, \"双拼\"\n大份",
                occurredAt = 1781438400000,
                createdAt = 1781541152859,
            )
        )
    }

    @Test
    fun collectsInvalidRowsWithoutDroppingValidRows() {
        val csv = """
            id,amount,categoryId,note,occurredAt,createdAt
            1,9.0,drink,有效记录,1781438400000,1781541152859
            2,-1.0,food,金额错误,1781438400000,1781541152859
            3,8.0,unknown,分类错误,1781438400000,1781541152859
            4,8.0,food,时间错误,abc,1781541152859
        """.trimIndent()

        val result = CsvExpenseImporter.parse(csv)

        assertThat(result.expenses).hasSize(1)
        assertThat(result.issues).hasSize(3)
        assertThat(result.issues.map { it.recordNumber }).containsExactly(3, 4, 5)
    }

    @Test
    fun parsesDecimalTextDirectlyToRoundedCentsAndRejectsExponentNotation() {
        val csv = """
            id,amount,categoryId,note,occurredAt,createdAt
            1,12.345,food,三位小数,1781438400000,1781541152859
            2,1e2,food,指数金额,1781438400000,1781541152859
        """.trimIndent()

        val result = CsvExpenseImporter.parse(csv)

        assertThat(result.expenses.single().amountCents).isEqualTo(1_235L)
        assertThat(result.issues.single().recordNumber).isEqualTo(3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCsvWithoutRequiredHeader() {
        CsvExpenseImporter.parse("amount,note\n12.5,午饭")
    }

    @Test
    fun parsesUserFixtureWhenEnvironmentVariableIsPresent() {
        val path = System.getenv("CSV_IMPORT_FIXTURE")
        assumeTrue("CSV_IMPORT_FIXTURE not set", !path.isNullOrBlank())
        val expectedRows = System.getenv("CSV_IMPORT_EXPECTED_ROWS")?.toInt() ?: 128

        val result = CsvExpenseImporter.parse(File(path!!).readText(Charsets.UTF_8))

        assertThat(result.expenses).hasSize(expectedRows)
        assertThat(result.issues).isEmpty()
    }
}
