package com.expense.tracker.agent

import com.expense.tracker.data.model.Category

/**
 * 分类别名 → 分类 id。用于查询轮的本地意图解析（Router 决定 query_expenses 过滤哪些分类）。
 * 别名只做"宽召回"：多匹配时包含所有命中分类，宁可多查不可漏查。
 */
object AgentCategories {

    private val aliases: Map<String, List<String>> = linkedMapOf(
        "food" to listOf("吃", "餐", "饭", "食", "外卖", "宵夜", "早饭", "午餐", "晚饭", "夜宵", "火锅", "早餐", "正餐"),
        "drink" to listOf("咖啡", "奶茶", "饮品", "果汁", "饮料", "可乐", "茶"),
        "transport" to listOf("交通", "打车", "出租车", "滴滴", "地铁", "公交", "加油", "油费", "停车", "高速费", "高铁", "火车", "机票", "车费", "通勤"),
        "shopping" to listOf("购物", "衣服", "淘宝", "京东", "拼多多", "日用品", "超市", "网购"),
        "entertainment" to listOf("娱乐", "游戏", "电影", "演唱会", "ktv", "会员", "视频", "旅游", "门票", "演出"),
        "housing" to listOf("房租", "水电", "物业", "燃气", "宽带", "网费", "房", "租"),
        "medical" to listOf("药", "看病", "挂号", "体检", "医疗", "医院", "门诊", "牙"),
        "education" to listOf("书", "课程", "课", "学费", "教育", "api", "模型", "算力", "云", "域名", "知识", "学习"),
        "investment" to listOf("投资", "基金", "股票", "理财", "黄金", "短线", "证券"),
    )

    private val normalizedAliases: Map<String, List<String>> =
        aliases.mapValues { (_, words) -> words.map { it.lowercase() } }

    fun resolve(text: String): Set<String> {
        val normalized = text.lowercase()
        return normalizedAliases
            .filter { (_, words) -> words.any { normalized.contains(it) } }
            .keys
            .toSet()
    }

    fun displayName(id: String): String = Category.byIdOrOther(id).displayName
}
