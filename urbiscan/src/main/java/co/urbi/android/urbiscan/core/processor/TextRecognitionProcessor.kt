package co.urbi.android.urbiscan.core.processor

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import co.urbi.android.urbiscan.core.FrameMetadata
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import java.io.IOException

/** Processor for the text recognition demo.  */
class TextRecognitionProcessor(var callback: ProcessorCallback<String>, var top: IntArray? = null, var bottom: IntArray? = null, var rect: Rect? = null) :
    VisionProcessorBase<Text>(top, bottom, rect) {

    private val detector: TextRecognizer = TextRecognition.getClient()

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: $e")
        }
    }

    override fun detectInImage(image: InputImage): Task<Text> {
        return detector.process(image)
    }

    override fun onSuccess(
            originalCameraImage: Bitmap?,
            results: Text,
            frameMetadata: FrameMetadata
    ) {
        callback.scannedResults(results.text)
    }

    override fun onFailure(e: Exception) {
        Log.w(TAG, "Text detection failed.$e")
        callback.scannedResults("")
    }

    companion object {

        private const val TAG = "TextRecProc"
    }
}
