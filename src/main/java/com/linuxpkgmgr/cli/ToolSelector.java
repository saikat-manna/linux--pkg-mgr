package com.linuxpkgmgr.cli;

import com.linuxpkgmgr.tool.ToolEmbeddingIndex;
import org.springframework.stereotype.Component;

/**
 * Selects which tool beans to pass to the LLM for a given user query.
 * Delegates to ToolEmbeddingIndex for semantic similarity filtering.
 */
@Component
public class ToolSelector {

    private final ToolEmbeddingIndex index;

    public ToolSelector(ToolEmbeddingIndex index) {
        this.index = index;
    }

    public Object[] select(String userQuery) {
        return index.findRelevant(userQuery);
    }
}
