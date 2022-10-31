package com.gradlevv.ocr

import android.graphics.Bitmap

interface OnObjectListener {
    fun onPrediction(
        bitmap: Bitmap?, imageWidth: Int,
        imageHeight: Int
    )

    fun onObjectFatalError()
}