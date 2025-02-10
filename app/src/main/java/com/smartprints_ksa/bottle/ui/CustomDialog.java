package com.smartprints_ksa.bottle.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.smartprints_ksa.bottle.demo.R;

import org.w3c.dom.Text;

public class CustomDialog extends Dialog {

    public CustomDialog(Context context, String message, int color) {
        super(context);
        setContentView(R.layout.custom_dialog);

        TextView textView = this.findViewById(R.id.dialog_message);
        textView.setText(message);

        setCancelable(true);

        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.y += 400; // Modify the y position if needed
            window.setAttributes(params);
            window.setBackgroundDrawableResource(color);
            window.setDimAmount(0.0f);
            window.setElevation(0);
        }
    }


    public void showMomentarily(long duration){
        super.show();
        new Handler().postDelayed(super::dismiss, duration);
    }
}

