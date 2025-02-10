// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package com.smartprints_ksa.battery_detector;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

public class BaseModuleActivity extends AppCompatActivity {
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Handler mUIHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUIHandler = new Handler(getMainLooper());
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startBackgroundThread();
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("ModuleActivity");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onDestroy() {
        stopBackgroundThread();
        super.onDestroy();
    }

    public void stopBackgroundThread() {
      mBackgroundThread.quitSafely();
      try {
          mBackgroundThread.join();
          mBackgroundThread = null;
          mBackgroundHandler = null;
      } catch (InterruptedException e) {
          Log.e("Object Detection", "Error on stopping background thread", e);
      }
    }
}
