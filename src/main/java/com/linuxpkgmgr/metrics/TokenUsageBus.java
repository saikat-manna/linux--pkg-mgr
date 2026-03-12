package com.linuxpkgmgr.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Simple pub-sub event bus for token usage updates.
 * Subscribers receive events on the calling thread (typically background).
 * UI updates should use Platform.runLater().
 */
@Slf4j
@Component
public class TokenUsageBus {

    private final Set<Consumer<TokenUsageEvent>> subscribers = new CopyOnWriteArraySet<>();

    /**
     * Subscribe to token usage events. Duplicate subscribers are ignored.
     *
     * @param subscriber callback invoked on each token usage update
     */
    public void subscribe(Consumer<TokenUsageEvent> subscriber) {
        subscribers.add(subscriber);
        log.debug("TokenUsageBus — subscriber added, total: {}", subscribers.size());
    }

    /**
     * Unsubscribe from token usage events.
     *
     * @param subscriber the callback to remove
     */
    public void unsubscribe(Consumer<TokenUsageEvent> subscriber) {
        subscribers.remove(subscriber);
        log.debug("TokenUsageBus — subscriber removed, remaining: {}", subscribers.size());
    }

    /**
     * Publish a token usage event to all subscribers.
     *
     * @param event the token usage event to publish
     */
    public void publish(TokenUsageEvent event) {
        for (Consumer<TokenUsageEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.warn("TokenUsageBus — subscriber threw exception: {}", e.getMessage());
            }
        }
    }

    /**
     * Returns current subscriber count (useful for debugging).
     */
    public int subscriberCount() {
        return subscribers.size();
    }
}
