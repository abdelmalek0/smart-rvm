package com.smartprints_ksa.battery_detector;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogToFile {
    private static File logFile;
    private static final String LOG_TAG = "LogToFile";
    private static final String LOG_FILE_NAME = "app_logs.txt";

    // Initialize once in Application class
    public static void init(Application application) {
        logFile = new File(application.getExternalFilesDir(null), LOG_FILE_NAME);
    }

    // Log method without needing Context
    public static void log(String tag, String message) {
        if (logFile == null) {
            Log.e(LOG_TAG, "Log file is not initialized. Call LogToFile.init() in Application class.");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timestamp + " [" + tag + "] " + message + "\n";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(logMessage);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error writing log to file", e);
        }
    }
}
