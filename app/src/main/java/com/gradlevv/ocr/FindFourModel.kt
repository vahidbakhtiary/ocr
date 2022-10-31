package com.gradlevv.ocr

import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import javax.inject.Inject


class FindFourModel @Inject constructor(private val resourceModel: ResourceModelFactory) {

    /**
     * Preallocated buffers for storing image data in.
     */
    private val intValues = IntArray(imageSizeX * imageSizeY)

    /**
     * Options for configuring the Interpreter.
     */
    private val tfliteOptions: Interpreter.Options = Interpreter.Options()

    /**
     * The loaded TensorFlow Lite model.
     */
    private var tfliteModel: MappedByteBuffer? = null

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    private var tflite: Interpreter? = null

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    private var imgData: ByteBuffer? = null

    val rows = 34
    val cols = 51
    val boxSize: CGSize = CGSize(80.toFloat(), 36.toFloat())
    val cardSize: CGSize = CGSize(480.toFloat(), 302.toFloat())

    private val labelProbArray: Array<Array<Array<FloatArray>>>

    private val imageSizeX: Int
        get() = 480
    private val imageSizeY: Int
        get() = 302


    private val numBytesPerChannel: Int
        get() = 4

    /**
     * Initializes an `ImageClassifierFloatMobileNet`.
     */
    init {

        tfliteModel = loadModelFile()

        tflite = Interpreter(tfliteModel!!, tfliteOptions)

        imgData = ByteBuffer.allocateDirect(
            DIM_BATCH_SIZE
                    * imageSizeX
                    * imageSizeY
                    * DIM_PIXEL_SIZE
                    * numBytesPerChannel
        )
        imgData?.order(ByteOrder.nativeOrder())

        val classes = 3
        labelProbArray = Array(1) {
            Array(rows) {
                Array(cols) {
                    FloatArray(classes)
                }
            }
        }
    }


    /**
     * Classifies a frame from the preview stream.
     */
    fun classifyFrame(bitmap: Bitmap) {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.")
        }
        convertBitmapToByteBuffer(bitmap)
        // Here's where the magic happens!!!
        runInference()
    }

    private fun recreateInterpreter() {
        if (tflite != null) {
            tflite?.close()
            tflite = Interpreter(tfliteModel!!, tfliteOptions)
        }
    }

    fun setNumThreads(numThreads: Int) {
        tfliteOptions.setNumThreads(numThreads)
        recreateInterpreter()
    }


    /**
     * Writes Image data into a `ByteBuffer`.
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            return
        }
        imgData?.rewind()

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            imageSizeX,
            imageSizeY, false
        )
        resizedBitmap.getPixels(
            intValues, 0, resizedBitmap.width, 0, 0,
            resizedBitmap.width, resizedBitmap.height
        )
        // Convert the image to floating point.
        var pixel = 0
        for (i in 0 until imageSizeX) {
            for (j in 0 until imageSizeY) {
                val `val` = intValues[pixel++]
                addPixelValue(`val`)
            }
        }
    }

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
     * of the super class, because we need a primitive array here.
     */
    fun hasDigits(row: Int, col: Int): Boolean {
        return digitConfidence(row, col) >= 0.5
    }


    fun digitConfidence(row: Int, col: Int): Float {
        val digitClass = 1
        return labelProbArray[0][row][col][digitClass]
    }

    private fun loadModelFile(): MappedByteBuffer {
        return resourceModel.loadFindFourFile()
    }

    private fun addPixelValue(pixelValue: Int) {
        imgData?.putFloat((pixelValue shr 16 and 0xFF) / 255f)
        imgData?.putFloat((pixelValue shr 8 and 0xFF) / 255f)
        imgData?.putFloat((pixelValue and 0xFF) / 255f)
    }

    private fun runInference() {
        tflite?.run(imgData, labelProbArray)
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "CardScan"

        /**
         * Dimensions of inputs.
         */
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
    }
}