package com.expense.tracker.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class PlatformCsvImporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val wechatCsv = """
        \uFEFF交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
        2026-08-06 09:15:23,商户消费,蜜雪冰城,柠檬水,支出,8.00,零钱,支付成功,2026080622001,1001,
        2026-08-06 12:30:11,商户消费,美团外卖,午餐,支出,25.50,零钱,支付成功,2026080622002,1002,
        2026-08-05 20:10:00,转账,张三,红包,收入,100.00,零钱,已收钱,2026080522003,1003,
        2026-08-04 08:00:00,商户消费,京东商城,充电宝,支出,69.90,零钱,支付成功,2026080422004,1004,
        2026-08-03 22:00:00,退款,蜜雪冰城,柠檬水退款,收入,8.00,零钱,已退款,2026080322005,1005,
    """.trimIndent()

    @Test
    fun wechatBillOnlyImportsSuccessfulExpenseRows() {
        val result = PlatformCsvImporter.parse(wechatCsv, zone)

        assertThat(result.expenses).hasSize(3)
        assertThat(result.expenses[0].amountCents).isEqualTo(800L)
        assertThat(result.expenses[0].categoryId).isEqualTo("food")
        assertThat(result.expenses[0].note).contains("蜜雪冰城")
        assertThat(result.expenses[0].occurredAt)
            .isEqualTo(LocalDateTime.of(2026, 8, 6, 9, 15, 23).atZone(zone).toInstant().toEpochMilli())
        assertThat(result.expenses[1].amountCents).isEqualTo(2_550L)
        assertThat(result.expenses[1].categoryId).isEqualTo("food")
        assertThat(result.expenses[2].amountCents).isEqualTo(6_990L)
        assertThat(result.expenses[2].categoryId).isEqualTo("shopping")

        // 收入、退款被跳过并说明
        val messages = result.issues.joinToString { it.message }
        assertThat(messages).contains("收入")
        assertThat(messages).contains("退款")
    }

    private val alipayCsv = """
        \uFEFF交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,备注
        2026080622001412345678,2026080610001,2026-08-06 09:20:00,2026-08-06 09:20:01,2026-08-06 09:20:02,手机客户端,即时到账交易,滴滴出行,打车,15.60,支出,交易成功,0.00,0.00,
        2026080622001412345679,2026080610002,2026-08-05 19:00:00,2026-08-05 19:00:01,2026-08-05 19:00:02,手机客户端,即时到账交易,某公司,工资,5000.00,收入,交易成功,0.00,0.00,
    """.trimIndent()

    @Test
    fun alipayBillImportsExpenseAndSkipsIncome() {
        val result = PlatformCsvImporter.parse(alipayCsv, zone)

        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses[0].amountCents).isEqualTo(1_560L)
        assertThat(result.expenses[0].categoryId).isEqualTo("transport")
        assertThat(result.expenses[0].note).contains("滴滴出行")
        assertThat(result.issues.joinToString()).contains("收入")
    }

    @Test
    fun alipayRefundAndClosedAndFailedStatesAreAllSkipped() {
        val csv = """
            \uFEFF交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,备注
            2026080622001412345680,2026080610003,2026-08-06 10:00:00,2026-08-06 10:00:01,2026-08-06 10:00:02,手机客户端,即时到账交易,蜜雪冰城,柠檬水,8.00,支出,退款成功,0.00,8.00,
            2026080622001412345681,2026080610004,2026-08-06 11:00:00,2026-08-06 11:00:01,2026-08-06 11:00:02,手机客户端,即时到账交易,某某店铺,商品,30.00,支出,交易关闭,0.00,0.00,
            2026080622001412345682,2026080610005,2026-08-06 12:00:00,2026-08-06 12:00:01,2026-08-06 12:00:02,手机客户端,即时到账交易,某某店铺,商品,99.00,支出,交易失败,0.00,0.00,
            2026080622001412345683,2026080610006,2026-08-06 13:00:00,2026-08-06 13:00:01,2026-08-06 13:00:02,手机客户端,即时到账交易,瑞幸咖啡,拿铁,19.90,支出,交易成功,0.00,0.00,
        """.trimIndent()

        val result = PlatformCsvImporter.parse(csv, zone)

        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses[0].amountCents).isEqualTo(1_990L)
        assertThat(result.expenses[0].categoryId).isEqualTo("food")
        val messages = result.issues.joinToString { it.message }
        assertThat(messages).contains("退款")
        assertThat(messages).contains("交易关闭")
        assertThat(messages).contains("交易失败")
    }

    @Test
    fun wechatBillWithPreambleFindsHeaderRowAndImports() {
        // 官方导出常带前置元信息行，表头不在第一行
        val csv = """
            微信支付账单明细,,,,,,,,,,
            导出时间：2026-08-07 10:00:00,,,,,,,,,,
            --------------------------
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-08-06 09:15:23,商户消费,蜜雪冰城,柠檬水,支出,8.00,零钱,支付成功,2026080622001,1001,
            2026-08-06 12:30:11,商户消费,美团外卖,午餐,支出,25.50,零钱,支付成功,2026080622002,1002,
        """.trimIndent()

        assertThat(PlatformCsvImporter.looksLikePlatformCsv(csv)).isTrue()
        val result = PlatformCsvImporter.parse(csv, zone)

        assertThat(result.expenses).hasSize(2)
        assertThat(result.expenses[0].amountCents).isEqualTo(800L)
        assertThat(result.expenses[0].occurredAt)
            .isEqualTo(LocalDateTime.of(2026, 8, 6, 9, 15, 23).atZone(zone).toInstant().toEpochMilli())
        assertThat(result.expenses[1].amountCents).isEqualTo(2_550L)
    }

    @Test
    fun detectRecognizesPlatformHeaders() {
        assertThat(PlatformCsvImporter.looksLikePlatformCsv("交易时间,交易类型,交易对方,商品,收/支,金额(元)"))
            .isTrue()
        assertThat(PlatformCsvImporter.looksLikePlatformCsv("交易号,商家订单号,交易创建时间,类型,收/支"))
            .isTrue()
        assertThat(PlatformCsvImporter.looksLikePlatformCsv("id,amount,categoryId,note,occurredAt"))
            .isFalse()
    }

    @Test
    fun invalidAmountOrUnknownFormatReportsCleanly() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformCsvImporter.parse("hello,world\n1,2")
        }

        val bad = PlatformCsvImporter.parse(
            """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-08-06 09:15:23,商户消费,某某,xx,支出,abc,零钱,支付成功,1,2,
            """.trimIndent(),
        )
        assertThat(bad.expenses).isEmpty()
        assertThat(bad.issues.joinToString()).contains("金额无效")
    }

    /**
     * 回归：官方导出的前置说明（昵称 / 时间范围 / 导出类型 / 笔数统计 / 注意事项 / 分隔线）
     * 常超过 15 行。检测窗口若截断在前 15 行，入口会误判为「非平台账单」并回落到本应用 CSV 解析器，
     * 用户看到的就是「CSV 缺少字段：id, amount, ...」——即「账单不支持导入」。
     */
    @Test
    fun wechatBillWithLongOfficialPreambleIsStillDetected() {
        val preamble = buildString {
            appendLine("微信支付账单明细")
            appendLine("微信昵称：[演示用户]")
            appendLine("起始时间：[2026-08-01 00:00:00] 终止时间：[2026-08-31 23:59:59]")
            appendLine("导出类型：[全部]")
            appendLine("导出时间：[2026-09-01 10:00:00]")
            appendLine()
            appendLine("共 12 笔记录")
            appendLine("收入：2 笔 100.00 元")
            appendLine("支出：10 笔 258.30 元")
            appendLine("中性交易：0 笔 0.00 元")
            appendLine("注：")
            appendLine("1. 本账单仅展示微信支付相关交易。")
            appendLine("2. 账单中的金额单位为人民币元。")
            appendLine("3. 本账单仅供参考，以实际交易为准。")
            appendLine("4. 如需用于报销，请以支付凭证为准。")
            appendLine("5. 若对账单有疑问，请联系客服。")
            appendLine()
            appendLine("----------------------微信支付账单明细列表--------------------")
        }
        val csv = preamble +
            "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\n" +
            "2026-08-06 09:15:23,商户消费,蜜雪冰城,柠檬水,支出,8.00,零钱,支付成功,2026080622001,1001,\n"

        val headerLineIndex = csv.lines().indexOfFirst { it.startsWith("交易时间") }
        assertThat(headerLineIndex).isGreaterThan(15) // 表头确实在旧扫描上限之外
        assertThat(PlatformCsvImporter.looksLikePlatformCsv(csv)).isTrue()
        val result = PlatformCsvImporter.parse(csv, zone)
        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses[0].amountCents).isEqualTo(800L)
    }
}
