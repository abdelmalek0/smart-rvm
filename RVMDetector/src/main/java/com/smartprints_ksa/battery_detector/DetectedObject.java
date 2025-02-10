package com.smartprints_ksa.battery_detector;

import static com.smartprints_ksa.battery_detector.BottleDetector.nClassifierOutput;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Locale;

public class DetectedObject {

    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private int id;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    private final float detectionConfidence;
    private float[] embedding;

    /** Optional location within the source image for the location of the recognized object. */
    private RectF location;
    private Rect rect;

    public DetectedObject(
            final int id, final Float detectionConfidence, final RectF location) {
        this.id = id;
        this.detectionConfidence = detectionConfidence;
        this.location = location;
        this.rect = new Rect();
        this.location.roundOut(rect);
        this.embedding = new float[Embeddings.embeddingSize];
    }

    public int getId() {
        return id;
    }
    public float getDetectionConfidence() {
        return detectionConfidence;
    }
    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    protected RectF getLocation() {
        return new RectF(rect);
    }
    protected void setLocation(RectF location) {
        this.location = location;
        this.rect = new Rect();
        this.location.roundOut(rect);
    }

    public Rect getRect() {
        return rect;
    }
    public void setRect(Rect rect) {
        this.rect = rect;
        this.location = new RectF(rect);
    }

    @NonNull
    public String toString(){
        return String.format(Locale.getDefault(), "ID %d: RECT(L,R,T,B): (%d,%d,%d,%d)", getId(),
                this.rect.left, this.rect.right, this.rect.top, this.rect.bottom);
    }
}

