package com.expense.tracker.ocr

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface OcrRecognizer {
    suspend fun recognize(bitmap: Bitmap): String
}

/**
 * 多路 OCR 识别：中英双识别器并行 + 深色反转 + 对比度增强兜底。
 *
 * 解决三类"读不出"：
 * 1. 英文/繁体/数字为主 → 拉丁识别器并行
 * 2. 深色主题截图（深底白字）→ 亮度检测 + 颜色反转
 * 3. 模糊/小字 → 灰度 + 对比度增强
 */
class MlKitOcrRecognizer : OcrRecognizer {

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(bitmap: Bitmap): String {
        val passes = buildList {
            add(bitmap)
            if (isDarkDominant(bitmap)) add(invert(bitmap))
            add(enhance(bitmap))
        }

        for (pass in passes) {
            val text = runBoth(pass)
            if (text.isNotBlank()) return text
        }
        return ""
    }

    /** 中文与拉丁识别器并行，取文字更多的一路。 */
    private suspend fun runBoth(bitmap: Bitmap): String = coroutineScope {
        val chinese = async { recognizeWith(chineseRecognizer, bitmap) }
        val latin = async { recognizeWith(latinRecognizer, bitmap) }
        val results = listOf(chinese, latin).awaitAll()
        results.maxByOrNull { it.length } ?: ""
    }

    private suspend fun recognizeWith(recognizer: TextRecognizer, bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { }
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    /** 采样九宫格判断整体是否深色背景（深色截图先反转成浅底再识别）。 */
    private fun isDarkDominant(bitmap: Bitmap): Boolean {
        val sampleRect = Rect(
            0,
            0,
            bitmap.width.coerceAtMost(600),
            bitmap.height.coerceAtMost(600),
        )
        val sample = if (sampleRect.width() == bitmap.width && sampleRect.height() == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, sampleRect.width(), sampleRect.height())
        }
        var sum = 0.0
        var count = 0
        val step = 11
        for (y in 0 until sample.height step step) {
            for (x in 0 until sample.width step step) {
                val pixel = sample.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                sum += (r * 0.299 + g * 0.587 + b * 0.114)
                count++
            }
        }
        if (sample !== bitmap) sample.recycle()
        return if (count == 0) false else sum / count < DARK_LUMINANCE_THRESHOLD
    }

    /** 颜色反转（深底白字 → 白底黑字）。 */
    private fun invert(bitmap: Bitmap): Bitmap {
        val inverted = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(inverted)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return inverted
    }

    /** 灰度 + 对比度增强，帮助模糊/小字截图。 */
    private fun enhance(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(enhanced)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1.35f, 0f, 0f, 0f, -50f,
                0f, 1.35f, 0f, 0f, -50f,
                0f, 0f, 1.35f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }

    companion object {
        private const val DARK_LUMINANCE_THRESHOLD = 110.0
    }
}
