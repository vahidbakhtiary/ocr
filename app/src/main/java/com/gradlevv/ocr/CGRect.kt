package com.gradlevv.ocr

import android.graphics.RectF

class CGRect(val x: Float,val y: Float,val width: Float,val height: Float) {

	val newInstance: RectF
		get() = RectF(x, y, x + width, y + height)

}