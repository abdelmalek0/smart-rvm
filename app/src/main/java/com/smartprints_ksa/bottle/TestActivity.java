package com.smartprints_ksa.bottle;

import static com.smartprints_ksa.bottle.Utils.rescaleBBox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.Snapshot;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, Runnable {

    private String[] permissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
    };
    private Boolean permitted = false;
    private Uri image_uri;
    private ImageView mImageView;
    private TextView timeView;
    private Button galleryBtn, cameraBtn, backBtn;
    private Button mButtonDetect;
    private Bitmap mBitmap = null;
    private float mIvScaleX, mIvScaleY, mStartX, mStartY;
    private Bitmap mTrackResultBitmap = null;
    private Canvas mTrackResultCanvas = null;
    private Paint mTrackBboxPaint = null;
    private Paint mTrackTextPaint = null;
    private PorterDuffXfermode mPorterDuffXfermodeClear;
    private PorterDuffXfermode mPorterDuffXfermodeSRC;
    private ImageView mTrackResultView;
    private Thread inferenceThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        mTrackResultView = this.findViewById(R.id.canvasView);
        checkPermissions();

        mTrackBboxPaint = new Paint();
        mTrackBboxPaint.setColor(Color.GREEN);
        mTrackBboxPaint.setStrokeWidth(5);
        mTrackBboxPaint.setStyle(Paint.Style.STROKE);

        mTrackTextPaint = new Paint();
        mTrackTextPaint.setTextSize(48);
        mTrackTextPaint.setColor(Color.GREEN);
        mTrackTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mPorterDuffXfermodeClear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
        mPorterDuffXfermodeSRC = new PorterDuffXfermode(PorterDuff.Mode.SRC);


        mImageView = this.findViewById(R.id.imageView);
        backBtn = this.findViewById(R.id.back);
        galleryBtn = this.findViewById(R.id.button);
        cameraBtn = this.findViewById(R.id.button2);
        timeView = this.findViewById(R.id.time);

        backBtn.setOnClickListener(v -> {
            Intent i = new Intent(getApplicationContext(),MainActivity.class);
            startActivity(i);
            finish();
        });

        galleryBtn.setOnClickListener(v -> {
            clearCanvas();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            Intent.createChooser(intent, "Select Picture");
            galleryActivityResultLauncher.launch(intent);

        });

        cameraBtn.setOnClickListener(view -> {
            clearCanvas();
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED) {
                String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
                requestPermissions(permission, 42);
            } else {
                openCamera();
            }
        });

        mButtonDetect = this.findViewById(R.id.detectButton);
        mButtonDetect.setOnClickListener(v -> {
            if (mBitmap == null) {
                Toast.makeText(getApplicationContext(), "The picture must be selected!", Toast.LENGTH_LONG).show();
            } else {
                
                mButtonDetect.setEnabled(false);
                mButtonDetect.setText(getString(R.string.run_model));

                int mBitmapWidth = mBitmap.getWidth();
                int mBitmapHeight = mBitmap.getHeight();
                int mImageViewWidth = mImageView.getWidth();
                int mImageViewHeight = mImageView.getHeight();

                mIvScaleX = (mBitmapWidth > mBitmapHeight ?
                        (float) mImageViewWidth / mBitmapWidth :
                        (float) mImageViewHeight / mBitmapHeight);
                mIvScaleY = (mBitmapHeight > mBitmapWidth ?
                        (float) mImageViewHeight / mBitmapHeight :
                        (float) mImageViewWidth / mBitmapWidth);

                mStartX = (mImageViewWidth - mIvScaleX * mBitmapWidth) / 2;
                mStartY = (mImageViewHeight - mIvScaleY * mBitmapHeight) / 2;

                inferenceThread= new Thread(TestActivity.this);
                inferenceThread.start();
            }
        });
    }
    private void checkPermissions() {
        if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(permissions[1]) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(permissions[2]) != PackageManager.PERMISSION_GRANTED) {
            startRequestPermission();
        } else {
            permitted = true;
        }
    }
    private void startRequestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 321);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("yolo", "onRequestPermissionsResult:" + requestCode);
        if (requestCode == 321) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast toast = Toast.makeText(this, "Please turn on the permission " +
                                "from the setting interface",
                        Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                finish();
            } else {
                permitted = true;
            }
        }
    }
    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        cameraActivityResultLauncher.launch(cameraIntent);

    }
    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input) {
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
        }
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(orientation);
        return Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), rotationMatrix, true);
    }

    @Override
    public void run() {
        if (!permitted) return;
        ArrayList<Snapshot>  batteryObjects = RVMDetector.detectAll(mBitmap);

        runOnUiThread(() -> {
            clearCanvas();
            int width = mImageView.getWidth();
            int height = mImageView.getHeight();
            int total_time = 0;

            mTrackResultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mTrackResultCanvas = new Canvas(mTrackResultBitmap);


            for (Snapshot batteryObject : batteryObjects) {
                Rect detection = rescaleBBox(batteryObject.getRect(), mStartX, mStartY, mIvScaleX, mIvScaleY);

                System.out.println(detection.top + " " + detection.bottom + " " + detection.left
                        + " " + detection.right);
                if (batteryObject.getObjectType() == ObjectType.UNKNOWN){
                    mTrackBboxPaint.setColor(Color.RED);
                    mTrackTextPaint.setColor(Color.RED);
                } else {
                    mTrackTextPaint.setColor(Color.GREEN);
                    mTrackBboxPaint.setColor(Color.GREEN);
                }
                mTrackResultCanvas.drawText(batteryObject.getObjectType().name(), detection.left, detection.top - 10, mTrackTextPaint);
                mTrackResultCanvas.drawRect(detection, mTrackBboxPaint);
                total_time += (int) (batteryObject.getDetectionDuration() / 1_000_000.0);

            }

            timeView.setText(String.format("%d ms", total_time));
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));



            mTrackResultView.setScaleType(ImageView.ScaleType.FIT_XY);
            mTrackResultView.setImageBitmap(mTrackResultBitmap);

        });
    }

    ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {

                        assert result.getData() != null;
                        Uri image_uri = result.getData().getData();
                        mBitmap = uriToBitmap(image_uri);
                        mImageView.setImageURI(image_uri);
                    }
                }
            });

    ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {


                        mBitmap = uriToBitmap(image_uri);
                        mBitmap = rotateBitmap(mBitmap);
                        mImageView.setImageBitmap(mBitmap);

                    }
                }
            });

    private void recycleBitmaps() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        if (mTrackResultBitmap != null) {
            mTrackResultBitmap.recycle();
            mTrackResultBitmap = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        inferenceThread.interrupt();
        recycleBitmaps();
    }

    private void clearCanvas() {
        timeView.setText("");
//        recycleBitmaps();
        if (mTrackResultCanvas != null) {
            mTrackBboxPaint.setXfermode(mPorterDuffXfermodeClear);
            mTrackResultCanvas.drawPaint(mTrackBboxPaint);
            mTrackBboxPaint.setXfermode(mPorterDuffXfermodeSRC);
        }
    }
}
