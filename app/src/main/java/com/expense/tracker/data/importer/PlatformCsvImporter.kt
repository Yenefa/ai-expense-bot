package com.expense.tracker.data.importer

import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 微信/支付宝账单 CSV 导入。
 *
 * 微信导出路径：我 → 支付 → 钱包 → 账单 → 常见问题 → 下载账单（CSV）
 * 支付宝导出路径：我的 → 账单 → 右上角 → 开具交易流水证明（CSV）
 *
 * 列名随平台版本可能微调，全部用「包含匹配」宽容解析；
 * 只导入「支出」，收入/退款/失败行跳过并在 issues 中说明。
 */
object PlatformCsvImporter {

    enum class Platform { WECHAT, ALIPAY, UNKNOWN }

    private val wechatMarker = listOf("交易时间", "交易类型", "交易对方")
    private val alipayMarker = listOf("交易号", "商家订单号", "交易创建时间", "收/支")

    fun detect(headers: List<String>): Platform = when {
        headers.any { it.contains("交易时间") } && headers.any { it.contains("交易对方") } -> Platform.WECHAT
        headers.any { it.contains("交易号") } && headers.any { it.contains("收/支") } -> Platform.ALIPAY
        else -> Platform.UNKNOWN
    }

    /** 表头行是否像平台账单；用于在导入入口自动切换解析器。 */
    fun looksLikePlatformCsv(firstLine: String): Boolean {
        val headers = firstLine.trim().trimStart('\uFEFF').split(',')
        return detect(headers) != Platform.UNKNOWN
    }

    fun parse(csv: String): CsvImportResult {
        val records = CsvExpenseImporter.parseRecords(csv)
        require(records.isNotEmpty()) { "CSV 文件为空" }
        val headers = records.first().fields.mapIndexed { index, value ->
            if (index == 0) value.trim().trimStart('\uFEFF') else value.trim()
        }
        val platform = detect(headers)
        require(platform != Platform.UNKNOWN) { "不是微信/支付宝账单格式" }
        val idx = headers.withIndex().associate { it.value.trim() to it.index }

        fun field(record: CsvExpenseImporter.CsvRecord, name: String): String = when (platform) {
            Platform.WECHAT -> wechatField(record, idx, name)
            Platform.ALIPAY -> alipayField(record, idx, name)
            Platform.UNKNOWN -> ""
        }

        val expenses = mutableListOf<ImportedExpense>()
        val issues = mutableListOf<CsvImportIssue>()
        val now = System.currentTimeMillis()

        records.drop(1).forEach { record ->
            if (record.fields.all(String::isBlank)) return@forEach
            val platformRecord = record.recordNumber

            when (platform) {
                Platform.WECHAT -> parseWechatRow(record, idx, expenses, issues, platformRecord, now)
                Platform.ALIPAY -> parseAlipayRow(record, idx, expenses, issues, platformRecord, now)
                Platform.UNKNOWN -> Unit
            }
        }

        return CsvImportResult(expenses, issues)
    }

    private fun parseWechatRow(
        record: CsvExpenseImporter.CsvRecord,
        idx: Map<String, Int>,
        expenses: MutableList<ImportedExpense>,
        issues: MutableList<CsvImportIssue>,
        line: Int,
        now: Long,
    ) {
        val timeText = field(record, idx, "交易时间")
        val tradeType = field(record, idx, "交易类型")
        val counterparty = field(record, idx, "交易对方")
        val product = field(record, idx, "商品")
        val direction = field(record, idx, "收/支")
        val amountText = field(record, idx, "金额(元)")
        val status = field(record, idx, "当前状态")

        val errors = mutableListOf<String>()
        if (tradeType.contains("退款")) {
            issues += CsvImportIssue(line, "退款交易「$counterparty」已跳过")
            return
        }
        if (direction.contains("收入")) {
            issues += CsvImportIssue(line, "收入「$counterparty ¥$amountText」已跳过（暂只支持支出）")
            return
        }
        if (status.isNotBlank() && !status.contains("成功") && !status.contains("已支付")) {
            issues += CsvImportIssue(line, "状态为「$status」，已跳过")
            return
        }
        val occurredAt = parseDateTime(timeText) ?: now.also { errors.add("交易时间无法解析，按当前时间") }
        val amountCents = parseAmount(amountText)
        if (amountCents == null || amountCents <= 0L) {
            issues += CsvImportIssue(line, "金额无效「$amountText」，已跳过")
            return
        }
        if (errors.isNotEmpty()) {
            issues += CsvImportIssue(line, errors.joinToString("；"))
        }
        val note = listOf(counterparty, product).filter { it.isNotBlank() }.joinToString(" ")
        expenses += ImportedExpense(
            amountCents = amountCents,
            categoryId = guessCategory("$counterparty $product"),
            note = note,
            occurredAt = occurredAt,
            createdAt = occurredAt,
        )
    }

    private fun parseAlipayRow(
        record: CsvExpenseImporter.CsvRecord,
        idx: Map<String, Int>,
        expenses: MutableList<ImportedExpense>,
        issues: MutableList<CsvImportIssue>,
        line: Int,
        now: Long,
    ) {
        val timeText = listOf("付款时间", "交易创建时间").mapNotNull { name ->
            idx[name]?.let { record.fields.getOrNull(it) }
        }.firstOrNull { it.isNotBlank() } ?: ""
        val tradeType = field(record, idx, "类型")
        val counterparty = field(record, idx, "交易对方")
        val product = field(record, idx, "商品名称")
        val direction = field(record, idx, "收/支")
        val amountText = field(record, idx, "金额（元）")
        val status = field(record, idx, "交易状态")
        val refunded = field(record, idx, "成功退款（元）")

        if (tradeType.contains("退款") || status.contains("退款")) {
            issues += CsvImportIssue(line, "退款交易「$counterparty」已跳过")
            return
        }
        if (direction.contains("收入")) {
            issues += CsvImportIssue(line, "收入「$counterparty ¥$amountText」已跳过（暂只支持支出）")
            return
        }
        if (refunded.isNotBlank() && parseAmount(refunded)?.let { it > 0L } == true) {
            issues += CsvImportIssue(line, "含成功退款「$counterparty ¥$refunded」，已跳过")
            return
        }
        if (status.isNotBlank() && !isAlipaySuccess(status)) {
            issues += CsvImportIssue(line, "状态为「$status」，已跳过")
            return
        }
        val occurredAt = parseDateTime(timeText) ?: now
        val amountCents = parseAmount(amountText)
        if (amountCents == null || amountCents <= 0L) {
            issues += CsvImportIssue(line, "金额无效「$amountText」，已跳过")
            return
        }
        val note = listOf(counterparty, product).filter { it.isNotBlank() }.joinToString(" ")
        expenses += ImportedExpense(
            amountCents = amountCents,
            categoryId = guessCategory("$counterparty $product"),
            note = note,
            occurredAt = occurredAt,
            createdAt = occurredAt,
        )
    }

    // === 字段读取（宽容匹配：全角/半角括号、别名） ===

    /** 支付宝状态白名单：只认纯成功；退款成功/关闭/失败都不算支出。 */
    private fun isAlipaySuccess(status: String): Boolean {
        val normalized = status.trim().replace(" ", "")
        return normalized == "交易成功" ||
            normalized == "支付成功" ||
            (normalized.contains("成功") && !normalized.contains("退款"))
    }

    private fun field(record: CsvExpenseImporter.CsvRecord, idx: Map<String, Int>, name: String): String {
        val key = idx.keys.firstOrNull { normalize(it).contains(normalize(name)) }
            ?: return ""
        return record.fields.getOrElse(idx.getValue(key)) { "" }.trim()
    }

    private fun normalize(text: String): String =
        text.trim()
            .replace('（', '(')
            .replace('）', ')')
            .replace(" ", "")

    private fun wechatField(record: CsvExpenseImporter.CsvRecord, idx: Map<String, Int>, name: String): String =
        field(record, idx, name)

    private fun alipayField(record: CsvExpenseImporter.CsvRecord, idx: Map<String, Int>, name: String): String =
        field(record, idx, name)

    private fun parseAmount(text: String): Long? {
        val cleaned = text.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("，", "")
            .replace(" ", "")
            .trimStart('-')
            .trimStart('+')
        return runCatching { Money.parseYuanToCents(cleaned) }.getOrNull()
    }

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/M/d"),
    )

    private fun parseDateTime(text: String): Long? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        for (formatter in dateFormats) {
            val parsed = runCatching {
                if (formatter.toString().contains("yyyy-MM-dd")) {
                    LocalDateTime.parse(cleaned, formatter)
                } else {
                    LocalDateTime.parse(cleaned, formatter)
                }
            }.getOrNull()
            if (parsed != null) return parsed.atZone(zone).toInstant().toEpochMilli()
        }
        // 纯日期（无时间）
        val dateOnly = runCatching {
            LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }.getOrNull()
        return dateOnly?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    }

    /** 按交易对方/商品关键词猜分类；猜不中归 other，导入后可手动改。 */
    private fun guessCategory(keywordText: String): String {
        val text = keywordText.toLowerCase(Locale.ROOT)
        val rules = listOf(
            listOf("food" to listOf("美团", "饿了么", "瑞幸", "蜜雪", "麦当劳", "肯德基", "星巴克", "喜茶", "奈雪", "茶百道", "古茗", "一点点", "餐饮", "餐厅", "食堂", "外卖", "面包", "咖啡", "奶茶", "超市", "便利店", "生鲜", "水果", "买菜", "food", "restaurant", "kfc", "mcdonald", "starbucks", "cafe", "coffee")),
            listOf("transport" to listOf("滴滴", "高德", "地铁", "公交", "出租车", "打车", "高铁", "火车", "机票", "航旅", "顺风车", "加油站", "停车", "过路费", "ticket", "uber", "didic", "metro")),
            listOf("shopping" to listOf("淘宝", "天猫", "京东", "拼多多", "抖音", "快手", "唯品会", "得物", "亚马逊", "amazon", "taobao", "jd.com", "pdd", "shopping", "衣服", "鞋", "数码", "家电", "百货")),
            listOf("drink" to listOf("饮品", "饮料", "矿泉水", "水", "coco", "可乐", "雪碧")),
            listOf("entertainment" to listOf("电影", "影院", "游戏", "steam", "epic", "王者", "原神", "b站", "bilibili", "腾讯视频", "爱奇艺", "优酷", "网吧", "ktv", "酒吧", "演唱会", "门票")),
            listOf("housing" to listOf("房租", "物业", "水电", "燃气", "水费", "电费", "暖气", "宽带", "房租")),
            listOf("medical" to listOf("医院", "药房", "药店", "医药", "诊所", "挂号", "体检", "医保", "口腔", "牙科")),
            listOf("education" to listOf("书", "课程", "培训", "学费", "网课", "电子书", "kindle", "编程", "教程", "考试", "报名")),
            listOf("investment" to listOf("基金", "股票", "理财", "证券", "期货", "币", "黄金", "外汇")),
        )
        for (rule in rules) {
            for ((categoryId, keywords) in rule) {
                if (keywords.any { text.contains(it) }) return categoryId
            }
        }
        return "other"
    }
}
