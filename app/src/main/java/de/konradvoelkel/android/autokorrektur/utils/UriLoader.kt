package de.konradvoelkel.android.autokorrektur.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

/**
 * Utility to load Bitmaps from Android URIs with safe downsampling and EXIF rotation correction.
 */
class UriLoader(private val context: Context) {

    companion object {
        private const val MEGAPIXEL = 1_000_000f
    }

    /** A decoded bitmap, and whether the decoder has already applied the EXIF orientation. */
    private class Decoded(val bitmap: Bitmap, val exifOrientationApplied: Boolean)

    /**
     * Loads a [Bitmap] from a given [Uri], applying safe downsampling to fit [maxMegapixels]
     * and correcting orientation based on EXIF metadata.
     *
     * The orientation is applied exactly once. [ImageDecoder] applies it while decoding;
     * [BitmapFactory] does not, so only bitmaps from that path are turned here. Turning the
     * ImageDecoder result a second time made every portrait photo (EXIF `ROTATE_90`) a landscape
     * one with the cars on their side — and the camera and the photo picker both hand over
     * content:// URIs, which take the ImageDecoder path.
     */
    @Throws(IOException::class)
    fun loadRotatedBitmap(imageUri: Uri, maxMegapixels: Float): Bitmap {
        val decoded = when (val scheme = imageUri.scheme?.lowercase()) {
            "file" -> loadBitmapFromFile(imageUri, maxMegapixels)
            "content" -> loadBitmapFromContentProvider(imageUri, maxMegapixels)
            else -> throw IOException("Unsupported URI scheme: $scheme")
        }
        val upright = if (decoded.exifOrientationApplied) {
            decoded.bitmap
        } else {
            rotateBitmapIfRequired(decoded.bitmap, imageUri)
        }
        return ensureStandardSoftwareArgb8888(upright)
    }

    private fun ensureStandardSoftwareArgb8888(bitmap: Bitmap): Bitmap {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            bitmap.gainmap = null
        }
        val standard = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            standard.gainmap = null
        }
        val canvas = android.graphics.Canvas(standard)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        if (standard !== bitmap) {
            bitmap.recycle()
        }
        return standard
    }

    /** Turns a bitmap whose decoder ignored the EXIF orientation tag ([BitmapFactory]). */
    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        val inputStream = try {
            when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let { java.io.FileInputStream(it) }
                "content" -> context.contentResolver.openInputStream(uri)
                else -> null
            }
        } catch (e: Exception) {
            null
        } ?: return bitmap

        val exif = try {
            ExifInterface(inputStream)
        } catch (e: Exception) {
            return bitmap
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {
            }
        }

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            bitmap.gainmap = null
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            rotated.gainmap = null
        }
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private fun loadBitmapFromFile(uri: Uri, maxMegapixels: Float): Decoded {
        val path = uri.path ?: throw IOException("File path is null for URI: $uri")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        val imageWidth = options.outWidth
        val imageHeight = options.outHeight
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw IOException("Invalid image dimensions from file: ${imageWidth}x${imageHeight}")
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(imageWidth, imageHeight, maxMegapixels)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(path, decodeOptions)
            ?: throw IOException("BitmapFactory.decodeFile failed for path: $path")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            bmp.gainmap = null
        }
        return Decoded(bmp, exifOrientationApplied = false)
    }

    private fun loadBitmapFromContentProvider(uri: Uri, maxMegapixels: Float): Decoded {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val sampleSize =
                    calculateInSampleSize(info.size.width, info.size.height, maxMegapixels)
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                bmp.gainmap = null
            }
            // ImageDecoder has already turned the pixels as the EXIF orientation tag asks.
            return Decoded(bmp, exifOrientationApplied = true)
        } catch (e: Exception) {
            AppLogger.warn("UriLoader: ImageDecoder failed for $uri, falling back. ${e.message}")
        }

        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize =
                    calculateInSampleSize(options.outWidth, options.outHeight, maxMegapixels)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOptions)
                ?: throw IOException("BitmapFactory.decodeFileDescriptor failed for URI: $uri")
            return Decoded(bmp, exifOrientationApplied = false)
        } ?: throw IOException("Could not get FileDescriptor for URI: $uri")
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxMegapixels: Float): Int {
        if (width <= 0 || height <= 0) return 1
        val targetMaxPixels = (maxMegapixels * MEGAPIXEL).toLong()
        var sampleSize = 1
        while ((width / sampleSize).toLong() * (height / sampleSize) > targetMaxPixels) {
            sampleSize *= 2
        }
        AppLogger.info("UriLoader: Downsampling by ${sampleSize}x to fit ${maxMegapixels}MP limit (${width / sampleSize}x${height / sampleSize})")
        return sampleSize
    }
}
