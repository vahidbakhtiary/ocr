package com.gradlevv.ocr

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.view.View

open class Overlay(context: Context) : View(context) {

    private var rect: RectF? = null
    private val oval = RectF()
    private var radius = 0
    private val mXfermode: Xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    var cornerDp = 6
    var drawCorners = true

    open val backgroundColorId: Int
        get() = Color.parseColor("#992F3542")
    open val cornerColorId: Int
        get() = Color.parseColor("#4CD964")


    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setCircle(rect: RectF?, radius: Int) {
        this.rect = rect
        this.radius = radius
        postInvalidate()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rect != null) {
            val paintAntiAlias = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = resources.getColor(backgroundColorId)
                style = Paint.Style.FILL
                xfermode = mXfermode
            }
            canvas.drawPaint(paintAntiAlias)

            canvas.drawRoundRect(rect!!, radius.toFloat(), radius.toFloat(), paintAntiAlias)
            if (!drawCorners) {
                return
            }
            val paint = Paint().apply {
                color = resources.getColor(cornerColorId)
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(cornerDp).toFloat()
            }

            // top left
            val lineLength = dpToPx(20)
            var x = rect!!.left - dpToPx(1)
            var y = rect!!.top - dpToPx(1)

            oval.left = x
            oval.top = y
            oval.right = x + 2 * radius
            oval.bottom = y + 2 * radius

            canvas.drawArc(oval, 180f, 90f, false, paint)
            canvas.drawLine(
                oval.left, oval.bottom - radius, oval.left,
                oval.bottom - radius + lineLength, paint
            )
            canvas.drawLine(
                oval.right - radius, oval.top,
                oval.right - radius + lineLength, oval.top, paint
            )

            // top right
            x = rect!!.right + dpToPx(1) - 2 * radius
            y = rect!!.top - dpToPx(1)
            oval.left = x
            oval.top = y
            oval.right = x + 2 * radius
            oval.bottom = y + 2 * radius
            canvas.drawArc(oval, 270f, 90f, false, paint)
            canvas.drawLine(
                oval.right, oval.bottom - radius, oval.right,
                oval.bottom - radius + lineLength, paint
            )
            canvas.drawLine(
                oval.right - radius, oval.top,
                oval.right - radius - lineLength, oval.top, paint
            )

            // bottom right
            x = rect!!.right + dpToPx(1) - 2 * radius
            y = rect!!.bottom + dpToPx(1) - 2 * radius
            oval.left = x
            oval.top = y
            oval.right = x + 2 * radius
            oval.bottom = y + 2 * radius
            canvas.drawArc(oval, 0f, 90f, false, paint)
            canvas.drawLine(
                oval.right, oval.bottom - radius, oval.right,
                oval.bottom - radius - lineLength, paint
            )
            canvas.drawLine(
                oval.right - radius, oval.bottom,
                oval.right - radius - lineLength, oval.bottom, paint
            )

            // bottom left
            x = rect!!.left - dpToPx(1)
            y = rect!!.bottom + dpToPx(1) - 2 * radius
            oval.left = x
            oval.top = y
            oval.right = x + 2 * radius
            oval.bottom = y + 2 * radius
            canvas.drawArc(oval, 90f, 90f, false, paint)
            canvas.drawLine(
                oval.left, oval.bottom - radius, oval.left,
                oval.bottom - radius - lineLength, paint
            )
            canvas.drawLine(
                oval.right - radius, oval.bottom,
                oval.right - radius + lineLength, oval.bottom, paint
            )
        }
    }

}