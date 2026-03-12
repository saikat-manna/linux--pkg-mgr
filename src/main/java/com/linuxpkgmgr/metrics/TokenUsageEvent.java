package com.linuxpkgmgr.metrics;

/**
 * Event published whenever token usage is recorded for a session.
 * Immutable record suitable for pub-sub distribution.
 *
 * @param sessionId the session identifier
 * @param promptTokens cumulative prompt tokens for this session
 * @param completionTokens cumulative completion tokens for this session
 * @param totalTokens cumulative total tokens for this session
 * @param requestCount number of requests made in this session
 * @param deltaPrompt tokens used in this specific request
 * @param deltaCompletion tokens generated in this specific request
 * @param deltaTotal total tokens for this specific request
 */
public record TokenUsageEvent(
        String sessionId,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        int requestCount,
        long deltaPrompt,
        long deltaCompletion,
        long deltaTotal
) {}
