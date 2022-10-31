package com.gradlevv.ocr.java;

import android.graphics.Bitmap;
import android.util.Log;

import com.gradlevv.ocr.ResourceModelFactory;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

import javax.inject.Inject;

public class RecognizedDigitsModel {

    public static final int kNumPredictions = 17;
    private final int classes = 11;

    private ResourceModelFactory resourceModelFactory;
    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
     * of the super class, because we need a primitive array here.
     */
    private float[][][][] labelProbArray;

    @Inject
    public RecognizedDigitsModel(ResourceModelFactory resourceModelFactory) {
        this.resourceModelFactory = resourceModelFactory;
		labelProbArray = new float[1][1][kNumPredictions][classes];
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
	public void classifyFrame(Bitmap bitmap) {
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

	public void useNNAPI() {
		tfliteOptions.setUseNNAPI(true);
		recreateInterpreter();
	}

	public void setNumThreads(int numThreads) {
		tfliteOptions.setNumThreads(numThreads);
		recreateInterpreter();
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

    public class ArgMaxAndConfidence {
        public final int argMax;
        public final float confidence;

        ArgMaxAndConfidence(int argMax, float confidence) {
            this.argMax = argMax;
            this.confidence = confidence;
        }
    }

    public ArgMaxAndConfidence argAndValueMax(int col) {
        int maxIdx = -1;
        float maxValue = (float) -1.0;
        for (int idx = 0; idx < classes; idx++) {
            float value = this.labelProbArray[0][0][col][idx];
            if (value > maxValue) {
                maxIdx = idx;
                maxValue = value;
            }
        }

        return new ArgMaxAndConfidence(maxIdx, maxValue);
    }


    public MappedByteBuffer loadModelFile() {
        return resourceModelFactory.loadRecognizeDigitsFile();
    }


    protected int getImageSizeX() {
        return 80;
    }

    protected int getImageSizeY() {
        return 36;
    }

    protected int getNumBytesPerChannel() {
        return 4;
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
