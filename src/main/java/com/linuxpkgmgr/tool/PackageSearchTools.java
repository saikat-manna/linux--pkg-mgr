package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

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
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
