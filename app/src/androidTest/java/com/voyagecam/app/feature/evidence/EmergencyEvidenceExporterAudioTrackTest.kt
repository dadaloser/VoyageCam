package com.voyagecam.app.feature.evidence

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.data.storage.RecordingStorageManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class EmergencyEvidenceExporterAudioTrackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storageManager = RecordingStorageManager(context)
    private val exporter = EmergencyEvidenceExporter(context, storageManager)

    @Test
    fun exportWithoutBurnedWatermarkPreservesRearAudioTrack() {
        val clip = createRearClip()
        val packageFile = exporter.export(
            event = eventFor(clip),
            includeBurnedWatermarkVideos = false,
            segmentDurationMinutes = 3,
        )
        val extractedClip = extractClipFromPackage(packageFile.file, clip.name)

        try {
            assertEquals(1, trackCount(extractedClip, "video/"))
            assertEquals(1, trackCount(extractedClip, "audio/"))
        } finally {
            extractedClip.delete()
            packageFile.file.delete()
            clip.delete()
        }
    }

    @Test
    fun exportWithBurnedWatermarkPreservesRearAudioTrack() {
        val clip = createRearClip()
        val packageFile = exporter.export(
            event = eventFor(clip),
            includeBurnedWatermarkVideos = true,
            segmentDurationMinutes = 3,
        )
        val extractedClip = extractClipFromPackage(packageFile.file, clip.name)

        try {
            assertEquals(1, trackCount(extractedClip, "video/"))
            assertEquals(1, trackCount(extractedClip, "audio/"))
        } finally {
            extractedClip.delete()
            packageFile.file.delete()
            clip.delete()
        }
    }

    private fun createRearClip(): File {
        val startedAtMillis = 1_720_000_000_000L
        return storageManager.createNormalSegmentFile(startedAtMillis, "rear").also { file ->
            file.parentFile?.mkdirs()
            createSyntheticAvClip(file)
            assertEquals(1, trackCount(file, "video/"))
            assertEquals(1, trackCount(file, "audio/"))
        }
    }

    private fun eventFor(clip: File): EmergencyEvent {
        return EmergencyEvent(
            id = UUID.randomUUID().toString(),
            trigger = EmergencyTrigger.Manual,
            triggeredAtMillis = 1_720_000_030_000L,
            accelerationG = null,
            thresholdG = null,
            latitude = null,
            longitude = null,
            speedMetersPerSecond = null,
            bearingDegrees = null,
            locationCapturedAtMillis = null,
            segmentPaths = listOfNotNull(storageManager.dashcamRelativePath(clip)),
            gpsTrackPoints = emptyList(),
        )
    }

    private fun extractClipFromPackage(packageFile: File, clipName: String): File {
        val extractedFile = File(context.cacheDir, "extracted_$clipName").apply {
            delete()
        }
        ZipFile(packageFile).use { zip ->
            val entry = zip.getEntry("clips/$clipName")
            assertTrue("missing exported clip entry", entry != null)
            zip.getInputStream(entry).use { input ->
                extractedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return extractedFile
    }

    private fun trackCount(file: File, mimePrefix: String): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).count { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith(mimePrefix) == true
            }
        } finally {
            extractor.release()
        }
    }

    private fun createSyntheticAvClip(outputFile: File) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        val audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)

        var inputSurface: InputSurface? = null
        var muxerStarted = false
        try {
            videoEncoder.configure(
                MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                },
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            inputSurface = InputSurface(videoEncoder.createInputSurface())
            videoEncoder.start()

            audioEncoder.configure(
                MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_BUFFER_SIZE)
                },
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            audioEncoder.start()

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = -1
            var audioTrackIndex = -1

            fun maybeStartMuxer() {
                if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    muxer.start()
                    muxerStarted = true
                }
            }

            fun drainVideo(endOfStream: Boolean): Boolean {
                while (true) {
                    val outputIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, CODEC_TIMEOUT_US)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return endOfStream
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                            maybeStartMuxer()
                        }
                        outputIndex >= 0 -> {
                            val outputBuffer = videoEncoder.getOutputBuffer(outputIndex)
                                ?: error("missing video output buffer")
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                videoBufferInfo.size = 0
                            }
                            if (videoBufferInfo.size > 0) {
                                check(muxerStarted) { "muxer not started for video output" }
                                outputBuffer.position(videoBufferInfo.offset)
                                outputBuffer.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferInfo)
                            }
                            val finished = (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            videoEncoder.releaseOutputBuffer(outputIndex, false)
                            if (finished) return true
                        }
                    }
                }
            }

            fun drainAudio(endOfStream: Boolean): Boolean {
                while (true) {
                    val outputIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, CODEC_TIMEOUT_US)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return endOfStream
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                            maybeStartMuxer()
                        }
                        outputIndex >= 0 -> {
                            val outputBuffer = audioEncoder.getOutputBuffer(outputIndex)
                                ?: error("missing audio output buffer")
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                audioBufferInfo.size = 0
                            }
                            if (audioBufferInfo.size > 0) {
                                check(muxerStarted) { "muxer not started for audio output" }
                                outputBuffer.position(audioBufferInfo.offset)
                                outputBuffer.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                muxer.writeSampleData(audioTrackIndex, outputBuffer, audioBufferInfo)
                            }
                            val finished = (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            audioEncoder.releaseOutputBuffer(outputIndex, false)
                            if (finished) return true
                        }
                    }
                }
            }

            fun feedAudio() {
                val totalSamples = AUDIO_SAMPLE_RATE * CLIP_DURATION_MS / 1_000
                var sampleCursor = 0
                while (sampleCursor < totalSamples) {
                    val inputIndex = audioEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex < 0) {
                        drainAudio(endOfStream = false)
                        continue
                    }
                    val inputBuffer = audioEncoder.getInputBuffer(inputIndex) ?: error("missing audio input buffer")
                    inputBuffer.clear()
                    val samplesThisBuffer = minOf(AUDIO_SAMPLES_PER_BUFFER, totalSamples - sampleCursor)
                    writeSineWave(inputBuffer, sampleCursor, samplesThisBuffer)
                    val presentationTimeUs = sampleCursor * 1_000_000L / AUDIO_SAMPLE_RATE
                    audioEncoder.queueInputBuffer(
                        inputIndex,
                        0,
                        samplesThisBuffer * AUDIO_BYTES_PER_SAMPLE,
                        presentationTimeUs,
                        0,
                    )
                    sampleCursor += samplesThisBuffer
                    drainAudio(endOfStream = false)
                }

                var eosQueued = false
                while (!eosQueued) {
                    val inputIndex = audioEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex < 0) {
                        drainAudio(endOfStream = false)
                        continue
                    }
                    audioEncoder.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        CLIP_DURATION_MS * 1_000L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    eosQueued = true
                }
            }

            inputSurface.makeCurrent()
            repeat(VIDEO_FRAME_COUNT) { frameIndex ->
                val progress = frameIndex.toFloat() / VIDEO_FRAME_COUNT.toFloat()
                GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
                GLES20.glClearColor(progress, 0.2f, 1f - progress, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                inputSurface.setPresentationTime(frameIndex * FRAME_DURATION_NS)
                inputSurface.swapBuffers()
                drainVideo(endOfStream = false)
            }
            videoEncoder.signalEndOfInputStream()
            feedAudio()

            var videoFinished = false
            var audioFinished = false
            while (!videoFinished || !audioFinished) {
                if (!videoFinished) videoFinished = drainVideo(endOfStream = true)
                if (!audioFinished) audioFinished = drainAudio(endOfStream = true)
            }
        } finally {
            runCatching { inputSurface?.release() }
            runCatching { videoEncoder.stop() }
            runCatching { videoEncoder.release() }
            runCatching { audioEncoder.stop() }
            runCatching { audioEncoder.release() }
            runCatching {
                if (muxerStarted) {
                    muxer.stop()
                }
            }
            runCatching { muxer.release() }
        }
    }

    private fun writeSineWave(
        buffer: ByteBuffer,
        startSample: Int,
        sampleCount: Int,
    ) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { sampleIndex ->
            val phase = 2.0 * PI * AUDIO_TONE_HZ * (startSample + sampleIndex) / AUDIO_SAMPLE_RATE
            val amplitude = (sin(phase) * Short.MAX_VALUE * 0.25).toInt().toShort()
            buffer.putShort(amplitude)
        }
    }

    private class InputSurface(
        private val surface: Surface,
    ) {
        private val eglDisplay: EGLDisplay
        private val eglContext: EGLContext
        private val eglSurface: EGLSurface

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "unable to get EGL display" }

            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "unable to initialize EGL" }

            val configAttributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    eglDisplay,
                    configAttributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    numConfigs,
                    0,
                ) && numConfigs[0] > 0,
            ) { "unable to choose EGL config" }

            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "unable to create EGL context" }

            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                configs[0],
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "unable to create EGL surface" }
        }

        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                "unable to make EGL context current"
            }
        }

        fun swapBuffers() {
            check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "unable to swap EGL buffers" }
        }

        fun setPresentationTime(presentationTimeNs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        }

        fun release() {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
            surface.release()
        }
    }

    private companion object {
        const val VIDEO_MIME_TYPE = "video/avc"
        const val AUDIO_MIME_TYPE = "audio/mp4a-latm"
        const val VIDEO_WIDTH = 320
        const val VIDEO_HEIGHT = 240
        const val VIDEO_FRAME_RATE = 10
        const val VIDEO_FRAME_COUNT = 10
        const val VIDEO_BIT_RATE = 512_000
        const val CLIP_DURATION_MS = 1_000
        const val AUDIO_SAMPLE_RATE = 44_100
        const val AUDIO_CHANNEL_COUNT = 1
        const val AUDIO_BIT_RATE = 64_000
        const val AUDIO_BUFFER_SIZE = 16 * 1024
        const val AUDIO_TONE_HZ = 440.0
        const val AUDIO_SAMPLES_PER_BUFFER = 1_024
        const val AUDIO_BYTES_PER_SAMPLE = 2
        const val CODEC_TIMEOUT_US = 10_000L
        const val FRAME_DURATION_NS = 1_000_000_000L / VIDEO_FRAME_RATE
    }
}
