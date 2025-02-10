package com.smartprints_ksa.battery_detector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.smartprints_ksa.battery_detector.data_structure.Operation;
import com.smartprints_ksa.battery_detector.data_structure.Session;
import com.smartprints_ksa.battery_detector.data_structure.Snapshot;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.battery_detector.data_structure.enums.Phase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RVMDetector {

    private static int nbFramesUntilConfirmation = 9;

    /**
     * Getter method to get the number of empty frames needed to confirm
     * that an operation has really ended.
     * @return The value for nbFramesUntilConfirmation.
     */
    public static int getNbFramesUntilConfirmation() {
        return nbFramesUntilConfirmation;
    }

    /**
     * Setter method to adjust the count of empty frames required to validate
     * the completion of an operation.
     * @param nbFramesUntilConfirmation The new value for nbFramesUntilConfirmation.
     * @throws IllegalArgumentException if nbFramesUntilConfirmation is negative or 0.
     */
    public static void setNbFramesUntilConfirmation(int nbFramesUntilConfirmation){
        if (nbFramesUntilConfirmation <= 0) {
            throw new IllegalArgumentException("Number of frames must be more than 0.");
        }
        RVMDetector.nbFramesUntilConfirmation = nbFramesUntilConfirmation;
    }

    /**
     * Generates a request code needed for the device licence verification.
     *
     * @param context The Context object used to access system resources and services
     * @return A string representing the request code
     */
    public static String getRequestCode(Context context){
        return BottleDetector.getRequestCode(context);
    }

    /**
     * validates the verification code and initializes the deep learning models. Once the verification code is verified,
     * the function will unlock access to the deep learning models, allowing you to use them in your projects.
     *
     * @param context The Context object used to access system resources and services
     * @param validationCode The license code to be verified
     * @return A boolean value indicating the success of the license validation
     */
    public static boolean setup(Context context, String validationCode){
        return BottleDetector.setup(context, validationCode);
    }



    /**
     * Initiates a new session for the RVM detector, allowing the user to perform multiple operations.
     *
     * @param mDetectCropRect The designated region within the RVM, spanning from the entrance
     *                        to the exit, where bottle movements are detected.
     */

    public static void startNewSession(Rect mDetectCropRect){
        Session.getNewInstance(mDetectCropRect);
    }

    /**
     * Terminates the current session of the RVM detector and wipe out any data on it.
     */
    public static void destroySession(){
        if (Session.getInstance() != null)
            Session.getInstance().destroy();
    }

    /**
     * Performs object detection and tracking on a Bitmap image and provides information about
     * the detected object, including its position, type, and additional relevant details.
     *
     * @param mBitmap The Bitmap image on which object detection and tracking will be executed.
     * @return The detected object in the image including its position, its type
     * and other related information
     */
    public static Snapshot process(Bitmap mBitmap){
        if (Session.getInstance() != null)
            return Session.getInstance().addBitmap(mBitmap);
        else return null;
    }

    public static Snapshot detect(Bitmap bitmap){
        ArrayList<DetectedObject> detectedObjects = BottleDetector.recognize(bitmap);
        if (!detectedObjects.isEmpty()){
            Snapshot currentSnapshot = new Snapshot(detectedObjects.get(0), bitmap);

//            Log.d("Scores", Arrays.toString(currentSnapshot.g()));
            long startTime = System.nanoTime();
            List<Map.Entry<ObjectType, Double>> results = Embeddings.semanticSearch(currentSnapshot.getEmbedding());
            long endTime = System.nanoTime(); // End time
            long duration = endTime - startTime; // Calculate execution time in nanoseconds
            System.out.println("Results: " + 
            "1: " + results.get(0).getKey().toString() + "~~~" + results.get(0).getValue().toString() +
            "2: " + results.get(1).getKey().toString() + "~~~" + results.get(1).getValue().toString() +
            "3: " + results.get(2).getKey().toString() + "~~~" + results.get(2).getValue().toString()
            );
	        System.out.println("1. Execution time: " + duration / 1_000_000.0 + " ms");
            System.out.println("2. Execution time: " + BottleDetector.getPipelineExecutionTime() / 1_000_000.0 + " ms");
            currentSnapshot.setDetectionDuration(BottleDetector.getPipelineExecutionTime() + duration);
            currentSnapshot.setObjectType(results.get(0).getValue() > 1.0 ? ObjectType.UNKNOWN : results.get(0).getKey());
            return currentSnapshot;
        }
        return null;
    }

    public static ArrayList<Snapshot> detectAll(Bitmap bitmap){
        ArrayList<DetectedObject> detectedObjects = BottleDetector.recognize(bitmap);
        ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
        for (DetectedObject detectedObject: detectedObjects){
            Snapshot currentSnapshot = new Snapshot(detectedObject, bitmap);
            long startTime = System.nanoTime();
            List<Map.Entry<ObjectType, Double>> results = Embeddings.semanticSearch(currentSnapshot.getEmbedding());
            long endTime = System.nanoTime(); // End time
            long duration = endTime - startTime; // Calculate execution time in nanoseconds
            System.out.println("Results: " +
                    "1: " + results.get(0).getKey().toString() + "~~~" + results.get(0).getValue().toString() +
                    "2: " + results.get(1).getKey().toString() + "~~~" + results.get(1).getValue().toString() +
                    "3: " + results.get(2).getKey().toString() + "~~~" + results.get(2).getValue().toString()
            );
            System.out.println("1. Execution time: " + duration / 1_000_000.0 + " ms");
            System.out.println("2. Execution time: " + BottleDetector.getPipelineExecutionTime() / 1_000_000.0 + " ms");
            currentSnapshot.setDetectionDuration(BottleDetector.getPipelineExecutionTime() / detectedObjects.size() + duration);
            currentSnapshot.setObjectType(results.get(0).getValue() > 1.0 ? ObjectType.UNKNOWN : results.get(0).getKey());

            snapshots.add(currentSnapshot);
        }
        return snapshots;
    }

    /**
     * Retrieves the ongoing user operation, initiated with the addition of a bottle or can,
     * and continues until the item is no longer visible on the screen.
     *
     * @return The current operation if a session is already in progress.
     */
    public static Operation getCurrentOperation(){
        if (Session.getInstance() != null)
            return Session.getInstance().getCurrentOperation();
        else return null;
    }

    /**
     * Retrieves the count of items added by the user during this session, categorized by object type.
     *
     * @param objectType The specific object type for which you want to retrieve the count.
     * @return The count of objects of the given "type" added by the user.
     */
    public static int countObjectsOfType(ObjectType objectType){
        if (Session.getInstance() != null)
            return Session.getInstance().getCount(objectType);
        else return -1;
    }


    public static void addEmbeddings(ObjectType type, List<float[]> embeddings) {
        Embeddings.addEmbedding(type, embeddings);
    }

}
