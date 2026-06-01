package com.hitesh.rag.model;
import lombok.Data;
@Data
public class SearchRequest {
    private String query;
    private int topK = 5;
    private String category;
    private boolean generateAnswer;
}
