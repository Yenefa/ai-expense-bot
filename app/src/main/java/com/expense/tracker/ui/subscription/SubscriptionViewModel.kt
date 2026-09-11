package com.expense.tracker.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.subscription.RedeemResponse
import com.expense.tracker.data.subscription.ServerHealthResponse
import com.expense.tracker.data.subscription.SubscriptionApiException
import com.expense.tracker.data.subscription.SubscriptionPrefs
import com.expense.tracker.data.subscription.SubscriptionStatus
import com.expense.tracker.data.subscription.SubscriptionStatusResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val status: SubscriptionStatus = SubscriptionStatus.INACTIVE,
    val expiresAtMillis: Long = 0L,
    val credentialAvailable: Boolean = false,
    val credentialInput: String = "",
    val redeeming: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val serverReachable: Boolean = false,
    val serverTimeMillis: Long? = null,
    val serverCheckedAtMillis: Long = 0L,
    val serverVersion: String = "",
    val serverConfigured: Boolean = true,
)

class SubscriptionViewModel(
    private val prefs: SubscriptionPrefs,
    private val redeemer: suspend (String, String) -> RedeemResponse,
    private val statusChecker: suspend (String, String) -> SubscriptionStatusResponse,
    private val healthChecker: suspend () -> ServerHealthResponse? = { null },
    private val onActivated: suspend () -> Unit = {},
    private val healthPollEnabled: Boolean = true,
) : ViewModel() {
    private val internal = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = internal.asStateFlow()

    private var healthPollJob: Job? = null

    init {
        viewModelScope.launch {
            launch {
                prefs.snapshot.collect { snapshot ->
                    internal.update { state ->
                        state.copy(
                            status = snapshot.status,
                            expiresAtMillis = snapshot.expiresAtMillis,
                            credentialAvailable = snapshot.credentialAvailable,
                            errorMessage = if (snapshot.storageError) {
                                "订阅凭证安全存储暂不可用，请重新兑换。"
                            } else {
                                state.errorMessage
                            },
                        )
                    }
                }
            }
            refreshServerStatus()
        }
    }

    /** 页面可见时启动健康轮询；离开页面（DisposableEffect onDispose）必须调用 [stopHealthPolling]，避免后台 30s 轮询耗电。 */
    fun startHealthPolling() {
        if (!healthPollEnabled || healthPollJob?.isActive == true) return
        healthPollJob = viewModelScope.launch { pollServerHealth() }
    }

    fun stopHealthPolling() {
        healthPollJob?.cancel()
        healthPollJob = null
    }

    /** 周期性探测服务器在线状态与服务器时间，让用户确认时间在走、服务在云端。 */
    private suspend fun pollServerHealth() {
        while (true) {
            val health = runCatching { healthChecker() }.getOrNull()
            if (health == null) {
                internal.update {
                    it.copy(serverReachable = false, serverTimeMillis = null)
                }
            } else {
                internal.update {
                    it.copy(
                        serverReachable = true,
                        serverTimeMillis = health.serverTime,
                        serverCheckedAtMillis = System.currentTimeMillis(),
                        serverVersion = health.version,
                    )
                }
            }
            delay(HEALTH_POLL_INTERVAL_MS)
        }
    }

    fun updateCredential(value: String) {
        internal.update {
            it.copy(
                credentialInput = value,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun redeem() {
        val code = internal.value.credentialInput.trim()
        if (code.isEmpty()) {
            internal.update { it.copy(errorMessage = "兑换码不能为空。", successMessage = null) }
            return
        }
        if (internal.value.redeeming) return

        internal.update { it.copy(redeeming = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            runCatching {
                val installationId = prefs.installationId()
                val response = redeemer(code, installationId)
                prefs.activateServerToken(response.accessToken, response.expiresAtMillis)
            }.onSuccess { snapshot ->
                runCatching { onActivated() }
                internal.update {
                    it.copy(
                        status = snapshot.status,
                        expiresAtMillis = snapshot.expiresAtMillis,
                        credentialAvailable = snapshot.credentialAvailable,
                        credentialInput = "",
                        redeeming = false,
                        errorMessage = null,
                        successMessage = "兑换成功，AI 会员已增加 30 天。",
                    )
                }
            }.onFailure { error ->
                internal.update {
                    it.copy(
                        redeeming = false,
                        errorMessage = redemptionErrorMessage(error),
                        successMessage = null,
                    )
                }
            }
        }
    }

    private suspend fun refreshServerStatus() {
        val token = prefs.currentToken() ?: return
        val installationId = prefs.installationId()
        runCatching { statusChecker(token, installationId) }
            .onSuccess { response ->
                prefs.activateServerToken(token, response.expiresAtMillis)
            }
            .onFailure { error ->
                if (error.isUnauthorizedSubscription()) {
                    prefs.clearSubscription()
                    internal.update {
                        it.copy(
                            status = SubscriptionStatus.INACTIVE,
                            expiresAtMillis = 0L,
                            credentialAvailable = false,
                            errorMessage = "AI 会员已失效，请重新兑换。",
                        )
                    }
                }
            }
    }

    private fun redemptionErrorMessage(error: Throwable): String = when {
        error is SubscriptionApiException && error.code == "CODE_INVALID_OR_USED" ->
            "兑换码无效或已使用。"
        else -> "兑换失败，请检查网络后重试。"
    }

    private fun Throwable.isUnauthorizedSubscription(): Boolean =
        this is SubscriptionApiException &&
            (httpStatus == 401 || code == "SUBSCRIPTION_UNAUTHORIZED")

    companion object {
        private const val HEALTH_POLL_INTERVAL_MS = 30_000L
    }
}
