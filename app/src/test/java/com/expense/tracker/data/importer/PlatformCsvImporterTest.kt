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
        val result = PlatformCsvImporter.parse(wechatCsv)

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
        val result = PlatformCsvImporter.parse(alipayCsv)

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

        val result = PlatformCsvImporter.parse(csv)

        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses[0].amountCents).isEqualTo(1_990L)
        assertThat(result.expenses[0].categoryId).isEqualTo("food")
        val messages = result.issues.joinToString { it.message }
        assertThat(messages).contains("退款")
        assertThat(messages).contains("交易关闭")
        assertThat(messages).contains("交易失败")
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
}
