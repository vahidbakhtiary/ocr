package com.gradlevv.ocr

import android.graphics.Bitmap
import com.gradlevv.ocr.java.RecognizedDigitsModel
import java.util.*
import kotlin.math.roundToInt

class RecognizedDigits private constructor(
    private val digits: List<Int>,
    private val confidence: List<Float>
) {
    private fun nonMaxSuppression(): List<Int> {
        val digits = ArrayList(digits)
        val confidence = ArrayList(confidence)

        // greedy non max suppression
        for (idx in 0 until kNumPredictions - 1) {
            if (digits[idx] != kBackgroundClass && digits[idx + 1] != kBackgroundClass) {
                if (confidence[idx] < confidence[idx + 1]) {
                    digits[idx] = kBackgroundClass
                    confidence[idx] = 1.0.toFloat()
                } else {
                    digits[idx + 1] = kBackgroundClass
                    confidence[idx + 1] = 1.0.toFloat()
                }
            }
        }
        return digits
    }

    fun stringResult(): String {
        val digits = nonMaxSuppression()
        val result = StringBuilder()
        for (digit in digits) {
            if (digit != kBackgroundClass) {
                result.append(digit)
            }
        }
        return result.toString()
    }

    companion object {
        private val kNumPredictions: Int = RecognizedDigitsModel.kNumPredictions
        private const val kBackgroundClass = 10
        private const val kDigitMinConfidence = 0.15.toFloat()

        fun from(model: com.gradlevv.ocr.java.RecognizedDigitsModel, image: Bitmap, box: CGRect): RecognizedDigits {
            val frame: Bitmap = Bitmap.createBitmap(
                image, box.x.roundToInt(), box.y.roundToInt(),
                box.width.toInt(), box.height.toInt()
            )
            model.classifyFrame(frame)
            val digits = ArrayList<Int>()
            val confidence = ArrayList<Float>()
            for (col in 0 until kNumPredictions) {
                val argAndConf: com.gradlevv.ocr.java.RecognizedDigitsModel.ArgMaxAndConfidence =
                    model.argAndValueMax(col)
                if (argAndConf.confidence < kDigitMinConfidence) {
                    digits.add(kBackgroundClass)
                } else {
                    digits.add(argAndConf.argMax)
                }
                confidence.add(argAndConf.confidence)
            }
            return RecognizedDigits(digits, confidence)
        }
    }
}