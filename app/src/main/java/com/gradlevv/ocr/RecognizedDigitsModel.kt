package com.gradlevv.ocr

import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import javax.inject.Inject

class RecognizedDigitsModel @Inject constructor(
    private val resourceModel: ResourceModelFactory
) {

    private val classes = 11

    private val imageSizeX: Int
        get() = 80
    private val imageSizeY: Int
        get() = 36
    private val numBytesPerChannel: Int
        get() = 4

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
     * of the super class, because we need a primitive array here.
     */
    private val labelProbArray: Array<Array<Array<FloatArray>>> = Array(1) {
        Array(1) {
            Array(kNumPredictions) {
                FloatArray(classes)
            }
        }
    }

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
    var tflite: Interpreter? = null

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    var imgData: ByteBuffer? = null

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
        imgData!!.rewind()
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


    inner class ArgMaxAndConfidence(val argMax: Int, val confidence: Float)

    fun argAndValueMax(col: Int): ArgMaxAndConfidence {
        var maxIdx = -1
        var maxValue = (-1.0).toFloat()
        for (idx in 0 until classes) {
            val value = labelProbArray[0][0][col][idx]
            if (value > maxValue) {
                maxIdx = idx
                maxValue = value
            }
        }
        return ArgMaxAndConfidence(maxIdx, maxValue)
    }

    private fun loadModelFile(): MappedByteBuffer {
        return resourceModel.loadRecognizeDigitsFile()
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
        const val kNumPredictions = 17

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