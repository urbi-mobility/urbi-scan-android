package co.urbi.android.urbiscan.core.processor

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import androidx.annotation.GuardedBy
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import co.urbi.android.urbiscan.core.FrameMetadata
import co.urbi.android.urbiscan.utils.JavaUtils
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer

/**
 * Abstract base class for ML Kit frame processors. Subclasses need to implement {@link
 * #onSuccess(T, FrameMetadata, GraphicOverlay)} to define what they want to with the detection
 * results and {@link #detectInImage(FirebaseVisionImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
abstract class VisionProcessorBase<T>(var topLimit: IntArray? = null, var bottomLimit: IntArray? = null, var cropRect: Rect? = null) : VisionImageProcessor {

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private var latestImage: ByteBuffer? = null

    @GuardedBy("this")
    private var latestImageMetaData: FrameMetadata? = null

    // To keep the images and metadata in process.
    @GuardedBy("this")
    private var processingImage: ByteBuffer? = null

    @GuardedBy("this")
    private var processingMetaData: FrameMetadata? = null

    override fun getCoordinatesLimit(): Triple<IntArray?, IntArray?, Rect?> {
        return Triple(topLimit ?: intArrayOf(), bottomLimit ?: intArrayOf(), cropRect)
    }

    // Image version
    @Synchronized
    @ExperimentalGetImage
    override fun process(
        image: ImageProxy
    ) {
        processImageProxy(image)
    }

    private fun processImage(
            data: ByteBuffer,
            frameMetadata: FrameMetadata
    ) {
        val inputImage = InputImage.fromByteBuffer(
            data,
            frameMetadata.width,
            frameMetadata.height,
            frameMetadata.rotation,
            InputImage.IMAGE_FORMAT_NV21
        )

        val bitmap = JavaUtils.getBitmap(data, frameMetadata)
        detectInVisionImage(
            bitmap,
            inputImage,
            frameMetadata
        )
    }

    private fun processImage(
        image: Image,
        rotation: Int
    ) {
        detectInVisionImage(
            null,
            InputImage.fromMediaImage(image, rotation),
            null
        )
    }

    @ExperimentalGetImage
    private fun processImageProxy(
        image: ImageProxy
    ) {
        val imageToAnalyze = InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)
        detectInImage(imageToAnalyze)
            .addOnSuccessListener { results ->
                onSuccess(
                    null,
                    results,
                    FrameMetadata.Builder().build()
                )
            }
            .addOnFailureListener {
                e ->
                onFailure(e)
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    private fun processImage(
        data: ByteArray,
        frameMetadata: FrameMetadata
    ) {
        val inputImage = InputImage.fromByteArray(
            data,
            frameMetadata.width,
            frameMetadata.height,
            frameMetadata.getConvertedRotation(),
            InputImage.IMAGE_FORMAT_YV12 // TODO set dynamic
        )

        detectInVisionImage(
            null,
            inputImage,
            frameMetadata
        )
    }

    private fun detectInVisionImage(
        originalCameraImage: Bitmap?,
        image: InputImage,
        metadata: FrameMetadata?
    ) {
        detectInImage(image)
            .addOnSuccessListener { results ->
                onSuccess(
                    originalCameraImage,
                    results,
                    metadata ?: FrameMetadata.Builder().build()
                )
            }
            .addOnFailureListener {
                e ->
                onFailure(e)
            }
    }

    override fun stop() {}

    protected abstract fun detectInImage(image: InputImage): Task<T>

    /**
     * Callback that executes with a successful detection result.
     *
     * @param originalCameraImage hold the original image from camera, used to draw the background
     * image.
     */
    protected abstract fun onSuccess(
        originalCameraImage: Bitmap?,
        results: T,
        frameMetadata: FrameMetadata
    )

    protected abstract fun onFailure(e: Exception)
}
