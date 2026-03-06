package com.linuxpkgmgr.contextmanagement;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A group of messages that together represent one user goal — from the initial
 * request through tool calls to the final LLM response.
 *
 * @param messages all messages belonging to this intent (no SystemMessage)
 * @param active   true if this is the current in-flight intent (not yet complete)
 */
public record Intent(List<Message> messages, boolean active) {

    /**
     * Compact one-line description for logging.
     * Example: [COMPLETE] 7 msgs | root: "install KCalc" | tools: [search_flathub(START), install_flatpak(END)]
     */
    public String summary() {
        String root = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .findFirst()
                .map(Message::getText)
                .map(t -> t.length() > 60 ? t.substring(0, 57) + "..." : t)
                .orElse("(no user message)");

        String tools = messages.stream()
                .filter(m -> m instanceof AssistantMessage am && am.hasToolCalls())
                .flatMap(m -> ((AssistantMessage) m).getToolCalls().stream())
                .map(AssistantMessage.ToolCall::name)
                .collect(Collectors.joining(", "));

        return "[%s] %d msgs | root: \"%s\"%s".formatted(
                active ? "ACTIVE" : "COMPLETE",
                messages.size(),
                root,
                tools.isEmpty() ? "" : " | tools: [" + tools + "]");
    }
}
