package com.smartprints_ksa.bottle;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.NumberPicker;

import com.smartprints_ksa.battery_detector.RVMDetector;
import com.smartprints_ksa.bottle.demo.R;
public class SettingsActivity extends AppCompatActivity {
    private Button areasBtn, saveBtn;
    private NumberPicker nbFramesUntilConfirmation;
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize Shared Preferences
        sharedPref = getSharedPreferences("Bottle", MODE_PRIVATE);
        editor = sharedPref.edit();

        // Initialize Number Picker
        nbFramesUntilConfirmation = this.findViewById(R.id.sessionConfirmation);
        nbFramesUntilConfirmation.setMinValue(1);
        nbFramesUntilConfirmation.setMaxValue(600);
        nbFramesUntilConfirmation.setValue(sharedPref.getInt("nbFramesUntilConfirmation", RVMDetector.getNbFramesUntilConfirmation()));

        // Save Settings to shared preferences
        saveBtn = this.findViewById(R.id.save);
        saveBtn.setOnClickListener(v -> {
            editor.putInt("nbFramesUntilConfirmation", nbFramesUntilConfirmation.getValue());
            editor.apply();
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            finish();
        });

        // Go to Detection area selection page
        areasBtn = this.findViewById(R.id.selectAreas);
        areasBtn.setOnClickListener(v -> {
            startActivity(new Intent(getApplicationContext(), CropActivity.class));
            finish();
        });
    }
}