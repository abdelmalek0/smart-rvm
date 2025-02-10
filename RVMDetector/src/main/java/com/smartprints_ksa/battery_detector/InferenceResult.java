package com.smartprints_ksa.battery_detector;

import java.io.IOException;
import java.util.ArrayList;
import static java.lang.System.arraycopy;

public class InferenceResult {

    public OutputBuffer mOutputBuffer;
    public ArrayList<DetectedObject> recognitions = null;
    private boolean mIsVaild = false;

    public void init() throws IOException {
        mOutputBuffer = new OutputBuffer();}

    public void reset() {
        if (recognitions != null) {
            recognitions.clear();
            mIsVaild = true;
        }
    }
    public synchronized void setResult(OutputBuffer outputs) {

        if (mOutputBuffer.mGrid0Out == null) {
            mOutputBuffer.mGrid0Out = outputs.mGrid0Out.clone();
            mOutputBuffer.mGrid1Out = outputs.mGrid1Out.clone();
            mOutputBuffer.mGrid2Out = outputs.mGrid2Out.clone();
        } else {
            arraycopy(outputs.mGrid0Out, 0, mOutputBuffer.mGrid0Out, 0,
                    outputs.mGrid0Out.length);
            arraycopy(outputs.mGrid1Out, 0, mOutputBuffer.mGrid1Out, 0,
                    outputs.mGrid1Out.length);
            arraycopy(outputs.mGrid2Out, 0, mOutputBuffer.mGrid2Out, 0,
                    outputs.mGrid2Out.length);
        }
        mIsVaild = false;
    }

    public synchronized ArrayList<DetectedObject> getResult(InferenceWrapper mInferenceWrapper) {
        if (!mIsVaild) {
            mIsVaild = true;

            recognitions = mInferenceWrapper.postProcess(mOutputBuffer);
        }

        return recognitions;
    }

    public static class OutputBuffer {
        public byte[] mGrid0Out;
        public byte[] mGrid1Out;
        public byte[] mGrid2Out;
    }

    /**
     * Detected objects, returned from native yolo_post_process
     */
    public static class DetectResultGroup {
        /**
         * detected objects count.
         */
        public int count = 0;

        /**
         * id for each detected object.
         */
        public int[] ids;

        /**
         * score for each detected object.
         */
        public float[] scores;

        /**
         * box for each detected object.
         */
        public float[] boxes;
    }
}
