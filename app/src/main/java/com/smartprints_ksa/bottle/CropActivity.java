package com.smartprints_ksa.bottle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import com.smartprints_ksa.battery_detector.LogToFile;
import com.smartprints_ksa.bottle.demo.R;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;

public class CropActivity extends AppCompatActivity{

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Rect mDetectCropRect;
    private Paint mDetectCropPaint;
    private boolean mIsDrawingCrop;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private Button detectBtn, saveBtn, clearBtn, backBtn;

    private Bitmap mainBitmap = null;

    private CameraDevice cameraDevice;
    private CameraManager cameraManager;
    private boolean running = false;
    private int xCenterSurfaceView = 0;
    private int yCenterSurfaceView = 0;
    private boolean isCenterSurfaceView = false;
    int FRAME_WIDTH = 1080;
    int FRAME_HEIGHT = 610;
    private Thread mainLoop = null;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        // Granting Camera permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    2000);
        }

        // Initialize Shared Preferences
        editor = getSharedPreferences("Bottle", MODE_PRIVATE).edit();
        prefs = getSharedPreferences("Bottle", MODE_PRIVATE);

        // Get reference to SurfaceView
        mSurfaceView = this.findViewById(R.id.surfaceView);

        // Get reference to SurfaceHolder and register callback
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

        // Initialize Rect and Paint for cropping
        mDetectCropRect = new Rect();

        mDetectCropPaint = new Paint();
        mDetectCropPaint.setColor(Color.RED);
        mDetectCropPaint.setStyle(Paint.Style.STROKE);
        mDetectCropPaint.setStrokeWidth(3);

        // Click on this button to start selecting the area of detection
        detectBtn = this.findViewById(R.id.detect);
        detectBtn.setOnClickListener((v) -> {
            Toast.makeText(this, "Select the area of detection", Toast.LENGTH_SHORT).show();

            mSurfaceView.setOnTouchListener((v1, event) -> {
                running = false;
                if (mainBitmap != null) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // Start drawing detection area
                            mIsDrawingCrop = true;
                            mDetectCropRect.left = (int) Math.min(Math.max(event.getX(), xCenterSurfaceView), xCenterSurfaceView + mainBitmap.getWidth());
                            mDetectCropRect.top = (int) Math.min(Math.max(event.getY(), yCenterSurfaceView), yCenterSurfaceView + mainBitmap.getHeight());
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            // Update detection area
                            if (mIsDrawingCrop) {
                                mDetectCropRect.right = (int) Math.min(Math.max(event.getX(), xCenterSurfaceView), xCenterSurfaceView + mainBitmap.getWidth());
                                mDetectCropRect.bottom = (int) Math.min(Math.max(event.getY(), yCenterSurfaceView), yCenterSurfaceView + mainBitmap.getHeight());
                                drawFrame();
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            // Stop drawing/updating detection area
                            if (mIsDrawingCrop) {
                                mIsDrawingCrop = false;
                            }
                            return true;
                    }
                }
                return false;
            });
        });

        // Save the detection area coordinates
        saveBtn = this.findViewById(R.id.save);
        saveBtn.setOnClickListener(v-> saveCropValues());

        // clear the drawn detection area and the coordinates
        clearBtn = this.findViewById(R.id.clear);
        clearBtn.setOnClickListener(v-> removeCrop());

        // return to the Settings page
        backBtn = this.findViewById(R.id.back);
        backBtn.setOnClickListener(v-> goBack());


        // Starting Camera Preview
        if (mainLoop == null)
            mainLoop = new Thread() {
                @Override
                public void run() {
                    openCamera();

                }
            };
        if (mainLoop.isInterrupted() || !mainLoop.isAlive())
            mainLoop.start();

    }

    private void drawFrame() {
        if (mainBitmap != null) {
            Canvas canvas = mSurfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(mainBitmap, xCenterSurfaceView, yCenterSurfaceView, null);
                if (mDetectCropRect.right != 0)
                    canvas.drawRect(mDetectCropRect, mDetectCropPaint);
                mSurfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//
//        if (mSurfaceHolder != null) {
//            mSurfaceHolder = null;
//        }
//    }

    @Override
    protected void onResume() {
        super.onResume();
        mSurfaceHolder = mSurfaceView.getHolder();
        if (mainBitmap != null) {
            drawFrame();
        }
    }

    private void openCamera() {
        runOnUiThread(() -> {
            cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            try {
                String cameraId = cameraManager.getCameraIdList()[0]; // Use first camera
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                cameraManager.openCamera(cameraId, stateCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        });
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            CameraCharacteristics characteristics;
            try {
                characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            } catch (CameraAccessException e) {
                e.printStackTrace();
                cameraDevice.close();
                return;
            }

            StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streamConfigurationMap == null) {
                cameraDevice.close();
                return;
            }

            // Get all available output sizes for the desired output format (e.g., ImageFormat.JPEG)
            Size[] outputSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);

            startCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void startCameraPreview() {
        if (cameraDevice == null) {
            return;
        }

        try {
            final ImageReader imageReader = ImageReader.newInstance(
//                    1920, 1080,
                    FRAME_WIDTH, FRAME_HEIGHT,
                    ImageFormat.YUV_420_888, 3 // Max images in the reader's buffer
            );

            imageReader.setOnImageAvailableListener(
                    reader -> {
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            mainBitmap = convertYuvToBitmap(image);
                            if (!isCenterSurfaceView) {
                                xCenterSurfaceView = (mSurfaceView.getWidth() - mainBitmap.getWidth()) / 2;
                                yCenterSurfaceView = (mSurfaceView.getHeight() - mainBitmap.getHeight()) / 2;
                                isCenterSurfaceView = true;
                            }
                            runOnUiThread(() -> {
                                try {
                                    if (mSurfaceHolder != null) {
                                        drawFrame();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                            image.close();
                        }
                    },
                    null
            );

            cameraDevice.createCaptureSession(
                    Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                CaptureRequest.Builder captureRequestBuilder =
                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                captureRequestBuilder.addTarget(imageReader.getSurface());

                                session.setRepeatingRequest(
                                        captureRequestBuilder.build(),
                                        null,
                                        null
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            // Handle configuration failure
                        }
                    },
                    null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Convert Captured Image from ImageReader to Bitmap
    public static Bitmap convertYuvToBitmap(Image image) {
        if (image == null) {
            LogToFile.log("TAG", "Image is null");
            return null;
        }

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uvSize = uBuffer.remaining() + vBuffer.remaining();

        byte[] yuvData = new byte[ySize + uvSize];
        yBuffer.get(yuvData, 0, ySize);
        uBuffer.get(yuvData, ySize, uBuffer.remaining());
        vBuffer.get(yuvData, ySize + uBuffer.remaining(), vBuffer.remaining());

        int width = image.getWidth();
        int height = image.getHeight();
        YuvImage yuvImage = new YuvImage(yuvData, ImageFormat.NV21, width, height, null);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
        byte[] jpegData = stream.toByteArray();

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

        image.close();
        return bitmap;
    }

    // Save Detection Area coordinates in shared preferences
    public void saveCropValues() {
        if (mDetectCropRect.right == 0) {
            Toast.makeText(this,  "No Cropping data to save.", Toast.LENGTH_SHORT).show();
            return;
        }
        int cropLeft = (int) ((mDetectCropRect.left - xCenterSurfaceView) * ((float) 1080 / mainBitmap.getWidth()));
        int cropTop = (int) ((mDetectCropRect.top - yCenterSurfaceView) * ((float) 610 / mainBitmap.getHeight()));
        int cropRight = (int) ((mDetectCropRect.right - xCenterSurfaceView) * ((float) 1080 / mainBitmap.getWidth()));
        int cropBottom = (int) ((mDetectCropRect.bottom - yCenterSurfaceView) * ((float) 610 / mainBitmap.getHeight()));

        editor.putInt("DetectCropLeft", Math.min(cropLeft, cropRight));
        editor.putInt("DetectCropTop", Math.min(cropTop, cropBottom));
        editor.putInt("DetectCropRight", Math.max(cropLeft, cropRight));
        editor.putInt("DetectCropBottom", Math.max(cropTop, cropBottom));

        editor.putBoolean("crop", true);
        editor.apply();

        Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas != null && mainBitmap != null) {
            // Clear previous drawing
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(mainBitmap, xCenterSurfaceView, yCenterSurfaceView, null);
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        }

        goBack();
    }

    public void goBack() {
        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        finish();
    }

    public void removeCrop() {
        Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas != null && mainBitmap != null) {
            // Clear previous drawing
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(mainBitmap, xCenterSurfaceView, yCenterSurfaceView, null);
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        }

        mDetectCropRect = new Rect(0,0,0,0);

        editor.putBoolean("crop", false);
        editor.apply();

        Toast.makeText(this, "Cropping data has been removed successfully!", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onPause() {
        releaseCamera();
//        releaseHandlerThread();
        if (mSurfaceHolder != null) {
            mSurfaceHolder = null;
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        releaseCamera();
//        releaseHandlerThread();
        if (mSurfaceHolder != null) {
            mSurfaceHolder = null;
        }
        super.onStop();
    }

    private void releaseCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

}