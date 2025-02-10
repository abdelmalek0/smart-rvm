package com.smartprints_ksa.bottle;

import static com.smartprints_ksa.bottle.Utils.getClassEmbeddings;
import static com.smartprints_ksa.bottle.Utils.showSnackBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.leesche.yyyiotlib.serial.manager.RvmHelper;
import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;
import com.smartprints_ksa.bottle.rvm.ThreadManager;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button activationBtn, detectionBtn, rvmBtn, settingsBtn, testBtn;
    private Boolean isAppActivated = false;
    private ProgressDialog progressDialog;

    boolean isInitSuccess = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Thread thread = new Thread(()->{
            List<float[]> bottles = getClassEmbeddings(this, "data.json", ObjectType.BOTTLE.name());
            List<float[]> cans = getClassEmbeddings(this, "data.json", ObjectType.CAN.name());
            List<float[]> unknown = getClassEmbeddings(this, "data.json", ObjectType.UNKNOWN.name());

            //
            RVMDetector.addEmbeddings(ObjectType.BOTTLE, bottles);
            RVMDetector.addEmbeddings(ObjectType.CAN, cans);
            RVMDetector.addEmbeddings(ObjectType.UNKNOWN, unknown);
        });
        thread.start();


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
        if(!activation.equals("")){
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


}