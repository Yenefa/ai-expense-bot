package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Box(
                    Modifier.fillMaxSize().background(AppColors.Bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("记账助手 — 骨架就绪")
                }
            }
        }
    }
}
