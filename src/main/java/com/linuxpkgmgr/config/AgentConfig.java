package com.linuxpkgmgr.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.linuxpkgmgr.metrics.TokenUsageAdvisor;
import com.linuxpkgmgr.metrics.TokenUsageAdvisor;
import com.linuxpkgmgr.service.SystemInfoService;

@Configuration
public class AgentConfig {

    @Value("${spring.ai.ollama.base-url}")       private String localBaseUrl;
    @Value("${spring.ai.ollama.chat.model}")      private String localModel;
    @Value("${spring.ai.ollama.chat.options.temperature:0.2}") private double localTemperature;
    @Value("${spring.ai.ollama.chat.options.num-ctx:8192}")    private int localNumCtx;

//    @Value("${spring.ai.ollama.embedding.model}")  private String embeddingModelName;

    @Value("${pkg-mgr.cloud.base-url}")           private String cloudBaseUrl;
    @Value("${pkg-mgr.cloud.model}")              private String cloudModel;
    @Value("${pkg-mgr.cloud.temperature:0.2}")    private double cloudTemperature;
    @Value("${pkg-mgr.cloud.num-ctx:32768}")      private int cloudNumCtx;

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

//    @Bean
//    public EmbeddingModel embeddingModel() {
//        return OllamaEmbeddingModel.builder()
//                .ollamaApi(OllamaApi.builder().baseUrl(localBaseUrl).build())
//                .defaultOptions(OllamaEmbeddingOptions.builder()
//                        .model(embeddingModelName)
//                        .build())
//                .build();
//    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    @Bean("localChatClient")
    public ChatClient localChatClient(ChatMemory chatMemory, SystemInfoService systemInfoService,
                                      PayloadInterceptorAdvisor payloadInterceptor,
                                      TokenUsageAdvisor tokenUsageAdvisor) {
        return buildChatClient(localModel(), chatMemory, systemInfoService, payloadInterceptor, tokenUsageAdvisor);
    }

    /**
     * Cloud advisor — bare cloud client used to generate a planning hint before the local model runs.
     * No tools, no memory: one-shot reasoning only.
     */
    @Bean("cloudAdvisorClient")
    public ChatClient cloudAdvisorClient() {
        return ChatClient.builder(cloudModel())
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
    public ChatClient cloudChatClient(ChatMemory chatMemory, SystemInfoService systemInfoService,
                                      PayloadInterceptorAdvisor payloadInterceptor,
                                      TokenUsageAdvisor tokenUsageAdvisor) {
        return buildChatClient(cloudModel(), chatMemory, systemInfoService, payloadInterceptor, tokenUsageAdvisor);
    }

    private OllamaChatModel localModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(localBaseUrl).build())
                .defaultOptions(OllamaChatOptions.builder()
                        .model(localModel)
                        .temperature(localTemperature)
                        .numCtx(localNumCtx)
                        .build())
                .build();
    }

    private OllamaChatModel cloudModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(cloudBaseUrl).build())
                .defaultOptions(OllamaChatOptions.builder()
                        .model(cloudModel)
                        .temperature(cloudTemperature)
                        .numCtx(cloudNumCtx)
                        .build())
                .build();
    }

    private ChatClient buildChatClient(OllamaChatModel model,
                                       ChatMemory chatMemory,
                                       SystemInfoService systemInfoService,
                                       PayloadInterceptorAdvisor payloadInterceptor,
                                       TokenUsageAdvisor tokenUsageAdvisor) {

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace(
                "${system_details}", systemInfoService.getSystemDetails());

        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        payloadInterceptor,
                        tokenUsageAdvisor)
                .build();
    }
}
