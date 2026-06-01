package com.hitesh.rag.controller;

import com.hitesh.rag.model.Document;
import com.hitesh.rag.model.SearchRequest;
import com.hitesh.rag.model.SearchResult;
import com.hitesh.rag.service.DocumentIngestionService;
import com.hitesh.rag.service.RagSearchService;
import com.hitesh.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/docs")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final RagSearchService searchService;
    private final DocumentRepository documentRepository;

    /**
     * Ingest a document by uploading text content
     */
    @PostMapping("/ingest")
    public ResponseEntity<Document> ingestDocument(
        @RequestParam String title,
        @RequestParam String category,
        @RequestBody String content) {
        return ResponseEntity.ok(ingestionService.ingestDocument(title, "api-upload", category, content));
    }

    /**
     * Ingest a document from uploaded file
     */
    @PostMapping("/ingest/file")
    public ResponseEntity<Document> ingestFile(
        @RequestParam String title,
        @RequestParam String category,
        @RequestParam MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(ingestionService.ingestDocument(title, file.getOriginalFilename(), category, content));
    }

    /**
     * Search documents using RAG
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResult> search(@RequestBody SearchRequest searchRequest) {
        return ResponseEntity.ok(searchService.search(searchRequest));
    }

    /**
     * Shorthand search endpoint
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResult> quickSearch(
        @RequestParam String q,
        @RequestParam(defaultValue = "5") int topK,
        @RequestParam(defaultValue = "false") boolean generateAnswer) {
        SearchRequest req = new SearchRequest();
        req.setQuery(q);
        req.setTopK(topK);
        req.setGenerateAnswer(generateAnswer);
        return ResponseEntity.ok(searchService.search(req));
    }

    @GetMapping
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentRepository.findAll());
    }
}
