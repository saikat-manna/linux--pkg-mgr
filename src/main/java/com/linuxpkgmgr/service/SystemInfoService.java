package com.linuxpkgmgr.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Queries Linux system details once at startup and caches them.
 * Reads directly from /proc and /etc/os-release — no external commands needed.
 */
@Slf4j
@Service
public class SystemInfoService {

    private String systemDetails;

    @PostConstruct
    void init() {
        String distro = readDistro();
        String cpu    = readCpu();
        String ramGb  = readRamGb();

        systemDetails = "Distro: %s | CPU: %s | RAM: %s GB".formatted(distro, cpu, ramGb);
        log.info("System info cached: {}", systemDetails);
    }

    /** Single-line summary suitable for embedding in the system prompt. */
    public String getSystemDetails() {
        return systemDetails;
    }

    // -------------------------------------------------------------------------

    private String readDistro() {
        try {
            Map<String, String> fields = Files.readAllLines(Path.of("/etc/os-release"))
                    .stream()
                    .filter(l -> l.contains("="))
                    .collect(Collectors.toMap(
                            l -> l.substring(0, l.indexOf('=')).trim(),
                            l -> l.substring(l.indexOf('=') + 1).trim().replace("\"", ""),
                            (a, b) -> a
                    ));
            return fields.getOrDefault("PRETTY_NAME", fields.getOrDefault("NAME", "Unknown Linux"));
        } catch (IOException e) {
            log.warn("Could not read /etc/os-release", e);
            return "Unknown Linux";
        }
    }

    private String readCpu() {
        try {
            return Files.readAllLines(Path.of("/proc/cpuinfo")).stream()
                    .filter(l -> l.startsWith("model name"))
                    .findFirst()
                    .map(l -> l.substring(l.indexOf(':') + 1).trim())
                    .orElse("Unknown CPU");
        } catch (IOException e) {
            log.warn("Could not read /proc/cpuinfo", e);
            return "Unknown CPU";
        }
    }

    private String readRamGb() {
        try {
            return Files.readAllLines(Path.of("/proc/meminfo")).stream()
                    .filter(l -> l.startsWith("MemTotal:"))
                    .findFirst()
                    .map(l -> {
                        // value is in kB, e.g. "MemTotal:       32768000 kB"
                        String kbStr = l.replaceAll("[^0-9]", "");
                        long kb = Long.parseLong(kbStr);
                        return String.valueOf(Math.round(kb / 1_048_576.0));
                    })
                    .orElse("Unknown");
        } catch (IOException e) {
            log.warn("Could not read /proc/meminfo", e);
            return "Unknown";
        }
    }
}
