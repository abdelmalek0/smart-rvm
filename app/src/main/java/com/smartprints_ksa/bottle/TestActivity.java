package com.smartprints_ksa.bottle;

import static com.smartprints_ksa.bottle.Utils.isPointInRect;
import static com.smartprints_ksa.bottle.Utils.rescaleBBox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
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
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartprints_ksa.battery_detector.LogToFile;
import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.Snapshot;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

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
    private ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();

    @SuppressLint("ClickableViewAccessibility")
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

        mImageView.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) {
                return true;
            }

            float touchX = event.getX();
            float touchY = event.getY();

            Snapshot touchedSnapshot = findTouchedSnapshot(touchX, touchY);
            if (touchedSnapshot != null) {
                showClassPickerDialog(touchedSnapshot);
            }

            return true;
        });

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

    private Snapshot findTouchedSnapshot(float x, float y) {
        return snapshots.stream()
                .filter(snapshot -> isPointInRect(snapshot.getRect(), x, y))
                .findFirst()
                .orElse(null);
    }

    private void showClassPickerDialog(Snapshot currentSnapshot) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_layout, null);
        builder.setView(dialogView);

        Spinner classDropdown = dialogView.findViewById(R.id.classDropdown);

        // Sample class names (replace with your actual class names)
        String[] classNames = new String[]{ObjectType.BOTTLE.name(), ObjectType.CAN.name(), ObjectType.UNKNOWN.name()};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                classNames);
        classDropdown.setAdapter(adapter);
        classDropdown.setSelection(Arrays.asList(classNames).indexOf(currentSnapshot.getObjectType().name()));

        builder.setPositiveButton("Save", (dialog, which) -> {
            String selectedClass = classDropdown.getSelectedItem().toString();

            if (!selectedClass.isEmpty()) {
                String embeddingId = String.valueOf(System.currentTimeMillis());
                currentSnapshot.setObjectType(ObjectType.valueOf(selectedClass));

                RVMDetector.addEmbedding(currentSnapshot.getObjectType(), embeddingId, currentSnapshot.getEmbedding());

                // Show ProgressDialog
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Saving data...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                new Thread(() -> {
                    // Save embedding to JSON file
                    saveEmbeddingToJson(getApplicationContext(), currentSnapshot.getObjectType(), embeddingId, currentSnapshot.getEmbedding());

                    // Save bitmap to internal storage
                    saveBitmapToStorage(getApplicationContext(), currentSnapshot.getObjectBitmap(), embeddingId);

                    // Hide ProgressDialog on UI thread
                    runOnUiThread(() -> {
                        progressDialog.dismiss(); // Dismiss progress dialog
                        drawBboxes(snapshots);
                    });
                }).start();

            } else {
                Toast.makeText(this, "Please select a class", Toast.LENGTH_SHORT).show();
                showClassPickerDialog(currentSnapshot); // Re-show the dialog if no selection
            }

            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }


    public static void saveEmbeddingToJson(Context context, ObjectType objectType, String embeddingId, float[] embedding) {
        try {
            // Define the file path for data.json
            File dir = context.getExternalFilesDir(null);
            if (!dir.exists()) {
                dir.mkdirs(); // Create the directory if it doesn't exist
            }

            File file = new File(dir, "data.json");

            // Create a Gson instance
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject rootObject;

            // Load existing JSON file if it exists, otherwise create a new object
            if (file.exists()) {
                FileReader fileReader = new FileReader(file);
                rootObject = JsonParser.parseReader(fileReader).getAsJsonObject();
                fileReader.close();
            } else {
                rootObject = new JsonObject();
            }

            // Get or create the "embeddings" object
            JsonObject embeddingsObject = rootObject.has("embeddings") ?
                    rootObject.getAsJsonObject("embeddings") :
                    new JsonObject();

            // Get or create the array for the specific object type
            JsonArray classEmbeddingsArray = embeddingsObject.has(objectType.name()) ?
                    embeddingsObject.getAsJsonArray(objectType.name()) :
                    new JsonArray();

            // Remove existing entry with the same embeddingId (to update it)
            for (int i = 0; i < classEmbeddingsArray.size(); i++) {
                JsonObject obj = classEmbeddingsArray.get(i).getAsJsonObject();
                if (obj.get("id").getAsString().equals(embeddingId)) {
                    classEmbeddingsArray.remove(i);
                    break;
                }
            }

            // Create a new embedding object
            JsonObject embeddingObject = new JsonObject();
            embeddingObject.addProperty("id", embeddingId);

            // Convert float array to JsonArray
            JsonArray embeddingArray = new JsonArray();
            for (float value : embedding) {
                embeddingArray.add(value);
            }
            embeddingObject.add("embedding", embeddingArray);

            // Add the new embedding
            classEmbeddingsArray.add(embeddingObject);
            embeddingsObject.add(objectType.name(), classEmbeddingsArray);
            rootObject.add("embeddings", embeddingsObject);

            // Write back to data.json
            FileWriter fileWriter = new FileWriter(file);
            gson.toJson(rootObject, fileWriter);
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
            LogToFile.log("saveEmbeddingToJson", e.getMessage());
        }
    }


    private void saveBitmapToStorage(Context context, Bitmap bitmap, String embeddingId) {
        try {
            File appDirectory = context.getExternalFilesDir(null);
            if (appDirectory == null) {
                Toast.makeText(context, "External storage unavailable", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create images subdirectory
            File imagesDirectory = new File(appDirectory, "images");
            if (!imagesDirectory.exists()) {
                imagesDirectory.mkdirs();
            }

            // Create file with embedding ID as name inside images directory
            File file = new File(imagesDirectory, embeddingId + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);

            // Compress and save the bitmap
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

            // Make the file visible in gallery
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{file.getAbsolutePath()},
                    new String[]{"image/jpeg"},
                    null
            );
        } catch (IOException e) {
            e.printStackTrace();
            LogToFile.log("saveBitmapToStorage", e.getMessage());
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
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
            LogToFile.log("uriToBitmap", e.getMessage());
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
        ArrayList<Snapshot> batteryObjects = RVMDetector.detectAll(mBitmap);

        runOnUiThread(() -> {
            clearCanvas();
            int width = mImageView.getWidth();
            int height = mImageView.getHeight();
            int total_time = 0;

            mTrackResultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mTrackResultCanvas = new Canvas(mTrackResultBitmap);


            for (Snapshot batteryObject : batteryObjects) {
                batteryObject.setRect(rescaleBBox(batteryObject.getRect(), mStartX, mStartY, mIvScaleX, mIvScaleY));

                System.out.println(batteryObject.getRect().top + " " + batteryObject.getRect().bottom + " " + batteryObject.getRect().left
                        + " " + batteryObject.getRect().right);
                LogToFile.log("Detection: ", batteryObject.getRect().top + " " + batteryObject.getRect().bottom + " " + batteryObject.getRect().left
                        + " " + batteryObject.getRect().right);
                total_time += (int) (batteryObject.getDetectionDuration() / 1_000_000.0);
            }
            snapshots = batteryObjects;
            drawBboxes(snapshots);

            timeView.setText(String.format("%d ms", total_time));
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));



            mTrackResultView.setScaleType(ImageView.ScaleType.FIT_XY);
            mTrackResultView.setImageBitmap(mTrackResultBitmap);

        });
    }
    void drawBboxes(ArrayList<Snapshot> snapshots) {
        if (mTrackResultCanvas != null) {
            mTrackBboxPaint.setXfermode(mPorterDuffXfermodeClear);
            mTrackResultCanvas.drawPaint(mTrackBboxPaint);
            mTrackBboxPaint.setXfermode(mPorterDuffXfermodeSRC);
        }
        for (Snapshot batteryObject : snapshots) {
            if (batteryObject.getObjectType() == ObjectType.UNKNOWN) {
                mTrackBboxPaint.setColor(Color.RED);
                mTrackTextPaint.setColor(Color.RED);
            } else {
                mTrackTextPaint.setColor(Color.GREEN);
                mTrackBboxPaint.setColor(Color.GREEN);
            }
            mTrackResultCanvas.drawText(batteryObject.getObjectType().name(), batteryObject.getRect().left, batteryObject.getRect().top - 10, mTrackTextPaint);
            mTrackResultCanvas.drawRect(batteryObject.getRect(), mTrackBboxPaint);
        }
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
        snapshots = new ArrayList<Snapshot>();
        if (mTrackResultCanvas != null) {
            mTrackBboxPaint.setXfermode(mPorterDuffXfermodeClear);
            mTrackResultCanvas.drawPaint(mTrackBboxPaint);
            mTrackBboxPaint.setXfermode(mPorterDuffXfermodeSRC);
        }
    }
}
