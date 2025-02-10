package com.smartprints_ksa.battery_detector;

import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

public class Embeddings {
    public static final int embeddingSize = 256;
    public static HashMap<ObjectType, List<float[]>> embeddings = new HashMap<>();

    public static void addEmbedding(ObjectType type, List<float[]> embedding){
        embeddings.put(type, embedding);
    }

    public static void removeEmbedding(ObjectType type){
        embeddings.remove(type);
    }

    public static void clearEmbeddings(){
        embeddings.clear();
    }

    public static boolean isEmpty(){
        return embeddings.isEmpty();
    }

    public static List<Map.Entry<ObjectType, Double>> semanticSearch(float[] query) {
        if (query == null || query.length == 0) {
            throw new IllegalArgumentException("Query vector cannot be null or empty");
        }

        // Priority queue to store the top K closest embeddings (max-heap based on distance)
        // Using max-heap so we can remove the largest distances when queue exceeds topK
        PriorityQueue<Map.Entry<ObjectType, Double>> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(b.getValue(), a.getValue())
        );

        for (ObjectType type : embeddings.keySet()) {
            List<float[]> typeEmbeddings = embeddings.get(type);
            if (typeEmbeddings == null) continue;

            for (float[] embedding : typeEmbeddings) {
                if (embedding == null || embedding.length != query.length) {
                    continue;
                }

                double distance = l2Distance(embedding, query);
                Map.Entry<ObjectType, Double> entry = new AbstractMap.SimpleEntry<>(type, distance);

                if (pq.size() < 20) {
                    pq.offer(entry);
                } else if (distance < pq.peek().getValue()) {
                    pq.poll();
                    pq.offer(entry);
                }
            }
        }

        // Convert priority queue to a sorted list (ascending order of distance)
        List<Map.Entry<ObjectType, Double>> result = new ArrayList<>(pq.size());
        while (!pq.isEmpty()) {
            result.add(0, pq.poll()); // Add to front to maintain ascending order
        }

        return result;
    }

    private static double l2Distance(float[] v1, float[] v2) {
        double sum = 0.0;
        for (int i = 0; i < v1.length; i++) {
            double diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
