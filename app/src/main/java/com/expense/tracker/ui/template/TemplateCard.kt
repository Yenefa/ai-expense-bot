package com.expense.tracker.ui.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors

@Composable
fun TemplateCard(
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    onSubmit: (Long) -> Unit,
    onDoubleClickCategory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Text(
            text = "选择分类，然后输入金额（双击分类可填写备注）",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = Category.ALL, key = { it.id }) { c ->
                CategoryChip(
                    category = c,
                    selected = c.id == selectedCategoryId,
                    onClick = { onSelectCategory(c.id) },
                    onDoubleClick = { onDoubleClickCategory(c.id) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        AmountInput(onSubmit = onSubmit)
    }
}
