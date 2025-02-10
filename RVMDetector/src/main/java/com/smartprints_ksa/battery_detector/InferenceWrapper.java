package com.smartprints_ksa.battery_detector;

import static com.smartprints_ksa.battery_detector.BottleDetector.OBJ_NUMB_MAX_SIZE;
import static com.smartprints_ksa.battery_detector.BottleDetector.nClassifierOutput;

import android.graphics.RectF;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;


public class InferenceWrapper {
    private final String TAG = "rkyolo.InferenceWrapper";

    static {
        System.loadLibrary("rknn4j");
    }

    private InferenceResult.OutputBuffer mOutputs;
    private ArrayList<DetectedObject> mRecognitions = new ArrayList<>();
    private InferenceResult.DetectResultGroup mDetectResults;



    public int initYolo(int im_height, int im_width, int im_channel, String modelPath) throws Exception {
        mOutputs = new InferenceResult.OutputBuffer();
        mOutputs.mGrid0Out = new byte[255 * 80 * 80 * 4];
        mOutputs.mGrid1Out = new byte[255 * 40 * 40 * 4];
        mOutputs.mGrid2Out = new byte[255 * 20 * 20 * 4];
        if (native_init_yolo(im_height, im_width, im_channel, modelPath) != 0) {
            throw new IOException("rknn init fail!");
        }
        return 0;
    }

    public int initClassifier(int im_height, int im_width, int im_channel, String modelPath) throws Exception {
        if (native_init_classifier(im_height, im_width, im_channel, modelPath) != 0) {
            throw new IOException("classifier init fail!");
        }
        return 0;
    }


    public void deinit() {
        native_de_init_yolo();
        mOutputs.mGrid0Out = null;
        mOutputs.mGrid1Out = null;
        mOutputs.mGrid2Out = null;
        mOutputs = null;

    }

    public float[] runClassifier(byte[] inData) {
        float[] outData = new float[nClassifierOutput];
        native_run_classifier(inData, outData);

        return  outData;
    }

    public InferenceResult.OutputBuffer run(byte[] inData) {
        native_run_yolo(inData, mOutputs.mGrid0Out, mOutputs.mGrid1Out, mOutputs.mGrid2Out);
        return  mOutputs;
    }

    public ArrayList<DetectedObject> postProcess(InferenceResult.OutputBuffer outputs) {
        ArrayList<DetectedObject> recognitions = new ArrayList<DetectedObject>();

        mDetectResults = new InferenceResult.DetectResultGroup();
        mDetectResults.count = 0;
        mDetectResults.ids = new int[OBJ_NUMB_MAX_SIZE];
        mDetectResults.scores = new float[OBJ_NUMB_MAX_SIZE];
        mDetectResults.boxes = new float[4 * OBJ_NUMB_MAX_SIZE];

        if (null == outputs || null == outputs.mGrid0Out || null == outputs.mGrid1Out
                || null == outputs.mGrid2Out) {
            return recognitions;
        }

        int count = native_post_process_yolo(outputs.mGrid0Out, outputs.mGrid1Out, outputs.mGrid2Out,
                mDetectResults.ids, mDetectResults.scores, mDetectResults.boxes);
        if (count < 0) {
            Log.w(TAG, "post_process may fail.");
            mDetectResults.count = 0;
        } else {
            mDetectResults.count = count;
        }

        for (int i = 0; i < count; ++i) {
            RectF rect = new RectF();
            rect.left = mDetectResults.boxes[i * 4];
            rect.top = mDetectResults.boxes[i*4+1];
            rect.right = mDetectResults.boxes[i*4+2];
            rect.bottom = mDetectResults.boxes[i*4+3];

            DetectedObject recog = new DetectedObject(mDetectResults.ids[i],
                    mDetectResults.scores[i], rect);
            recognitions.add(recog);
        }

        return recognitions;
    }

    private native int native_init_yolo(int im_height, int im_width, int im_channel, String modelPath);
    private native void native_de_init_yolo();
    private native int native_run_yolo(byte[] inData, byte[] grid0Out, byte[] grid1Out, byte[] grid2Out);
    private native int native_post_process_yolo(byte[] grid0Out, byte[] grid1Out, byte[] grid2Out,
                                                int[] ids, float[] scores, float[] boxes);

    private native int native_init_classifier(int im_height, int im_width, int im_channel, String modelPath);
    private native int native_run_classifier(byte[] inData, float[] outData);
}