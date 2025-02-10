package com.smartprints_ksa.battery_detector;

import static com.smartprints_ksa.battery_detector.Processor.em;
import static com.smartprints_ksa.battery_detector.Processor.pw;
import static com.smartprints_ksa.battery_detector.Utils.l2Normalize;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BottleDetector {

    private static InferenceWrapper mInferenceWrapper;
    private static String fileDirPath;
    protected static byte[] d = "N5Iq1stLlFZZXlYXw".getBytes(StandardCharsets.UTF_8);
    protected static byte[] lai = "q1strj6P".getBytes(StandardCharsets.UTF_8);
    private static String mYoloModelName = "4145532f4342432f504b43533550616464696e67.bin";
    private static String mClassifierModelName = "550616464696e674145532f4342432f504b43533.bin";

    private static String tmFormat = "yyyyMMddHHmmss";

    public static  int OBJ_NUMB_MAX_SIZE = 64;
    public static int nClassifierOutput = 256;
    protected static float confidenceThreshold = 0.1f;
    protected static float classThreshold = 0.5f;
    protected static float nmsThreshold = 0.5f;
    private static String device = "";
    private static long durationInference = 0;
    private static String jargon = "5c44f2467766";
    private static boolean trimmed = false;
    private static char number = '0';
    private static char letter = 'A';
    private static boolean validated = false;

    private static long preprocessingTime = 0;
    private static long detectionTime = 0;
    private static long classificationTime = 0;

    private static InferenceResult mInferenceResult = new InferenceResult();  // detection result
    public static String getRequestCode(Context context){
        SimpleDateFormat dateFormat = new SimpleDateFormat(tmFormat);
        String currentDateTime = dateFormat.format(new Date());
        if (!trimmed) {pw = trim(pw,d); em = trim(em, lai); trimmed = true;}
        if (device.equals("")) device = getUniqueID();
        // System.out.println(device);
        String output = "";
        for (int i = 0; i < jargon.length()/2; i += 1) {
            output += jargon.charAt(jargon.length() - 1 - i) + "" + jargon.charAt(i);
        }
        String mediator = jargon.toString(); jargon = hexToBase8(output);
        // System.out.println(mediator + "  " + output + "   " + hexToBase8(output)  + "    " + base8ToHex(hexToBase8(output)));
        try {
            byte[] byteArray = encrypt((jargon + " " + hexToBase8(device) + " " + currentDateTime).getBytes(StandardCharsets.UTF_8), pw, em);
            jargon = mediator.toString();
            return byteArrayToHex(byteArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "FALSE";
    }


    public static boolean setup(Context context, String validationCode){
        if (!trimmed) {pw = trim(pw,d); em = trim(em, lai); trimmed = true;}
        if (device.equals("")) device = getUniqueID();
        // System.out.println(device);
        validated = validateActivationCode(validationCode);
        if (! validated) return false;
        String platform = getPlatform();
        Log.d("setup", "get soc platform:" + platform);

        if (!platform.equals("rk3588")) {
            return false;
        }
        fileDirPath = context.getCacheDir().getAbsolutePath();
        try {
            mInferenceResult.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mInferenceWrapper = new InferenceWrapper();
        initializeObjectDetectionModel(context, mYoloModelName);
        initializeClassificationModel(context, mClassifierModelName);
        return validated;
    }
    public static ArrayList<DetectedObject> recognize(Bitmap mBitmap){
        long startTime = System.nanoTime();
        float mImgScaleX = (float)mBitmap.getWidth() / Processor.YOLO_INPUT;
        float mImgScaleY = (float)mBitmap.getHeight() / Processor.YOLO_INPUT;
        if(!validated) return new ArrayList<>();
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, Processor.YOLO_INPUT, Processor.YOLO_INPUT, true);
        byte[] input = Processor.convertBitmapToByteArray(resizedBitmap);

        InferenceResult.OutputBuffer outputs = mInferenceWrapper.run(input);
        mInferenceResult.setResult(outputs);

        detectionTime = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        ArrayList<DetectedObject> recognitions = mInferenceResult.getResult(mInferenceWrapper);
        Bitmap bm;
        for (int i = 0; i < recognitions.size(); ++i) {
            DetectedObject recognition = recognitions.get(i);
            RectF detection = recognition.getLocation();
            detection.top = (mImgScaleY * detection.top);
            detection.left = (mImgScaleX * detection.left);
            detection.bottom = (mImgScaleY * detection.bottom);
            detection.right = (mImgScaleX * detection.right);

            recognitions.get(i).setLocation(detection);
            bm = Processor.extractAndResizeBBoxWithAspectRatio(mBitmap, detection, Processor.CLASSIFIER_INPUT);

            float[] embedding = mInferenceWrapper.runClassifier(Processor.convertBitmapToByteArray(bm));
            embedding = l2Normalize(embedding);
            System.out.println("prediction: " + Arrays.toString(embedding));
//            recognition.setScore(predictions);
            recognition.setEmbedding(embedding);
        }

        classificationTime = System.nanoTime() - startTime;

        return recognitions;
    }

    public static ArrayList<DetectedObject> detect(Bitmap mBitmap){
        long startTime = System.nanoTime();
        float mImgScaleX = (float)mBitmap.getWidth() / Processor.YOLO_INPUT;
        float mImgScaleY = (float)mBitmap.getHeight() / Processor.YOLO_INPUT;
        if(!validated) return new ArrayList<>();
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, Processor.YOLO_INPUT, Processor.YOLO_INPUT, true);
        byte[] input = Processor.convertBitmapToByteArray(resizedBitmap);

        InferenceResult.OutputBuffer outputs = mInferenceWrapper.run(input);
        mInferenceResult.setResult(outputs);
//        Log.w("INFERENCE", "detection time: " + detectionTime);
        ArrayList<DetectedObject> recognitions = mInferenceResult.getResult(mInferenceWrapper);
        Bitmap bm;
        for (int i = 0; i < recognitions.size(); ++i) {
            DetectedObject recognition = recognitions.get(i);
            RectF detection = recognition.getLocation();
            detection.top = (mImgScaleY * detection.top);
            detection.left = (mImgScaleX * detection.left);
            detection.bottom = (mImgScaleY * detection.bottom);
            detection.right = (mImgScaleX * detection.right);

            recognitions.get(i).setLocation(detection);
        }
        detectionTime = System.nanoTime() - startTime;
//        Log.w("INFERENCE", "classification time: " + classificationTime);

        return recognitions;
    }
    private static boolean isFirstRun(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("setting", context.MODE_PRIVATE);
        boolean isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (isFirstRun) {
            editor.putBoolean("isFirstRun", false);
            editor.commit();
        }

        return isFirstRun;
    }
    private static void createFile(Context context, String fileName) {
        String filePath = fileDirPath + "/" + fileName;
        try {
            File dir = new File(fileDirPath);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(filePath);

            if (!file.exists() || isFirstRun(context)) {

                InputStream ins = context.getAssets().open(fileName);
                FileOutputStream fos = new FileOutputStream(file);

                byte[] targetArray = new byte[ins.available()];
                ins.read(targetArray);
                InputStream input =  new ByteArrayInputStream(decrypt(targetArray, pw, em ));
                ins.close();

                byte[] buffer = new byte[8192];
                int count = 0;

                while ((count = input.read(buffer)) > 0) {
                    fos.write(buffer, 0, count);
                }

                fos.close();
                input.close();

                Log.d("createFile", "Create " + filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private  static byte[] decrypt(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {

        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/ISO10126Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        return cipher.doFinal(ciphertext);
    }

    private static String getPlatform()
    {
        String platform = null;
        try {
            Class<?> classType = Class.forName("android.os.SystemProperties");
            Method getMethod = classType.getDeclaredMethod("get", new Class<?>[]{String.class});
            platform = (String) getMethod.invoke(classType, new Object[]{"ro.board.platform"});
        } catch (Exception e) {
            e.printStackTrace();
        }
        return platform;
    }
    private static void initializeObjectDetectionModel(Context context, String mYoloModelName){
        createFile(context, mYoloModelName);
        try {
            mInferenceWrapper.initYolo(Processor.YOLO_INPUT, Processor.YOLO_INPUT,
                    Processor.CHANNELS, fileDirPath + "/" + mYoloModelName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initializeClassificationModel(Context context, String mClassifierModelName){
        createFile(context, mClassifierModelName);
        try {
            mInferenceWrapper.initClassifier(Processor.CLASSIFIER_INPUT, Processor.CLASSIFIER_INPUT,
                    Processor.CHANNELS, fileDirPath + "/" + mClassifierModelName);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static String byteArrayToHex(byte[] byteArray) {
        StringBuilder sb = new StringBuilder(byteArray.length * 2);
        for(byte b: byteArray)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexStringToByteArray(String str) {
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                    + Character.digit(str.charAt(i+1), 16));
        }
        return data;
    }

    private static String hexToBase8(String hexString) {
        String binaryString = "";
        String octalString = "";

        // Convert the hexadecimal string to binary string
        for (int i = 0; i < hexString.length(); i++) {
            String binary = Integer.toBinaryString(Character.digit(hexString.charAt(i), 16));
            while (binary.length() < 4) {
                binary = "0" + binary;
            }
            binaryString += binary;
        }

        // Convert the binary string to octal string
        int length = binaryString.length();
        int padding = length % 3;
        if (padding > 0) {
            for (int i = 0; i < 3 - padding; i++) {
                binaryString = "0" + binaryString;
            }
        }
        length = binaryString.length();
        for (int i = 0; i < length; i += 3) {
            String triplet = binaryString.substring(i, i + 3);
            int octal = Integer.parseInt(triplet, 2);
            octalString += Integer.toString(octal);
        }

        return octalString;
    }

    private static String base8ToHex(String octalString) {
        String binaryString = "";
        String hexString = "";

        // Convert the octal string to binary string
        for (int i = 0; i < octalString.length(); i++) {
            String binary = Integer.toBinaryString(Character.digit(octalString.charAt(i), 8));
            while (binary.length() < 3) {
                binary = "0" + binary;
            }
            binaryString += binary;
        }

        // Convert the binary string to hexadecimal string
        int length = binaryString.length();
        int padding = length % 4;
        if (padding > 0) {
            for (int i = 0; i < 4 - padding; i++) {
                binaryString = "0" + binaryString;
            }
        }
        length = binaryString.length();
        for (int i = 0; i < length; i += 4) {
            String quadruplet = binaryString.substring(i, i + 4);
            int decimal = Integer.parseInt(quadruplet, 2);
            hexString += Integer.toHexString(decimal);
        }

        if (hexString.charAt(0) == '0') return hexString.substring(1);
        return hexString;
    }

    private static boolean validateActivationCode(String validation){
        if (validation.length() == 0) return false;
        try{
            String[] decoded = new String(decrypt(hexStringToByteArray(validation), pw, em), StandardCharsets.UTF_8).split(" ");
            System.out.println("validateActivationCode: " + Arrays.toString(decoded));
            String output = "";
            for (int i = 0; i < jargon.length()/2; i += 1) {
                output += jargon.charAt(jargon.length() - 1 - i) + "" + jargon.charAt(i);
            }
            return device.equals(decoded[0]) && output.equals(base8ToHex(decoded[1]));
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

    private static byte[] encrypt(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/ISO10126Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        return cipher.doFinal(plaintext);
    }

    public static long getPipelineExecutionTime(){
        return preprocessingTime + detectionTime + classificationTime;
    }


    public static String getUniqueID() {
        String serialNumber;

        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);

            serialNumber = (String) get.invoke(c, "gsm.sn1");
            if (serialNumber.equals(""))
                serialNumber = (String) get.invoke(c, "ril.serialnumber");
            if (serialNumber.equals(""))
                serialNumber = (String) get.invoke(c, "ro.serialno");
            if (serialNumber.equals(""))
                serialNumber = (String) get.invoke(c, "sys.serialnumber");
            if (serialNumber.equals(""))
                serialNumber = Build.SERIAL;

            // If none of the methods above worked
            if (serialNumber.equals(""))
                serialNumber = "SMARTPRINTS-EMPTY";
        } catch (Exception e) {
            e.printStackTrace();
            serialNumber = "SMARTPRINTS-EXCEPTION";
        }

        return serialNumber;
    }


//    private static String getUniqueID(Context context){
//        String myAndroidDeviceId = "";
//        TelephonyManager mTelephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        try {
//            if (mTelephony.getDeviceId() != null) {
//                return mTelephony.getDeviceId();
//            }
//        }catch(Exception ignored){};
//
//        myAndroidDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
//
//        return myAndroidDeviceId;
//    }

    private static byte[] trim(byte[] firstArray, byte[] secondArray) {
        byte[] mergedArray = new byte[firstArray.length + secondArray.length];
        System.arraycopy(firstArray, 0, mergedArray, 0, firstArray.length);
        System.arraycopy(secondArray, 0, mergedArray, firstArray.length, secondArray.length);
        return mergedArray;
    }

    private static ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap, Boolean quant, int inputSize) {
        ByteBuffer byteBuffer;

        if (quant) {
            byteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * Processor.CHANNELS);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(Processor.BYTES_SIZE * inputSize * inputSize * Processor.CHANNELS);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                final int val = intValues[pixel++];
                if (quant) {
                    byteBuffer.put((byte) ((val >> 16) & 0xFF));
                    byteBuffer.put((byte) ((val >> 8) & 0xFF));
                    byteBuffer.put((byte) (val & 0xFF));
                } else {

                    byteBuffer.putFloat((((val >> 16) & 0xFF)) * 1.0f );
                    byteBuffer.putFloat((((val >> 8) & 0xFF))* 1.0f );
                    byteBuffer.putFloat((((val) & 0xFF))* 1.0f );

                }
            }
        }
        return byteBuffer;
    }
}
