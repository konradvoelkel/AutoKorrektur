package de.konradvoelkel.android.autokorrektur.ar

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.konradvoelkel.android.autokorrektur.ml.api.YoloServiceImpl
import de.konradvoelkel.android.autokorrektur.ml.engine.YoloTFLiteEngine
import de.konradvoelkel.android.autokorrektur.shared.AndroidInstrumentedBaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

@RunWith(AndroidJUnit4::class)
class RealtimeArPipelineTest : AndroidInstrumentedBaseTest() {

    /**
     * Budget for one frame to come back through [RealtimeArPipeline.onFrameRendered]. Deliberately
     * far above the ~1 s a device needs: the first frame of the run also pays for the TFLite
     * delegate warm-up, and a CI emulator on a shared runner is slow and uneven. This asserts that
     * a frame arrives at all, not how fast — frame rate is a field-testing question, not a CI one.
     */
    private val frameTimeoutMs = 60_000L

    /**
     * Installs a one-shot listener, submits [frame], and returns the bitmap the pipeline renders
     * from it. The listener is installed before [RealtimeArPipeline.processFrame] so a fast frame
     * cannot be delivered before anyone is listening.
     */
    private suspend fun RealtimeArPipeline.renderFrame(frame: Mat): Bitmap {
        val rendered = CompletableDeferred<Bitmap>()
        onFrameRendered = { bitmap, _ -> rendered.complete(bitmap) }
        processFrame(frame)
        val result = withTimeoutOrNull(frameTimeoutMs) { rendered.await() }
        // processFrame() logs and swallows exceptions, so a missing frame is otherwise
        // indistinguishable from a slow one; the counters say which of the two happened.
        assertNotNull(
            "Expected pipeline to render blended output bitmap within $frameTimeoutMs ms ($stats)",
            result
        )
        return result!!
    }

    @Test
    fun testRealtimeArPipeline_initializationAndProcessFrame() = runBlocking {
        val pipeline = RealtimeArPipeline(YoloServiceImpl(YoloTFLiteEngine(appContext)))

        try {
            pipeline.initialize(modelName = "yolo11s")
            assertTrue(pipeline.isInitialized)

            // Sample 640x480 RGBA frame.
            val frameMat = Mat(480, 640, CvType.CV_8UC4, Scalar(120.0, 140.0, 160.0, 255.0))
            try {
                pipeline.renderFrame(frameMat)
                assertTrue(pipeline.accumulator.hasAccumulatedBackground)
            } finally {
                frameMat.release()
            }
        } finally {
            pipeline.close()
            assertTrue(pipeline.isClosed)
        }
    }

    @Test
    fun testRealtimeArPipeline_reset_clearsAccumulator() = runBlocking {
        val pipeline = RealtimeArPipeline(YoloServiceImpl(YoloTFLiteEngine(appContext)))

        try {
            pipeline.initialize(modelName = "yolo11s")
            val frameMat = Mat(480, 640, CvType.CV_8UC4, Scalar(100.0, 100.0, 100.0, 255.0))
            try {
                pipeline.renderFrame(frameMat)
                assertTrue(pipeline.accumulator.hasAccumulatedBackground)

                pipeline.reset()
                assertTrue(!pipeline.accumulator.hasAccumulatedBackground)
            } finally {
                frameMat.release()
            }
        } finally {
            pipeline.close()
        }
    }
}
