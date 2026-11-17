package de.konradvoelkel.android.autokorrektur.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import de.konradvoelkel.android.autokorrektur.ml.config.YoloConfig
import de.konradvoelkel.android.autokorrektur.shared.AndroidInstrumentedBaseTest
import de.konradvoelkel.android.autokorrektur.shared.PipelineTestFixtures
import de.konradvoelkel.android.autokorrektur.utils.UriLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * TODO.md BUG-01: `highrescar.jpg` (8160x6144, progressive JPEG, GIMP sRGB ICC profile) picked
 * through the photo picker on the Pixel 10 Pro gave a uniform cyan mask preview and no detections.
 * The picker hands over a content:// URI, which UriLoader decodes with ImageDecoder; the suite's
 * high-res tests use file:// (BitmapFactory) and synthetic images, so they could not see it.
 * Both tests here take the real file, byte-identical, through a FileProvider content:// URI.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class HighResProgressiveJpegTest : AndroidInstrumentedBaseTest() {

    private fun contentUriOf(file: File): Uri =
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    /** Over a 32x32 grid: mean |a-b| per channel, and the channel spread (std dev) of [b]. */
    private fun compare(a: Bitmap, b: Bitmap): Triple<Double, Double, String> {
        var diff = 0.0
        var n = 0
        val vals = ArrayList<Int>(32 * 32 * 3)
        var r = 0L; var g = 0L; var bl = 0L
        for (gy in 0 until 32) for (gx in 0 until 32) {
            val x = (gx + 0.5) * b.width / 32; val y = (gy + 0.5) * b.height / 32
            val pa = a.getPixel(x.toInt(), y.toInt()); val pb = b.getPixel(x.toInt(), y.toInt())
            diff += abs(Color.red(pa) - Color.red(pb)) + abs(Color.green(pa) - Color.green(pb)) + abs(Color.blue(pa) - Color.blue(pb))
            n += 3
            vals += Color.red(pb); vals += Color.green(pb); vals += Color.blue(pb)
            r += Color.red(pb); g += Color.green(pb); bl += Color.blue(pb)
        }
        val mean = vals.average()
        val spread = sqrt(vals.sumOf { (it - mean) * (it - mean) } / vals.size)
        val k = 32 * 32
        return Triple(diff / n, spread, "mean RGB of content decode = (${r / k}, ${g / k}, ${bl / k})")
    }

    @Test
    fun highResProgressiveJpeg_viaContentUri_decodesLikeTheFilePath() {
        val file = cacheAsset("highrescar.jpg")
        val loader = UriLoader(appContext)
        // 8 MP is ImageProcessor.DEFAULT_MAX_MEGAPIXELS: 50 MP -> inSampleSize 4 -> 2040x1536
        val viaFile = loader.loadRotatedBitmap(Uri.fromFile(file), maxMegapixels = 8.0f)
        val viaContent = loader.loadRotatedBitmap(contentUriOf(file), maxMegapixels = 8.0f)
        try {
            assertEquals("width", viaFile.width, viaContent.width)
            assertEquals("height", viaFile.height, viaContent.height)
            val (meanDiff, spread, summary) = compare(viaFile, viaContent)
            assertTrue("content:// decode is a flat colour (channel spread $spread; $summary): BUG-01", spread > 10.0)
            assertTrue("content:// decode differs from file:// decode by $meanDiff per channel; $summary", meanDiff < 12.0)
        } finally {
            viaFile.recycle()
            viaContent.recycle()
        }
    }

    @Test
    fun highResProgressiveJpeg_viaContentUri_vehicleIsDetected() {
        val file = cacheAsset("highrescar.jpg")
        val processed = PipelineTestFixtures.imageProcessor().processInputImage(
            imageUri = contentUriOf(file),
            modelWidth = 640,
            modelHeight = 640,
            downscaleMp = null
        )
        try {
            val result = runBlocking {
                PipelineTestFixtures.yolo().inferDetailed(
                    transformedMat = processed.transformedMat,
                    xRatio = processed.xRatio,
                    yRatio = processed.yRatio,
                    upscaleFactor = 1.0f,
                    overrideConfig = YoloConfig(scoreThreshold = 0.35f)
                )
            }
            try {
                assertTrue("no vehicle detected in highrescar.jpg via content://: BUG-01", result.detections.isNotEmpty())
            } finally {
                result.mask.release()
            }
        } finally {
            processed.release(recycleBitmaps = true)
        }
    }
}
