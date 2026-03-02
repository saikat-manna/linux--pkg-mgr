package com.linuxpkgmgr.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Composite chat client that transparently routes each user query to either
 * the local or cloud Ollama model based on {@link ModelSelector#select}.
 *
 *   User query
 *       ├─ LOCAL ─► Local Model  (tools + memory)
 *       └─ CLOUD ─► Cloud Model  (tools + memory)
 */
@Component
public class RoutingChatClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatClient.class);

    private final ChatClient localClient;
    private final ChatClient cloudClient;
    private final ModelSelector selector;
    private final ToolSelector toolSelector;

    public RoutingChatClient(@Qualifier("localChatClient") ChatClient localClient,
                             @Qualifier("cloudChatClient") ChatClient cloudClient,
                             ModelSelector selector,
                             ToolSelector toolSelector) {
        this.localClient = localClient;
        this.cloudClient = cloudClient;
        this.selector = selector;
        this.toolSelector = toolSelector;
    }

    public String chat(String userQuery, String conversationId) {
        ModelSelector.Model model = selector.select(userQuery);
        Object[] tools = toolSelector.select(userQuery);
        log.debug("ModelSelector → [{}] for query: {}", model, userQuery);

        ChatClient client = (model == ModelSelector.Model.LOCAL) ? localClient : cloudClient;
        return client.prompt()
                .user(userQuery)
                .tools(tools)
                .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }
}
