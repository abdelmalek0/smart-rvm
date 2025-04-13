package com.smartprints_ksa.bottle;

import android.app.Application;
import android.util.Log;

import com.smartprints_ksa.battery_detector.LogToFile;

public class App extends Application {
    // Check if the user just tried an activation code
    // The purpose is for UI
    // To show the window of success or failure
    protected Boolean tryVerificationCode = false;
    public boolean intialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        LogToFile.init(this);
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LogToFile.log("CRASH", Log.getStackTraceString(throwable));
        });

    }
}
