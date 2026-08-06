package com.expense.tracker.util

import android.util.Log
import com.expense.tracker.BuildConfig

internal object PrivacySafeLog {
    private const val TAG = "YECost"

    fun llmRequestStarted(recentRecordCount: Int) = debug {
        "llm_request_started recent_record_count=$recentRecordCount"
    }

    fun llmResponseParsed(expenseCount: Int, actionCount: Int) = debug {
        "llm_response_parsed expense_count=$expenseCount action_count=$actionCount"
    }

    fun llmRequestFailed() = debug { "llm_request_failed" }

    private inline fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) runCatching { Log.d(TAG, message()) }
    }
}
