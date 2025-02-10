package com.smartprints_ksa.bottle;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import com.smartprints_ksa.bottle.demo.R;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

    public static List<float[]> getClassEmbeddings(Context context, String fileName, String className) {
        List<float[]> embeddings = new ArrayList<>();

        try {
            // Open the file from the assets folder
            InputStream inputStream = context.getAssets().open(fileName);
            InputStreamReader reader = new InputStreamReader(inputStream);

            // Parse the JSON file using Gson
            JsonObject rootObject = JsonParser.parseReader(reader).getAsJsonObject();

            // Navigate to the embeddings object and the specific class
            JsonObject embeddingsObject = rootObject.getAsJsonObject("embeddings");
            JsonArray classEmbeddingsArray = embeddingsObject.getAsJsonArray(className);

            // Iterate over each embedding array
            for (int i = 0; i < classEmbeddingsArray.size(); i++) {
                JsonArray embeddingArray = classEmbeddingsArray.get(i).getAsJsonArray();
                float[] embedding = new float[embeddingArray.size()];

                for (int j = 0; j < embeddingArray.size(); j++) {
                    embedding[j] = embeddingArray.get(j).getAsFloat();
                }
                embeddings.add(embedding);
            }

            // Close the reader and stream
            reader.close();
            inputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return embeddings;
    }

}
