package com.linuxpkgmgr.service;

import com.linuxpkgmgr.model.InstalledPackage;
import com.linuxpkgmgr.model.InstalledPackage.Source;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects the native package manager at startup and provides
 * user-installed package listings (native + Flatpak), with caching.
 *
 * System/dependency packages are excluded by using each PM's own
 * "user-installed" query rather than pattern matching.
 */
@Slf4j
@Service
public class SystemPackageService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    public enum PackageManager { DNF, APT, PACMAN, ZYPPER, UNKNOWN }

    private final CommandExecutor executor;

    private PackageManager nativePackageManager;
    private boolean flatpakAvailable;

    private volatile List<InstalledPackage> cachedPackages = null;
    private volatile Instant cacheExpiry = Instant.MIN;

    public SystemPackageService(CommandExecutor executor) {
        this.executor = executor;
    }

    @PostConstruct
    void init() {
        nativePackageManager = detectNativePackageManager();
        flatpakAvailable = isCommandAvailable("flatpak");
        log.info("Native package manager: {}, Flatpak available: {}", nativePackageManager, flatpakAvailable);
    }

    public PackageManager getNativePackageManager() {
        return nativePackageManager;
    }

    public boolean isFlatpakAvailable() {
        return flatpakAvailable;
    }

    // -------------------------------------------------------------------------
    // Installed package listing (user-installed only, cached)
    // -------------------------------------------------------------------------

    /**
     * Returns all user-installed packages (native + Flatpak).
     * Results are cached for {@value} seconds to avoid repeated subprocess spawns.
     * Call {@link #invalidateCache()} after any install/update/remove operation.
     */
    public synchronized List<InstalledPackage> listInstalled() {
        if (cachedPackages != null && Instant.now().isBefore(cacheExpiry)) {
            log.debug("Returning {} cached packages", cachedPackages.size());
            return cachedPackages;
        }

        List<InstalledPackage> packages = new ArrayList<>();

        try {
            packages.addAll(fetchNativePackages());
        } catch (Exception e) {
            log.warn("Failed to fetch native packages: {}", e.getMessage());
        }

        if (flatpakAvailable) {
            try {
                packages.addAll(fetchFlatpakPackages());
            } catch (Exception e) {
                log.warn("Failed to fetch Flatpak packages: {}", e.getMessage());
            }
        }

        cachedPackages = Collections.unmodifiableList(packages);
        cacheExpiry = Instant.now().plus(CACHE_TTL);
        log.debug("Cached {} installed packages", cachedPackages.size());
        return cachedPackages;
    }

    /** Forces a fresh fetch on the next call to {@link #listInstalled()}. */
    public synchronized void invalidateCache() {
        cachedPackages = null;
        cacheExpiry = Instant.MIN;
        log.debug("Package cache invalidated");
    }

    // -------------------------------------------------------------------------
    // Native package fetching — user-installed only
    // -------------------------------------------------------------------------

    private List<InstalledPackage> fetchNativePackages() throws IOException, InterruptedException {
        return switch (nativePackageManager) {
            case DNF    -> fetchDnf();
            case APT    -> fetchApt();
            case PACMAN -> fetchPacman();
            case ZYPPER -> fetchZypper();
            default     -> {
                log.warn("No supported native package manager detected");
                yield List.of();
            }
        };
    }

    private List<InstalledPackage> fetchDnf() throws IOException, InterruptedException {
        // --userinstalled excludes packages pulled in purely as dependencies
        String output = executor.execute(List.of(
                "dnf", "repoquery", "--userinstalled",
                "--queryformat", "%{NAME}\t%{VERSION}-%{RELEASE}\t%{SUMMARY}\n"
        ));
        return Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank() && l.contains("\t"))
                .map(l -> {
                    String[] f = l.split("\t", 3);
                    String name = f[0].trim();
                    String ver  = f.length > 1 ? f[1].trim() : "";
                    String summ = f.length > 2 ? f[2].trim() : "";
                    return new InstalledPackage(name, name, ver, summ, Source.NATIVE);
                })
                .toList();
    }

    private List<InstalledPackage> fetchApt() throws IOException, InterruptedException {
        // apt-mark showmanual lists only manually installed packages
        String manualOutput = executor.execute(List.of("apt-mark", "showmanual"));
        Set<String> manualPackages = Arrays.stream(manualOutput.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        String detailOutput = executor.execute(List.of(
                "dpkg-query", "-W", "-f=${Package}\t${Version}\t${binary:Summary}\n"
        ));
        return Arrays.stream(detailOutput.split("\n"))
                .filter(l -> !l.isBlank() && l.contains("\t"))
                .map(l -> {
                    String[] f = l.split("\t", 3);
                    String name = f[0].trim();
                    String ver  = f.length > 1 ? f[1].trim() : "";
                    String summ = f.length > 2 ? f[2].trim() : "";
                    return new InstalledPackage(name, name, ver, summ, Source.NATIVE);
                })
                .filter(p -> manualPackages.contains(p.id()))
                .toList();
    }

    private List<InstalledPackage> fetchPacman() throws IOException, InterruptedException {
        // -Qe: explicitly installed, excludes pulled-in dependencies
        String output = executor.execute(List.of("pacman", "-Qe"));
        return Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .map(l -> {
                    String[] f = l.trim().split("\\s+", 2);
                    String name = f[0];
                    String ver  = f.length > 1 ? f[1] : "";
                    return new InstalledPackage(name, name, ver, "", Source.NATIVE);
                })
                .toList();
    }

    private List<InstalledPackage> fetchZypper() throws IOException, InterruptedException {
        // --userinstalled shows only packages the user explicitly installed
        String output = executor.execute(List.of(
                "zypper", "--no-refresh", "packages", "--userinstalled"
        ));
        // Output rows: "i | repo | name | version | arch"
        return Arrays.stream(output.split("\n"))
                .filter(l -> l.startsWith("i ") || l.startsWith("i|"))
                .map(l -> {
                    String[] f = l.split("\\|");
                    if (f.length < 4) return null;
                    String name = f[2].trim();
                    String ver  = f[3].trim();
                    return new InstalledPackage(name, name, ver, "", Source.NATIVE);
                })
                .filter(p -> p != null)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Flatpak fetching
    // -------------------------------------------------------------------------

    private List<InstalledPackage> fetchFlatpakPackages() throws IOException, InterruptedException {
        // --app excludes runtimes; columns: human name, app-id, version
        String output = executor.execute(List.of(
                "flatpak", "list", "--app", "--columns=name,application,version"
        ));
        return Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank() && l.contains("\t"))
                .map(l -> {
                    String[] f = l.split("\t", 3);
                    String name  = f[0].trim();
                    String appId = f.length > 1 ? f[1].trim() : name;
                    String ver   = f.length > 2 ? f[2].trim() : "";
                    return new InstalledPackage(name, appId, ver, "", Source.FLATPAK);
                })
                .toList();
    }

    // -------------------------------------------------------------------------
    // Detection helpers
    // -------------------------------------------------------------------------

    private PackageManager detectNativePackageManager() {
        if (isCommandAvailable("dnf"))    return PackageManager.DNF;
        if (isCommandAvailable("apt"))    return PackageManager.APT;
        if (isCommandAvailable("pacman")) return PackageManager.PACMAN;
        if (isCommandAvailable("zypper")) return PackageManager.ZYPPER;
        return PackageManager.UNKNOWN;
    }

    private boolean isCommandAvailable(String command) {
        return List.of("/usr/bin/", "/bin/", "/usr/local/bin/", "/usr/sbin/")
                .stream()
                .anyMatch(dir -> Files.exists(Path.of(dir + command)));
    }
}
