package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.model.PackageInfo;
import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import com.linuxpkgmgr.tool.IntentRole;
import com.linuxpkgmgr.tool.PkgTool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools for querying already-installed packages (native + Flatpak).
 */
@Slf4j
@Component
public class PackageQueryTools implements ToolBean {

    private final SystemPackageService packageService;
    private final CommandExecutor executor;

    public PackageQueryTools(SystemPackageService packageService, CommandExecutor executor) {
        this.packageService = packageService;
        this.executor = executor;
    }

    @PkgTool(name = "get_package_info", description = """
            Returns detailed information about a specific installed package — version, description,
            installed size, and source (Flatpak or native repo).
            Use this when the user asks for details about a specific package.
            packageName: exact native package name or Flatpak application ID (e.g. org.videolan.VLC).
            If unsure of the exact name, call listInstalledApps first to find it.
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

}
