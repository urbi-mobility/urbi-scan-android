package co.urbi.android.urbiscan.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import co.urbi.android.urbiscan.AnalyzingGuardActivity
import co.urbi.android.urbiscan.R
import co.urbi.android.urbiscan.core.processor.BarcodeDetectionProcessor
import co.urbi.android.urbiscan.core.ImageAnalyzer
import co.urbi.android.urbiscan.core.processor.ProcessorCallback
import co.urbi.android.urbiscan.databinding.ActivityCameraXBarcodeScanBinding
import co.urbi.android.urbiscan.utils.ImageUtil
import co.urbi.android.urbiscan.utils.PermissionUtils
import co.urbi.android.urbiscan.utils.ScanUtils
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXBarcodeScanActivity : AnalyzingGuardActivity(), ActivityCompat.OnRequestPermissionsResultCallback, ProcessorCallback<String?> {

    private val TAG = "CameraXMrzScanActivity"
    private val requiredPermissions: Array<String> = arrayOf(Manifest.permission.CAMERA)
    private lateinit var binding: ActivityCameraXBarcodeScanBinding
    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var stop = false
    private var flashOn = false

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraXBarcodeScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.flash.setOnClickListener {
            toggleFlashlight()
        }
        if (PermissionUtils.allPermissionsGranted(this, requiredPermissions)) {
            startCamera()
        } else {
            PermissionUtils.getRuntimePermissions(this, requiredPermissions)
        }
    }

    private fun toggleFlashlight() {
        val on = ContextCompat.getDrawable(this, R.drawable.ic_flash_on)
        val off = ContextCompat.getDrawable(this, R.drawable.ic_flash_off)
        if (flashOn) {
            binding.flash.setImageDrawable(off)
        } else {
            binding.flash.setImageDrawable(on)
        }
        flashOn = !flashOn
        camera?.cameraControl?.enableTorch(flashOn)
    }

    private fun startCamera() {
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = binding.viewFinder.display.displayId

            // Bind use cases
            bindCameraUseCases()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down our background executor
        if (::cameraExecutor.isInitialized)
            cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (PermissionUtils.allPermissionsGranted(this, requiredPermissions)) {
            startCamera()
        } else {
            setResult(ScanUtils.RESULT_RETRY, Intent())
            finish()
        }
    }

    override fun scannedResults(result: String?) {
        setAnalysisGuard(false)
        if (!stop && result?.isNotBlank() == true) {
            Log.d("SCANNED RESULTS", result)
            stop = true
            returnScannedString(result)
        }
    }

    private fun returnScannedString(result: String) {
        Intent().apply { putExtra(ScanUtils.BARCODE_SCAN_RESULT, result) }.also { setResult(Activity.RESULT_OK, it) }
        finish()
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = ImageUtil.aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = binding.viewFinder.display.rotation

        // Bind the CameraProvider to the LifeCycleOwner
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            Runnable {

                // CameraProvider
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

// Preview
                preview = Preview.Builder()
// We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
// Set initial target rotation
                    .setTargetRotation(rotation)
                    .build()

// ImageCapture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
// We request aspect ratio but no resolution to match preview config, but letting
// CameraX optimize for whatever specific resolution best fits our use cases
                    .setTargetAspectRatio(screenAspectRatio)
// Set initial target rotation, we will have to call this again if rotation changes
// during the lifecycle of this use case
                    .setTargetRotation(rotation)
                    .build()

// ImageAnalysis
                imageAnalyzer = ImageAnalysis.Builder()
// We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
// Set initial target rotation, we will have to call this again if rotation changes
// during the lifecycle of this use case
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
// The analyzer can then be assigned to the instance
                    .also {
                        it.setAnalyzer(cameraExecutor, ImageAnalyzer(BarcodeDetectionProcessor(this), WeakReference(this)))
                    }

// Must unbind the use-cases before rebinding them
                cameraProvider.unbindAll()

                try {
// A variable number of use-cases can be passed here -
// camera provides access to CameraControl & CameraInfo
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalyzer
                    )

// Attach the viewfinder's surface provider to preview use case
                    preview?.setSurfaceProvider(binding.viewFinder.createSurfaceProvider())
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }
}
