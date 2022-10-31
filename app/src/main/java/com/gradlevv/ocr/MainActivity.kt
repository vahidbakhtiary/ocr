package com.gradlevv.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.gradlevv.ocr.di.DaggerScannerComponent
import com.gradlevv.ocr.java.DetectedBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Semaphore
import javax.inject.Inject

class MainActivity : AppCompatActivity(), Camera.PreviewCallback,
    View.OnClickListener, OnScanListener, OnObjectListener {

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        daggerSetup()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createUi()
        setUpUi()
    }

    private fun daggerSetup() {
        DaggerScannerComponent.builder().application(this.application).create().inject(this)
    }

    private lateinit var root: FrameLayout
    private lateinit var scannerLayout: LinearLayout

    private lateinit var mCamera: Camera

    private val mMachineLearningSemaphore = Semaphore(1)
    private var mRotation = 0
    private var mSentResponse = false
    private var numberResults = HashMap<String, Int>()
    private var firstResultMs: Long = 0
    private var mFlashlightId = 0
    private var mCardNumberId = 0
    private var mExpiryId = 0
    private var mTextureId = 0
    private var mRoiCenterYRatio = 0f
    private var mIsOcr = true

    private var machineLearningRunnable: MachineLearningRunnable? = null

    // set when this activity posts to the machineLearningThread
    var mPredictionStartMs: Long = 0


    private var mShowNumberAndExpiryAsScanning = true
    private var objectDetectFile: File? = null

    var errorCorrectionDurationMs: Long = 0


    @RequiresApi(Build.VERSION_CODES.M)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { permission ->
            when (permission) {
                true -> {
                    startCamera()
                }
                false -> {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        showPermissionBottomSheet(true)
                    } else {
                        showPermissionBottomSheet(false)
                    }
                }
            }
        }


    @Inject
    lateinit var resourceModel: ResourceModelFactory

    private val numberResult: String?
        get() {
            var result: String? = null
            var maxValue = 0
            for (number in numberResults.keys) {
                var value = 0
                val count = numberResults[number]
                if (count != null) {
                    value = count
                }
                if (value > maxValue) {
                    result = number
                    maxValue = value
                }
            }
            return result
        }

    private fun createUi(): View {

        root = findViewById(R.id.rootView)

        scannerLayout = LinearLayout(this@MainActivity)

        root.addView(
            scannerLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                topMargin = 120
            })

        return root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setUpUi() {

        val isFirstTimeCheckPermission = false

        if (!isPermissionGranted(Manifest.permission.CAMERA)) {

            when {
                !isFirstTimeCheckPermission -> {
                    showPermissionBottomSheet(true)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    showPermissionBottomSheet(true)
                }
                else -> {
                    showPermissionBottomSheet(false)
                }
            }
        } else {
            startCamera()
        }

    }

    private fun isPermissionGranted(permission: String?): Boolean {
        return (ActivityCompat.checkSelfPermission(this, permission!!)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun startCamera() {

        firstResultMs = 0

        lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                onCameraOpen()
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showPermissionBottomSheet(isGoingToAppSetting: Boolean) {
        if (isGoingToAppSetting) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {

//        if (mMachineLearningSemaphore.tryAcquire()) {
        val parameters = camera?.parameters
        val width = parameters?.previewSize?.width ?: 0
        val height = parameters?.previewSize?.height ?: 0
        val format = parameters?.previewFormat ?: 0
        mPredictionStartMs = SystemClock.uptimeMillis()

        // Use the application context here because the machine learning thread's lifecycle
        // is connected to the application and not this activity

        lifecycleScope.launch(Dispatchers.Main) {

            withContext(Dispatchers.IO) {

                if (machineLearningRunnable == null) {
                    machineLearningRunnable = MachineLearningRunnable()
                    Thread(machineLearningRunnable).start()
                }

                machineLearningRunnable?.warmUp(this@MainActivity, resourceModel)
                machineLearningRunnable?.post(
                    this@MainActivity,
                    data,
                    width,
                    height,
                    format,
                    mRotation,
                    this@MainActivity,
                    mRoiCenterYRatio,
                    resourceModel
                )
            }

        }

//        }
    }

    override fun onClick(v: View?) {
        if (mFlashlightId == v?.id) {
            val parameters = mCamera.parameters
            if (parameters.flashMode == Camera.Parameters.FLASH_MODE_TORCH) {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            } else {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            }
            mCamera.parameters = parameters
            mCamera.startPreview()
        }
    }


    private fun onCameraOpen() {

        mCamera = Camera.open()

        setCameraDisplayOrientation(mCamera)
        // Create our Preview view and set it as the content of our activity.
        val cameraPreview = CameraPreview(this, this)

        lifecycleScope.launch(Dispatchers.Main) {
            scannerLayout.addView(cameraPreview)
        }
        mCamera.setPreviewCallback(this)

    }

    override fun onObjectFatalError() {

    }

    override fun onDestroy() {
        super.onDestroy()
        mCamera.stopPreview()
        mCamera.setPreviewCallback(null)
        mCamera.release()
        machineLearningRunnable = null
    }

    private fun setCameraDisplayOrientation(camera: Camera) {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)
        val rotation = this.windowManager.defaultDisplay
            .rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        camera.setDisplayOrientation(result)
        mRotation = result
    }


    override fun onPrediction(
        number: String?,
        bitmap: Bitmap?,
        digitBoxes: List<DetectedBox>
    ) {

        if (number != null && firstResultMs == 0L) {
            firstResultMs = SystemClock.uptimeMillis()
        }
        number?.let { incrementNumber(it) }
        val duration = SystemClock.uptimeMillis() - firstResultMs
        if (firstResultMs != 0L && mShowNumberAndExpiryAsScanning) {

        }
        if (firstResultMs != 0L && duration >= errorCorrectionDurationMs) {

            val numberResult = numberResult
            Toast.makeText(this, "$numberResult", Toast.LENGTH_SHORT).show()
            //todo result is here

        }

        if (!numberResult.isNullOrEmpty()) {
            mMachineLearningSemaphore.release()
        }
    }

    override fun onPrediction(bm: Bitmap?, imageWidth: Int, imageHeight: Int) {
        if (!mSentResponse) {
            // do something with the prediction
        }
        mMachineLearningSemaphore.release()
    }

    override fun onFatalError() {

    }

    private fun incrementNumber(number: String) {
        var currentValue = numberResults[number]
        if (currentValue == null) {
            currentValue = 0
        }
        numberResults[number] = currentValue + 1
    }

    private fun setValueAnimated(textView: TextView, value: String) {
        if (textView.visibility != View.VISIBLE) {
            textView.visibility = View.VISIBLE
            textView.alpha = 0.0f
        }
        textView.text = value
    }

    /**
     * A basic Camera preview class
     */
    inner class CameraPreview(
        context: Context,
        private val mPreviewCallback: Camera.PreviewCallback
    ) : SurfaceView(context), Camera.AutoFocusCallback, SurfaceHolder.Callback {
        private val mHolder: SurfaceHolder = holder

        init {

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder.addCallback(this)
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
            val params = mCamera.parameters
            val focusModes = params?.supportedFocusModes
            if (focusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            } else if (focusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }
            params?.setRecordingHint(true)
            mCamera.parameters = params
        }

        override fun onAutoFocus(success: Boolean, camera: Camera) {}
        override fun surfaceCreated(holder: SurfaceHolder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                if (mCamera == null) return
                mCamera.setPreviewDisplay(holder)
                mCamera.startPreview()
            } catch (e: IOException) {
                Log.d("CameraCaptureActivity", "Error setting camera preview: " + e.message)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.
            if (mHolder.surface == null) {
                // preview surface does not exist
                return
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview()
            } catch (e: Exception) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder)
                mCamera.setPreviewCallback(mPreviewCallback)
                mCamera.startPreview()
            } catch (e: Exception) {
                Log.d("CameraCaptureActivity", "Error starting camera preview: " + e.message)
            }
        }
    }
}