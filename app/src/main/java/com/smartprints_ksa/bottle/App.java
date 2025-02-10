package com.smartprints_ksa.bottle;

import android.app.Application;

public class App extends Application {
    // Check if the user just tried an activation code
    // The purpose is for UI
    // To show the window of success or failure
    protected Boolean tryVerificationCode = false;
}
