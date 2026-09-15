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

    /**
     * 回归：RFC 4180 只允许**字段开头**的 `"` 起始引用；字段中间出现的 `"` 是普通字符。
     * 旧实现把任何位置的 `"` 都当成引号开关 —— 账单里只要有一个落单的引号
     * （备注 / 商品名里很常见），整个文件就会被判为「存在未闭合的引号」而**整体拒绝导入**。
     */
    @Test
    fun strayQuoteInsideUnquotedFieldIsKeptLiteralAndDoesNotBreakTheFile() {
        val csv = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-08-06 09:15:23,商户消费,某某店,5寸"特价蛋糕,支出,8.00,零钱,支付成功,1,2,备注里有个"引号
        """.trimIndent()

        val result = PlatformCsvImporter.parse(csv, java.time.ZoneId.of("Asia/Shanghai"))

        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses.single().amountCents).isEqualTo(800L)
        assertThat(result.expenses.single().note).contains("\"")
    }

    @Test
    fun decodeCsvBytesRejectsZipArchivesWithAUsefulMessage() {
        // 官方账单的邮件/消息附件常是带密码的 ZIP，按文本解码只会得到乱码
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)

        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CsvExpenseImporter.decodeCsvBytes(zipHeader)
        }

        assertThat(error.message).contains("压缩包")
    }

    @Test
    fun decodeCsvBytesFallsBackToGbkForPlatformExports() {
        val text = "交易时间,交易对方\n2026-08-06 09:15:23,蜜雪冰城\n"
        val gbkBytes = text.toByteArray(java.nio.charset.Charset.forName("GBK"))

        assertThat(CsvExpenseImporter.decodeCsvBytes(gbkBytes)).isEqualTo(text)
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
