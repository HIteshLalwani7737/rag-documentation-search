package com.hitesh.rag.repository;
import com.hitesh.rag.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByCategory(String category);
}
