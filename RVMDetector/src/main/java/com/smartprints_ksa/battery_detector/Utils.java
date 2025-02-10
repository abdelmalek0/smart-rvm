package com.smartprints_ksa.battery_detector;

import android.graphics.PointF;
import android.graphics.Rect;

public class Utils {

    public static boolean isCenterInsideRect(PointF point, Rect outerRect) {
        int outerLeft = outerRect.left;
        int outerTop = outerRect.top;
        int outerRight = outerRect.right;
        int outerBottom = outerRect.bottom;

        return outerLeft <= point.x && point.x <= outerRight
                && outerTop <= point.y && point.y <= outerBottom;
    }

    public static double calculateIntersectionRatio(Rect rect1, Rect rect2) {

        int maxLeft = Math.max(rect1.left, rect2.left);
        int maxTop = Math.max(rect1.top, rect2.top);
        int minRight = Math.min(rect1.right, rect2.right);
        int minBottom = Math.min(rect1.bottom, rect2.bottom);

        int intersectionWidth = Math.max(0, minRight - maxLeft);
        int intersectionHeight = Math.max(0, minBottom - maxTop);

        double intersectionArea = intersectionWidth * intersectionHeight;
        double rect1Area = (rect1.right - rect1.left) * (rect1.bottom - rect1.top);

        double intersectionRatio = intersectionArea / rect1Area;

        return intersectionRatio;
    }

    public static int argmax(float[] arr) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array is empty or null");
        }

        int maxIndex = 0;
        float maxValue = arr[0];

        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > maxValue) {
                maxValue = arr[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    public static float[] l2Normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Input vector must not be null or empty");
        }

        // Calculate the L2 norm (Euclidean norm) of the vector
        float sumOfSquares = 0.0f;
        for (float value : vector) {
            sumOfSquares += value * value;
        }

        float norm = (float) Math.sqrt(sumOfSquares);

        // Check if the norm is zero to avoid division by zero
        if (norm == 0.0f) {
            throw new ArithmeticException("Cannot normalize a zero vector");
        }

        // Normalize the vector
        float[] normalizedVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalizedVector[i] = vector[i] / norm;
        }

        return normalizedVector;
    }
}
