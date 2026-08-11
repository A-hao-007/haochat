package com.ahao.haochat.common.chatai.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Structured RAG result used by runtime tracing and offline evaluation. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemoryEnrichmentResult {
    private String enrichedMessage;
    private String retrievalQuery;
    private boolean rewritten;
    private int vectorHits;
    private int graphHits;
    private int totalHits;
    private boolean vectorDegraded;
    private boolean graphDegraded;
}
