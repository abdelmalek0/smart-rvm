package com.smartprints_ksa.battery_detector.data_structure;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.smartprints_ksa.battery_detector.DetectedObject;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.battery_detector.data_structure.enums.Phase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Snapshot extends DetectedObject {
    private ObjectType objectType = ObjectType.UNKNOWN;
    private Phase phase = Phase.NONE;
    private Bitmap bitmap;
    private Date currentDate;

    private long detectionDuration = 0;

    /**
     * Constructor for the Snapshot class.
     */
    public Snapshot(DetectedObject object, Bitmap bitmap){
        super(object.getId(), object.getDetectionConfidence(), new RectF(object.getRect()));
        super.setEmbedding(object.getEmbedding());
        super.setObjectBitmap(object.getObjectBitmap());
        this.bitmap = Bitmap.createBitmap(bitmap);
        this.currentDate = new Date();
    }

    /**
     * Provides the overall time taken for the process of detection on the bitmap associated.
     *
     * @return The total time taken for the prediction, in milliseconds
     */
    public long getDetectionDuration(){
        return detectionDuration;
    }

    /**
     * Sets the total time taken for detection on the snapshot's bitmap.
     *
     * @param detectionDuration The total time taken for the prediction, in milliseconds.
     */
    public void setDetectionDuration(long detectionDuration){
        this.detectionDuration = detectionDuration;
    }

    /**
     * Retrieves the phase at which this snapshot is captured (Detection, Tracking, Rejecting or None).
     * @return The current phase.
     */
    public Phase getPhase() { return phase; }

    /**
     * Sets the phase of the snapshot to the specified value.
     * @param phase The phase to be set.
     */
    void setPhase(Phase phase) {
        this.phase = phase;
    }


    /**
     * Retrieves the type of object in the snapshot ( Bottle, Can, ...)
     * @return The object type.
     */
    public ObjectType getObjectType() {
        return objectType;
    }

    /**
     * Sets the type of object in the snapshot to the specified value.
     * @param objectType The object type to be set.
     */
    public void setObjectType(ObjectType objectType) {
        this.objectType = objectType;
    }

    /**
     * Obtains the bitmap of the snapshot to be used for detection and tracking.
     * @return The bitmap of the snapshot.
     */
    public Bitmap getBitmap() {
        return bitmap;
    }

    /**
     * Sets the bitmap for the snapshot.
     * @param bitmap The bitmap to be set.
     */
    void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    /**
     * Retrieves the timestamp of the snapshot's creation.
     *
     * @return The timestamp indicating the time of snapshot creation.
     */
    public Date getCurrentDate() {
        return currentDate;
    }

    /**
     * Sets the timestamp to indicate the time of the snapshot creation.
     *
     * @param currentDate The date to be assigned as the time of snapshot creation.
     */
    void setCurrentDate(Date currentDate) {
        this.currentDate = currentDate;
    }

    /**
     * Retrieves the position of the detected object on the screen as a PointF (x, y).
     * @return PointF representing the center coordinates of the detected object.
     */
    PointF getReferencePoint() {
        return new PointF(
                this.getLocation().left + (float) (this.getLocation().right - this.getLocation().left) / 2,
                this.getLocation().top + (float) (this.getLocation().bottom - this.getLocation().top ) / 2
        );
    }

    /**
     * Generates a string representation of the snapshot.
     *
     * @return A formatted string containing Time, ID, Type, and Phase of the snapshot.
     */
    @SuppressLint("SimpleDateFormat")
    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "Time: %s --- ID %d: Type %s, Phase %s", new SimpleDateFormat("yy MM dd: HH:mm:ss").format(currentDate),
                getId(), getObjectType().name(), getPhase().name());
    }

    /**
     * Destroys the snapshot's data by recycling the bitmap inside.
     */
    void destroy(){
        bitmap.recycle();
    }
}

