package com.expense.tracker.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreApiKeyStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = AndroidKeystoreApiKeyStorage(context)

    @After
    fun clearStoredValue() {
        storage.write("")
    }

    @Test
    fun roundTripStoresOnlyCiphertextInPreferences() {
        val secret = "sk-instrumentation-${System.nanoTime()}"

        storage.write(secret)

        assertEquals(secret, storage.read())
        val storedValues = context
            .getSharedPreferences("secure_api_key", Context.MODE_PRIVATE)
            .all
            .values
            .joinToString(separator = "|")
        assertFalse(storedValues.contains(secret))
    }
}
