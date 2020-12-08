package co.urbi.android.urbiscan.core

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import co.urbi.android.urbiscan.AnalyzingGuardActivity
import co.urbi.android.urbiscan.core.processor.VisionImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.lang.ref.WeakReference

/**
 * Our custom image analysis class.
 *
 * <p>All we need to do is override the function `analyze` with our desired operations. Here,
 * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
 */
class ImageAnalyzer(val processor: VisionImageProcessor?, private val weakRef: WeakReference<AnalyzingGuardActivity>) : ImageAnalysis.Analyzer, CoroutineScope by MainScope() {

    /**
     * Analyzes an image to produce a result.
     *
     * <p>The caller is responsible for ensuring this analysis method can be executed quickly
     * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
     * images will not be acquired and analyzed.
     *
     * <p>The image passed to this method becomes invalid after this method returns. The caller
     * should not store external references to this image, as these references will become
     * invalid.
     *
     * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
     * call image.close() on received images when finished using them. Otherwise, new images
     * may not be received or the camera may stall, depending on back pressure setting.
     *
     */
    override fun analyze(image: ImageProxy) {
        weakRef.get()?.let {
            if (!it.getAnalysisGuard()) {
                it.setAnalysisGuard(true)
                Log.d("PROCESSING X", "OK")
                processor?.process(image)
            } else {
                image.close()
            }
        } ?: kotlin.run { image.close() }
    }
}
