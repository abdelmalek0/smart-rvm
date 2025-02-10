package com.smartprints_ksa.battery_detector;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Processor {
    public static byte[] pw = "rj6PyRNPUBgOPQv".getBytes(StandardCharsets.UTF_8);
    public static byte[] em = "gOPQvN5I".getBytes(StandardCharsets.UTF_8);
    public final static int YOLO_INPUT = 640;
    public final static int CLASSIFIER_INPUT = 224;
    public final static int CHANNELS = 3;
    public final static int BYTES_SIZE = 4;
    public final static float CLASS_THRESHOLD = 0.5f;
    public static Bitmap bboxExtractor(Bitmap bitmap, RectF rect, int inputSize) {
        if (rect.bottom > rect.top && rect.right > rect.left ) {
            double x = max(0, rect.left);
            double y = max(0, rect.top);
            bitmap = Bitmap.createBitmap(bitmap,
                    (int) x, (int)  y,
                    (int) (min(bitmap.getWidth(), rect.right )-   x),
                    (int) (min(bitmap.getHeight(), rect.bottom )- y));
        }
        return Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
    }
    public static Bitmap extractAndResizeBBoxWithAspectRatio(Bitmap bitmap, RectF rect, int inputSize) {
        if (rect.bottom > rect.top && rect.right > rect.left) {
            // Ensure the rectangle coordinates are within the bounds of the bitmap
            int left = Math.max(0, (int) rect.left);
            int top = Math.max(0, (int) rect.top);
            int right = Math.min(bitmap.getWidth(), (int) rect.right);
            int bottom = Math.min(bitmap.getHeight(), (int) rect.bottom);

            // Extract the bounding box from the bitmap
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);

            // Calculate the aspect ratio of the cropped bitmap
            float originalWidth = croppedBitmap.getWidth();
            float originalHeight = croppedBitmap.getHeight();
            float aspectRatio = originalWidth / originalHeight;

            // Determine the new dimensions while maintaining the aspect ratio
            int newWidth, newHeight;
            if (aspectRatio > 1) {
                // Landscape orientation
                newWidth = inputSize;
                newHeight = (int) (newWidth / aspectRatio);
            } else {
                // Portrait orientation or square
                newHeight = inputSize;
                newWidth = (int) (newHeight * aspectRatio);
            }

            // Create a scaled bitmap with the new dimensions
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, newWidth, newHeight, true);

            // Create a new bitmap with the input size and center the scaled bitmap within it
            Bitmap resultBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(resultBitmap);
            canvas.drawColor(Color.WHITE); // Optional: fill the background with a color

            // Calculate the position to center the scaled bitmap
            int x = (inputSize - newWidth) / 2;
            int y = (inputSize - newHeight) / 2;

            // Draw the scaled bitmap onto the result bitmap
            canvas.drawBitmap(scaledBitmap, x, y, null);

            return resultBitmap;
        } else {
            // If the rectangle is invalid, return a blank bitmap or handle it as needed
            return Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888);
        }
    }
    public static byte[] convertBitmapToByteArray(Bitmap bitmap) {
        int byteCount = bitmap.getByteCount();
        ByteBuffer buffer = ByteBuffer.allocate(byteCount);
        bitmap.copyPixelsToBuffer(buffer);
        return buffer.array();
    }
}
