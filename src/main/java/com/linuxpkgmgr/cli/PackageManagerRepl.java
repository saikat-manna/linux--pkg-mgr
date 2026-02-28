package com.linuxpkgmgr.cli;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.UUID;

/**
 * Interactive REPL for the agentic package manager.
 *
 * Each JVM run gets a unique sessionId so the MessageChatMemoryAdvisor
 * scopes conversation history to this session only.
 */
@Component
public class PackageManagerRepl implements ApplicationRunner {

    private static final String BANNER = """
            ╔══════════════════════════════════════════════╗
            ║      Linux Package Manager  (AI-powered)     ║
            ║  Type your request in plain English.         ║
            ║  Type 'exit' or 'quit' to stop.              ║
            ╚══════════════════════════════════════════════╝
            """;

    private final ChatClient chatClient;
    private final String sessionId = UUID.randomUUID().toString();

    public PackageManagerRepl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println(BANNER);

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                System.out.flush();

                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();

                if (input.isBlank()) continue;
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) break;

                try {
                    String response = chatClient.prompt()
                            .user(input)
                            .advisors(a -> a.param(
                                    "chat_memory_conversation_id", sessionId))
                            .call()
                            .content();

                    System.out.println("\n" + response + "\n");
                } catch (Exception e) {
                    System.err.println("[Error] " + e.getMessage());
                }
            }
        }

        System.out.println("Goodbye!");
    }
}
