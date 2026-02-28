package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.model.PackageInfo;
import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools for querying already-installed packages (native + Flatpak).
 */
@Slf4j
@Component
public class PackageQueryTools {

    private final SystemPackageService packageService;
    private final CommandExecutor executor;

    @Value("${pkg-mgr.list.max-results:50}")
    private int maxResults;

    public PackageQueryTools(SystemPackageService packageService, CommandExecutor executor) {
        this.packageService = packageService;
        this.executor = executor;
    }

    @Tool(description = """
            Use this when the user asks what packages are installed, wants to see a list of installed software,
            asks if a specific package is already installed, or wants to check current installations.
            Lists user-installed packages (excludes system/dependency packages) from both native PM and Flatpak.
            Do NOT use searchFlathub or searchNativeRepo for this — those only search packages not yet installed.
            filter: keyword to narrow results by name or description — pass empty string to list all.
            Results are capped; use a specific filter if there are too many matches.
            """)
    public String listInstalledPackages(String filter) {
        log.debug("listInstalledPackages called — filter: '{}'", filter);

        List<PackageInfo> all;
        try {
            all = packageService.listInstalled();
        } catch (Exception e) {
            return "Error querying installed packages: " + e.getMessage();
        }

        log.debug("Total user-installed packages fetched: {}", all.size());

        boolean hasFilter = filter != null && !filter.isBlank();
        List<PackageInfo> matched = hasFilter
                ? all.stream()
                     .filter(p -> matches(p, filter.toLowerCase()))
                     .toList()
                : all;

        log.debug("Packages after filtering: {} (hasFilter={})", matched.size(), hasFilter);

        if (matched.isEmpty()) {
            return hasFilter
                    ? "No installed packages found matching \"" + filter + "\"."
                    : "No user-installed packages found.";
        }

        boolean truncated = matched.size() > maxResults;
        List<PackageInfo> displayed = truncated ? matched.subList(0, maxResults) : matched;

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matched.size()).append(" installed package(s)");
        if (hasFilter) sb.append(" matching \"").append(filter).append("\"");
        sb.append(":\n\n");

        for (PackageInfo pkg : displayed) {
            sb.append(format(pkg)).append("\n");
        }

        if (truncated) {
            sb.append("\n(Showing ").append(maxResults).append(" of ").append(matched.size())
              .append(". Use a more specific filter to narrow results.)");
        }

        return sb.toString();
    }

    @Tool(description = """
            Returns detailed information about a specific installed package — version, description,
            installed size, and source (Flatpak or native repo).
            Use this when the user asks for details about a specific package.
            packageName: exact native package name or Flatpak application ID (e.g. org.videolan.VLC).
            If unsure of the exact name, call listInstalledPackages first to find it.
            """)
    public String getPackageInfo(String packageName) {
        log.debug("getPackageInfo called — packageName: '{}'", packageName);

        List<PackageInfo> installed;
        try {
            installed = packageService.listInstalled();
        } catch (Exception e) {
            log.warn("getPackageInfo — failed to fetch installed list: {}", e.getMessage());
            installed = List.of();
        }

        String lower = packageName.toLowerCase();
        List<PackageInfo> matches = installed.stream()
                .filter(p -> p.id().equalsIgnoreCase(packageName)
                        || p.name().equalsIgnoreCase(packageName)
                        || p.id().toLowerCase().contains(lower)
                        || p.name().toLowerCase().contains(lower))
                .toList();

        log.debug("getPackageInfo — installed matches for '{}': {}", packageName, matches.size());

        if (matches.isEmpty()) {
            log.debug("getPackageInfo — '{}' not found in installed list", packageName);
            return "Package \"" + packageName + "\" does not appear to be installed. "
                    + "Use searchFlathub or searchNativeRepo to find it.";
        }

        StringBuilder sb = new StringBuilder();
        for (PackageInfo match : matches) {
            log.debug("getPackageInfo — fetching info for '{}' (source: {})", match.id(), match.source());
            sb.append(fetchInfo(match)).append("\n\n");
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------

    private String fetchInfo(PackageInfo pkg) {
        try {
            return switch (pkg.source()) {
                case FLATPAK -> fetchFlatpakInfo(pkg.id());
                case NATIVE  -> fetchNativeInfo(pkg.id());
            };
        } catch (Exception e) {
            log.warn("getPackageInfo — error fetching info for '{}': {}", pkg.id(), e.getMessage());
            return "Error fetching info for \"" + pkg.id() + "\": " + e.getMessage();
        }
    }

    /**
     * Command: flatpak info <app-id>
     * Output: key-value table with ID, Version, License, Origin, Installed size, etc.
     */
    private String fetchFlatpakInfo(String appId) throws Exception {
        log.debug("fetchFlatpakInfo — appId: '{}'", appId);
        String output = executor.execute(List.of("flatpak", "info", appId));
        return "[flatpak] " + appId + "\n" + output.strip();
    }

    /**
     * Commands per PM:
     *   DNF:    dnf info <pkg>           — "Name : ...", "Version : ...", "Description : ..."
     *   APT:    apt show <pkg>           — "Package: ...", "Version: ...", "Description: ..."
     *   Pacman: pacman -Qi <pkg>         — "Name : ...", "Installed Size : ...", "Description : ..."
     *   Zypper: zypper --no-refresh info — "Name: ...", "Version: ...", "Description: ..."
     * Metadata/noise lines (repository refresh, warnings) are stripped.
     */
    private String fetchNativeInfo(String packageName) throws Exception {
        log.debug("fetchNativeInfo — package: '{}', pm: {}", packageName, packageService.getNativePackageManager());
        String raw = switch (packageService.getNativePackageManager()) {
            case DNF    -> executor.execute(List.of("dnf", "info", packageName));
            case APT    -> executor.execute(List.of("apt", "show", packageName));
            case PACMAN -> executor.execute(List.of("pacman", "-Qi", packageName));
            case ZYPPER -> executor.execute(List.of("zypper", "--no-refresh", "info", packageName));
            default     -> "No supported native package manager detected.";
        };
        String cleaned = Arrays.stream(raw.split("\n"))
                .filter(l -> !l.startsWith("Last metadata")
                        && !l.startsWith("WARNING:")
                        && !l.startsWith("Loading repository")
                        && !l.startsWith("Reading installed"))
                .collect(Collectors.joining("\n"))
                .strip();
        return "[native] " + packageName + "\n" + cleaned;
    }

    private boolean matches(PackageInfo pkg, String keyword) {
        return pkg.name().toLowerCase().contains(keyword)
                || pkg.id().toLowerCase().contains(keyword)
                || pkg.summary().toLowerCase().contains(keyword);
    }

    private String format(PackageInfo pkg) {
        String tag = pkg.source() == PackageInfo.Source.FLATPAK ? "[flatpak]" : "[native] ";
        String label = pkg.source() == PackageInfo.Source.FLATPAK
                ? pkg.name() + " (" + pkg.id() + ")"
                : pkg.id();
        String summary = pkg.summary().isBlank() ? "" : " — " + pkg.summary();
        return "%s  %-45s %s%s".formatted(tag, label, pkg.version(), summary);
    }
}
