-keep class com.smartprints_ksa.battery_detector.RVMDetector {*;}
-keep class com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType {*;}
-keep class com.smartprints_ksa.battery_detector.data_structure.enums.Phase {*;}
-keep class com.smartprints_ksa.battery_detector.data_structure.enums.Direction {*;}
-keep class com.smartprints_ksa.battery_detector.DetectedObject {
    android.graphics.Rect getRect();
}
-keep class com.smartprints_ksa.battery_detector.data_structure.Snapshot {
    long getDetectionDuration();
    com.smartprints_ksa.battery_detector.data_structure.enums.Phase getPhase();
    android.graphics.Bitmap getBitmap();
    com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType getObjectType();
    java.util.Date getCurrentDate();
    java.lang.String toString();
}

-keep class com.smartprints_ksa.battery_detector.data_structure.Operation {
    java.util.List getSnapshots();
    com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType getType();
    com.smartprints_ksa.battery_detector.data_structure.enums.Direction getDirection();
    com.smartprints_ksa.battery_detector.data_structure.enums.Phase getPhase();
    boolean isTrackingFinished();
    boolean hasFinishedSuccessfully();
    boolean isObjectAccepted();
    accept();
    reject();
}
-optimizationpasses 5