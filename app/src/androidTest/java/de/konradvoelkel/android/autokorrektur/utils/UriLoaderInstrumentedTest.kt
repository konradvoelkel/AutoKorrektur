package de.konradvoelkel.android.autokorrektur.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import de.konradvoelkel.android.autokorrektur.shared.AndroidInstrumentedBaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@SmallTest
class UriLoaderInstrumentedTest : AndroidInstrumentedBaseTest() {

    @Test
    fun loadRotatedBitmap_unsupportedSchemeThrowsIOException() {
        val loader = UriLoader(appContext)
        val unsupportedUri = Uri.parse("http://example.com/image.jpg")
        try {
            loader.loadRotatedBitmap(unsupportedUri, maxMegapixels = 2.0f)
            fail("Expected IOException for unsupported URI scheme")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Unsupported URI scheme") == true)
        }
    }

    @Test
    fun loadRotatedBitmap_loadsFileUriSuccessfully() {
        val loader = UriLoader(appContext)
        val file = File(appContext.cacheDir, "test_file_uri.jpg")
        baseTempFiles.add(file)

        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.BLUE)
        FileOutputStream(file).use { out ->
            src.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        src.recycle()

        val loaded = loader.loadRotatedBitmap(Uri.fromFile(file), maxMegapixels = 1.0f)
        assertNotNull(loaded)
        assertEquals(200, loaded.width)
        assertEquals(100, loaded.height)
        loaded.recycle()
    }

    @Test
    fun loadRotatedBitmap_exifRotationApplied() {
        val loader = UriLoader(appContext)
        val file = File(appContext.cacheDir, "test_exif_rotation.jpg")
        baseTempFiles.add(file)

        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.MAGENTA)
        FileOutputStream(file).use { out ->
            src.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        src.recycle()

        // Set EXIF orientation to 90 degrees clockwise
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        exif.saveAttributes()

        val loaded = loader.loadRotatedBitmap(Uri.fromFile(file), maxMegapixels = 1.0f)
        assertNotNull(loaded)
        // Original 200x100 rotated 90 degrees becomes 100x200
        assertEquals(100, loaded.width)
        assertEquals(200, loaded.height)
        loaded.recycle()
    }

    /**
     * The camera and the photo picker hand over content:// URIs, which the loader decodes with
     * ImageDecoder. ImageDecoder applies the EXIF orientation itself; applying it a second time
     * turned every portrait photo into a landscape one with the cars on their side, so only
     * landscape shots worked (reported 2026-10-30). The file:// test above never saw this because
     * that path decodes with BitmapFactory, which leaves the orientation to the caller.
     */
    @Test
    fun loadRotatedBitmap_contentUri_appliesExifOrientationExactlyOnce() {
        val loader = UriLoader(appContext)
        // Upright 100x200: red top half, blue bottom half.
        val upright = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        Canvas(upright).apply {
            drawRect(0f, 0f, 100f, 100f, Paint().apply { color = Color.RED })
            drawRect(0f, 100f, 100f, 200f, Paint().apply { color = Color.BLUE })
        }
        val uri = cameraStylePortraitJpegUri(upright, "test_content_exif_once.jpg")
        upright.recycle()
        assertEquals("content", uri.scheme)

        val loaded = loader.loadRotatedBitmap(uri, maxMegapixels = 1.0f)
        assertEquals("portrait photo came out landscape: EXIF orientation applied twice?", 100, loaded.width)
        assertEquals(200, loaded.height)
        assertColorNear("top half should be red", Color.RED, loaded.getPixel(50, 30))
        assertColorNear("bottom half should be blue", Color.BLUE, loaded.getPixel(50, 170))
        loaded.recycle()
    }

    /** JPEG compression moves solid colours by a few counts; a wrong turn moves them by ~255. */
    private fun assertColorNear(message: String, expected: Int, actual: Int, tolerance: Int = 40) {
        val off = maxOf(
            abs(Color.red(expected) - Color.red(actual)),
            abs(Color.green(expected) - Color.green(actual)),
            abs(Color.blue(expected) - Color.blue(actual))
        )
        assertTrue(
            "$message — expected #${Integer.toHexString(expected)}, got #${Integer.toHexString(actual)}",
            off <= tolerance
        )
    }
}
