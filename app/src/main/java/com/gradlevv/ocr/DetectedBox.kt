package com.gradlevv.ocr

class DetectedBox(
    row: Int, col: Int, confidence: Float, numRows: Int, numCols: Int,
    boxSize: CGSize, cardSize: CGSize, imageSize: CGSize
) : Comparable<Any?> {

    val rect: CGRect
    val row: Int
    val col: Int
    private val confidence: Float


    override fun compareTo(other: Any?): Int {
        return confidence.compareTo((other as DetectedBox).confidence)
    }

    init {
        // Resize the box to transform it from the model's coordinates into
        // the image's coordinates
        val w = boxSize.width * imageSize.width / cardSize.width
        val h = boxSize.height * imageSize.height / cardSize.height
        val x = (imageSize.width - w) / (numCols - 1).toFloat() * col.toFloat()
        val y = (imageSize.height - h) / (numRows - 1).toFloat() * row.toFloat()
        rect = CGRect(x, y, w, h)
        this.row = row
        this.col = col
        this.confidence = confidence
    }
}