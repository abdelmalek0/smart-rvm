package com.smartprints_ksa.bottle.rvm;


import android.util.Pair;

import com.leesche.logger.Logger;
import com.leesche.yyyiotlib.entity.CmdResultEntity;
import com.leesche.yyyiotlib.serial.manager.RvmHelper;
import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;

public class BottleChecker {

    private boolean bottleWeightHaveChecked,
            bottleWeightCheckedIsValid, bottleBWeightHaveChecked
            ,bottleBWeightCheckedIsValid,
            bottleTypeCheckedIsValid, bottleBTypeCheckedIsValid, bottleTypeHaveChecked, bottleBTypeHaveChecked;
    private static BottleChecker bottleChecker;

    public static BottleChecker getInstance() {
        synchronized (BottleChecker.class) {
            if (bottleChecker == null) {
                bottleChecker = new BottleChecker();
            }
        }
        return bottleChecker;
    }

    public void init(int boxCode) {
        if (boxCode == 1) {
//            this.bottleCodeHaveChecked = false;
            this.bottleWeightHaveChecked = false;
            this.bottleTypeHaveChecked = false;
//            this.bottleCodeCheckedIsValid = false;
            this.bottleWeightCheckedIsValid = false;
            this.bottleTypeCheckedIsValid = false;

        } else {
//            this.bottleBCodeHaveChecked = false;
            this.bottleBWeightHaveChecked = false;
            this.bottleBTypeCheckedIsValid = false;
//            this.bottleBCodeCheckedIsValid = false;
            this.bottleBWeightCheckedIsValid = false;
            this.bottleBTypeHaveChecked = false;
        }
    }

    public void setBottleWeightStatus(int boxCode, int weight) {
        Logger.i("【Bottle Checker】weight " + boxCode);
        if (boxCode == 1) {
            this.bottleWeightHaveChecked = true;
            this.bottleWeightCheckedIsValid = weight < 100;
        } else {
            this.bottleBWeightHaveChecked = true;
            this.bottleBWeightCheckedIsValid = weight < 100;
        }
    }

    public boolean isCanSendCmdToStm(int boxCode) {
        Logger.i("【Bottle Checker】check " + boxCode);
        if (boxCode == 1) {
            if (bottleWeightHaveChecked && !bottleWeightCheckedIsValid) return true;
            if (bottleTypeHaveChecked && !bottleTypeCheckedIsValid) return true;
            return bottleWeightHaveChecked && bottleTypeHaveChecked;
        } else {
            if (bottleBWeightHaveChecked && !bottleBWeightCheckedIsValid) return true;
            if (bottleBTypeHaveChecked && !bottleBTypeCheckedIsValid) return true;
            return bottleBWeightHaveChecked && bottleBTypeHaveChecked;
        }
    }

    // Checks the validity of the item based on the type
    public void setBottleTypeStatus(CmdResultEntity cmdResultEntity) {
        if (cmdResultEntity.getBox_code() == 1) {
            this.bottleTypeHaveChecked = true;
            this.bottleTypeCheckedIsValid = RVMDetector.getCurrentOperation() != null && (RVMDetector.getCurrentOperation().getType() == ObjectType.BOTTLE ||
                    RVMDetector.getCurrentOperation().getType() == ObjectType.CAN);
        } else {
            this.bottleBTypeHaveChecked = true;
            this.bottleBTypeCheckedIsValid = RVMDetector.getCurrentOperation() != null &&
                    (RVMDetector.getCurrentOperation().getType() == ObjectType.BOTTLE ||
                    RVMDetector.getCurrentOperation().getType() == ObjectType.CAN);
        }
    }

    public boolean isBottleValid(int boxCode) {
        if (boxCode == 1) {
            return bottleWeightCheckedIsValid && bottleTypeCheckedIsValid;
        } else {
            return bottleBWeightCheckedIsValid && bottleBTypeCheckedIsValid;
        }
    }

    public int checkIsPass(CmdResultEntity cmdResultEntity) {
        if (isCanSendCmdToStm(cmdResultEntity.getBox_code())) {
            boolean isPass = BottleChecker.getInstance().isBottleValid(cmdResultEntity.getBox_code());
            RvmHelper.getInstance().uploadCheckResultToBoard(cmdResultEntity.getBox_code(), cmdResultEntity.getStatus(), isPass);
            int statusCode = 2;
            if (!isPass) {
                if (cmdResultEntity.getBox_code() == 1) {
                    if (bottleTypeHaveChecked && bottleWeightHaveChecked) {
                        if (!bottleTypeCheckedIsValid && !bottleWeightCheckedIsValid)
                            statusCode = 3;
                        if (bottleTypeCheckedIsValid && !bottleWeightCheckedIsValid)
                            statusCode = 4;
                        if (!bottleTypeCheckedIsValid && bottleWeightCheckedIsValid)
                            statusCode = 5;
                    }
                    if (bottleTypeHaveChecked && !bottleWeightHaveChecked) {
                        return 6;
                    }
                    if (!bottleTypeHaveChecked && bottleWeightHaveChecked) {
                        return 7;
                    }
                }
                if (cmdResultEntity.getBox_code() == 2) {
                    if (bottleBTypeHaveChecked && bottleBWeightHaveChecked) {
                        if (!bottleBTypeCheckedIsValid && !bottleBWeightCheckedIsValid)
                            statusCode = 3;
                        if (bottleBTypeCheckedIsValid && !bottleBWeightCheckedIsValid)
                            statusCode = 4;
                        if (!bottleBTypeCheckedIsValid  && bottleBWeightCheckedIsValid)
                            statusCode = 5;
                    }
                    if (bottleBTypeHaveChecked && !bottleBWeightHaveChecked) {
                        return 6;
                    }
                    if (!bottleBTypeHaveChecked && bottleBWeightHaveChecked) {
                        return 7;
                    }
                }
            }
            init(cmdResultEntity.getBox_code());
            return statusCode;
        } else {
            if (cmdResultEntity.getBox_code() == 1) {
                if (bottleTypeHaveChecked) {
                    Logger.i("【Bottle Checker】 等待检查重量 (" + cmdResultEntity.getBox_code() + ")");
                    return 1;
                }
                if (bottleWeightHaveChecked) {
                    Logger.i("【Bottle Checker】 等待检查条码(" + cmdResultEntity.getBox_code() + ")");
                    return 0;
                }
            }
            if (cmdResultEntity.getBox_code() == 2) {
                if (bottleBTypeHaveChecked) {
                    Logger.i("【Bottle Checker】 等待检查重量 (" + cmdResultEntity.getBox_code() + ")");
                    return 1;
                }
                if (bottleBWeightHaveChecked) {
                    Logger.i("【Bottle Checker】 等待检查条码(" + cmdResultEntity.getBox_code() + ")");
                    return 0;
                }
            }
            return -1;
        }
    }

    public String getMsgHintByStatus(int boxCode, int status) {
        String hintMsg = null;
        switch (status) {
            case 0:
            case 1:
            case 2:
                break;
            case 3:
                hintMsg = "Overweight + Non bottle!";
                break;
            case 4:
            case 7:
                hintMsg = "Overweight!";
                break;
            case 5:
            case 6:
                hintMsg = "Non bottle!";
                break;
            case -1:
                if (boxCode == 1) {
                    if ((( bottleTypeHaveChecked && bottleTypeCheckedIsValid) && !bottleWeightHaveChecked) ||
                            (!bottleTypeHaveChecked && bottleWeightHaveChecked && bottleWeightCheckedIsValid)) {
                        hintMsg = "Overtime!\n";
                    } else {
                        hintMsg = "pls deposit standardize!\n";
                    }
                }
                if (boxCode == 2) {
                    if ((( bottleBTypeHaveChecked && bottleBTypeCheckedIsValid) && !bottleBWeightHaveChecked) ||
                            (!bottleBTypeHaveChecked && bottleBWeightHaveChecked && bottleBWeightCheckedIsValid)) {
                        hintMsg = "Overtime!\n";
                    } else {
                        hintMsg = "pls deposit standardize!\n";
                    }
                }
                break;
        }
        return hintMsg;
    }

    /**
     * @return A Pair containing the status and hint message.
 *          first: The status indicates the outcome of the check:
     *         -1: RVM (Reverse Vending Machine) rejected item,
     *          0: Item still in check,
     *          1: RVM accepted item.
     *      second: The hint message provides additional information based on the status.
     */
    public Pair<Integer, String> checkToSendCmdToStm(CmdResultEntity cmdResultEntity) {
        // first - 1: rvm accepted item, -1: rvm rejected item, 0: item still in check
        // second - hintMsg
        int code = checkIsPass(cmdResultEntity);
        return new Pair<>((code > 2) ? -1 : (code == 2 ? 1 : 0)
                , getMsgHintByStatus(cmdResultEntity.getBox_code(), code));
    }
}

