package com.hitesh.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitesh.rag.model.DocumentChunk;
import com.hitesh.rag.model.SearchRequest;
import com.hitesh.rag.model.SearchResult;
import com.hitesh.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core RAG search service.
 * Flow: Query -> Embed query -> Find similar chunks -> (Optional) LLM synthesis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:sk-dummy-key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SearchResult search(SearchRequest searchRequest) {
        long startTime = System.currentTimeMillis();
        log.info("RAG search query: '{}'", searchRequest.getQuery());

        // Step 1: Embed the query
        List<Double> queryEmbedding = embeddingService.generateEmbedding(searchRequest.getQuery());

        // Step 2: Retrieve all chunks (in production, use vector DB for ANN search)
        List<DocumentChunk> allChunks = chunkRepository.findAll();

        // Step 3: Compute similarity scores and rank
        List<SearchResult.RelevantChunk> ranked = allChunks.stream()
            .map(chunk -> {
                try {
                    List<Double> chunkEmbedding = objectMapper.readValue(
                        chunk.getEmbeddingJson(), new TypeReference<>() {}
                    );
                    double score = embeddingService.cosineSimilarity(queryEmbedding, chunkEmbedding);
                    return SearchResult.RelevantChunk.builder()
                        .documentId(chunk.getDocumentId())
                        .documentTitle(chunk.getDocumentTitle())
                        .content(chunk.getContent())
                        .similarityScore(score)
                        .chunkIndex(chunk.getChunkIndex())
                        .build();
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(c -> c != null)
            .sorted(Comparator.comparingDouble(SearchResult.RelevantChunk::getSimilarityScore).reversed())
            .limit(searchRequest.getTopK())
            .collect(Collectors.toList());

        // Step 4: Optionally generate synthesized answer using LLM
        String synthesizedAnswer = null;
        if (searchRequest.isGenerateAnswer() && !ranked.isEmpty()) {
            synthesizedAnswer = generateAnswer(searchRequest.getQuery(), ranked);
        }

        return SearchResult.builder()
            .query(searchRequest.getQuery())
            .relevantChunks(ranked)
            .synthesizedAnswer(synthesizedAnswer)
            .retrievalTimeMs(System.currentTimeMillis() - startTime)
            .build();
    }

    private String generateAnswer(String query, List<SearchResult.RelevantChunk> chunks) {
        String context = chunks.stream()
            .map(c -> "Source: " + c.getDocumentTitle() + "\n" + c.getContent())
            .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = String.format("Context:\n%s\n\nQuestion: %s\nAnswer based only on the context:", context, query);

        try {
            String body = String.format("""
                {"model": "gpt-4o-mini", "messages": [
                    {"role": "system", "content": "Answer questions using only the provided documentation context. Be concise and cite sources."},
                    {"role": "user", "content": "%s"}
                ], "max_tokens": 500}
                """, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                int start = response.body().indexOf("\"content\":\"") + 11;
                int end = response.body().indexOf("\"", start);
                return response.body().substring(start, end).replace("\\n", "\n");
            }
        } catch (Exception e) {
            log.warn("LLM synthesis failed: {}", e.getMessage());
        }
        return "Based on retrieved documents: " + chunks.get(0).getContent();
    }
}
