package com.linuxpkgmgr.contextmanagement;

import com.linuxpkgmgr.tool.IntentRole;
import com.linuxpkgmgr.tool.ToolIntentRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a flat message list into {@link Intent} groups using tool-call metadata.
 *
 * <p>Boundary rule: a new intent begins at the {@link UserMessage} that
 * immediately precedes an {@link AssistantMessage} containing a
 * {@link IntentRole#START}-marked tool call. That UserMessage is the root cause
 * of the new intent; everything before it closes the previous intent.
 *
 * <p>{@link SystemMessage}s are excluded from all intents — callers handle them
 * separately.
 */
@Component
public class IntentBoundaryDetector {

    private final ToolIntentRegistry registry;

    public IntentBoundaryDetector(ToolIntentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Detects intent boundaries in {@code messages}.
     *
     * @param messages full message list from {@code Prompt.getInstructions()},
     *                 may start with a {@link SystemMessage}
     * @return ordered list of intents; the last one is always marked active
     */
    public List<Intent> detect(List<Message> messages) {
        List<Intent> result = new ArrayList<>();

        // Skip leading SystemMessages — they belong to no intent
        int start = 0;
        while ( messages.get(start) instanceof SystemMessage) {
            start++;
        }

        int intentStart = start;

        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (!(msg instanceof AssistantMessage am)) continue;
            if (!am.hasToolCalls()) continue;

            boolean hasStartTool = am.getToolCalls().stream()
                    .anyMatch(tc -> registry.getRole(tc.name()) == IntentRole.START);
            if (!hasStartTool) continue;

            // Find the UserMessage immediately preceding this AssistantMessage
            int rootIdx = i - 1;
            while (rootIdx >= start && !(messages.get(rootIdx) instanceof UserMessage)) {
                rootIdx--;
            }
            if (rootIdx < start) continue;  // no preceding UserMessage found

            // Close previous intent: everything before the root UserMessage
            if (rootIdx > intentStart) {
                result.add(new Intent(new ArrayList<>(messages.subList(intentStart, rootIdx)), false));
            }

            // New intent starts at the root UserMessage
            intentStart = rootIdx;
        }

        // Remaining messages form the current active intent
        if (intentStart < messages.size()) {
            result.add(new Intent(new ArrayList<>(messages.subList(intentStart, messages.size())), true));
        }

        return result;
    }
}
