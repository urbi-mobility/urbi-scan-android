package co.urbi.android.urbiscan.core.processor

import android.graphics.Bitmap
import android.util.Log
import co.urbi.android.urbiscan.core.FrameMetadata
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.IOException

class BarcodeDetectionProcessor(var callback: ProcessorCallback<String?>) : VisionProcessorBase<List<Barcode>>() {

    private val detector: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Barcode Detector: $e")
        }
    }

    override fun detectInImage(image: InputImage): Task<List<Barcode>> {
        return detector.process(image)
    }

    override fun onSuccess(
            originalCameraImage: Bitmap?,
            results: List<Barcode>,
            frameMetadata: FrameMetadata
    ) {

        for (i in results.indices) {
            val barcode = results[i]
            callback.scannedResults(barcode.displayValue)
        }
        callback.scannedResults(null)
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Barcode detection failed $e")
    }

    companion object {
        private const val TAG = "BarcodeDetectionProc"
    }
}
