package com.smartprints_ksa.battery_detector;
import com.smartprints_ksa.battery_detector.data_structure.enums.ObjectType;
import java.util.*;

public class Embeddings {
    public static final int embeddingSize = 128;

    // Changed to store ID and embedding pairs for each ObjectType
    public static HashMap<ObjectType, Map<String, float[]>> embeddings = new HashMap<>();

    // Modified to accept ID for each embedding
    public static void addEmbedding(ObjectType type, String id, float[] embedding) {
        embeddings.computeIfAbsent(type, k -> new HashMap<>())
                .put(id, embedding);
    }

    // Add multiple embeddings with IDs
    public static void addEmbeddings(ObjectType type, Map<String, float[]> typeEmbeddings) {
        embeddings.computeIfAbsent(type, k -> new HashMap<>())
                .putAll(typeEmbeddings);
    }

    public static void removeEmbedding(ObjectType type, String id) {
        if (embeddings.containsKey(type)) {
            embeddings.get(type).remove(id);
            if (embeddings.get(type).isEmpty()) {
                embeddings.remove(type);
            }
        }
    }

    public static void removeType(ObjectType type) {
        embeddings.remove(type);
    }

    public static void clearEmbeddings() {
        embeddings.clear();
    }

    public static boolean isEmpty() {
        return embeddings.isEmpty();
    }

    public static List<Map.Entry<AbstractMap.SimpleEntry<ObjectType, String>, Double>> semanticSearch(float[] query) {
        if (query == null || query.length == 0) {
            throw new IllegalArgumentException("Query vector cannot be null or empty");
        }

        // Use a reverse order comparator to maintain top K closest matches (smallest distances)
        PriorityQueue<Map.Entry<AbstractMap.SimpleEntry<ObjectType, String>, Double>> pq =
                new PriorityQueue<>(
                        (e1, e2) -> Double.compare(e2.getValue(), e1.getValue())  // Reversed comparison
                );

        // Define max results constant
        final int MAX_RESULTS = 20;

        for (Map.Entry<ObjectType, Map<String, float[]>> typeEntry : embeddings.entrySet()) {
            ObjectType type = typeEntry.getKey();
            Map<String, float[]> typeEmbeddings = typeEntry.getValue();

            for (Map.Entry<String, float[]> embeddingEntry : typeEmbeddings.entrySet()) {
                String id = embeddingEntry.getKey();
                float[] embedding = embeddingEntry.getValue();

                // Add null check for embedding
                if (embedding == null) {
                    continue;
                }

                // Validate vector dimensions match
                if (embedding.length != query.length) {
                    throw new IllegalArgumentException(
                            String.format("Embedding dimension mismatch: expected %d, got %d for ID %s",
                                    query.length, embedding.length, id)
                    );
                }

                double distance = cosineDistance(embedding, query);
                AbstractMap.SimpleEntry<ObjectType, String> key =
                        new AbstractMap.SimpleEntry<>(type, id);
                Map.Entry<AbstractMap.SimpleEntry<ObjectType, String>, Double> entry =
                        new AbstractMap.SimpleEntry<>(key, distance);

                if (pq.size() < MAX_RESULTS) {
                    pq.offer(entry);
                } else if (distance < pq.peek().getValue()) {
                    pq.poll();
                    pq.offer(entry);
                }
            }
        }

        // Convert priority queue to sorted list (ascending order of distance)
        ArrayList<Map.Entry<AbstractMap.SimpleEntry<ObjectType, String>, Double>> result =
                new ArrayList<>(pq.size());
        while (!pq.isEmpty()) {
            result.add(0, pq.poll());  // Insert at beginning to maintain ascending order
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

    private static double cosineDistance(float[] v1, float[] v2) {
        double dotProduct = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
        }

        return 1.0 - dotProduct; // Cosine distance = 1 - cosine similarity
    }

}