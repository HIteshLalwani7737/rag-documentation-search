package com.hitesh.rag.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "document_chunks")
public class DocumentChunk {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String documentId;
    private String documentTitle;
    private int chunkIndex;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(columnDefinition = "TEXT")
    private String embeddingJson;
    private LocalDateTime createdAt;
}
