package com.expense.tracker.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OCR 多路识别冒烟：深色截图 / 英文截图 / 繁体截图 三类"读不出"场景。
 * 需要在模拟器或真机上运行（ML Kit 本地模型）。
 */
@RunWith(AndroidJUnit4::class)
class OcrMultiPassSmokeTest {

    private fun readAsset(name: String): Bitmap {
        val context: Context = InstrumentationRegistry.getInstrumentation().context
        context.assets.open(name).use { input ->
            return BitmapFactory.decodeStream(input)
                ?: error("无法解码 $name")
        }
    }

    @Test
    fun darkScreenshotIsReadable() = runBlocking {
        val text = MlKitOcrRecognizer().recognize(readAsset("ocr-1-dark.png"))
        assertThat(text).isNotEmpty()
        assertThat(text).contains("12.50")
        assertThat(text).contains("支付成功")
    }

    @Test
    fun englishScreenshotIsReadable() = runBlocking {
        val text = MlKitOcrRecognizer().recognize(readAsset("ocr-2-en.png"))
        assertThat(text).isNotEmpty()
        assertThat(text).contains("Amazon")
        assertThat(text).contains("99.98")
    }

    @Test
    fun traditionalChineseScreenshotIsReadable() = runBlocking {
        val text = MlKitOcrRecognizer().recognize(readAsset("ocr-3-traditional.png"))
        assertThat(text).isNotEmpty()
        assertThat(text).contains("240")
    }
}
