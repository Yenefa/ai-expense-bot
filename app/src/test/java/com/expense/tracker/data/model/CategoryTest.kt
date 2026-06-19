package com.expense.tracker.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryTest {
    @Test fun all9CategoriesExist() {
        assertThat(Category.ALL.map { it.id })
            .containsExactly("food", "transport", "shopping", "drink",
                             "entertainment", "housing", "medical", "investment", "other")
    }
    @Test fun foodCategoryHasEmoji() {
        assertThat(Category.byId("food")!!.emoji).isEqualTo("🍜")
    }
    @Test fun unknownIdReturnsOther() {
        assertThat(Category.byIdOrOther("nonexistent").id).isEqualTo("other")
    }
    @Test fun investmentIsMarked() {
        assertThat(Category.byId("investment")!!.isInvestment).isTrue()
        assertThat(Category.byId("food")!!.isInvestment).isFalse()
    }
}
