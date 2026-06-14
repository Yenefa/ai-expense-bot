package com.expense.tracker.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryTest {
    @Test fun all8CategoriesExist() {
        assertThat(Category.ALL.map { it.id })
            .containsExactly("food", "transport", "shopping", "drink",
                             "entertainment", "housing", "medical", "other")
    }
    @Test fun foodCategoryHasEmoji() {
        assertThat(Category.byId("food")!!.emoji).isEqualTo("🍜")
    }
    @Test fun unknownIdReturnsOther() {
        assertThat(Category.byIdOrOther("nonexistent").id).isEqualTo("other")
    }
}
