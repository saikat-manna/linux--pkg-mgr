package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tools for searching available packages in repositories and Flathub.
 * Search order: Flathub first, then native repo.
 */
@Slf4j
@Component
public class PackageSearchTools {

    private static final int MAX_RESULTS = 20;

    private final SystemPackageService packageService;
    private final CommandExecutor executor;

    public PackageSearchTools(SystemPackageService packageService, CommandExecutor executor) {
        this.packageService = packageService;
        this.executor = executor;
    }

    @Tool(description = """
            Searches Flathub for Flatpak applications NOT YET INSTALLED on the system.
            Use this ONLY when the user wants to discover or install new packages.
            Do NOT use this to list or query already-installed packages — use listInstalledPackages for that.
            Use this before searchNativeRepo; check Flathub first.
            query: keyword, app name, or description term (e.g. 'media player', 'vlc', 'scanner').
            Returns application name, ID, description, and version.
            """)
    public String searchFlathub(String query) {
        log.debug("searchFlathub called — query: '{}'", query);

        if (!packageService.isFlatpakAvailable()) {
            return "Flatpak is not available on this system.";
        }

        String output;
        try {
            output = executor.execute(List.of(
                    "flatpak", "search", query, "--columns=name,application,description,version"
            ));
        } catch (Exception e) {
            return "Error searching Flathub: " + e.getMessage();
        }

        List<String[]> rows = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank() && l.contains("\t"))
                .map(l -> l.split("\t", 4))
                .filter(f -> f.length >= 2)
                .limit(MAX_RESULTS)
                .toList();

        log.debug("searchFlathub — results: {}", rows.size());

        if (rows.isEmpty()) {
            return "No Flatpak applications found on Flathub matching \"" + query + "\".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Flathub results for \"").append(query)
          .append("\" (").append(rows.size()).append(" shown):\n\n");

        for (String[] f : rows) {
            String name    = f[0].trim();
            String appId   = f.length > 1 ? f[1].trim() : "";
            String desc    = f.length > 2 ? f[2].trim() : "";
            String version = f.length > 3 ? f[3].trim() : "";
            String summary = desc.isBlank() ? "" : " — " + desc;
            sb.append("[flatpak]  %-45s %-15s%s\n".formatted(
                    name + " (" + appId + ")", version, summary));
        }

        return sb.toString();
    }

    @Tool(description = """
            Searches the native system repository (dnf/apt/pacman/zypper) for packages NOT YET INSTALLED.
            Use this ONLY when the user wants to discover or install new packages from the native repo.
            Do NOT use this to list or query already-installed packages — use listInstalledPackages for that.
            Use this after searchFlathub when the package is not on Flathub, or for system-level packages
            (drivers, libraries, CLI tools) that should not be Flatpaks.
            query: keyword or package name.
            """)
    public String searchNativeRepo(String query) {
        log.debug("searchNativeRepo called — query: '{}', pm: {}", query, packageService.getNativePackageManager());

        try {
            return switch (packageService.getNativePackageManager()) {
                case DNF    -> searchDnf(query);
                case APT    -> searchApt(query);
                case PACMAN -> searchPacman(query);
                case ZYPPER -> searchZypper(query);
                default     -> "No supported native package manager detected on this system.";
            };
        } catch (Exception e) {
            return "Error searching native repo: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Searches DNF (Fedora/RHEL) repositories.
     * Command: dnf search <query>
     * Output line format: "vlc.x86_64 : VLC media player"
     * Filters out section headers (starting with '=') and metadata lines.
     */
    private String searchDnf(String query) throws Exception {
        String output = executor.execute(List.of("dnf", "search", query));
        List<String> lines = Arrays.stream(output.split("\n"))
                .filter(l -> l.contains(" : ") && !l.startsWith("=") && !l.startsWith("Last") && !l.startsWith("Error"))
                .limit(MAX_RESULTS)
                .toList();
        log.debug("searchDnf — lines: {}", lines.size());
        if (lines.isEmpty()) return "No native packages found matching \"" + query + "\".";
        return formatNativeOutput(query, lines);
    }

    /**
     * Searches APT (Debian/Ubuntu) repositories.
     * Command: apt-cache search <query>
     * Output line format: "vlc - multimedia player and streamer"
     * Output is already clean one-line-per-package, no headers to strip.
     */
    private String searchApt(String query) throws Exception {
        String output = executor.execute(List.of("apt-cache", "search", query));
        List<String> lines = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .limit(MAX_RESULTS)
                .toList();
        log.debug("searchApt — lines: {}", lines.size());
        if (lines.isEmpty()) return "No native packages found matching \"" + query + "\".";
        return formatNativeOutput(query, lines);
    }

    /**
     * Searches Pacman (Arch Linux) repositories.
     * Command: pacman -Ss <query>
     * Output format is two lines per package:
     *   "extra/vlc 3.0.20-1 [installed]"
     *   "    VLC media player"
     * Both lines are merged into a single entry: "extra/vlc 3.0.20-1 — VLC media player"
     */
    private String searchPacman(String query) throws Exception {
        String[] rawLines = executor.execute(List.of("pacman", "-Ss", query)).split("\n");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rawLines.length && lines.size() < MAX_RESULTS; i++) {
            String l = rawLines[i];
            if (!l.startsWith(" ") && l.contains("/")) {
                String desc = (i + 1 < rawLines.length) ? rawLines[i + 1].trim() : "";
                lines.add(l.trim() + (desc.isBlank() ? "" : " — " + desc));
                i++; // skip the description line we already consumed
            }
        }
        log.debug("searchPacman — lines: {}", lines.size());
        if (lines.isEmpty()) return "No native packages found matching \"" + query + "\".";
        return formatNativeOutput(query, lines);
    }

    /**
     * Searches Zypper (openSUSE/SUSE) repositories.
     * Command: zypper --no-refresh search <query>
     * Output row format: "i | vlc | VLC media player | package"
     *   Column 0: status ("i" = installed, blank = available)
     *   Column 1: package name
     *   Column 2: summary
     * Header and separator rows (containing "Name" or "---") are skipped.
     */
    private String searchZypper(String query) throws Exception {
        String output = executor.execute(List.of("zypper", "--no-refresh", "search", query));
        List<String> lines = Arrays.stream(output.split("\n"))
                .filter(l -> l.contains("|") && !l.contains("Name") && !l.contains("---"))
                .map(l -> {
                    String[] f = l.split("\\|");
                    if (f.length < 3) return l.trim();
                    String installed = f[0].trim().equals("i") ? "[installed] " : "";
                    String name      = f[1].trim();
                    String summary   = f[2].trim();
                    return installed + name + " — " + summary;
                })
                .limit(MAX_RESULTS)
                .toList();
        log.debug("searchZypper — lines: {}", lines.size());
        if (lines.isEmpty()) return "No native packages found matching \"" + query + "\".";
        return formatNativeOutput(query, lines);
    }

    private String formatNativeOutput(String query, List<String> lines) {
        return "[native] results for \"" + query + "\" (" + lines.size() + " shown):\n\n"
                + String.join("\n", lines);
    }
}
