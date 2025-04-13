package com.smartprints_ksa.battery_detector.data_structure;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;

import com.smartprints_ksa.battery_detector.BottleDetector;
import com.smartprints_ksa.battery_detector.DetectedObject;
import com.smartprints_ksa.battery_detector.Embeddings;
import com.smartprints_ksa.battery_detector.Utils;
import com.smartprints_ksa.battery_detector.data_structure.enums.Direction;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.battery_detector.data_structure.enums.Phase;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Session {
    private List<Operation> operations;
    private Map<ObjectType, Integer> typeCounter;
    private boolean isOperationResultConfirmed = true;
    private Rect mDetectCropRect;
    private static Session instance;

    /* Singleton pattern on the Session class */
    public static Session getNewInstance(Rect mDetectCropRect) {
        if (instance != null) {
            instance.destroy();
            instance = null;
        }
        instance = new Session(mDetectCropRect);
        return instance;
    }

    public static Session getInstance() {
        if (instance == null) return null;
        return instance;
    }
    private Session(Rect mDetectCropRect){
        this.operations = new ArrayList<>();
        this.typeCounter = new HashMap<>();
        for (ObjectType type:
                ObjectType.values())
            typeCounter.put(type, 0);

        this.mDetectCropRect = mDetectCropRect;
    }

    // returns the latest operation
    public Operation getCurrentOperation(){
        if(operations.size() == 0){
            return null;
        } else {
            return operations.get(operations.size()-1);
        }
    }

    // Create a new operation
    private Operation createNewOperation(){
        Operation operation = new Operation();
        operation.setPhase(Phase.DETECTION);
        return operation;
    }


    // Get the number of each object type that entered the RVM
    public int getCount(ObjectType objectType){
        if (instance != null)
            return typeCounter.get(objectType);
        else return 0;
    }

    // Update the counter of each object type using the latest operation
    private void updateTypeCounting(Operation operation){
        for (ObjectType type:
                ObjectType.values()) {
            if (operation.getType() == type){
                if(operation.getDirection() == Direction.IN)
                    typeCounter.put(type, typeCounter.get(type) + 1);
            }
        }
    }

     // Performs object detection and tracking on a Bitmap image and returns information about
     // the detected object, including its position, type, and additional relevant details.
    public Snapshot addBitmap(Bitmap mBitmap){
        ArrayList<DetectedObject> detectedObjects;
        if (getCurrentOperation() == null || getCurrentOperation().isTrackingFinished()
                || getCurrentOperation().getPhase() == Phase.DETECTION)
            detectedObjects = BottleDetector.recognize(mBitmap);
        else
            detectedObjects = BottleDetector.detect(mBitmap);

        for (int i = detectedObjects.size() - 1; i >= 0; i--) {
            DetectedObject detectedObject = detectedObjects.get(i);
            if (Utils.calculateIntersectionRatio(detectedObject.getRect(), mDetectCropRect) < .5) {
                detectedObjects.remove(i);
            }
        }

        if(detectedObjects.size() > 0) {
            isOperationResultConfirmed = false;

            // Create a new Operation if the last one ended
            if (getCurrentOperation() == null || getCurrentOperation().isTrackingFinished())
                operations.add(createNewOperation());


            // If its a new operation take the object on the right first
            // other wise we take the nearest object to the last detected one
            DetectedObject detectedObject = detectedObjects.get(0);

            // we have to manage one detected object here
            Snapshot currentSnapshot = new Snapshot(detectedObject, mBitmap);
            currentSnapshot.setPhase(this.getCurrentOperation().getPhase());
            currentSnapshot.setDetectionDuration(BottleDetector.getPipelineExecutionTime());

            List<Map.Entry<AbstractMap.SimpleEntry<ObjectType, String>, Double>> results = Embeddings.semanticSearch(currentSnapshot.getEmbedding());
            currentSnapshot.setObjectType(results.get(0).getValue() > .4f ? ObjectType.UNKNOWN : results.get(0).getKey().getKey());
            this.getCurrentOperation().addSnapshot(currentSnapshot);

            return currentSnapshot;

        }
        else if(!isOperationResultConfirmed){

            this.getCurrentOperation().addSnapshot(null);

            if(this.getCurrentOperation().isTrackingFinished()){
                this.updateTypeCounting(this.getCurrentOperation());

                this.isOperationResultConfirmed = true;
            }
        }
        return null;
    }

    // returns the position of the detection area's center
    protected PointF getMidPointOfDetectionArea(){
        return new PointF((float) ( mDetectCropRect.right + mDetectCropRect.left ) / 2,
                (float) ( mDetectCropRect.bottom + mDetectCropRect.top ) / 2);
    }

    // Clears a session then destroy it
    public void destroy() {
        operations.forEach(Operation::destroy);
        operations = null;
        instance = null;
    }
}