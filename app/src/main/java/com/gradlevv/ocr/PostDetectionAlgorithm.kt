package com.gradlevv.ocr

import com.gradlevv.ocr.java.DetectedBox
import java.util.*

class PostDetectionAlgorithm(boxes: ArrayList<com.gradlevv.ocr.java.DetectedBox>, findFour: FindFourModel) {

    private val kDeltaRowForCombine = 2
    private val kDeltaColForCombine = 2
    private val sortedBoxes: ArrayList<com.gradlevv.ocr.java.DetectedBox?>
    private val numRows: Int
    private val numCols: Int

    init {
        numCols = findFour.cols
        numRows = findFour.rows
        sortedBoxes = ArrayList()
        boxes.sort()
        boxes.reverse()
        for (box in boxes) {
            val kMaxBoxesToDetect = 20
            if (sortedBoxes.size >= kMaxBoxesToDetect) {
                break
            }
            sortedBoxes.add(box)
        }
    }

    fun horizontalNumbers(): ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>> {
        val boxes = combineCloseBoxes(
            kDeltaRowForCombine,
            kDeltaColForCombine
        )
        val kNumberWordCount = 4
        val lines = findHorizontalNumbers(boxes, kNumberWordCount)
        val linesOut = ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>>()
        // boxes should be roughly evenly spaced, reject any that aren't
        for (line in lines) {
            val deltas = ArrayList<Int>()
            for (idx in 0 until line.size - 1) {
                deltas.add(line[idx + 1].col - line[idx].col)
            }
            deltas.sort()
            val maxDelta = deltas[deltas.size - 1]
            val minDelta = deltas[0]
            if (maxDelta - minDelta <= 2) {
                linesOut.add(line)
            }
        }
        return linesOut
    }

    fun verticalNumbers(): ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>> {
        val boxes = combineCloseBoxes(
            kDeltaRowForCombine,
            kDeltaColForCombine
        )
        val lines = findVerticalNumbers(boxes)
        val linesOut = ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>>()
        // boxes should be roughly evenly spaced, reject any that aren't
        for (line in lines) {
            val deltas = ArrayList<Int>()
            for (idx in 0 until line.size - 1) {
                deltas.add(line[idx + 1].row - line[idx].row)
            }
            deltas.sort()
            val maxDelta = deltas[deltas.size - 1]
            val minDelta = deltas[0]
            if (maxDelta - minDelta <= 2) {
                linesOut.add(line)
            }
        }
        return linesOut
    }

    private fun horizontalPredicate(currentWord: com.gradlevv.ocr.java.DetectedBox, nextWord: com.gradlevv.ocr.java.DetectedBox?): Boolean {
        val kDeltaRowForHorizontalNumbers = 1
        return nextWord!!.col > currentWord.col && nextWord.row >= currentWord.row - kDeltaRowForHorizontalNumbers && nextWord.row <= currentWord.row + kDeltaRowForHorizontalNumbers
    }

    private fun verticalPredicate(currentWord: com.gradlevv.ocr.java.DetectedBox, nextWord: com.gradlevv.ocr.java.DetectedBox?): Boolean {
        val kDeltaColForVerticalNumbers = 1
        return nextWord!!.row > currentWord.row && nextWord.col >= currentWord.col - kDeltaColForVerticalNumbers && nextWord.col <= currentWord.col + kDeltaColForVerticalNumbers
    }

    private fun findNumbers(
        currentLine: ArrayList<com.gradlevv.ocr.java.DetectedBox>, words: ArrayList<com.gradlevv.ocr.java.DetectedBox>,
        useHorizontalPredicate: Boolean, numberOfBoxes: Int,
        lines: ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>>
    ) {
        if (currentLine.size == numberOfBoxes) {
            lines.add(currentLine)
            return
        }
        if (words.size == 0) {
            return
        }
        val currentWord = currentLine[currentLine.size - 1]
        for (idx in words.indices) {
            val word = words[idx]
            if (useHorizontalPredicate && horizontalPredicate(currentWord, word)) {
                val newCurrentLine = ArrayList(currentLine)
                newCurrentLine.add(word)
                findNumbers(
                    newCurrentLine, dropFirst(words, idx + 1), useHorizontalPredicate,
                    numberOfBoxes, lines
                )
            } else if (verticalPredicate(currentWord, word)) {
                val newCurrentLine = ArrayList(currentLine)
                newCurrentLine.add(word)
                findNumbers(
                    newCurrentLine, dropFirst(words, idx + 1), useHorizontalPredicate,
                    numberOfBoxes, lines
                )
            }
        }
    }

    private fun dropFirst(boxes: ArrayList<com.gradlevv.ocr.java.DetectedBox>, n: Int): ArrayList<com.gradlevv.ocr.java.DetectedBox> {
        val result = ArrayList<com.gradlevv.ocr.java.DetectedBox>()
        for (idx in n until boxes.size) {
            result.add(boxes[idx])
        }
        return result
    }

    // Note: this is simple but inefficient. Since we're dealing with small
    // lists (eg 20 items) it should be fine
    private fun findHorizontalNumbers(
        words: ArrayList<com.gradlevv.ocr.java.DetectedBox>,
        numberOfBoxes: Int
    ): ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>> {
        Collections.sort(words, colCompare)
        val lines = ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>>()
        for (idx in words.indices) {
            val currentLine = ArrayList<com.gradlevv.ocr.java.DetectedBox>()
            currentLine.add(words[idx])
            findNumbers(
                currentLine, dropFirst(words, idx + 1), true,
                numberOfBoxes, lines
            )
        }
        return lines
    }

    private fun findVerticalNumbers(words: ArrayList<com.gradlevv.ocr.java.DetectedBox>): ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>> {
        val numberOfBoxes = 4
        Collections.sort(words, rowCompare)
        val lines = ArrayList<ArrayList<com.gradlevv.ocr.java.DetectedBox>>()
        for (idx in words.indices) {
            val currentLine = ArrayList<com.gradlevv.ocr.java.DetectedBox>()
            currentLine.add(words[idx])
            findNumbers(
                currentLine, dropFirst(words, idx + 1), false,
                numberOfBoxes, lines
            )
        }
        return lines
    }

    /**
     * Combine close boxes favoring high confidence boxes.
     */
    private fun combineCloseBoxes(deltaRow: Int, deltaCol: Int): ArrayList<com.gradlevv.ocr.java.DetectedBox> {
        val cardGrid = Array(numRows) {
            BooleanArray(
                numCols
            )
        }
        for (row in 0 until numRows) {
            for (col in 0 until numCols) {
                cardGrid[row][col] = false
            }
        }
        for (box in sortedBoxes) {
            cardGrid[box!!.row][box.col] = true
        }

        // since the boxes are sorted by confidence, go through them in order to
        // result in only high confidence boxes winning. There are corner cases
        // where this will leave extra boxes, but that's ok because we don't
        // need to be perfect here
        for (box in sortedBoxes) {
            if (!cardGrid[box!!.row][box.col]) {
                continue
            }
            for (row in box.row - deltaRow..box.row + deltaRow) {
                for (col in box.col - deltaCol..box.col + deltaCol) {
                    if (row in 0 until numRows && col >= 0 && col < numCols) {
                        cardGrid[row][col] = false
                    }
                }
            }

            // add this box back
            cardGrid[box.row][box.col] = true
        }
        val combinedBoxes = ArrayList<DetectedBox>()
        for (box in sortedBoxes) {
            if (cardGrid[box!!.row][box.col]) {
                combinedBoxes.add(box)
            }
        }
        return combinedBoxes
    }

    companion object {
        private val colCompare =
            Comparator<com.gradlevv.ocr.java.DetectedBox> { o1, o2 -> if (o1.col < o2.col) -1 else if (o1.col == o2.col) 0 else 1 }
        private val rowCompare =
            Comparator<com.gradlevv.ocr.java.DetectedBox> { o1, o2 -> if (o1.row < o2.row) -1 else if (o1.row == o2.row) 0 else 1 }
    }
}