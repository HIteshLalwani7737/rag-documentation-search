package com.hitesh.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to generate text embeddings using OpenAI's text-embedding-ada-002.
 * Embeddings are 1536-dimensional float vectors used for semantic similarity search.
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${openai.api.key:sk-dummy-key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Generate embedding vector for a text using OpenAI API.
     * @return List of floats representing the embedding vector
     */
    public List<Double> generateEmbedding(String text) {
        try {
            String requestBody = String.format("""
                {"model": "text-embedding-ada-002", "input": "%s"}
                """, escapeJson(text));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseEmbeddingFromResponse(response.body());
            } else {
                log.warn("OpenAI embedding API error {}. Using mock embedding.", response.statusCode());
                return generateMockEmbedding(text);
            }
        } catch (Exception e) {
            log.warn("Embedding generation failed: {}. Using mock embedding.", e.getMessage());
            return generateMockEmbedding(text);
        }
    }

    /**
     * Compute cosine similarity between two embedding vectors.
     * Range: -1 to 1 (1 = identical, 0 = orthogonal, -1 = opposite)
     */
    public double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA.size() != vecB.size()) return 0.0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < vecA.size(); i++) {
            dotProduct += vecA.get(i) * vecB.get(i);
            normA += vecA.get(i) * vecA.get(i);
            normB += vecB.get(i) * vecB.get(i);
        }
        return normA == 0 || normB == 0 ? 0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Double> generateMockEmbedding(String text) {
        List<Double> embedding = new ArrayList<>();
        int dim = 1536;
        for (int i = 0; i < dim; i++) {
            embedding.add(Math.sin(i + text.hashCode() * 0.001));
        }
        return embedding;
    }

    private List<Double> parseEmbeddingFromResponse(String body) {
        // Extract embedding array from OpenAI response
        int dataStart = body.indexOf("\"embedding\":[") + 13;
        int dataEnd = body.indexOf("]", dataStart);
        String[] values = body.substring(dataStart, dataEnd).split(",");
        List<Double> embedding = new ArrayList<>();
        for (String v : values) embedding.add(Double.parseDouble(v.trim()));
        return embedding;
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", "");
    }
}
