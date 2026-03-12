package com.linuxpkgmgr.metrics;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks cumulative token usage per session across all AI interactions.
 *
 * <p>Records prompt tokens, completion tokens, and total tokens for each session.
 * Thread-safe using ConcurrentHashMap for concurrent access from multiple chat calls.</p>
 *
 * <p>Usage example:
 * <pre>
 *   ChatClientResponse response = client.prompt().call().chatResponse();
 *   Usage usage = response.getMetadata().getUsage();
 *   tokenMetricsService.record(sessionId, usage);
 * </pre></p>
 */
@Slf4j
@Service
public class TokenMetricsService {

    private final Map<String, TokenCounter> sessionCounters = new ConcurrentHashMap<>();

    private final TokenUsageBus usageBus;

    public TokenMetricsService(TokenUsageBus usageBus) {
        this.usageBus = usageBus;
    }

    /**
     * Records token usage for a given session.
     *
     * @param sessionId the conversation session ID
     * @param usage the Usage object from ChatResponseMetadata (may be null)
     */
    public void record(String sessionId, Usage usage) {
        if (usage == null) {
            log.debug("No usage data available for session {}", sessionId);
            return;
        }

        TokenCounter counter = sessionCounters.computeIfAbsent(sessionId, TokenCounter::new);
        counter.add(usage);

        log.debug("Session {} token usage: prompt={}, completion={}, total={}",
                sessionId, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        // Publish event to subscribers
        TokenUsageEvent event = new TokenUsageEvent(
                sessionId,
                counter.getPromptTokens(),
                counter.getCompletionTokens(),
                counter.getTotalTokens(),
                counter.getRequestCount(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );
        usageBus.publish(event);
    }

    /**
     * Gets the current token counter for a session.
     *
     * @param sessionId the conversation session ID
     * @return the TokenCounter for this session, or null if no records exist
     */
    public TokenCounter getSessionStats(String sessionId) {
        return sessionCounters.get(sessionId);
    }

    /**
     * Gets all session stats (useful for admin dashboards).
     *
     * @return unmodifiable view of all session counters
     */
    public Map<String, TokenCounter> getAllStats() {
        return Map.copyOf(sessionCounters);
    }

    /**
     * Clears stats for a session (call on session end to free memory).
     *
     * @param sessionId the conversation session ID
     */
    public void clearSession(String sessionId) {
        sessionCounters.remove(sessionId);
        log.debug("Cleared token metrics for session {}", sessionId);
    }

    /**
     * Per-session token accumulator with thread-safe operations.
     */
    @Getter
    @ToString
    public static class TokenCounter {
        private final String sessionId;
        private final Instant createdAt;
        private volatile long promptTokens = 0;
        private volatile long completionTokens = 0;
        private volatile long totalTokens = 0;
        private volatile int requestCount = 0;

        TokenCounter(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = Instant.now();
        }

        /**
         * Atomically adds usage statistics.
         */
        synchronized void add(Usage usage) {
            this.promptTokens += usage.getPromptTokens();
            this.completionTokens += usage.getCompletionTokens();
            this.totalTokens += usage.getTotalTokens();
            this.requestCount++;
        }

        /**
         * Gets the average tokens per request.
         */
        public double getAverageTokensPerRequest() {
            return requestCount > 0 ? (double) totalTokens / requestCount : 0;
        }

        /**
         * Gets the session duration in seconds.
         */
        public long getDurationSeconds() {
            return java.time.Duration.between(createdAt, Instant.now()).getSeconds();
        }
    }
}
