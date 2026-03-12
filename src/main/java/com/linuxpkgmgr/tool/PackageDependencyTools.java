package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools for querying package dependency relationships —
 * direct dependencies, reverse dependencies, and full dependency trees.
 */
@Slf4j
@Component
public class PackageDependencyTools implements ToolBean {

    private final SystemPackageService packageService;
    private final CommandExecutor executor;

    public PackageDependencyTools(SystemPackageService packageService, CommandExecutor executor) {
        this.packageService = packageService;
        this.executor = executor;
    }

    // ── Tool 1: Direct dependencies ──────────────────────────────────────────

    @PkgTool(name = "show_package_dependencies", role = IntentRole.NEUTRAL, description = """
            Shows the direct dependencies of a package — what libraries and packages it requires to run.
            Works for both installed and repository packages.
            packageName: exact native package name (e.g. 'firefox', 'gcc', 'vim').
            """)
    public String showPackageDependencies(String packageName) {
        log.debug("showPackageDependencies called — packageName: '{}'", packageName);
        try {
            String raw = switch (packageService.getNativePackageManager()) {
                case DNF    -> executor.execute(List.of("dnf", "repoquery", "--requires", packageName));
                case APT    -> executor.execute(List.of("apt-cache", "depends", packageName));
                case PACMAN -> pacmanDeps(packageName);
                case ZYPPER -> executor.execute(List.of("zypper", "--no-refresh", "info", "--requires", packageName));
                default     -> "No supported native package manager detected.";
            };
            return "Dependencies for \"" + packageName + "\":\n" + stripNoise(raw);
        } catch (Exception e) {
            log.warn("showPackageDependencies — error for '{}': {}", packageName, e.getMessage());
            return "Error fetching dependencies for \"" + packageName + "\": " + e.getMessage();
        }
    }

    // ── Tool 2: Reverse dependencies ─────────────────────────────────────────

    @PkgTool(name = "show_reverse_dependencies", role = IntentRole.NEUTRAL, description = """
            Shows reverse dependencies — what other installed packages depend on this package.
            Useful for understanding the blast radius before removing a package.
            packageName: exact native package name (e.g. 'openssl', 'glibc', 'zlib').
            """)
    public String showReverseDependencies(String packageName) {
        log.debug("showReverseDependencies called — packageName: '{}'", packageName);
        try {
            String raw = switch (packageService.getNativePackageManager()) {
                case DNF    -> executor.execute(List.of(
                        "dnf", "repoquery", "--installed", "--whatrequires", packageName));
                case APT    -> executor.execute(List.of(
                        "apt-cache", "rdepends", "--installed", packageName));
                case PACMAN -> pacmanReverseDeps(packageName);
                case ZYPPER -> executor.execute(List.of(
                        "zypper", "--no-refresh", "search", "--requires", packageName));
                default     -> "No supported native package manager detected.";
            };
            return "Reverse dependencies (what depends on \"" + packageName + "\"):\n" + stripNoise(raw);
        } catch (Exception e) {
            log.warn("showReverseDependencies — error for '{}': {}", packageName, e.getMessage());
            return "Error fetching reverse dependencies for \"" + packageName + "\": " + e.getMessage();
        }
    }

    // ── Tool 3: Dependency tree ──────────────────────────────────────────────

    @PkgTool(name = "show_dependency_tree", role = IntentRole.NEUTRAL, description = """
            Shows the full recursive dependency tree for a package — all transitive dependencies
            visualized as a tree structure.
            packageName: exact native package name (e.g. 'firefox', 'vlc', 'python3').
            """)
    public String showDependencyTree(String packageName) {
        log.debug("showDependencyTree called — packageName: '{}'", packageName);
        try {
            String raw = switch (packageService.getNativePackageManager()) {
                case DNF    -> executor.execute(List.of(
                        "dnf", "repoquery", "--tree", "--requires", packageName));
                case APT    -> executor.execute(List.of(
                        "apt-cache", "depends", "--recurse",
                        "--no-recommends", "--no-suggests", "--no-conflicts",
                        "--no-breaks", "--no-replaces", "--no-enhances", packageName));
                case PACMAN -> pacmanTree(packageName);
                case ZYPPER -> {
                    String output = executor.execute(List.of(
                            "zypper", "--no-refresh", "info", "--requires", packageName));
                    yield "(zypper does not support recursive dependency trees; showing direct requirements)\n"
                            + output;
                }
                default     -> "No supported native package manager detected.";
            };
            return "Dependency tree for \"" + packageName + "\":\n" + stripNoise(raw);
        } catch (Exception e) {
            log.warn("showDependencyTree — error for '{}': {}", packageName, e.getMessage());
            return "Error fetching dependency tree for \"" + packageName + "\": " + e.getMessage();
        }
    }

    // ── Pacman helpers ───────────────────────────────────────────────────────

    private String pacmanDeps(String packageName) throws Exception {
        // Try sync db first (-Si), fall back to installed (-Qi)
        String output = executor.execute(List.of("pacman", "-Si", packageName));
        if (output.isBlank() || output.contains("error:")) {
            output = executor.execute(List.of("pacman", "-Qi", packageName));
        }
        return output;
    }

    private String pacmanReverseDeps(String packageName) throws Exception {
        if (isPactreeAvailable()) {
            return executor.execute(List.of("pactree", "-r", packageName));
        }
        log.info("pactree not available, falling back to pacman -Qi for reverse deps");
        String output = executor.execute(List.of("pacman", "-Qi", packageName));
        String requiredBy = Arrays.stream(output.split("\n"))
                .filter(l -> l.startsWith("Required By"))
                .findFirst()
                .orElse("Required By : None");
        return "(pactree not available — install pacman-contrib for tree view)\n" + requiredBy;
    }

    private String pacmanTree(String packageName) throws Exception {
        if (isPactreeAvailable()) {
            return executor.execute(List.of("pactree", packageName));
        }
        log.info("pactree not available, falling back to pacman -Si for direct deps");
        String output = executor.execute(List.of("pacman", "-Si", packageName));
        if (output.isBlank() || output.contains("error:")) {
            output = executor.execute(List.of("pacman", "-Qi", packageName));
        }
        return "(pactree not available — install pacman-contrib for tree visualization; showing direct deps only)\n"
                + output;
    }

    private boolean isPactreeAvailable() {
        return Files.exists(Path.of("/usr/bin/pactree"))
                || Files.exists(Path.of("/usr/local/bin/pactree"));
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private String stripNoise(String raw) {
        return Arrays.stream(raw.split("\n"))
                .filter(l -> !l.startsWith("Last metadata")
                        && !l.startsWith("WARNING:")
                        && !l.startsWith("Loading repository")
                        && !l.startsWith("Reading installed"))
                .collect(Collectors.joining("\n"))
                .strip();
    }
}
