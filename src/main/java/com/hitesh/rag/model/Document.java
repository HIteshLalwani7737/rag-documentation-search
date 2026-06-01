package com.hitesh.rag.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "documents")
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String title;
    private String source;
    private String category;
    @Column(columnDefinition = "TEXT")
    private String content;
    private LocalDateTime ingestedAt;
    private int chunkCount;
}
