package com.sourcepad.opensource.ezcamera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.NonNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraView : FrameLayout {


    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private var textureView: AutoFitTextureView = AutoFitTextureView(context, null, 0)

    init {
        (context as Activity).window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN

        setBackgroundColor(Color.BLACK)
    }

    private var cameraId: String = ""
    private lateinit var manager: CameraManager
    private var blinkerTimer: Timer? = null
    private var path: File? = null

    private var cameraDevice: CameraDevice? = null
    private var previewSessionHighSpeed: CameraConstrainedHighSpeedCaptureSession? = null
    private var previewSession: CameraCaptureSession? = null
    //Must be at least 120 fps
    private lateinit var fps: Range<Int>

    lateinit var videoSize: Size
    private var previewBuilder: CaptureRequest.Builder? = null
    private var mediaRecorder: MediaRecorder? = null
    private var surfaces: MutableList<Surface> = ArrayList()

    private var isRecordingVideo = false
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "Surface texture available $width x $height")

            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "Surface texture size changed")

            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            Log.d(TAG, "Surface texture destroyed")

            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {

        }


    }


        private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(@NonNull cameraDevice: CameraDevice) {
            this@CameraView.cameraDevice = cameraDevice
            Log.d(TAG, "Camera opened")
            startPreview()
            cameraOpenCloseLock.release()
        }

        override fun onDisconnected(@NonNull cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraView.cameraDevice = null
            Log.d(TAG, "Camera disconnected")

        }

        override fun onError(@NonNull cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()

            this@CameraView.cameraDevice = null
            Log.d(TAG, "Camera error")
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            Log.d(TAG, "Camera closed")

        }


    }


    fun isRecording(): Boolean {
        return isRecordingVideo
    }


    private fun getFpsVideoSizePair(map: StreamConfigurationMap): kotlin.Pair<Range<Int>, Size>? {
        val fpsRange = map.highSpeedVideoFpsRanges.filter { it.lower == it.upper }
        val lowestSlowMoFps = fpsRange.minBy { it.upper }
        val supportedSizeList = map.getHighSpeedVideoSizesFor(lowestSlowMoFps)
        val highestPossibleResolution = supportedSizeList.maxBy { it.width }

        for (size in map.getHighSpeedVideoSizesFor(lowestSlowMoFps)) {
            Log.d("SIZE FOR FPS RANGE", "$lowestSlowMoFps -- $size")
        }

        if (lowestSlowMoFps != null && highestPossibleResolution != null) {
            return lowestSlowMoFps to highestPossibleResolution
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            Log.d(TAG, "tryAcquire")
            if (!cameraOpenCloseLock.tryAcquire(3500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            cameraId = manager.cameraIdList[0]

            val characteristics = manager.getCameraCharacteristics(cameraId)

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val fpsResolutionConfig = getFpsVideoSizePair(map!!)

            if (fpsResolutionConfig != null) {
                fps = fpsResolutionConfig.first
                videoSize = fpsResolutionConfig.second
//                textureView.setAspectRatio()
//                textureView.setAspectRatio(videoSize.width, videoSize.height)

//                previewSize = chooseOptimalSize(
//                    map.getOutputSizes(SurfaceTexture::class.java),
//                    width, height, videoSize!!)
//                            previewSize = videoSize

            } else {
                throw UnsupportedOperationException()
            }

            Log.d(TAG, "FPS $fps")
            Log.d(TAG, "VideoDto size $videoSize")

            configureTransform(width, height)

            mediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, stateCallback, null)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot access the camera.", Toast.LENGTH_SHORT).show()
            (context as Activity).finish()
        } catch (e: NullPointerException) {
            e.printStackTrace()
            Toast.makeText(context, "Camera2 API not supported.", Toast.LENGTH_SHORT).show()
            (context as Activity).finish()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        } catch (e: UnsupportedOperationException) {
            e.printStackTrace()
            Toast.makeText(context, "Camera cannot record slow motion videos", Toast.LENGTH_SHORT)
                .show()
            (context as Activity).finish()
        }

    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != cameraDevice) {
                cameraDevice?.close()
                cameraDevice = null
            }
            if (null != mediaRecorder) {
                mediaRecorder?.release()
                mediaRecorder = null
            }

        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } finally {
            cameraOpenCloseLock.release()
        }

    }


    /**
     * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(
        choices: Array<Size>,
        width: Int,
        height: Int,
        aspectRatio: Size
    ): Size {
        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        choices.forEach {
            if (it.height == it.width * h / w &&
                it.width <= width && it.height <= height
            ) {
                bigEnough.add(it)
            }
        }
        // Pick the smallest of those, assuming we found any
        return if (bigEnough.size > 0) {
            Collections.max(bigEnough, CompareSizesByArea())
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size")
            choices[0]
        }
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }

    }

    fun onResume() {

        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        startBackgroundThread()
        if (textureView.isAvailable) {
            Log.d(TAG, "Open camera ${textureView.width} x ${textureView.height}")
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    fun onPause() {
        if (isRecordingVideo) {
            stopRecordingVideoOnPause()
        }
        removeView(textureView)

        closeCamera()
        stopBackgroundThread()
    }


    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = context as Activity
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, videoSize.height.toFloat(), videoSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / videoSize.height,
                viewWidth.toFloat() / videoSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        Log.d(TAG, "Configure transform $matrix $width x $height")
        textureView.setTransform(matrix)
    }


    //    private MediaFormat mMediaFormat;
    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val activity = context as Activity
        mediaRecorder?.run {
            path = getVideoFile()
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(path?.absolutePath)
            setMaxDuration(5000)
            setVideoEncodingBitRate(20000000)
            setVideoFrameRate(fps.upper)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            val rotation = activity.windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS.get(rotation)
            setOrientationHint(orientation)
            prepare()
            Log.d(TAG, "Setup media recorder")
        }

    }

    /**
     * This method chooses where to save the video and what the name of the video file is
     *
     * @param context where the camera activity is
     * @return path + filename
     */
    private fun getVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(
            context.filesDir,
            "VID_$timeStamp.mp4"
        )
    }

    fun startRecordingVideo() {
        try {
            // UI
            isRecordingVideo = true
            previewBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            mediaRecorder?.start()

        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun stopRecordingVideo(onRecordingFinished: (file: File) -> Unit) {
        if (isRecordingVideo) {
            isRecordingVideo = false
            blinkerTimer?.cancel()
            try {
                mediaRecorder?.run {
                    stop()
                    reset()
                }
                path?.let { onRecordingFinished.invoke(it) }

            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }

        }

    }

    private fun stopRecordingVideoOnPause() {


        try {
            previewSessionHighSpeed?.stopRepeating()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        mediaRecorder?.run {
            stop()
            reset()
        }
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        if (null == cameraDevice || !textureView.isAvailable) {
            return
        }
        try {
            Log.d(TAG, "Start preview")

            surfaces.clear()
            val texture = textureView.surfaceTexture
            texture.setDefaultBufferSize(videoSize.width, videoSize.height)

            Log.d(TAG, "Set texture buffer ${videoSize.width} x ${videoSize.height}")

            previewBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            previewBuilder?.addTarget(previewSurface)

            setUpMediaRecorder()
            val recorderSurface = mediaRecorder?.surface
            if (recorderSurface != null) {
                surfaces.add(recorderSurface)
                previewBuilder?.addTarget(recorderSurface)
            }

            startConstrainedHighSpeedCaptureSession()

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun startConstrainedHighSpeedCaptureSession() {
        cameraDevice?.createConstrainedHighSpeedCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {

                override fun onActive(session: CameraCaptureSession) {
                    Log.d(TAG, "On session active")

                    previewSession = session
                    previewSessionHighSpeed =
                        previewSession as CameraConstrainedHighSpeedCaptureSession?
                }

                override fun onReady(session: CameraCaptureSession) {
                    Log.d(TAG, "On session ready")
                    previewSession = session
                    previewSessionHighSpeed =
                        previewSession as CameraConstrainedHighSpeedCaptureSession?
                    updatePreview()
                }

                override fun onConfigured(@NonNull cameraCaptureSession: CameraCaptureSession) {
                    previewSession = cameraCaptureSession
                    previewSessionHighSpeed =
                        previewSession as CameraConstrainedHighSpeedCaptureSession?
                    Log.d(TAG, "On session configured")

                }

                override fun onConfigureFailed(@NonNull cameraCaptureSession: CameraCaptureSession) {
                    val activity = context as Activity
                    Log.d("ERROR", "COULD NOT START CAMERA")
                    Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                }


            },
            backgroundHandler
        )
    }


    private fun updatePreview() {
        if (null == cameraDevice) {
            return
        }
        try {
            val thread = HandlerThread("CameraHighSpeedPreview")
            thread.start()

            startCaptureRequest()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            backgroundHandler?.postDelayed({ updatePreview() }, 1000)
            e.printStackTrace()
        }
    }

    private fun startCaptureRequest() {

        previewBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)

        previewBuilder?.run {
            val previewBuilderBurst =
                previewSessionHighSpeed?.createHighSpeedRequestList(this.build())
            previewBuilderBurst?.let {
                previewSessionHighSpeed?.setRepeatingBurst(
                    it,
                    object : CameraCaptureSession.CaptureCallback() {
                    },
                    backgroundHandler
                )
            }
        }
    }


    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }


    companion object {
        private const val TAG = "CameraView"
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

    }
}