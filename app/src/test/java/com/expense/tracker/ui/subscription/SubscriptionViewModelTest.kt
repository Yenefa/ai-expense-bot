package com.expense.tracker.ui.subscription

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.expense.tracker.data.prefs.ApiKeyStorage
import com.expense.tracker.data.subscription.RedeemResponse
import com.expense.tracker.data.subscription.SubscriptionApiException
import com.expense.tracker.data.subscription.SubscriptionPrefs
import com.expense.tracker.data.subscription.SubscriptionStatus
import com.expense.tracker.data.subscription.SubscriptionStatusResponse
import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun successfulServerRedemptionUsesExactExpiryAndClearsInput() = runTest {
        val captured = mutableListOf<Pair<String, String>>()
        val fixture = fixture(
            redeemer = { code, installationId ->
                captured += code to installationId
                RedeemResponse("ACTIVE", "opaque-token", 9_000L)
            },
        )
        fixture.vm.updateCredential("  ye30-abcd-2345-wxyz  ")

        fixture.vm.redeem()

        val state = fixture.vm.uiState.first { !it.redeeming && it.status == SubscriptionStatus.ACTIVE }
        assertThat(state.expiresAtMillis).isEqualTo(9_000L)
        assertThat(state.credentialInput).isEmpty()
        assertThat(state.successMessage).contains("30 天")
        assertThat(fixture.storage.value).isEqualTo("opaque-token")
        assertThat(captured.single().first).isEqualTo("ye30-abcd-2345-wxyz")
        assertThat(captured.single().second).matches("^[0-9a-f-]{36}$")
    }

    @Test fun invalidOrUsedCodeShowsStableMessageAndDoesNotPersist() = runTest {
        val fixture = fixture(
            redeemer = { _, _ -> throw SubscriptionApiException("CODE_INVALID_OR_USED", 400) },
        )
        fixture.vm.updateCredential("invalid-code")

        fixture.vm.redeem()

        val state = fixture.vm.uiState.first { !it.redeeming && it.errorMessage != null }
        assertThat(state.errorMessage).isEqualTo("兑换码无效或已使用。")
        assertThat(fixture.storage.value).isEmpty()
    }

    @Test fun networkFailurePreservesInputAndHidesDiagnostics() = runTest {
        val fixture = fixture(
            redeemer = { _, _ -> error("private network diagnostic") },
        )
        fixture.vm.updateCredential("YE30-ABCD-2345-WXYZ")

        fixture.vm.redeem()

        val state = fixture.vm.uiState.first { !it.redeeming && it.errorMessage != null }
        assertThat(state.credentialInput).isEqualTo("YE30-ABCD-2345-WXYZ")
        assertThat(state.errorMessage).contains("网络")
        assertThat(state.errorMessage).doesNotContain("private network diagnostic")
    }

    @Test fun blankCodeDoesNotCallServer() = runTest {
        var calls = 0
        val fixture = fixture(redeemer = { _, _ ->
            calls++
            RedeemResponse("ACTIVE", "token", 9_000L)
        })

        fixture.vm.redeem()

        assertThat(calls).isEqualTo(0)
        assertThat(fixture.vm.uiState.value.errorMessage).contains("不能为空")
    }

    @Test fun successfulRedemptionEnablesAiMode() = runTest {
        var activationCalls = 0
        val fixture = fixture(onActivated = { activationCalls++ })
        fixture.vm.updateCredential("YE30-ABCD-2345-WXYZ")

        fixture.vm.redeem()
        fixture.vm.uiState.first { it.successMessage != null }

        assertThat(activationCalls).isEqualTo(1)
    }

    @Test fun startupUnauthorizedStatusClearsLocalSubscription() = runTest {
        val fixture = fixture(
            initialToken = "old-token",
            statusChecker = { _, _ -> throw SubscriptionApiException("SUBSCRIPTION_UNAUTHORIZED", 401) },
        )

        // 401 处理先清空本地 token（触发 snapshot 发射 INACTIVE），随后才补上失效文案；
        // 必须等"已失效且文案已就位"的稳定态，否则会撞上中间态而偶发失败（CI flaky）。
        val state = fixture.vm.uiState.first {
            it.status == SubscriptionStatus.INACTIVE && it.errorMessage != null
        }

        assertThat(state.errorMessage).contains("失效")
        assertThat(fixture.storage.value).isEmpty()
    }

    @Test fun temporaryStatusFailureKeepsLocalSubscription() = runTest {
        val fixture = fixture(
            initialToken = "active-token",
            statusChecker = { _, _ -> error("temporary offline") },
        )

        val state = fixture.vm.uiState.first { it.status == SubscriptionStatus.ACTIVE }

        assertThat(state.expiresAtMillis).isEqualTo(9_000L)
        assertThat(fixture.storage.value).isEqualTo("active-token")
    }

    @Test fun keystoreReadFailureKeepsSubscriptionPageAliveAndClearsStaleEntitlement() = runTest {
        val fixture = fixture(initialToken = "active-token", storageFailReads = true)

        // 初始化协程完成清理后，陈旧权益元数据被移除；旧实现会在 currentToken() 抛异常。
        fixture.store.data.first { it[SubscriptionPrefs.EXPIRES_AT] == null }
        val state = fixture.vm.uiState.first { it.status == SubscriptionStatus.INACTIVE }

        assertThat(fixture.storage.value).isEmpty()
        assertThat(state.credentialAvailable).isFalse()
    }

    private suspend fun fixture(
        initialToken: String? = null,
        storageFailReads: Boolean = false,
        redeemer: suspend (String, String) -> RedeemResponse = { _, _ ->
            RedeemResponse("ACTIVE", "opaque-token", 9_000L)
        },
        statusChecker: suspend (String, String) -> SubscriptionStatusResponse = { _, _ ->
            SubscriptionStatusResponse("ACTIVE", 9_000L)
        },
        onActivated: suspend () -> Unit = {},
    ): Fixture {
        val store = TestStore()
        val storage = TestTokenStorage()
        val prefs = SubscriptionPrefs(store, storage, clock = { 1_000L })
        prefs.installationId()
        if (initialToken != null) prefs.activateServerToken(initialToken, 9_000L)
        // 在 ViewModel 构造前注入读取失败，模拟 Keystore 条目失效。
        storage.failReads = storageFailReads
        return Fixture(
            vm = SubscriptionViewModel(
                prefs = prefs,
                redeemer = redeemer,
                statusChecker = statusChecker,
                onActivated = onActivated,
                healthPollEnabled = false,
            ),
            storage = storage,
            store = store,
        )
    }

    private data class Fixture(
        val vm: SubscriptionViewModel,
        val storage: TestTokenStorage,
        val store: TestStore,
    )
}

private class TestStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class TestTokenStorage(var value: String = "") : ApiKeyStorage {
    var failReads = false

    override fun read(): String {
        if (failReads) throw GeneralSecurityException("keystore entry invalidated")
        return value
    }

    override fun write(value: String) {
        this.value = value
    }
}
