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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
            - ALWAYS call the appropriate tool for current information. Never answer from memory alone.
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    /**
     * Local model — explicitly created from spring.ai.ollama.* properties.
     * We cannot rely on the auto-configured bean because Spring AI's
     * @ConditionalOnMissingBean(OllamaChatModel.class) suppresses it as soon as
     * cloudChatModel (also OllamaChatModel) is registered.
     */
    @Bean("localChatModel")
    public OllamaChatModel localChatModel(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.chat.model}") String model,
            @Value("${spring.ai.ollama.chat.options.temperature:0.2}") double temperature,
            @Value("${spring.ai.ollama.chat.options.num-ctx:8192}") int numCtx) {

        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .numCtx(numCtx)
                        .build())
                .build();
    }

    /**
     * Cloud model — larger, remote Ollama instance (configured via pkg-mgr.cloud.*).
     */
    @Bean("cloudChatModel")
    public OllamaChatModel cloudChatModel(
            @Value("${pkg-mgr.cloud.base-url}") String baseUrl,
            @Value("${pkg-mgr.cloud.model}") String model,
            @Value("${pkg-mgr.cloud.temperature:0.2}") double temperature,
            @Value("${pkg-mgr.cloud.num-ctx:32768}") int numCtx) {

        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .numCtx(numCtx)
                        .build())
                .build();
    }

    @Bean("localChatClient")
    public ChatClient localChatClient(
            @Qualifier("localChatModel") OllamaChatModel localChatModel,
            ChatMemory chatMemory,
            SystemInfoService systemInfoService,
            AppQueryTools appQueryTools,
            PackageQueryTools queryTools,
            PackageSearchTools searchTools,
            PackageInstallTools installTools,
            PackageUpdateTools updateTools) {

        return buildChatClient(localChatModel, chatMemory, systemInfoService,
                appQueryTools, queryTools, searchTools, installTools, updateTools);
    }

    /**
     * Cloud advisor — bare cloud client used to generate a planning hint before the local model runs.
     * No tools, no memory: one-shot reasoning only. The hint is injected into the local model's
     * user message so the smaller model benefits from the larger model's analysis.
     */
    @Bean("cloudAdvisorClient")
    public ChatClient cloudAdvisorClient(@Qualifier("cloudChatModel") OllamaChatModel cloudChatModel) {
        return ChatClient.builder(cloudChatModel)
                .defaultSystem("""
                        You are a concise query planner for a Linux package manager assistant.
                        Given a user request, output a brief plan (1-3 sentences) that identifies:
                        - What the user wants
                        - Which tool(s) to call and with what parameter values
                        Available tools: listInstalledApps(category), getPackageInfo(packageName), \
                        searchFlathub(query), searchNativeRepo(query), installFlatpak(appId), \
                        installNativePackage(name), removeFlatpak(appId), removeNativePackage(name).
                        Reply with only the plan. No preamble, no explanation.
                        """)
                .build();
    }

    /**
     * Cloud ChatClient — shares the same ChatMemory as localChatClient so context
     * flows across model switches.
     */
    @Bean("cloudChatClient")
    public ChatClient cloudChatClient(
            @Qualifier("cloudChatModel") OllamaChatModel cloudChatModel,
            ChatMemory chatMemory,
            SystemInfoService systemInfoService,
            AppQueryTools appQueryTools,
            PackageQueryTools queryTools,
            PackageSearchTools searchTools,
            PackageInstallTools installTools,
            PackageUpdateTools updateTools) {

        return buildChatClient(cloudChatModel, chatMemory, systemInfoService,
                appQueryTools, queryTools, searchTools, installTools, updateTools);
    }

    private ChatClient buildChatClient(OllamaChatModel model,
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
