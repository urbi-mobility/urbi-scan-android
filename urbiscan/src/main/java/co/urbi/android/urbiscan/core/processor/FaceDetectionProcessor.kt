package co.urbi.android.urbiscan.core.processor

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import co.urbi.android.urbiscan.core.FrameMetadata
import co.urbi.android.urbiscan.utils.getNotNullValue
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.IOException

/** Face Detector Demo.  */
class FaceDetectionProcessor(var callback: ProcessorCallback<FaceDetectResult>, var top: IntArray? = null, var bottom: IntArray? = null, var rect: Rect? = null) :
    VisionProcessorBase<List<Face>>(top, bottom, rect) {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()

        detector = FaceDetection.getClient(options)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Face Detector: $e")
        }
    }

    override fun detectInImage(image: InputImage): Task<List<Face>> {
        return detector.process(image)
    }

    override fun onSuccess(
            originalCameraImage: Bitmap?,
            results: List<Face>,
            frameMetadata: FrameMetadata
    ) {
        if (results.isEmpty()) {
            callback.scannedResults(FaceDetectResult.NO_FACE)
        } else {
            if (results.size > 1) {
                callback.scannedResults(FaceDetectResult.MORE_FACE)
            } else {
                results.firstOrNull()?.let {
                    val isSmiling = it.smilingProbability.getNotNullValue() >= 0.5
                    val isOpenEyes = it.leftEyeOpenProbability.getNotNullValue() >= 0.5 && it.rightEyeOpenProbability.getNotNullValue() >= 0.5
                    when {
                        !isSmiling -> callback.scannedResults(FaceDetectResult.FACE_NOT_SMILING)
                        !isOpenEyes -> callback.scannedResults(FaceDetectResult.CLOSED_EYES)
                        else -> callback.scannedResults(FaceDetectResult.FACE_OK)
                    }
                }
            }
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Face detection failed $e")
    }

    companion object {

        private const val TAG = "FaceDetectionProcessor"
    }
}

sealed class FaceDetectResult {
    object FACE_OK : FaceDetectResult()
    object NO_FACE : FaceDetectResult()
    object MORE_FACE : FaceDetectResult()
    object FACE_NOT_SMILING : FaceDetectResult()
    object CLOSED_EYES : FaceDetectResult()
}
