package com.linuxpkgmgr.config;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Intercepts the full message payload immediately before it is dispatched to the LLM.
 *
 * By the time this advisor runs, all prior advisors (notably MessageChatMemoryAdvisor)
 * have already merged conversation history into the prompt, so
 * {@code request.prompt().getInstructions()} reflects the complete, assembled context
 * window — system prompt + full conversation history + current user turn.
 *
 * Override or replace {@link #interceptPayload} to implement context compaction,
 * token-budget trimming, or any other payload transformation.
 */
@Component
public class PayloadInterceptorAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest intercepted = interceptPayload(request);
        return chain.nextCall(intercepted);
    }

    /**
     * Hook called with the complete, memory-enriched payload before each LLM call.
     *
     * <p>The default implementation is a no-op pass-through. Replace the body with
     * compaction or trimming logic when ready. Use {@code request.mutate()} to build
     * a modified copy without touching the original:
     * <pre>{@code
     *   List<Message> trimmed = compact(request.prompt().getInstructions());
     *   Prompt newPrompt = new Prompt(trimmed, request.prompt().getOptions());
     *   return request.mutate().prompt(newPrompt).build();
     * }</pre>
     *
     * @param request the full request; {@code request.prompt().getInstructions()} contains
     *                the system prompt, full conversation history, and current user turn
     * @return the (possibly modified) request to forward to the model
     */
    protected ChatClientRequest interceptPayload(ChatClientRequest request) {
        return request;   // stub — pass through unchanged
    }

    /**
     * Runs as the innermost advisor so it observes the request only after all
     * preceding advisors (e.g. {@code MessageChatMemoryAdvisor}) have enriched it.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public String getName() {
        return PayloadInterceptorAdvisor.class.getSimpleName();
    }
}
