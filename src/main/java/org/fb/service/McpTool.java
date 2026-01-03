package org.fb.service;

import reactor.core.publisher.Mono;

public interface McpTool {
    String getName();

    String getDescription();

    Object getInputSchema();

    Object getOutputSchema();

    Mono<Object> execute(Object params);
}