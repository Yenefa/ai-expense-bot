package com.expense.tracker.memory

import com.expense.tracker.data.model.Category

/**
 * 类型校验（Memory 路径的独立一步）：
 * 草稿字段必须完整且合法，否则不生成提案。
 */
object MemoryTypeValidator {

    /** 单条金额上限 ¥1 亿（防解析/口误产生离谱数字）。 */
    const val MAX_AMOUNT_CENTS = 10_000_000_000L

    fun validate(draft: MemoryDraft): Boolean = when (draft.type) {
        MemoryType.MONTHLY_INCOME,
        MemoryType.SAVINGS_GOAL,
        -> draft.amountCents != null && draft.amountCents in 1..MAX_AMOUNT_CENTS

        MemoryType.MERCHANT_ALIAS ->
            !draft.merchant.isNullOrBlank() && draft.categoryId?.let { Category.byId(it) } != null

        MemoryType.CATEGORY_PREFERENCE ->
            draft.categoryId?.let { Category.byId(it) } != null
    }
}
