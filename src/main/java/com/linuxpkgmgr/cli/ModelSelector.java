package com.linuxpkgmgr.cli;

import org.springframework.stereotype.Component;

/**
 * Decides which Ollama model should handle a given user query.
 *
 * Stub — always routes to LOCAL.
 * Replace the body of {@link #select} with your routing logic, e.g.:
 *   - keyword/regex heuristic (complex queries → CLOUD)
 *   - query length threshold
 *   - embedding-based complexity score
 *   - round-robin / load balancing
 */
@Component
public class ModelSelector {

    public enum Model { LOCAL, CLOUD }

    public Model select(String userQuery) {
        return Model.CLOUD;
    }
}
