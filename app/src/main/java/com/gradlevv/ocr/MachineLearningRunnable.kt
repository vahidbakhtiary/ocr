package com.gradlevv.ocr

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.renderscript.*
import com.gradlevv.ocr.java.OCR
import java.io.File
import java.util.*
import kotlin.math.roundToInt

class MachineLearningRunnable : Runnable {

    inner class RunArguments {

        val mContext: Context?
        val mResourceModelFactory: ResourceModelFactory?
        val mBitmap: Bitmap?
        val mFormat: Int
        val mRoiCenterYRatio: Float
        val mIsOcr: Boolean
        val mWidth: Int
        val mSensorOrientation: Int
        val mHeight: Int
        val mScanListener: OnScanListener?
        val mFrameBytes: ByteArray?
        val mObjectDetectFile: File?
        val mObjectListener: OnObjectListener?

        constructor(
            context: Context,
            frameBytes: ByteArray?, width: Int, height: Int, format: Int,
            sensorOrientation: Int, scanListener: OnScanListener?,
            roiCenterYRatio: Float,
            resourceModelFactory: ResourceModelFactory
        ) {
            mFrameBytes = frameBytes
            mBitmap = null
            mWidth = width
            mHeight = height
            mFormat = format
            mScanListener = scanListener
            mSensorOrientation = sensorOrientation
            mRoiCenterYRatio = roiCenterYRatio
            mIsOcr = true
            mObjectListener = null
            mObjectDetectFile = null
            mContext = context
            mResourceModelFactory = resourceModelFactory
        }

        constructor(
            context: Context,
            frameBytes: ByteArray?, width: Int, height: Int, format: Int,
            sensorOrientation: Int, objectListener: OnObjectListener?,
            roiCenterYRatio: Float, objectDetectFile: File?,
            resourceModelFactory: ResourceModelFactory
        ) {
            mFrameBytes = frameBytes
            mBitmap = null
            mWidth = width
            mHeight = height
            mFormat = format
            mScanListener = null
            mSensorOrientation = sensorOrientation
            mRoiCenterYRatio = roiCenterYRatio
            mIsOcr = false
            mObjectListener = objectListener
            mObjectDetectFile = objectDetectFile
            mContext = context
            mResourceModelFactory = resourceModelFactory
        }

    }

    private val queue = LinkedList<RunArguments>()

    @Synchronized
    fun warmUp(context: Context, resourceModelFactory: ResourceModelFactory) {
        if (OCR.isInit() || !queue.isEmpty()) {
            return
        }
        val args = RunArguments(
            context = context,
            null, 0, 0, 0,
            90, null, 0.5f,
            resourceModelFactory
        )
        queue.push(args)

    }


    @Synchronized
    fun post(
        context: Context,
        bytes: ByteArray?, width: Int, height: Int, format: Int, sensorOrientation: Int,
        scanListener: OnScanListener?, roiCenterYRatio: Float,
        resourceModelFactory: ResourceModelFactory
    ) {
        val args = RunArguments(
            context,
            bytes, width, height, format, sensorOrientation,
            scanListener, roiCenterYRatio,
            resourceModelFactory
        )
        queue.push(args)

    }

    @Synchronized
    fun post(
        context: Context,
        bytes: ByteArray?, width: Int, height: Int, format: Int, sensorOrientation: Int,
        objectListener: OnObjectListener, roiCenterYRatio: Float,
        objectDetectFile: File?,
        resourceModelFactory: ResourceModelFactory
    ) {
        val args = RunArguments(
            context,
            bytes, width, height, format, sensorOrientation,
            objectListener, roiCenterYRatio, objectDetectFile,
            resourceModelFactory
        )
        queue.push(args)

    }

    // from https://stackoverflow.com/questions/43623817/android-yuv-nv12-to-rgb-conversion-with-renderscript
    // interestingly the question had the right algorithm for our format (yuv nv21)
    private fun YUV_toRGB(yuvByteArray: ByteArray?, W: Int, H: Int, ctx: Context): Bitmap {
        val rs = RenderScript.create(ctx)
        val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(
            rs,
            Element.U8_4(rs)
        )
        val yuvType = Type.Builder(rs, Element.U8(rs)).setX(
            yuvByteArray!!.size
        )
        val `in` = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)
        val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(W).setY(H)
        val out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)
        `in`.copyFrom(yuvByteArray)
        yuvToRgbIntrinsic.setInput(`in`)
        yuvToRgbIntrinsic.forEach(out)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        out.copyTo(bmp)
        yuvToRgbIntrinsic.destroy()
        rs.destroy()
        `in`.destroy()
        out.destroy()
        return bmp
    }

    private fun getBitmap(
        bytes: ByteArray?, width: Int, height: Int, sensorOrientation: Int,
        roiCenterYRatio: Float, ctx: Context, isOcr: Boolean
    ): Bitmap {
        var orientation = sensorOrientation
        val bitmap = YUV_toRGB(bytes, width, height, ctx)
        orientation %= 360
        val h: Double
        val w: Double
        var x: Int
        var y: Int
        when (orientation) {
            0 -> {
                w = bitmap.width.toDouble()
                h = if (isOcr) w * 302.0 / 480.0 else w
                x = 0
                y = (bitmap.height.toDouble() * roiCenterYRatio - h * 0.5).roundToInt()
            }
            90 -> {
                h = bitmap.height.toDouble()
                w = if (isOcr) h * 302.0 / 480.0 else h
                y = 0
                x = (bitmap.width.toDouble() * roiCenterYRatio - w * 0.5).roundToInt()
            }
            180 -> {
                w = bitmap.width.toDouble()
                h = if (isOcr) w * 302.0 / 480.0 else w
                x = 0
                y = (bitmap.height.toDouble() * (1.0 - roiCenterYRatio) - h * 0.5).roundToInt()

            }
            else -> {
                h = bitmap.height.toDouble()
                w = if (isOcr) h * 302.0 / 480.0 else h
                x = (bitmap.width.toDouble() * (1.0 - roiCenterYRatio) - w * 0.5).roundToInt()
                y = 0
            }
        }

        // make sure that our crop stays within the image
        if (x < 0) {
            x = 0
        }
        if (y < 0) {
            y = 0
        }
        if (x + w > bitmap.width) {
            x = bitmap.width - w.toInt()
        }
        if (y + h > bitmap.height) {
            y = bitmap.height - h.toInt()
        }
        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w.toInt(), h.toInt())
        val matrix = Matrix()
        matrix.postRotate(orientation.toFloat())
        val bm = Bitmap.createBitmap(
            croppedBitmap, 0, 0, croppedBitmap.width, croppedBitmap.height,
            matrix, true
        )
        croppedBitmap.recycle()
        bitmap.recycle()
        return bm
    }

    @get:Synchronized
    private val nextImage: RunArguments
        get() {
            while (queue.size == 0) {
                try {

                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            return queue.pop()
        }

    private fun runObjectModel(bitmap: Bitmap?, args: RunArguments) {
        if (args.mObjectDetectFile == null) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                if (args.mObjectListener != null) {
                    args.mObjectListener.onPrediction(bitmap, bitmap!!.width, bitmap.height)
                }
            }
            return
        }
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                if (args.mObjectListener != null) {
                    args.mObjectListener.onPrediction(bitmap, bitmap!!.width, bitmap.height)
                }
            } catch (e: Error) {
                // prevent callbacks from crashing the app, swallow it
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runOcrModel(bitmap: Bitmap, args: RunArguments) {

        val ocr = com.gradlevv.ocr.java.OCR()
        val number: String? = ocr.predict(bitmap,args.mResourceModelFactory)
        val hadUnrecoverableException: Boolean = ocr.hadUnrecoverableException
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                if (args.mScanListener != null) {
                    if (hadUnrecoverableException) {
                        args.mScanListener.onFatalError()
                    } else {
                        args.mScanListener.onPrediction(
                            number, bitmap, ocr.digitBoxes
                        )
                    }
                }
            } catch (e: Error) {
                // prevent callbacks from crashing the app, swallow it
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runModel() {
        val args = nextImage
        val bm: Bitmap
        if (args.mFrameBytes != null) {
            bm = getBitmap(
                args.mFrameBytes,
                args.mWidth,
                args.mHeight,
                args.mSensorOrientation,
                args.mRoiCenterYRatio,
                args.mContext!!,
                args.mIsOcr
            )
        } else if (args.mBitmap != null) {
            bm = args.mBitmap
        } else {
            bm = Bitmap.createBitmap(480, 302, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            val paint = Paint().apply {
                color = Color.GRAY
            }
            canvas.drawRect(0.0f, 0.0f, 480.0f, 302.0f, paint)
        }
        if (args.mIsOcr) {
            runOcrModel(bm, args)
        } else {
            runObjectModel(bm, args)
        }
    }

    override fun run() {
        while (true) {
            try {
                runModel()
            } catch (e: Error) {
                // center field exception handling, make sure that the ml thread keeps running
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}