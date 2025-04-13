package com.smartprints_ksa.bottle;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Environment;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    // Show success/failure/info messages as snack bars
    public static void showSnackBar(Activity activity, String message, int color){
        Snackbar snackbar = Snackbar.make(activity.getWindow().getDecorView().getRootView(), message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
        params.setMargins(params.leftMargin,
                params.topMargin,
                params.rightMargin ,
                params.bottomMargin + 60);
        snackbarView.setLayoutParams(params);
        TextView tv= (TextView) snackbarView.findViewById(R.id.snackbar_text);
        tv.setMaxLines(2);
        snackbarView.setBackgroundColor(color);
        snackbar.show();
    }

    // Rescale the bounding box to the size of the View in the screen
    public static Rect rescaleBBox(Rect bbox, float mStartX, float mStartY, float mIvScaleX, float mIvScaleY){
        Rect rescaledBBox = new Rect();
        rescaledBBox.top = (int) (mStartY + mIvScaleY*bbox.top);
        rescaledBBox.left = (int) (mStartX + mIvScaleX*bbox.left);
        rescaledBBox.bottom = (int) (mStartY + mIvScaleY*bbox.bottom);
        rescaledBBox.right = (int) (mStartX + mIvScaleX*bbox.right);
        return rescaledBBox;
    }

    // Choose the color of the bounding box depending on the type of object
    public static int getColor(ObjectType type){
        switch (type) {
            case BOTTLE:
                return Color.GREEN;
            case CAN:
                return Color.BLUE;
            case NOT_VALID:
                return Color.RED;
            default:
                return Color.BLACK;
        }

    }

    public static Map<String, float[]> getClassEmbeddings(Context context, String fileName, String className) {
        Map<String, float[]> embeddings = new HashMap<>();
        try {
            // Construct the file path
            File file = new File(context.getExternalFilesDir(null), fileName);

            // Check if the file exists
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
            }

            // Open file input stream
            FileInputStream fileInputStream = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(fileInputStream);

            // Parse the JSON file using Gson
            JsonObject rootObject = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject embeddingsObject = rootObject.getAsJsonObject("embeddings");
            JsonArray classEmbeddingsArray = embeddingsObject.getAsJsonArray(className);

            // Iterate over each embedding object
            for (JsonElement element : classEmbeddingsArray) {
                JsonObject embeddingObject = element.getAsJsonObject();
                String id = embeddingObject.get("id").getAsString();
                JsonArray embeddingArray = embeddingObject.getAsJsonArray("embedding");

                // Convert JsonArray to float array
                float[] embedding = new float[embeddingArray.size()];
                for (int j = 0; j < embeddingArray.size(); j++) {
                    embedding[j] = embeddingArray.get(j).getAsFloat();
                }

                embeddings.put(id, embedding);
            }

            // Close the reader and stream
            reader.close();
            fileInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return embeddings;
    }

    public static boolean isPointInRect(Rect rect, float x, float y) {
        return x > rect.left && x < rect.right &&
                y > rect.top && y < rect.bottom;
    }
}
