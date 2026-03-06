package com.linuxpkgmgr.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Embeds every @PkgTool description at startup into an in-memory vector store.
 * At request time, similarity-searches the user query to return only the
 * most relevant tool beans — reducing token usage and model confusion.
 */
@Slf4j
@Component
public class ToolEmbeddingIndex {

    private final SimpleVectorStore store;
    private final Map<String, ToolBean> beanByToolName;
    private final List<ToolBean> anchorBeans;

    @Value("${pkg-mgr.tools.top-k:6}")
    private int topK;

    @Value("${pkg-mgr.tools.similarity-threshold:0.4}")
    private double similarityThreshold;

    public ToolEmbeddingIndex(EmbeddingModel embeddingModel, List<ToolBean> toolBeans) {
        this.store = SimpleVectorStore.builder(embeddingModel).build();
        this.beanByToolName = new HashMap<>();
        this.anchorBeans = new ArrayList<>();
        toolBeans.forEach(this::index);
        log.info("ToolEmbeddingIndex — indexed {} tool method(s) across {} bean(s)",
                beanByToolName.size(), toolBeans.size());
    }

    private void index(ToolBean bean) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            PkgTool ann = method.getAnnotation(PkgTool.class);
            if (ann == null) continue;
            store.add(List.of(new Document(ann.description(), Map.of("tool_name", ann.name()))));
            beanByToolName.put(ann.name(), bean);
            if (ann.anchor()) anchorBeans.add(bean);
            log.debug("Indexed tool: {} (anchor={})", ann.name(), ann.anchor());
        }
    }

    public Object[] findRelevant(String query) {
        List<Document> hits = store.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());

        log.debug("findRelevant('{}') — {} hit(s) above threshold {}", query, hits.size(), similarityThreshold);

        Set<ToolBean> result = new LinkedHashSet<>(anchorBeans);
        hits.stream()
                .map(d -> beanByToolName.get((String) d.getMetadata().get("tool_name")))
                .filter(Objects::nonNull)
                .forEach(result::add);

        log.debug("findRelevant — returning {} unique tool bean(s)", result.size());
        return result.toArray();
    }
}
