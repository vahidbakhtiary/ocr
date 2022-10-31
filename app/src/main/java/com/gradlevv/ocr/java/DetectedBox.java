package com.gradlevv.ocr.java;

import androidx.annotation.NonNull;

import com.gradlevv.ocr.CGRect;
import com.gradlevv.ocr.CGSize;

public class DetectedBox implements Comparable {

	private CGRect rect;
	public final int row;
	public final int col;
	private float confidence;

	public DetectedBox(int row, int col, float confidence, int numRows, int numCols,
				CGSize boxSize, CGSize cardSize, CGSize imageSize) {
		// Resize the box to transform it from the model's coordinates into
		// the image's coordinates
		float w = boxSize.getWidth() * imageSize.getWidth() / cardSize.getWidth();
		float h = boxSize.getHeight() * imageSize.getHeight() / cardSize.getHeight();
		float x = (imageSize.getWidth() - w) / ((float) (numCols - 1)) * ((float) col);
		float y = (imageSize.getHeight() - h) / ((float) (numRows - 1)) * ((float) row);
		this.rect = new CGRect(x, y, w, h);
		this.row = row;
		this.col = col;
		this.confidence = confidence;
	}

	@Override
	public int compareTo(@NonNull Object o) {
		return Float.compare(this.confidence, ((DetectedBox) o).confidence);
	}

	public CGRect getRect() {
		return rect;
	}
}
