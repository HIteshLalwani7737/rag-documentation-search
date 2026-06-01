# RAG Documentation Search

A **Retrieval Augmented Generation (RAG)** based documentation search engine built with **Spring Boot** and **OpenAI**. Ingest any text documents, generate semantic embeddings, and search with natural language queries — getting back the most relevant documentation chunks, optionally synthesized into a direct answer by GPT.

---

## What is RAG?

RAG combines vector-based semantic retrieval with LLM generation:

```
                  INGESTION PHASE (one-time)
                  ─────────────────────────
Document Text ──► Chunker ──► OpenAI Embeddings API ──► Vector Store (DB)


                  SEARCH PHASE (runtime)
                  ──────────────────────
User Query ──► Embed Query ──► Cosine Similarity Search ──► Top-K Chunks
                                                                  │
                                                     (Optional) GPT-4o-mini
                                                                  │
                                                         Synthesized Answer
```

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   REST API Layer                      │
│  POST /docs/ingest  │  POST /docs/search             │
└──────────┬──────────┴──────────┬────────────────────┘
           │                     │
           ▼                     ▼
  DocumentIngestionService   RagSearchService
           │                     │
           ▼                     ▼
  EmbeddingService          EmbeddingService
  (text-embedding-ada-002)  (embed user query)
           │                     │
           ▼                     ▼
  H2/PostgreSQL DB ◄────► Cosine Similarity Ranking
  (chunks + embeddings)          │
                                 ▼
                          Top-K Relevant Chunks
                                 │
                         (if generateAnswer=true)
                                 ▼
                          GPT-4o-mini Synthesis
```

---

## Key Features

### ✅ Document Ingestion (Training Phase)
- Upload text documents via REST API or file upload
- Documents auto-split into **500-character chunks with 50-char overlap** (prevents context loss at boundaries)
- Each chunk embedded via **OpenAI text-embedding-ada-002** (1536-dimensional vector)
- Embeddings stored in DB as JSON (swap with pgvector in production for O(1) ANN search)

### ✅ Semantic Search
- User query is embedded using the same model
- **Cosine similarity** computed between query vector and all stored chunk vectors
- Top-K most similar chunks returned ranked by score (0.0 to 1.0)
- Optional category filter to narrow search scope

### ✅ Answer Synthesis (Full RAG)
- When `generateAnswer: true`, retrieved chunks are fed to GPT-4o-mini as context
- LLM generates a direct, cited answer — not just document snippets
- Avoids hallucination: model instructed to answer only from provided context

### ✅ Mock Embeddings Fallback
- Works without OpenAI API key (uses deterministic mock embeddings)
- Perfect for development/testing without API costs

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2 |
| Embeddings | OpenAI text-embedding-ada-002 |
| LLM | OpenAI gpt-4o-mini |
| Vector Store | H2 (dev) / pgvector PostgreSQL (prod) |
| Text Parsing | Apache Tika |
| Build | Maven, Java 17 |

---

## API Reference

### POST `/api/v1/docs/ingest` — Ingest Document
```bash
curl -X POST "http://localhost:8082/api/v1/docs/ingest?title=API+Guide&category=API" \
  -H "Content-Type: text/plain" \
  -d "This guide explains how to use the Payment API. 
      To initiate a payment, POST to /api/v1/payments with orderId, amount, and currency..."
```

### POST `/api/v1/docs/ingest/file` — Ingest from File
```bash
curl -X POST "http://localhost:8082/api/v1/docs/ingest/file?title=Architecture+Doc&category=Architecture" \
  -F "file=@architecture.txt"
```

### POST `/api/v1/docs/search` — Semantic Search
```json
{
  "query": "How do I handle payment failures?",
  "topK": 5,
  "category": "API",
  "generateAnswer": true
}
```

**Response:**
```json
{
  "query": "How do I handle payment failures?",
  "relevantChunks": [
    {
      "documentTitle": "Payment API Guide",
      "content": "When a payment fails, the status field will be FAILED...",
      "similarityScore": 0.89,
      "chunkIndex": 3
    }
  ],
  "synthesizedAnswer": "Based on the Payment API Guide: When a payment fails, check the 'status' field for FAILED and read 'failureReason'...",
  "retrievalTimeMs": 245
}
```

### GET `/api/v1/docs/search?q=query&topK=5&generateAnswer=true` — Quick Search

---

## Setup

### 1. Configure OpenAI
```bash
export OPENAI_API_KEY=sk-your-key-here
```

### 2. Run
```bash
mvn spring-boot:run
```

### 3. Ingest your docs
```bash
# Ingest a documentation file
curl -X POST "http://localhost:8082/api/v1/docs/ingest?title=My+Docs&category=General" \
  -H "Content-Type: text/plain" \
  --data-binary @your-documentation.txt
```

### 4. Search
```bash
curl -X POST http://localhost:8082/api/v1/docs/search \
  -H "Content-Type: application/json" \
  -d '{"query": "your question here", "topK": 3, "generateAnswer": true}'
```

---

## Production Considerations

### Replace H2 with pgvector for Scale
```sql
-- Install pgvector extension in PostgreSQL
CREATE EXTENSION vector;

-- Store embeddings as native vectors
ALTER TABLE document_chunks ADD COLUMN embedding vector(1536);

-- Use cosine similarity index for fast ANN search
CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops);
```

### Chunking Strategy
Current implementation uses fixed-size chunking. For better results consider:
- **Semantic chunking**: split at paragraph/section boundaries
- **Recursive chunking**: split by `\n\n`, then `\n`, then sentences
- **Overlapping windows**: larger overlap for complex technical documents

---


1. **Why chunk documents?** Embedding models have token limits (~8K for ada-002). Chunking also improves retrieval precision — a 500-char chunk is more semantically focused than a 10-page document.

2. **Why cosine similarity?** Cosine similarity measures angular distance between vectors — robust to document length differences. Works better than Euclidean distance for high-dimensional embeddings.

3. **Why pgvector in production?** H2 requires comparing query against every chunk (O(n)). pgvector uses HNSW/IVFFlat approximate nearest neighbor indexes — O(log n) lookup even with millions of chunks.

4. **RAG vs fine-tuning?** RAG is preferred when documents change frequently (no retraining needed), when you need source citation, or when the knowledge base is too large to fit in context.
