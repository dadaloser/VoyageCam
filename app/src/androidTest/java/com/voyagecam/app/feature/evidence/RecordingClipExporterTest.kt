package com.voyagecam.app.feature.evidence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingClipExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val exporter = RecordingClipExporter(context)

    @Test
    fun rearOnlyExportCopiesClipWithoutMutatingOriginal() {
        val rearFile = createTempClip("voyagecam_test_rear.mp4", "rear-audio-track")
        val originalBytes = rearFile.readBytes()

        val exported = exporter.export(
            groupKey = "20260710/20260710_120000",
            rearFile = rearFile,
            frontFile = null,
            mode = RecordingClipExportMode.RearOnly,
        )

        try {
            assertTrue(exported.file.exists())
            assertTrue(rearFile.exists())
            assertArrayEquals(originalBytes, rearFile.readBytes())
            assertArrayEquals(originalBytes, exported.file.readBytes())
            assertEquals(1, exported.itemCount)
        } finally {
            exported.file.delete()
            rearFile.delete()
        }
    }

    @Test
    fun dualPackageExportKeepsBothFilesAndLeavesOriginalsUntouched() {
        val rearFile = createTempClip("voyagecam_test_dual_rear.mp4", "rear-evidence")
        val frontFile = createTempClip("voyagecam_test_dual_front.mp4", "front-evidence")
        val rearBytes = rearFile.readBytes()
        val frontBytes = frontFile.readBytes()

        val exported = exporter.export(
            groupKey = "20260710/20260710_130000",
            rearFile = rearFile,
            frontFile = frontFile,
            mode = RecordingClipExportMode.DualPackage,
        )

        try {
            assertTrue(exported.file.exists())
            assertEquals(2, exported.itemCount)
            ZipFile(exported.file).use { zip ->
                val rearEntry = zip.getEntry(rearFile.name)
                val frontEntry = zip.getEntry(frontFile.name)
                assertTrue(rearEntry != null)
                assertTrue(frontEntry != null)
                zip.getInputStream(rearEntry!!).use { input ->
                    assertArrayEquals(rearBytes, input.readBytes())
                }
                zip.getInputStream(frontEntry!!).use { input ->
                    assertArrayEquals(frontBytes, input.readBytes())
                }
            }
            assertArrayEquals(rearBytes, rearFile.readBytes())
            assertArrayEquals(frontBytes, frontFile.readBytes())
        } finally {
            exported.file.delete()
            rearFile.delete()
            frontFile.delete()
        }
    }

    private fun createTempClip(name: String, content: String): File {
        return File(context.cacheDir, name).apply {
            writeText(content)
        }
    }
}
