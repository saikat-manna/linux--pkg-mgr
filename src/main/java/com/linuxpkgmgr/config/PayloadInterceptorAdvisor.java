package com.linuxpkgmgr.config;

import com.linuxpkgmgr.contextmanagement.Intent;
import com.linuxpkgmgr.contextmanagement.IntentBoundaryDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Intercepts the full message payload immediately before it is dispatched to the LLM.
 *
 * By the time this advisor runs, all prior advisors (notably MessageChatMemoryAdvisor)
 * have already merged conversation history into the prompt, so
 * {@code request.prompt().getInstructions()} reflects the complete, assembled context
 * window — system prompt + full conversation history + current user turn.
 */
@Slf4j
@Component
public class PayloadInterceptorAdvisor implements CallAdvisor {

    private final IntentBoundaryDetector intentBoundaryDetector;

    public PayloadInterceptorAdvisor(IntentBoundaryDetector intentBoundaryDetector) {
        this.intentBoundaryDetector = intentBoundaryDetector;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    	var response = chain.nextCall(interceptPayload(request)); 
        return response;
    }

    /**
     * Hook called with the complete, memory-enriched payload before each LLM call.
     * Detects intent boundaries and logs them. Payload is returned unchanged for now —
     * replace the pass-through with compaction logic when ready.
     */
    protected ChatClientRequest interceptPayload(ChatClientRequest request) {
        List<Intent> intents = intentBoundaryDetector.detect(
                request.prompt().getInstructions());

        if (log.isDebugEnabled()) {
            log.debug("Intent boundary detection — {} intent(s) found:", intents.size());
            intents.forEach(intent -> log.debug("  {}", intent.summary()));
        }

        return request;
    }

    @Override
    public int getOrder() {
        // Ordered.LOWEST_PRECEDENCE is reserved by ChatModelCallAdvisor (the actual model call).
        // We must be one step higher-priority so we run just before the model call,
        // after all memory/enrichment advisors have already processed the request.
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public String getName() {
        return PayloadInterceptorAdvisor.class.getSimpleName();
    }
}
