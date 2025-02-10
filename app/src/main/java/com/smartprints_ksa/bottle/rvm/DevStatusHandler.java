package com.smartprints_ksa.bottle.rvm;

import com.leesche.yyyiotlib.entity.UnitEntity;

import java.util.List;

public class DevStatusHandler {

    static DevStatusHandler devConfigHandler;

    public static DevStatusHandler getInstance() {
        synchronized (DevStatusHandler.class) {
            if (devConfigHandler == null) {
                devConfigHandler = new DevStatusHandler();
            }
        }
        return devConfigHandler;
    }

    //Status information will be use to analysis when the machine not work normal.
    public void updateEntranceAStatus(String value, List<UnitEntity> devAList, List<UnitEntity> devBList) {
        String[] devStatus = value.split("\\|");
        int weightErr = Integer.parseInt(devStatus[2]);
        int rockErr = Integer.parseInt(devStatus[3]);
        int matStatus = Integer.parseInt(devStatus[4]);
        int eysStatus = Integer.parseInt(devStatus[5]);
        int mtFlag = Integer.parseInt(devStatus[6]);
        int mtErrReg = Integer.parseInt(devStatus[7]);
        int distanceStatus = Integer.parseInt(devStatus[9]);
        String A_WET = (weightErr & 0x01) == 0 ? "NORM" : "ERR";
        String A_ROCK_H = (rockErr & 0x01) == 0 ? "NORM" : "ERR";
        String A_ROCK_L = (((rockErr & 0x02) >> 1) == 0) ? "NORM" : "ERR";
        String A_MT_DOOR = getMatStatusHint(matStatus, 0);
        String A_MT_BELT = getMatStatusHint(matStatus, 1);
        String A_MT_ROLL = getMatStatusHint(matStatus, 4);
        String A_MT_ROCK = getMatStatusHint(matStatus, 5);
        String A_BackLock1 = getMatStatusHint(matStatus, 8);
        int A_SG1 = eysStatus & 0x01;
        int A_SG2 = (eysStatus & 0x02) >> 1;
        int A_SG3 = (eysStatus & 0x04) >> 2;
        int A_SG4 = (eysStatus & 0x08) >> 3;
        int A_Clamp = (eysStatus & 0x100) >> 8;
        int A_DownPos = (eysStatus & 0x400) >> 10;
        int A_UpPos = (eysStatus & 0x800) >> 11;
        int SG_BackLock = (eysStatus & 0x8000) >> 15;//回收锁
        int distanceA = distanceStatus & 0x01;
        String MT220_FLAG = getMat220StatusHint(mtFlag);
        if (devAList.size() > 0) devAList.clear();
        // status of entrance A
        devAList.add(new UnitEntity(0, 0, "Entrance_S(A)" + devStatus[0]));
        // status of electronic scale in entrance A
        devAList.add(new UnitEntity(2, 0, "A_WET " + A_WET));
        // status of the high point of swing motor in entrance A
        devAList.add(new UnitEntity(2, 0, "A_ROCK_H " + A_ROCK_H));
        //status of the high point of swing motor in entrance A
        devAList.add(new UnitEntity(2, 0, "A_ROCK_L " + A_ROCK_L));
        //status of the door in entrance A
        devAList.add(new UnitEntity(0, 0, "A_MT_DOOR " + A_MT_DOOR));
        //status of the belt motor in entrance A
        devAList.add(new UnitEntity(0, 0, "A_MT_BELT " + A_MT_BELT));
        //status of the drum motor in entrance A
        devAList.add(new UnitEntity(0, 0, "A_MT_ROLL " + A_MT_ROLL));
        //status of the swing motor int entrance A
        devAList.add(new UnitEntity(0, 0, "A_MT_ROCK " + A_MT_ROCK));
        //status of the 1th photoelectric sensor in entrance A
        devAList.add(new UnitEntity(0, A_SG1, A_SG1 == 1 ? "A_SG1(1)" : "A_SG1(0)"));
        //status of the 2th photoelectric sensor in entrance A
        devAList.add(new UnitEntity(0, A_SG2, A_SG2 == 1 ? "A_SG2(1)" : "A_SG2(0)"));
        //status of the 3th photoelectric sensor in entrance A
        devAList.add(new UnitEntity(0, A_SG3, A_SG3 == 1 ? "A_SG3(1)" : "A_SG3(0)"));
        //status of the 4th photoelectric sensor in entrance A
        devAList.add(new UnitEntity(0, A_SG4, A_SG4 == 1 ? "A_SG4(1)" : "A_SG4(0)"));
        //status of the Anti-pinch in entrance A
        devAList.add(new UnitEntity(0, A_Clamp, A_Clamp == 1 ? "A_Clamp(1)" : "A_Clamp(0)"));
        //If A_UpPos equal to 1, the swing motor running in the lowest position
        devAList.add(new UnitEntity(0, A_DownPos, A_DownPos == 1 ? "A_DownPos(1)" : "A_DownPos(0)"));
        //If A_UpPos equal to 1, the swing motor running in the highest position
        devAList.add(new UnitEntity(1, A_UpPos, A_UpPos == 1 ? "A_UpPos(1)" : "A_UpPos(0)"));
        //If distanceA equal to 1, the distance sensor have bean detect
        devAList.add(new UnitEntity(0, distanceA, distanceA == 1 ? "DistanceA(1)" : "DistanceA(0)"));

        devAList.add(new UnitEntity(0, mtFlag, MT220_FLAG));
        devAList.add(new UnitEntity(0, SG_BackLock, SG_BackLock == 1 ? "SG_BackLock(1)" : "SG_BackLock(0)"));
        devAList.add(new UnitEntity(0, 0, "SG_BackLock " + A_BackLock1));
//        devAList.add(new UnitEntity(0, recycleDoorStatus, recycleDoorStatus == 1 ? "回收门(超时)" : "回收门(正常)"));
        updateEntranceBStatus(devStatus, devBList);
    }


    public void updateEntranceBStatus(String[] devStatus, List<UnitEntity> devBList) {
        int weightErr = Integer.parseInt(devStatus[2]);
        int rockErr = Integer.parseInt(devStatus[3]);
        int matStatus = Integer.parseInt(devStatus[4]);
        int eysStatus = Integer.parseInt(devStatus[5]);
        int distanceStatus = Integer.parseInt(devStatus[9]);
        String B_WET = (((weightErr & 0x10) >> 4) == 0) ? "NORM" : "ERR";
        String B_ROCK_H = (((rockErr & 0x10) >> 4) == 0) ? "NORM" : "ERR";
        String B_ROCK_L = (((rockErr & 0x20) >> 5) == 0) ? "NORM" : "ERR";
        String B_MT_DOOR = getMatStatusHint(matStatus, 2);
        String B_MT_BELT = getMatStatusHint(matStatus, 3);
        String B_MT_ROLL = getMatStatusHint(matStatus, 6);
        String B_MT_ROCK = getMatStatusHint(matStatus, 7);
        int B_SG1 = (eysStatus & 0x10) >> 4;
        int B_SG2 = (eysStatus & 0x20) >> 5;
        int B_SG3 = (eysStatus & 0x40) >> 6;
        int B_SG4 = (eysStatus & 0x80) >> 7;
        int B_Clamp = (eysStatus & 0x200) >> 9;
        int B_DownPos = (eysStatus & 0x1000) >> 12;
        int B_UpPos = (eysStatus & 0x2000) >> 13;
        int distanceB = (distanceStatus & 0x02) >> 1;
        if (devBList.size() > 0) devBList.clear();
        devBList.add(new UnitEntity(0, 0, "Entrance_S(B)" + devStatus[1]));//投口B状态
        devBList.add(new UnitEntity(2, 0, "B_WET(B) " + B_WET));//电子秤
        devBList.add(new UnitEntity(2, 0, "B_ROCK_H " + B_ROCK_H));
        devBList.add(new UnitEntity(2, 0, "B_ROCK_L " + B_ROCK_L));
        devBList.add(new UnitEntity(0, 0, "B_MT_DOOR " + B_MT_DOOR));
        devBList.add(new UnitEntity(0, 0, "B_MT_BELT " + B_MT_BELT));
        devBList.add(new UnitEntity(0, 0, "B_MT_ROLL " + B_MT_ROLL));
        devBList.add(new UnitEntity(0, 0, "B_MT_ROCK " + B_MT_ROCK));
        devBList.add(new UnitEntity(0, B_SG1, B_SG1 == 1 ? "B_SG1(1)" : "B_SG1(0)"));
        devBList.add(new UnitEntity(0, B_SG2, B_SG2 == 1 ? "B_SG2(1)" : "B_SG2(0)"));
        devBList.add(new UnitEntity(0, B_SG3, B_SG3 == 1 ? "B_SG3(1)" : "B_SG3(0)"));
        devBList.add(new UnitEntity(0, B_SG4, B_SG4 == 1 ? "B_SG4(1)" : "B_SG4(0)"));
        devBList.add(new UnitEntity(0, B_Clamp, B_Clamp == 1 ? "B_Clamp(1)" : "B_Clamp(0)"));
        devBList.add(new UnitEntity(0, B_DownPos, B_DownPos == 1 ? "B_DownPos(1)" : "B_DownPos(0)"));
        devBList.add(new UnitEntity(1, B_UpPos, B_UpPos == 1 ? "B_UpPos(1)" : "B_UpPos(0)"));
        devBList.add(new UnitEntity(0, distanceB, distanceB == 1 ? "Distance_B(1)" : "Distance_B(0)"));
    }

    private String getMat220StatusHint(int mtFlag) {
        String hint = "Shredder(1)";
        if ((mtFlag & 0x01) == 1) {
            //shredder not detected
            hint = "Shredder(0)";
        }
        if (((mtFlag & 0x02) >> 1) == 1) {
            //shredder motor maybe have been block
            hint = "Shredder(-1)";
        }
//        if (((mtFlag & 0x04) >> 2) == 1) {
//            hint = "Shredder(-1)";
//        }
        return hint;
    }

    private String getMatStatusHint(int matStatus, int offset) {
        int status = 0;
        switch (offset) {
            case 0:
                status = matStatus & 0x03;
                break;
            case 1:
                status = (matStatus & 0x12) >> 2;
                break;
            case 2:
                status = (matStatus & 0x030) >> 4;
                break;
            case 3:
                status = (matStatus & 0x1200) >> 6;
                break;
            case 4:
                status = (matStatus & 0x0300) >> 8;
                break;
            case 5:
                status = (matStatus & 0x12000) >> 10;
                break;
            case 6:
                status = (matStatus & 0x03000) >> 12;
                break;
            case 7:
                status = (matStatus & 0x120000) >> 14;
                break;
            case 8:
                status = (matStatus & 0x030000) >> 16;
                break;
        }
        if (status == 0) {
            //no action
            return " NA";
        }
        if (status == 1) {
            //forward rotation
            return " FWD";
        }
        if (status == 2) {
            //reverse rotation
            return " REV";
        }
        if (status == 3) {
            //stop rotaiton
            return " STOP";
        }
        return "";
    }
}
