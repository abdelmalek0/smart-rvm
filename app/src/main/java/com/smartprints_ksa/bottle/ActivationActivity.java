package com.smartprints_ksa.bottle;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.bottle.demo.R;

public class ActivationActivity extends AppCompatActivity {

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        // retrieve and show the request code
        String str = RVMDetector.getRequestCode(getBaseContext());
        ((EditText)findViewById(R.id.reqCode)).setText(str);
        Log.d("request",str);


        // Initialize the shared preferences to store the activation code
        SharedPreferences sharedPref = getSharedPreferences("Bottle", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        // Initialize UI variables
        EditText activationCode = findViewById(R.id.valCode);
        Button validation = findViewById(R.id.validation);


        /*
         * Validates the activation code
         * if it's true, return to the main screen

         */
        validation.setOnClickListener(v -> {
            editor.putString("activation", activationCode.getText().toString());
            editor.apply();
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            finish();
        });
    }
}