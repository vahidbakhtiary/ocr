package com.gradlevv.ocr.java;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.util.Log;

import com.gradlevv.ocr.CGSize;

import com.gradlevv.ocr.RecognizeNumbers;
import com.gradlevv.ocr.ResourceModelFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is not thread safe, make sure that all methods run on the same thread.
 */
public class OCR {

	private static com.gradlevv.ocr.java.FindFourModel findFour = null;
	private static com.gradlevv.ocr.java.RecognizedDigitsModel recognizedDigitsModel = null;
	public List<DetectedBox> digitBoxes = new ArrayList<>();
	public boolean hadUnrecoverableException = false;

	public static boolean isInit() {
		return findFour != null && recognizedDigitsModel != null;
	}

	private ArrayList<DetectedBox> detectBoxes(Bitmap image) {
		ArrayList<DetectedBox> boxes = new ArrayList<>();
		for (int row = 0; row < findFour.rows; row++) {
			for (int col = 0; col < findFour.cols; col++) {
				if (findFour.hasDigits(row, col)) {
					float confidence = findFour.digitConfidence(row, col);
					CGSize imageSize = new CGSize(image.getWidth(), image.getHeight());
					DetectedBox box = new DetectedBox(row, col, confidence, findFour.rows,
							findFour.cols, findFour.boxSize, findFour.cardSize, imageSize);
					boxes.add(box);
				}
			}
		}
		return boxes;
	}


	private String runModel(Bitmap image) {
		findFour.classifyFrame(image);
		ArrayList<DetectedBox> boxes = detectBoxes(image);
		PostDetectionAlgorithm postDetection = new PostDetectionAlgorithm(boxes, findFour);

		RecognizeNumbers recognizeNumbers = new RecognizeNumbers(image, findFour.rows, findFour.cols);
		ArrayList<ArrayList<DetectedBox>> lines = postDetection.horizontalNumbers();
		String number = recognizeNumbers.number(recognizedDigitsModel, lines);

		if (number == null) {
			ArrayList<ArrayList<DetectedBox>> verticalLines = postDetection.verticalNumbers();
			number = recognizeNumbers.number(recognizedDigitsModel, verticalLines);
			lines.addAll(verticalLines);
		}

		boxes = new ArrayList<>();
		for (ArrayList<DetectedBox> numbers : lines) {
			boxes.addAll(numbers);
		}

		this.digitBoxes = boxes;

		return number;
	}

	private boolean hasOpenGl31(Context context) {
		int openGlVersion = 0x00030001;
		ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
		if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
			return configInfo.reqGlEsVersion >= openGlVersion;
		} else {
			return false;
		}
	}

	public synchronized String predict(Bitmap image, ResourceModelFactory resourceModelFactory) {
		final int NUM_THREADS = 4;
		try {

			if (findFour == null) {
				findFour = new com.gradlevv.ocr.java.FindFourModel(resourceModelFactory);
				findFour.setNumThreads(NUM_THREADS);
			}

			if (recognizedDigitsModel == null) {
				recognizedDigitsModel = new com.gradlevv.ocr.java.RecognizedDigitsModel(resourceModelFactory);
				recognizedDigitsModel.setNumThreads(NUM_THREADS);
			}

			try {
				return runModel(image);
			} catch (Error | Exception e) {
				Log.i("Ocr", "runModel exception, retry prediction", e);
				findFour = new FindFourModel(resourceModelFactory);
				recognizedDigitsModel = new RecognizedDigitsModel(resourceModelFactory);
				return runModel(image);
			}
		} catch (Error | Exception e) {
			Log.e("Ocr", "unrecoverable exception on Ocr", e);
			hadUnrecoverableException = true;
			return null;
		}
	}
}
