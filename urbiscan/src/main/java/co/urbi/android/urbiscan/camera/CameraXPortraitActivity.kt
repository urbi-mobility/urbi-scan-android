package co.urbi.android.urbiscan.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import co.urbi.android.urbiscan.AnalyzingGuardActivity
import co.urbi.android.urbiscan.R
import co.urbi.android.urbiscan.core.ImageAnalyzer
import co.urbi.android.urbiscan.core.processor.FaceDetectResult
import co.urbi.android.urbiscan.core.processor.FaceDetectionProcessor
import co.urbi.android.urbiscan.core.processor.ProcessorCallback
import co.urbi.android.urbiscan.databinding.ActivityCameraXPortraitBinding
import co.urbi.android.urbiscan.utils.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraXPortraitActivity : AnalyzingGuardActivity(), ActivityCompat.OnRequestPermissionsResultCallback,
        ProcessorCallback<FaceDetectResult> {

    private val TAG = "CameraXPortraitActivity"
    private val requiredPermissions: Array<String> = arrayOf(Manifest.permission.CAMERA)
    private lateinit var binding: ActivityCameraXPortraitBinding

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService
    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var faceOK = AtomicBoolean(false)

    private lateinit var outputDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraXPortraitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (PermissionUtils.allPermissionsGranted(this, requiredPermissions)) {
            startCamera()
        } else {
            PermissionUtils.getRuntimePermissions(this, requiredPermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        if (PermissionUtils.allPermissionsGranted(this, requiredPermissions)) {
            fullScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down our background executor
        if (::cameraExecutor.isInitialized)
            cameraExecutor.shutdown()
    }

    private fun fullScreen() {
        binding.cameraContainer.postDelayed(
                {
                    binding.cameraContainer.systemUiVisibility = FLAGS_FULLSCREEN
                },
                ScanUtils.IMMERSIVE_FLAG_TIMEOUT
        )
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraUi()
    }

    override fun scannedResults(result: FaceDetectResult) {
        setAnalysisGuard(false)
        if (faceOK.get()) {
            when (result) {
                FaceDetectResult.MORE_FACE -> {
                    faceOK.set(false)
                }
                FaceDetectResult.NO_FACE -> {
                    faceOK.set(false)
                }
            }
            if (!faceOK.get()) {
                Toast.makeText(this, getString(R.string.face_not_ok), Toast.LENGTH_LONG).show()
            }
        } else {
            when (result) {
                FaceDetectResult.CLOSED_EYES -> {
                    faceOK.set(true)
                }
                FaceDetectResult.FACE_NOT_SMILING -> {
                    faceOK.set(true)
                }
                FaceDetectResult.FACE_OK -> {
                    faceOK.set(true)
                }
            }
            if (faceOK.get()) {
                Toast.makeText(this, getString(R.string.face_ok), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Use external media if it is available, our app's file directory otherwise */
    private fun getOutputDirectory(): File {
        val appContext = applicationContext
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    private fun startCamera() {
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
// Determine the output directory
        outputDirectory = getOutputDirectory()
        binding.viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = binding.viewFinder.display.displayId
            // Build UI controls
            updateCameraUi()
            // Bind use cases
            bindCameraUseCases()
        }
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {

        // Listener for button used to capture photo
        binding.cameraCaptureButton.setOnClickListener {

            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->

                // Create output file to hold the image
                val photoFile = ImageUtil.createFile(outputDirectory, ImageUtil.FILENAME, ImageUtil.PHOTO_EXTENSION)

                // Setup image capture metadata
                val metadata = ImageCapture.Metadata().apply {

                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                // Create output options object which contains file + metadata
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                        .setMetadata(metadata)
                        .build()

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                Log.d(TAG, "Photo capture succeeded: $savedUri")

                                // We can only change the foreground Drawable using API level 23+ API
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    // Update the gallery thumbnail with latest picture taken
//                            setGalleryThumbnail(savedUri)
                                }

                                // Implicit broadcasts will be ignored for devices running API level >= 24
                                // so if you only target API level 24+ you can remove this statement
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                    sendBroadcast(
                                            Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                                    )
                                }

                                // If the folder selected is an external media directory, this is
                                // unnecessary but otherwise other apps will not be able to access our
                                // images unless we scan them using [MediaScannerConnection]
                                val mimeType = MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(savedUri.toFile().extension)
                                MediaScannerConnection.scanFile(
                                        this@CameraXPortraitActivity,
                                        arrayOf(savedUri.toString()),
                                        arrayOf(mimeType)
                                ) { _, uri ->
                                    Log.d(TAG, "Image capture scanned into media store: $uri")
                                }

                                savedUri?.let { uri ->
                                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                                    // Get screen metrics used to setup camera for full screen resolution
                                    val finalRes = bitmap.let { toOrientBitmap ->
                                        val matrix = Matrix().apply {
                                            postRotate(toOrientBitmap.fixOrientation())
                                            preScale(-1f, 1f)
                                        }
                                        val orientedBitmap = Bitmap.createBitmap(toOrientBitmap, 0, 0, toOrientBitmap.width, toOrientBitmap.height, matrix, true)
                                        val pixelTopCrop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics).toInt()
                                        val croppedBitmap = Bitmap.createBitmap(orientedBitmap, 0, pixelTopCrop, orientedBitmap.width, (orientedBitmap.height * 0.62).toInt())
                                        var ratio = ImageUtil.getRatio(croppedBitmap.height, ImageUtil.MAX_HEIGHT)
                                        var targetHeight = (croppedBitmap.height * ratio).toInt()
                                        var targetWidth = (croppedBitmap.width * ratio).toInt()

                                        // Check if width is under limit
                                        if (targetWidth > ImageUtil.MAX_WIDTH) {
                                            ratio = ImageUtil.getRatio(targetWidth, ImageUtil.MAX_WIDTH)
                                            targetHeight = (targetHeight * ratio).toInt()
                                            targetWidth = (targetWidth * ratio).toInt()
                                        }

                                        Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, false)
                                    }
                                    sendBitmapResult(finalRes)
                                }
                            }
                        }
                )

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    binding.cameraContainer.postDelayed(
                            {
                                binding.cameraContainer.foreground = ColorDrawable(Color.WHITE)
                                binding.cameraContainer.postDelayed(
                                        { binding.cameraContainer.foreground = null },
                                        ANIMATION_FAST_MILLIS
                                )
                            },
                            ANIMATION_SLOW_MILLIS
                    )
                }
            }
        }

        // Listener for button used to switch cameras
        binding.cameraSwitchButton.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            // Re-bind use cases to update selected camera
            bindCameraUseCases()
        }
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
                                it.setAnalyzer(cameraExecutor, ImageAnalyzer(FaceDetectionProcessor(this), WeakReference(this)))
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

    private fun sendBitmapResult(pic: Bitmap) {
        val resultIntent = Intent()
        resultIntent.apply { putExtra(ScanUtils.PORTRAIT_RESULT_BITMAP, pic) }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        if (PermissionUtils.allPermissionsGranted(this, requiredPermissions)) {
            startCamera()
            fullScreen()
        } else {
            setResult(Activity.RESULT_CANCELED, Intent())
            finish()
        }
    }
}
