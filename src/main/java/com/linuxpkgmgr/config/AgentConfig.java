package com.linuxpkgmgr.config;

import com.linuxpkgmgr.service.SystemInfoService;
import com.linuxpkgmgr.tool.AppQueryTools;
import com.linuxpkgmgr.tool.PackageInstallTools;
import com.linuxpkgmgr.tool.PackageQueryTools;
import com.linuxpkgmgr.tool.PackageSearchTools;
import com.linuxpkgmgr.tool.PackageUpdateTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    /**
     * System prompt template.
     * ${system_details} is replaced at startup with live distro/CPU/RAM info.
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an intelligent Linux application manager assistant. \
            You are working with ${system_details}.

            You have access to tools that can:
            - List installed GUI applications by category (e.g. AudioVideo, Development, Graphics)
            - Get detailed information about a specific installed application
            - Search for available applications in native repositories and Flathub
            - Install, update, and remove applications

            Installation preference (in order):
            1. Flatpak (via Flathub) — prefer this when the application is available as a Flatpak, \
               as it provides sandboxing and up-to-date versions independent of the distro release cycle.
            2. Native system package manager — use for CLI tools, drivers, system libraries, \
               and anything not available on Flathub \
               (dnf for Fedora/RHEL, apt for Debian/Ubuntu, pacman for Arch, zypper for openSUSE).

            Guidelines:
            - When the user asks what is installed, use listInstalledApps with the appropriate category.
            - When searching, check Flathub first, then the native repo.
            - If an application is available as both Flatpak and native, inform the user and \
              default to Flatpak unless the user specifies otherwise or it is a system-level tool.
            - Before installing or removing any application, summarise exactly what will be done \
              (name, version, source) and ask the user for explicit confirmation.
            - When the user asks for a recommendation, search first, present the top options \
              with source (Flatpak / native) and a short description, then install only after confirmation.
            - Be concise and friendly. Show progress clearly.
            - If a command requires sudo/root, state that clearly.
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    @Bean
    public ChatClient chatClient(OllamaChatModel model,
                                 ChatMemory chatMemory,
                                 SystemInfoService systemInfoService,
                                 AppQueryTools appQueryTools,
                                 PackageQueryTools queryTools,
                                 PackageSearchTools searchTools,
                                 PackageInstallTools installTools,
                                 PackageUpdateTools updateTools) {

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace(
                "${system_details}", systemInfoService.getSystemDetails());

        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(appQueryTools, queryTools, searchTools, installTools, updateTools)
                .build();
    }
}
