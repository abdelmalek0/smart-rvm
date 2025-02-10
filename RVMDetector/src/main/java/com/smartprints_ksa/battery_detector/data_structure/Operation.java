package com.smartprints_ksa.battery_detector.data_structure;

import android.graphics.PointF;

import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.enums.Direction;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.battery_detector.data_structure.enums.Phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Operation {

    private List<Snapshot> snapshots;
    private Phase phase;
    private int emptyFrameCount;
    private boolean isObjectAccepted = false;

    /**
     * Constructor for the Operation class.
     */
    Operation(){
        snapshots = new ArrayList<>();
        phase = Phase.NONE;
        emptyFrameCount = 0;
    }

    /**
     * Determines the eligibility of the object for passage to the inside of the RVM.
     * @return true if the object is accepted, false otherwise.
     */
    public boolean isObjectAccepted() {
        return isObjectAccepted;
    }

    /**
     * Indicate that the object has been accepted by the RVM,
     * triggering the transition to the Tracking phase.
     */
    public void accept() {
        isObjectAccepted = true;
        setPhase(Phase.TRACKING);
    }

    /**
     * Mark the object as being rejected by the RVM,
     * leading the operation into the Rejecting phase.
     */
    public void reject() {
        isObjectAccepted = false;
        setPhase(Phase.REJECTING);
    }


    /**
     * Retrieves the current phase of the operation (Detection, Tracking, Refused or None).
     * @return The current phase.
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     * Sets the phase of the operation to the specified value.
     * @param phase The new phase to be set.
     */
    void setPhase(Phase phase) {
        this.phase = phase;
    }


    /**
     * Adds a snapshot to the ongoing operation.
     * The added snapshot may be null if no object is present in the frame.
     * Subsequent null snapshots result in the completion of the operation.
     * @param snapshot The snapshot to be added.
     */
    void addSnapshot(Snapshot snapshot){
        if (snapshot == null)
            emptyFrameCount ++;
        else {
            emptyFrameCount = 0;
            snapshots.add(snapshot);
        }
    }

    /**
     * Retrieves the list of snapshots associated with the operation.
     * @return The list of snapshots.
     */
    public List<Snapshot> getSnapshots(){ return snapshots; }


    /**
     * Determines the direction of the object's movement,
     * either towards the interior or exterior of the RVM.
     *
     * @return The direction of movement (IN, OUT, or STILL).
     */
    public Direction getDirection() {

        if (snapshots.size() < 1)
            return Direction.STILL;

        PointF lastShot = snapshots.get(snapshots.size() -1).getReferencePoint();

        if(Session.getInstance() != null && this.getPhase() == Phase.TRACKING && this.isTrackingFinished())
            return (lastShot.x > Session.getInstance().getMidPointOfDetectionArea().x) ?
                    Direction.OUT:Direction.IN;
        else
            return Direction.STILL;
    }

    /**
     * Retrieves the predominant object type based on the snapshots collected in the operation.
     * @return The most frequently occurring object type, or ObjectType.UNDEFINED if no clear majority exists.
     */
    public ObjectType getType() {
        // Group snapshots by type and count occurrences
        Map<ObjectType, Long> countMap = this.snapshots.stream()
                .collect(Collectors.groupingBy(Snapshot::getObjectType, Collectors.counting()));

        if (!countMap.isEmpty()) {
            // Sort the map entries by value (count) in descending order
            List<Map.Entry<ObjectType, Long>> sortedEntries = countMap.entrySet().stream()
                    .sorted(Map.Entry.<ObjectType, Long>comparingByValue().reversed())
                    .collect(Collectors.toList());

            // Get the first entry with the highest count
            Map.Entry<ObjectType, Long> entryWithHighestCount = sortedEntries.get(0);
            if (entryWithHighestCount.getValue() < 2)
                return ObjectType.UNKNOWN;
            else if (entryWithHighestCount.getKey() == ObjectType.UNKNOWN
                    && sortedEntries.size() > 1 && sortedEntries.get(1).getValue() > 1)
                    return sortedEntries.get(1).getKey();
            return entryWithHighestCount.getKey();
        } else {
            return null;
        }
    }

    /**
     * Checks whether the operation has concluded.
     * @return true if the tracking has finished, false otherwise.
     */
    public boolean isTrackingFinished(){
        return emptyFrameCount > RVMDetector.getNbFramesUntilConfirmation() ||
                ( phase == Phase.REJECTING && emptyFrameCount > 2);
    }

    /**
     * Checks if the operation has been successfully completed.
     * @return true if the object is valid and no fraud is detected and false otherwise.
     */
    public boolean hasFinishedSuccessfully(){
        return isObjectAccepted() && getDirection() == Direction.IN;
    }

    /**
     * Destroys and wipes out the operation's data including all snapshots.
     */
    protected void destroy(){
        snapshots.forEach(Snapshot::destroy);
        snapshots = null;
    }
}
