/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.gradlevv.ocr.java;

import android.graphics.Bitmap;
import android.util.Log;

import com.gradlevv.ocr.CGSize;
import com.gradlevv.ocr.ResourceModelFactory;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

import javax.inject.Inject;

/**
 * This classifier works with the float MobileNet model.
 */
class FindFourModel {

	final int rows = 34;
	final int cols = 51;
	final CGSize boxSize = new CGSize(80, 36);
	final CGSize cardSize = new CGSize(480, 302);

	/**
	 * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
	 * of the super class, because we need a primitive array here.
	 */
	private float[][][][] labelProbArray;

	private ResourceModelFactory resourceModelFactory;
	/**
	 * Initializes an {@code ImageClassifierFloatMobileNet}.
	 */

	@Inject
	public FindFourModel(ResourceModelFactory resourceModelFactory) {
		this.resourceModelFactory = resourceModelFactory;
		int classes = 3;
		labelProbArray = new float[1][rows][cols][classes];
		init();
	}

	/**
	 * Tag for the {@link Log}.
	 */
	private static final String TAG = "CardScan";

	/**
	 * Dimensions of inputs.
	 */
	private static final int DIM_BATCH_SIZE = 1;

	private static final int DIM_PIXEL_SIZE = 3;

	/**
	 * Preallocated buffers for storing image data in.
	 */
	private int[] intValues = new int[getImageSizeX() * getImageSizeY()];

	/**
	 * Options for configuring the Interpreter.
	 */
	private final Interpreter.Options tfliteOptions = new Interpreter.Options();

	/**
	 * The loaded TensorFlow Lite model.
	 */
	private MappedByteBuffer tfliteModel;

	/**
	 * An instance of the driver class to run model inference with Tensorflow Lite.
	 */
	Interpreter tflite;

	/**
	 * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
	 */
	ByteBuffer imgData = null;

	/**
	 * holds a gpu delegate
	 */
//	private GpuDelegate gpuDelegate = null;

	/**
	 * Initializes an {@code ImageClassifier}.
	 */

	private void init() {
		tfliteModel = loadModelFile();
		tflite = new Interpreter(tfliteModel, tfliteOptions);
		imgData =
				ByteBuffer.allocateDirect(
						DIM_BATCH_SIZE
								* getImageSizeX()
								* getImageSizeY()
								* DIM_PIXEL_SIZE
								* getNumBytesPerChannel());
		imgData.order(ByteOrder.nativeOrder());
	}

	/**
	 * Classifies a frame from the preview stream.
	 */
	void classifyFrame(Bitmap bitmap) {
		if (tflite == null) {
			Log.e(TAG, "Image classifier has not been initialized; Skipped.");
		}
		convertBitmapToByteBuffer(bitmap);
		// Here's where the magic happens!!!
		runInference();
	}

	private void recreateInterpreter() {
		if (tflite != null) {
			tflite.close();
			tflite = new Interpreter(tfliteModel, tfliteOptions);
		}
	}

	public void useCPU() {
		tfliteOptions.setUseNNAPI(false);
		recreateInterpreter();
	}

	public void useGpu() {
//		if (gpuDelegate == null) {
//			gpuDelegate = new GpuDelegate();
//			tfliteOptions.addDelegate(gpuDelegate);
//			recreateInterpreter();
//		}
	}

	public void useNNAPI() {
		tfliteOptions.setUseNNAPI(true);
		recreateInterpreter();
	}

	public void setNumThreads(int numThreads) {
		tfliteOptions.setNumThreads(numThreads);
		recreateInterpreter();
	}

	/**
	 * Closes tflite to release resources.
	 */
	public void close() {
		tflite.close();
		tflite = null;
//		if (gpuDelegate != null) {
//			gpuDelegate.close();
//			gpuDelegate = null;
//		}
		tfliteModel = null;
	}


	/**
	 * Writes Image data into a {@code ByteBuffer}.
	 */
	private void convertBitmapToByteBuffer(Bitmap bitmap) {
		if (imgData == null) {
			return;
		}
		imgData.rewind();

		Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getImageSizeX(), getImageSizeY(), false);
		resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0,
				resizedBitmap.getWidth(), resizedBitmap.getHeight());
		// Convert the image to floating point.
		int pixel = 0;
		for (int i = 0; i < getImageSizeX(); ++i) {
			for (int j = 0; j < getImageSizeY(); ++j) {
				final int val = intValues[pixel++];
				addPixelValue(val);
			}
		}
	}

	public boolean hasDigits(int row, int col) {
		return this.digitConfidence(row, col) >= 0.5;
	}

	 public boolean hasExpiry(int row, int col) {
		return this.expiryConfidence(row, col) >= 0.5;
	}

	float digitConfidence(int row, int col) {
		int digitClass = 1;
		return labelProbArray[0][row][col][digitClass];
	}

	float expiryConfidence(int row, int col) {
		int expiryClass = 2;
		return labelProbArray[0][row][col][expiryClass];
	}


	public MappedByteBuffer loadModelFile() {
		return resourceModelFactory.loadFindFourFile();
	}


	protected int getImageSizeX() {
		return 480;
	}


	protected int getImageSizeY() {
		return 302;
	}


	protected int getNumBytesPerChannel() {
		return 4; // Float.SIZE / Byte.SIZE;
	}


	protected void addPixelValue(int pixelValue) {
		imgData.putFloat(((pixelValue >> 16) & 0xFF) / 255.f);
		imgData.putFloat(((pixelValue >> 8) & 0xFF) / 255.f);
		imgData.putFloat((pixelValue & 0xFF) / 255.f);
	}


	protected void runInference() {
		tflite.run(imgData, labelProbArray);
	}
}
