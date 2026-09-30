package com.thekidschannel.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal suspend fun captureVideoFrame(container: View): Bitmap? =
    withContext(Dispatchers.Main.immediate) {
        val source = container.videoOutputs()
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: return@withContext null
        val scale = minOf(1f, MAX_PREVIEW_WIDTH.toFloat() / source.width)
        val bitmap = Bitmap.createBitmap(
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )

        when (source) {
            is TextureView -> source.getBitmap(bitmap) ?: run {
                bitmap.recycle()
                null
            }
            is SurfaceView -> captureSurfaceView(source, bitmap)
            else -> {
                bitmap.recycle()
                null
            }
        }
    }

internal suspend fun hasVisibleVideoFrame(container: View): Boolean {
    val frame = captureVideoFrame(container) ?: return false
    return try {
        val stepX = (frame.width / 8).coerceAtLeast(1)
        val stepY = (frame.height / 8).coerceAtLeast(1)
        for (y in stepY / 2 until frame.height step stepY) {
            for (x in stepX / 2 until frame.width step stepX) {
                val pixel = frame.getPixel(x, y)
                if (Color.red(pixel) > 24 || Color.green(pixel) > 24 || Color.blue(pixel) > 24) {
                    return true
                }
            }
        }
        false
    } finally {
        frame.recycle()
    }
}

private suspend fun captureSurfaceView(source: SurfaceView, bitmap: Bitmap): Bitmap? {
    if (!source.holder.surface.isValid) {
        bitmap.recycle()
        return null
    }
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                source,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS && continuation.isActive) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            continuation.resume(null)
        }
    }
}

private fun View.videoOutputs(): List<View> = buildList {
    when (this@videoOutputs) {
        is SurfaceView -> add(this@videoOutputs)
        is TextureView -> if (isAvailable) add(this@videoOutputs)
        is ViewGroup -> repeat(childCount) { childIndex ->
            addAll(getChildAt(childIndex).videoOutputs())
        }
    }
}

private const val MAX_PREVIEW_WIDTH = 640
