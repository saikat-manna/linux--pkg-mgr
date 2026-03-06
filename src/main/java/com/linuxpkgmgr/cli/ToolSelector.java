package com.linuxpkgmgr.cli;

import com.linuxpkgmgr.tool.ToolEmbeddingIndex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Selects which tool beans to pass to the LLM for a given user query.
 * Delegates to ToolEmbeddingIndex for semantic similarity filtering.
 *
 * Short confirmations/negations (≤ 2 words) skip the embedding query and
 * reuse the tools returned for the previous substantive query.
 */
@Slf4j
@Component
public class ToolSelector {

    private final ToolEmbeddingIndex index;
    private Object[] lastTools = new Object[0];

    public ToolSelector(ToolEmbeddingIndex index) {
        this.index = index;
    }

    public Object[] select(String userQuery) {
        if (isTrivial(userQuery) && lastTools.length > 0) {
            log.debug("ToolSelector — trivial query '{}', reusing {} cached tool(s)", userQuery.trim(), lastTools.length);
            return lastTools;
        }
        lastTools = index.findRelevant(userQuery);
        return lastTools;
    }

    /** Returns true for short acknowledgements like "yes", "no", "ok", "sure", "proceed", etc. */
    private static boolean isTrivial(String query) {
        return query != null && query.trim().split("\\s+").length <= 2;
    }
}
