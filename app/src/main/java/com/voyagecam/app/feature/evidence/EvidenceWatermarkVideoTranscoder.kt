package com.voyagecam.app.feature.evidence

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt

@UnstableApi
internal class EvidenceWatermarkVideoTranscoder(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun transcode(
        inputFile: File,
        outputFile: File,
        watermark: EvidenceClipWatermark,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) {
        val thread = HandlerThread("voyagecam-watermark-export").apply { start() }
        val handler = Handler(thread.looper)
        val latch = CountDownLatch(1)
        val resolution = AtomicBoolean(false)
        val outcome = AtomicReference<Result<Unit>?>(null)
        val progressHolder = ProgressHolder()

        fun finish(result: Result<Unit>) {
            if (resolution.compareAndSet(false, true)) {
                outcome.set(result)
                latch.countDown()
            }
        }

        val transformer = Transformer.Builder(appContext)
            .setLooper(thread.looper)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        finish(Result.success(Unit))
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        finish(Result.failure(exportException))
                    }
                },
            )
            .build()

        val pollProgress = object : Runnable {
            override fun run() {
                if (resolution.get()) return
                if (isCancelled()) {
                    runCatching { transformer.cancel() }
                    finish(Result.failure(EvidenceExportCancelledException()))
                    return
                }

                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 100))
                }
                handler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
            }
        }

        handler.post {
            runCatching {
                val editedMediaItem = EditedMediaItem.Builder(
                    MediaItem.fromUri(Uri.fromFile(inputFile)),
                )
                    .setEffects(
                        Effects(
                            emptyList(),
                            listOf(
                                OverlayEffect(
                                    listOf(EvidenceWatermarkCanvasOverlay(watermark)),
                                ),
                            ),
                        ),
                    )
                    .build()
                transformer.start(editedMediaItem, outputFile.absolutePath)
                handler.post(pollProgress)
            }.onFailure { finish(Result.failure(it)) }
        }

        try {
            latch.await()
            outcome.get()?.getOrThrow() ?: error("水印转码未返回结果")
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
        }
    }

    private class EvidenceWatermarkCanvasOverlay(
        private val watermark: EvidenceClipWatermark,
    ) : CanvasOverlay(true) {
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(168, 0, 0, 0)
            style = Paint.Style.FILL
        }
        private var lastKey: String? = null

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            val lines = watermark.linesAt(presentationTimeUs)
            val textSizePx = max(canvas.width, canvas.height) * TEXT_SIZE_RATIO
            val key = buildString {
                append(canvas.width)
                append('x')
                append(canvas.height)
                append(':')
                append(textSizePx.roundToInt())
                append(':')
                append(lines.joinToString(separator = "\n"))
            }
            if (key == lastKey) return
            lastKey = key

            textPaint.textSize = textSizePx
            val lineHeight = textPaint.fontMetrics.run { bottom - top + textSizePx * 0.12f }
            val padding = textSizePx * 0.5f
            val maxLineWidth = lines.maxOfOrNull { line -> textPaint.measureText(line) } ?: 0f
            val blockHeight = lineHeight * lines.size
            val left = padding
            val bottom = canvas.height - padding
            val top = bottom - blockHeight - padding * 1.4f
            val right = left + maxLineWidth + padding * 2f

            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawRoundRect(
                RectF(left, top, right, bottom),
                padding * 0.6f,
                padding * 0.6f,
                backgroundPaint,
            )

            var baseline = top + padding - textPaint.fontMetrics.top
            lines.forEach { line ->
                canvas.drawText(line, left + padding, baseline, textPaint)
                baseline += lineHeight
            }
        }
    }
}

private const val PROGRESS_POLL_INTERVAL_MS = 300L
private const val TEXT_SIZE_RATIO = 0.035f
