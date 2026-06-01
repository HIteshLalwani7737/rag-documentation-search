package com.hitesh.rag.model;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SearchResult {
    private String query;
    private List<RelevantChunk> relevantChunks;
    private String synthesizedAnswer;
    private long retrievalTimeMs;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RelevantChunk {
        private String documentId;
        private String documentTitle;
        private String content;
        private double similarityScore;
        private int chunkIndex;
    }
}
