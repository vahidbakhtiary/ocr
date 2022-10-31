package com.gradlevv.ocr

import android.graphics.Bitmap
import com.gradlevv.ocr.DetectedBox

interface OnScanListener {
    fun onPrediction(
        number: String?, bitmap: Bitmap?,
        digitBoxes: List<com.gradlevv.ocr.java.DetectedBox>
    )

    fun onFatalError()
}