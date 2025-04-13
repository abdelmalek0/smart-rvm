package com.smartprints_ksa.bottle;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Pair;
import android.util.Range;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.leesche.logger.Logger;
import com.leesche.yyyiotlib.entity.CmdResultEntity;
import com.leesche.yyyiotlib.entity.UnitEntity;
import com.leesche.yyyiotlib.serial.callback.ControlCallBack;
import com.leesche.yyyiotlib.serial.manager.Cmd2Constants;
import com.leesche.yyyiotlib.serial.manager.RvmHelper;
import com.smartprints_ksa.battery_detector.LogToFile;
import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.enums.Phase;
import com.smartprints_ksa.battery_detector.data_structure.Snapshot;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;
import com.smartprints_ksa.bottle.rvm.BottleChecker;
import com.smartprints_ksa.bottle.rvm.DevStatusHandler;
import com.smartprints_ksa.bottle.ui.CustomDialog;
import com.smartprints_ksa.bottle.ui.CustomProgressDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class RealtimeTrackingActivity extends AppCompatActivity {
    private final String[] permissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
    };
    private Button backBtn;
    private float mIvScaleX = -1, mIvScaleY = -1, mStartX = -1, mStartY = -1;
    private Paint mTrackRectPaint = null;
    private Paint mTrackTextPaint = null;
    private Paint mTrackTitlePaintBackground = null;

    private Paint mTrackTitlePaint = null;

    private Paint mDetectPaint;
    private ImageView imageView;
    private CameraDevice cameraDevice;
    private HandlerThread handlerThread;
    private Handler handler;
    private CameraManager cameraManager;
    private TextureView textureView;
    private ExecutorService executorService;
    private Future<?> currentBitmapWorkerTask;
    public Rect mDetectCropRect;
    private SharedPreferences prefs;
    private CameraCaptureSession cameraCaptureSession;
    private Gson gson;
    private String barcode = "";
    private String rvmMessage = "";
    private List<UnitEntity> devAList = new ArrayList<>();
    private List<UnitEntity> devBList = new ArrayList<>();
    private CustomProgressDialog progressDialog = null;
    private CustomDialog initDialog = null;
    private ControlCallBack controlCallBack = new ControlCallBack() {
        @Override
        public void onResult(String cmdBackResult) {
            if (gson == null) gson = new Gson();
            CmdResultEntity cmdResultEntity = gson.fromJson(cmdBackResult, CmdResultEntity.class);
            switch (cmdResultEntity.getFunc_code()) {
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_BackMacInfo:
                    String[] values = cmdResultEntity.getValue().split("\\|");
                    System.out.println("CMD_OStar_BackMacInfo: " + Arrays.toString(values));
                    break;
                case Cmd2Constants.CMD_SCAN_StmToAndroid.CMD_OStar_BackMacInfo:
                case Cmd2Constants.CMD_SCAN_StmToAndroid.CMD_OStar_LoadCode:
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_QrCode:
                case Cmd2Constants.CMD_SCAN_StmToAndroid.CMD_OStar_Distance:
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_BackEnMT220V:
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_BackOpenCloseDoor:
                    break;
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_OutNum:
                    String[] bottleCountStr = cmdResultEntity.getValue().split("\\|");
                    System.out.println("bottleCountStr: " + Arrays.toString(bottleCountStr) + " scanner " + cmdResultEntity.getBox_code());
                    break;
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_WeightCheckRes:
                    int weight = Integer.parseInt(cmdResultEntity.getValue());
                    BottleChecker.getInstance().setBottleWeightStatus(cmdResultEntity.getBox_code(), weight);

                    // check the weight of bottle
                    Pair<Integer, String> response_ = BottleChecker
                            .getInstance().checkToSendCmdToStm(cmdResultEntity);

                    // Mark the object as being accepted/rejected by the RVM
                    if (RVMDetector.getCurrentOperation() != null) {
                        if (response_.second != null)
                            rvmMessage = response_.second + " : " + weight;
                        if (response_.first == -1)
                            RVMDetector.getCurrentOperation().reject();
                        else if (response_.first == 1) {
                            RVMDetector.getCurrentOperation().accept();
                        }
                    }
                    break;
                case Cmd2Constants.CMD_SCAN_StmToAndroid.CMD_OStar_BarCode:
                    BottleChecker.getInstance().setBottleTypeStatus(cmdResultEntity);

                    // check the item object type
                    Pair<Integer, String> response = BottleChecker.getInstance().
                            checkToSendCmdToStm(cmdResultEntity);

                    // Mark the object as being accepted/rejected by the RVM
                    if (RVMDetector.getCurrentOperation() != null) {
                        if (response.second != null)
                            rvmMessage = response.second;
                        if (response.first == -1)
                            RVMDetector.getCurrentOperation().reject();
                        else {
                            if (response.first == 1) {
                                RVMDetector.getCurrentOperation().accept();
                            }
                            barcode = cmdResultEntity.getValue();
                        }
                    }
                    break;
                case Cmd2Constants.CMD_StmToAndroid.CMD_OStar_StatusErr:
                    DevStatusHandler.getInstance().updateEntranceAStatus(cmdResultEntity.getValue(), devAList, devBList);
                    break;
                case Cmd2Constants.OtherSysStatus.ENTRANCE_STATUS:
                    BottleChecker.getInstance().init(cmdResultEntity.getBox_code());
                    break;
                default:
                    Logger.i("【RVM Result】 " + cmdBackResult);
                    break;
            }
        }

        @Override
        public void onSaleResult(String saleCmdBackResult) {}

        @Override
        public void onDeviceStatusResult(int code, String otherResult) {}
    };

    private TextureView.SurfaceTextureListener listener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            // Open up camera when Surface Texture is ready
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            stopRepeatingAndCloseSession(cameraCaptureSession);
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            // Pass a bitmap to the Thread executor if:
            // 0. no bitmap is being treated
            // 1. the last bitmap has finished being treated
            // 2. the operation is in the tracking phase
            if (currentBitmapWorkerTask == null || currentBitmapWorkerTask.isDone() ||
                    (RVMDetector.getCurrentOperation() != null && !RVMDetector.getCurrentOperation().isTrackingFinished()
                            && RVMDetector.getCurrentOperation().getPhase() == Phase.TRACKING)
            ) {
                currentBitmapWorkerTask = executorService.submit(new BitmapWorkerThread(surfaceTexture));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_realtime_tracking);

        // Camera Permissions
        checkPermissions();
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // set RVM callbacks
        RvmHelper.getInstance().addControlCallBack(controlCallBack);

        // Initialize Thread Executor to run detections sequentially
        ThreadFactory customThreadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        };
        executorService  = Executors.newSingleThreadExecutor(customThreadFactory);

        // Set up variables for painting and drawing.
        mDetectPaint = new Paint();
        mDetectPaint.setColor(Color.RED);
        mDetectPaint.setStyle(Paint.Style.STROKE);
        mDetectPaint.setStrokeWidth(3);

        mTrackRectPaint = new Paint();
        mTrackRectPaint.setColor(Color.BLACK);
        mTrackRectPaint.setStrokeWidth(5);
        mTrackRectPaint.setStyle(Paint.Style.STROKE);

        mTrackTitlePaintBackground = new Paint();
        mTrackTitlePaintBackground.setColor(Color.BLACK);
        mTrackTitlePaintBackground.setStrokeWidth(5);
        mTrackTitlePaintBackground.setStyle(Paint.Style.FILL_AND_STROKE);

        mTrackTextPaint = new Paint();
        mTrackTextPaint.setTextSize(48);
        mTrackTextPaint.setColor(Color.BLACK);
        mTrackTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mTrackTitlePaint = new Paint();
        mTrackTitlePaint.setTextSize(40);
        mTrackTitlePaint.setColor(Color.WHITE);
        mTrackTitlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Initialize progress and pop-up dialogs
        progressDialog = new CustomProgressDialog(this);
        progressDialog.setTitle("Bottle verification");
        progressDialog.setMessage("Please wait while the app handle the item ...");

        initDialog = new CustomDialog(RealtimeTrackingActivity.this, getString(R.string.add_bottle), R.color.green);

        // Initialize the ImageView for overlaying bounding boxes.
        imageView = this.findViewById(R.id.imageView);

        // Initialize TextureView for displaying camera preview
        textureView = this.findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(listener);

        // Initialize Back button to finish user session
        backBtn = this.findViewById(R.id.backButton);
        backBtn.setOnClickListener(v -> finishUserSession());

        // retrieve data from shared preferences
        prefs = getSharedPreferences("Bottle", MODE_PRIVATE);
        mDetectCropRect = new Rect(
                prefs.getInt("DetectCropLeft", 0),
                prefs.getInt("DetectCropTop", 0),
                prefs.getInt("DetectCropRight", 0),
                prefs.getInt("DetectCropBottom", 0)
        );

        // Set the count of empty frames required to validate the completion of an operation
        RVMDetector.setNbFramesUntilConfirmation(prefs.getInt("nbFramesUntilConfirmation",
                RVMDetector.getNbFramesUntilConfirmation()));

        // Start a new User session
        RVMDetector.startNewSession(mDetectCropRect);
    }

    private class BitmapWorkerThread implements Runnable {
        private final SurfaceTexture surfaceTexture;
        private final long NOTIFICATION_DURATION = 5000;

        BitmapWorkerThread(SurfaceTexture surfaceTexture) {
            this.surfaceTexture = surfaceTexture;
        }

        @Override
        public void run() {
            // Retrieve a bitmap from the camera preview and set the canvas to draw on
            Bitmap bitmap = textureView.getBitmap();
            if (bitmap == null) return;
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);// RotateBitmap(mBitmap.copy(Bitmap.Config.ARGB_8888, true), 270);
            Canvas canvas = new Canvas(mutableBitmap);

            // Calculate scaling ratios ( between bitmap and textureView/imageView )
            if (mIvScaleX == -1) {
                calculateScaleXY(bitmap);
                runOnUiThread(() -> initDialog.show());
            }

            // run the RVM detector on the bitmap ( detection + tracking )
            Snapshot snapshot = RVMDetector.process(mutableBitmap);

            // Rescale the bounding box results to the Imageview
            Rect detection = snapshot != null ?
                    Utils.rescaleBBox(snapshot.getRect(), mStartX, mStartY, mIvScaleX, mIvScaleY) : null;

            // Display info related to the Current operation as pop up windows
            if (RVMDetector.getCurrentOperation() != null) {

                // Progress info while Operation is ongoing
                if (!RVMDetector.getCurrentOperation().isTrackingFinished()) {
                    if (RVMDetector.getCurrentOperation().getSnapshots().size() == 1
                            && !progressDialog.isShowing()) {
                        runOnUiThread(() -> {
                            if(initDialog.isShowing()) initDialog.dismiss();
                            progressDialog.show();
                        });
                    }
                    if (RVMDetector.getCurrentOperation().getPhase() == Phase.DETECTION)
                        runOnUiThread(() -> {
                            progressDialog.setMessage("Reading the barcode of the bottle/can");
                        });
                    else if (RVMDetector.getCurrentOperation().getPhase() == Phase.TRACKING)
                        runOnUiThread(() -> {
                            progressDialog.setMessage("Tracking the bottle/can");
                        });
                    else if (RVMDetector.getCurrentOperation().getPhase() == Phase.REJECTING)
                        runOnUiThread(() -> {
                            progressDialog.setMessage("Finishing the operation");
                        });

                }
                // Dialogs when the operation ends
                else if (progressDialog.isShowing())
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        int color;
                        String message;
                        if (RVMDetector.getCurrentOperation().hasFinishedSuccessfully()) {
                            message = "Bottle/Can is added successfully! " +
                                    RVMDetector.getCurrentOperation().getType().name() + " : " + barcode;
                            color = R.color.green;
                        }
                        else if (RVMDetector.getCurrentOperation().isObjectAccepted()) {
                            message = "Bottle/Can is rejected : Fraudulent activity has been detected!";
                            color = R.color.red;
                        }
                        else {
                            message = "Bottle/Can is rejected : " + rvmMessage;
                            color = R.color.red;
                        }

                        new CustomDialog(RealtimeTrackingActivity.this, message, color)
                                .showMomentarily(NOTIFICATION_DURATION);

                        new Handler().postDelayed(() -> {
                            if(!progressDialog.isShowing()) initDialog.show();
                            }, NOTIFICATION_DURATION);
                    });
            }

            // Draw on the canvas
            runOnUiThread(() -> {
                canvas.drawRect(mDetectCropRect, mDetectPaint);
                canvas.drawRect(new RectF(0, 0, bitmap.getWidth(), 40), mTrackTitlePaintBackground);

                // Display the total number of items put from each ObjectType
                canvas.drawText("Bottles: " + RVMDetector.countObjectsOfType(ObjectType.BOTTLE),
                        5, 40, mTrackTitlePaint);
                canvas.drawText("Cans: " + RVMDetector.countObjectsOfType(ObjectType.CAN),
                        5 + bitmap.getWidth() * .5f, 40, mTrackTitlePaint);

                if (detection != null) {
                    ObjectType type = RVMDetector.getCurrentOperation().getType();

                    // Set colors based on Object Type
                    mTrackRectPaint.setColor(Utils.getColor(type));
                    mTrackTextPaint.setColor(Utils.getColor(type));

                    // Draw the bounding box around the object
                    canvas.drawRect(detection, mTrackRectPaint);
                    if (type != ObjectType.UNKNOWN && type != ObjectType.NOT_VALID)
                        canvas.drawText(type.name(), detection.left, detection.top - 10,
                                mTrackTextPaint);
                }

                // Display canvas
                try {
                    imageView.setImageBitmap(mutableBitmap);
                    surfaceTexture.releaseTexImage();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // Open camera using Camera2API
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                handlerThread = new HandlerThread("videoThread");
                handlerThread.start();
                handler = new Handler(handlerThread.getLooper());

                cameraManager.openCamera(cameraManager.getCameraIdList()[0], new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                        Surface surface = new Surface(surfaceTexture);
                        try {
                            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);

                            try {
                                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraManager.getCameraIdList()[0]);
                                Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRanges[1]);
                            } catch (Exception ignored) {
                            }

                            captureRequestBuilder.addTarget(surface);
                            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    cameraCaptureSession = session;
                                    try {
                                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                }
                            }, handler);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        releaseCamera();
                        releaseHandlerThread();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        releaseCamera();
                        releaseHandlerThread();
                    }
                }, handler);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Calculate the ratios between bitmap and ImageView
    private void calculateScaleXY(Bitmap bitmap) {
        int mBitmapWidth = bitmap.getWidth();
        int mBitmapHeight = bitmap.getHeight();
        int mImageViewWidth = imageView.getWidth();
        int mImageViewHeight = imageView.getHeight();

        mIvScaleX = (mBitmapWidth > mBitmapHeight ?
                (float) mImageViewWidth / mBitmapWidth :
                (float) mImageViewHeight / mBitmapHeight);

        mIvScaleY = (mBitmapHeight > mBitmapWidth ?
                (float) mImageViewHeight / mBitmapHeight :
                (float) mImageViewWidth / mBitmapWidth);

        mStartX = (mImageViewWidth - mIvScaleX * mBitmapWidth) / 2;
        mStartY = (mImageViewHeight - mIvScaleY * mBitmapHeight) / 2;
    }

    // Destroy User session
    public void finishUserSession() {
        RVMDetector.destroySession();
        startActivity(new Intent(getApplicationContext(), MainActivity.class));
        finish();
    }
    private void checkPermissions() {
        if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(permissions[1]) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(permissions[2]) != PackageManager.PERMISSION_GRANTED) {
            startRequestPermission();
        } else {
        }
    }
    private void startRequestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 321);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LogToFile.log("yolo", "onRequestPermissionsResult:" + requestCode);
        if (requestCode == 321) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast toast = Toast.makeText(this, "Please turn on the permission " +
                                "from the setting interface",
                        Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                finish();
            } else {
            }
        }
    }
    private void stopRepeatingAndCloseSession(CameraCaptureSession session) {
        try {
            // Stop the repeating request and close the session
            session.stopRepeating();
            session.close();
        } catch (CameraAccessException e) {
            // Handle the exception, for example, by releasing and reopening the camera
            e.printStackTrace();
            releaseCamera();
        } catch (Exception e) {
            session = null;
        }
    }
    private void releaseCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
    private void releaseHandlerThread() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
                handlerThread = null;
                handler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
        releaseHandlerThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(listener);
        }
    }

    @Override
    protected void onPause() {
        releaseCamera();
        releaseHandlerThread();
        super.onPause();
    }

    @Override
    protected void onStop() {
        releaseCamera();
        releaseHandlerThread();
        super.onStop();
    }
}
