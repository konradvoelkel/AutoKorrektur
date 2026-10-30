package de.konradvoelkel.android.autokorrektur.pipeline

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import de.konradvoelkel.android.autokorrektur.ml.config.YoloConfig
import de.konradvoelkel.android.autokorrektur.shared.AndroidInstrumentedBaseTest
import de.konradvoelkel.android.autokorrektur.shared.PipelineTestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reported 2026-10-30: photographed in portrait, the picture came out turned and nothing was
 * detected; only landscape shots worked. The camera hands the pipeline a content:// URI, and
 * on that path UriLoader applied the EXIF orientation twice. This drives the real path —
 * FileProvider URI, ImageProcessor, YOLO — with a portrait shot stored the way cameras store it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PortraitCameraPhotoTest : AndroidInstrumentedBaseTest() {

    @Test
    fun portraitCameraPhoto_isLoadedUpright_andTheCarIsDetected() {
        val uprightFile = cacheAsset("photo_with_car_1.png")
        val upright = BitmapFactory.decodeFile(uprightFile.absolutePath)
        assertTrue("fixture should be a portrait scene", upright.height > upright.width)
        val uri = cameraStylePortraitJpegUri(upright, "portrait_car_from_camera.jpg")

        val processed = PipelineTestFixtures.imageProcessor().processInputImage(
            imageUri = uri,
            modelWidth = 640,
            modelHeight = 640,
            downscaleMp = null
        )
        try {
            assertEquals(
                "portrait photo loaded as landscape: EXIF orientation applied twice?",
                upright.width,
                processed.originalBitmap.width
            )
            assertEquals(upright.height, processed.originalBitmap.height)

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
                assertTrue("no vehicle detected in the portrait camera photo", result.detections.isNotEmpty())
            } finally {
                result.mask.release()
            }
        } finally {
            processed.release(recycleBitmaps = true)
            upright.recycle()
        }
    }
}
