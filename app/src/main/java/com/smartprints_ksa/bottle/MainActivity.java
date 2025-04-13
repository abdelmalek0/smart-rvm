package com.smartprints_ksa.bottle;

import static com.smartprints_ksa.bottle.Utils.getClassEmbeddings;
import static com.smartprints_ksa.bottle.Utils.showSnackBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.leesche.yyyiotlib.serial.manager.RvmHelper;
import com.smartprints_ksa.battery_detector.LogToFile;
import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;
import com.smartprints_ksa.bottle.rvm.ThreadManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Button activationBtn, detectionBtn, rvmBtn, settingsBtn, testBtn;
    private Boolean isAppActivated = false;
    private ProgressDialog progressDialog;

    boolean isInitSuccess = false;
    private static final String FILE_NAME = "data.json";
    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    void addEmbeddings(){
        Thread thread = new Thread(()->{

            Map<String, float[]> bottles = getClassEmbeddings(this, FILE_NAME, ObjectType.BOTTLE.name());
            Map<String, float[]> cans = getClassEmbeddings(this, FILE_NAME, ObjectType.CAN.name());
            Map<String, float[]> unknown = getClassEmbeddings(this, FILE_NAME, ObjectType.UNKNOWN.name());

            RVMDetector.clearEmbeddings();
            RVMDetector.addEmbeddings(ObjectType.BOTTLE, bottles);
            RVMDetector.addEmbeddings(ObjectType.CAN, cans);
            RVMDetector.addEmbeddings(ObjectType.UNKNOWN, unknown);
        });
        thread.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        if (!((App) getApplication()).intialized) {
            addEmbeddings();
            ((App) getApplication()).intialized = true;
        }


        // Initialize dialogs and buttons
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("RVM initialization");
        progressDialog.setMessage(getString(R.string.wait));
        activationBtn = this.findViewById(R.id.activate);
        rvmBtn = this.findViewById(R.id.init);
        detectionBtn = this.findViewById(R.id.detect);
        testBtn = this.findViewById(R.id.test);

        // Initialize shared preferences
        SharedPreferences sharedPref = getSharedPreferences("Bottle", MODE_PRIVATE);


        String activation = sharedPref.getString("activation", "");
        if(!activation.isEmpty()){
            // Activate app using the stored activation code
            isAppActivated = RVMDetector.setup(getBaseContext(), activation);

            if (isAppActivated){

                activationBtn.setEnabled(false);

                // Show the success of activation with dialog
                if(((App) this.getApplication()).tryVerificationCode) {
                    Snackbar snackbar = Snackbar.make(getWindow().getDecorView().getRootView(), R.string.valid_code, Snackbar.LENGTH_LONG);
                    View snackbarView = snackbar.getView();
                    final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
                    params.setMargins(params.leftMargin,
                            params.topMargin,
                            params.rightMargin ,
                            params.bottomMargin + 60);
                    snackbarView.setLayoutParams(params);
                    TextView tv= (TextView) snackbarView.findViewById(R.id.snackbar_text);
                    tv.setMaxLines(2);
                    snackbarView.setBackgroundColor(ContextCompat.getColor(
                            this, R.color.green));
                    snackbar.show();
                }

            } else if (((App) this.getApplication()).tryVerificationCode){
                // Show the failure of activation with a dialog
                showSnackBar(this, getString(R.string.invalid_code),ContextCompat.getColor(
                        this, R.color.red));
            }

            ((App) this.getApplication()).tryVerificationCode = false;
        }

        // Go to activation page
        activationBtn.setOnClickListener(v -> {
            startActivity(new Intent(getApplicationContext(),ActivationActivity.class));
            finish();
        });

        Button initializeButton = findViewById(R.id.initialize);
        initializeButton.setOnClickListener(v -> {
            new Thread(()->{
                copyAssetToExternalStorage(getApplicationContext(), FILE_NAME);
                addEmbeddings();
            }).start();
        });


        // Go to RealTime detection and tracking page
        detectionBtn.setOnClickListener(v->{
            if(isAppActivated) {
                startActivity(new Intent(getApplicationContext(), RealtimeTrackingActivity.class));
                finish();
            } else {
                showSnackBar(this, getString(R.string.access),ContextCompat.getColor(
                        this, R.color.red));
            }
        });

        // Go to settings page
        settingsBtn = this.findViewById(R.id.settings);
        settingsBtn.setOnClickListener(v -> {
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
            finish();
        });

        testBtn.setOnClickListener(v->{
            startActivity(new Intent(getApplicationContext(), TestActivity.class));
            finish();
        });

        // Open up RVM
        rvmBtn.setOnClickListener(v -> initRVM());
    }

    private void initRVM() {
        progressDialog.show();
        ThreadManager.getThreadPollProxy().execute(() -> {
            try {
                do {
                    isInitSuccess = RvmHelper.getInstance().initDev(MainActivity.this, 1);//0-->single entrance  1-->double entrance
                } while (!isInitSuccess);

                RvmHelper.getInstance().getDevIdAndEnableEntrance(true, true);

                SystemClock.sleep(6000);

                RvmHelper.getInstance().openOrCloseEntrance(0, true);

                RvmHelper.getInstance().openOrCloseEntrance(1, true);

                runOnUiThread(() -> {
                    rvmBtn.setEnabled(false);
                    progressDialog.dismiss();
                });

                showSnackBar(this, getString(R.string.rvm_init), ContextCompat.getColor(
                        this, R.color.green));
            } catch (Exception e){
                showSnackBar(this, getString(R.string.rvm_error), ContextCompat.getColor(
                        this, R.color.red));
            }

        });
    }

    private void copyAssetToExternalStorage(Context context, String fileName) {
        File destFolder = context.getExternalFilesDir(null);
        if (destFolder == null) {
            Toast.makeText(context, "External storage unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        File outFile = new File(destFolder, fileName);
        // Check if the file exists and delete it
        if (outFile.exists()) {
            if (outFile.delete()) {
                Log.d("AssetCopy", "Existing file deleted: " + outFile.getAbsolutePath());
            } else {
                Log.d("AssetCopy", "Failed to delete existing file: " + outFile.getAbsolutePath());
                return; // Exit if unable to delete
            }
        }


        try (InputStream in = context.getAssets().open(fileName);
             FileOutputStream out = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[4096];  // Increased buffer size for efficiency
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "File copied successfully: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show()
            );


        } catch (IOException e) {
            LogToFile.log("AssetCopy", "Error copying file: " + fileName);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Error copying file: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

}