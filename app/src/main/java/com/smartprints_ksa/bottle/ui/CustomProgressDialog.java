package com.smartprints_ksa.bottle.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.WindowManager;
import android.widget.TextView;

import com.smartprints_ksa.bottle.demo.R;

public class CustomProgressDialog extends ProgressDialog {

    public CustomProgressDialog(Context context) {
        super(context, R.style.CustomProgressDialog);

        setCancelable(false);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.y += 400; // Move it 400 pixels down the Y-axis
        getWindow().setAttributes(params);
    }
}

