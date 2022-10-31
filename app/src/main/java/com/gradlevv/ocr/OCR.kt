package com.gradlevv.ocr

import android.graphics.Bitmap
import android.util.Log
import com.gradlevv.ocr.java.DetectedBox
import com.gradlevv.ocr.java.PostDetectionAlgorithm
import javax.inject.Inject

class OCR @Inject constructor(
    private val resourceModel: ResourceModelFactory
) {

    var digitBoxes: List<DetectedBox> = listOf()
    var hadUnrecoverableException = false

    private fun detectBoxes(image: Bitmap): ArrayList<com.gradlevv.ocr.java.DetectedBox> {
        val boxes = ArrayList<com.gradlevv.ocr.java.DetectedBox>()
        for (row in 0 until findFour!!.rows) {
            for (col in 0 until findFour!!.cols) {
                if (findFour?.hasDigits(row, col) == true) {
                    val confidence = findFour!!.digitConfidence(row, col)
                    val imageSize = CGSize(image.width.toFloat(), image.height.toFloat())
                    val box = com.gradlevv.ocr.java.DetectedBox(
                        row, col, confidence, findFour!!.rows,
                        findFour!!.cols, findFour!!.boxSize, findFour!!.cardSize, imageSize
                    )
                    boxes.add(box)
                }
            }
        }
        return boxes
    }


    private fun runModel(image: Bitmap): String? {
//        findFour?.classifyFrame(image)
//        var boxes = detectBoxes(image)
//        val postDetection =
//            PostDetectionAlgorithm(boxes, findFour!!)
//        val recognizeNumbers = RecognizeNumbers(image, findFour!!.rows, findFour!!.cols)
//        val lines: ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>> = postDetection.horizontalNumbers()
//        var number: String? = recognizeNumbers.number(recognizedDigitsModel!!, lines)
//        if (number == null) {
//            val verticalLines: ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>> = postDetection.verticalNumbers()
//            number = recognizeNumbers.number(recognizedDigitsModel!!, verticalLines)
//            lines.addAll(verticalLines)
//        }
//        boxes = ArrayList<com.gradlevv.ocr.java.DetectedBox>()
//        for (numbers in lines) {
//            boxes.addAll(numbers)
//        }
//
//        digitBoxes = boxes
//        return number
        return null
    }

    @Synchronized
    fun predict(image: Bitmap): String? {

        val NUM_THREADS = 4

        return try {

            if (findFour == null) {
                findFour = FindFourModel(resourceModel)
                findFour?.setNumThreads(NUM_THREADS)

            }
            if (recognizedDigitsModel == null) {
                recognizedDigitsModel = RecognizedDigitsModel(resourceModel)
                recognizedDigitsModel?.setNumThreads(NUM_THREADS)
            }
            try {
                runModel(image)
            } catch (e: Error) {
                Log.i("Ocr", "runModel exception, retry prediction", e)
                findFour = FindFourModel(resourceModel)
                recognizedDigitsModel = RecognizedDigitsModel(resourceModel)
                runModel(image)
            } catch (e: Exception) {
                Log.i("Ocr", "runModel exception, retry prediction", e)
                findFour = FindFourModel(resourceModel)
                recognizedDigitsModel = RecognizedDigitsModel(resourceModel)
                runModel(image)
            }
        } catch (e: Error) {
            Log.e("Ocr", "unrecoverable exception on Ocr", e)
            hadUnrecoverableException = true
            null
        } catch (e: Exception) {
            Log.e("Ocr", "unrecoverable exception on Ocr", e)
            hadUnrecoverableException = true
            null
        }
    }


    companion object {
        private var findFour: FindFourModel? = null
        private var recognizedDigitsModel: RecognizedDigitsModel? = null

        fun isInit(): Boolean {
            return findFour != null && recognizedDigitsModel != null
        }
    }
}