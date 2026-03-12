package com.linuxpkgmgr.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Advisor that intercepts LLM responses and records token usage metrics.
 *
 * <p>This advisor runs after the model call (LOWEST_PRECEDENCE means it wraps
 * around the actual model invocation), capturing usage statistics from the
 * response metadata and forwarding them to {@link TokenMetricsService}.</p>
 *
 * <p>The session ID is extracted from the advisor context parameter
 * "chat_memory_conversation_id" which is set by MessageChatMemoryAdvisor.</p>
 */
@Slf4j
@Component
public class TokenUsageAdvisor implements CallAdvisor {

    private final TokenMetricsService metricsService;

    public TokenUsageAdvisor(TokenMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Extract session ID from the advisor context
        String sessionId = Optional.ofNullable(
                        request.context().get("chat_memory_conversation_id"))
                .map(Object::toString)
                .orElse("unknown");

        // Execute the actual call through the chain
        ChatClientResponse response = chain.nextCall(request);

        // Extract and record token usage from response metadata
        recordUsageFromResponse(sessionId, response);

        return response;
    }

    /**
     * Extracts and records token usage from the response metadata.
     *
     * @param sessionId the session identifier for metrics aggregation
     * @param response the chat client response containing usage metadata
     */
    private void recordUsageFromResponse(String sessionId, ChatClientResponse response) {
        try {
            if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
                Usage usage = response.chatResponse().getMetadata().getUsage();
                if (usage != null) {
                    metricsService.record(sessionId, usage);
                    logUsage(sessionId, usage);
                } else {
                    log.debug("No usage data in response metadata for session {}", sessionId);
                }
            }
        } catch (Exception e) {
            // Never let metrics collection fail the actual call
            log.warn("Failed to record token usage for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Logs token usage details at debug level.
     *
     * @param sessionId the session identifier
     * @param usage the usage statistics to log
     */
    private void logUsage(String sessionId, Usage usage) {
        if (log.isDebugEnabled()) {
            log.debug("Token usage recorded for session {}: prompt={}, completion={}, total={}",
                    sessionId,
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }
    }

    @Override
    public int getOrder() {
        // Run after MessageChatMemoryAdvisor (which is typically around 100-200)
        // but before any other post-processing advisors
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public String getName() {
        return TokenUsageAdvisor.class.getSimpleName();
    }
}
