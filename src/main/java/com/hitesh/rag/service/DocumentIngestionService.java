package com.hitesh.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitesh.rag.model.Document;
import com.hitesh.rag.model.DocumentChunk;
import com.hitesh.rag.repository.DocumentChunkRepository;
import com.hitesh.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingests documents: splits into chunks, generates embeddings, stores in DB.
 * This is the "training" phase of RAG.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int CHUNK_SIZE = 500;  // characters per chunk
    private static final int CHUNK_OVERLAP = 50; // overlap between chunks

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public Document ingestDocument(String title, String source, String category, String content) {
        log.info("Ingesting document: {} from {}", title, source);

        Document doc = Document.builder()
            .title(title)
            .source(source)
            .category(category)
            .content(content)
            .ingestedAt(LocalDateTime.now())
            .build();

        Document savedDoc = documentRepository.save(doc);
        List<String> chunks = splitIntoChunks(content);

        log.info("Document '{}' split into {} chunks. Generating embeddings...", title, chunks.size());

        List<DocumentChunk> documentChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> embedding = embeddingService.generateEmbedding(chunk);

            try {
                documentChunks.add(DocumentChunk.builder()
                    .documentId(savedDoc.getId())
                    .documentTitle(title)
                    .chunkIndex(i)
                    .content(chunk)
                    .embeddingJson(objectMapper.writeValueAsString(embedding))
                    .createdAt(LocalDateTime.now())
                    .build());
            } catch (Exception e) {
                log.error("Failed to serialize embedding for chunk {}", i);
            }
        }

        chunkRepository.saveAll(documentChunks);
        savedDoc.setChunkCount(chunks.size());
        documentRepository.save(savedDoc);

        log.info("Document '{}' ingested with {} chunks", title, chunks.size());
        return savedDoc;
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            // Try to break at sentence boundary
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('.', end);
                if (lastPeriod > start + CHUNK_SIZE / 2) end = lastPeriod + 1;
            }
            chunks.add(text.substring(start, end).trim());
            start = end - CHUNK_OVERLAP;
            if (start < 0) start = 0;
        }
        return chunks;
    }
}
