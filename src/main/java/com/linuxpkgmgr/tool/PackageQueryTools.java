package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.model.InstalledPackage;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools for querying already-installed packages (native + Flatpak).
 */
@Slf4j
@Component
public class PackageQueryTools {

    private final SystemPackageService packageService;

    @Value("${pkg-mgr.list.max-results:50}")
    private int maxResults;

    public PackageQueryTools(SystemPackageService packageService) {
        this.packageService = packageService;
    }

    @Tool(description = """
            Lists user-installed packages on the system (excludes system/dependency packages).
            Searches both native package manager and Flatpak installations.
            filter: optional keyword to narrow results by name or description — pass empty string to list all.
            Results are capped; use a specific filter if there are too many matches.
            """)
    public String listInstalledPackages(String filter) {
        log.debug("listInstalledPackages called — filter: '{}'", filter);

        List<InstalledPackage> all;
        try {
            all = packageService.listInstalled();
        } catch (Exception e) {
            return "Error querying installed packages: " + e.getMessage();
        }

        log.debug("Total user-installed packages fetched: {}", all.size());

        boolean hasFilter = filter != null && !filter.isBlank();
        List<InstalledPackage> matched = hasFilter
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
        List<InstalledPackage> displayed = truncated ? matched.subList(0, maxResults) : matched;

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matched.size()).append(" installed package(s)");
        if (hasFilter) sb.append(" matching \"").append(filter).append("\"");
        sb.append(":\n\n");

        for (InstalledPackage pkg : displayed) {
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
            installed size, and whether it is from Flatpak or a native repo.
            packageName: exact native package name or Flatpak application ID (e.g. org.videolan.VLC).
            """)
    public String getPackageInfo(String packageName) {
        // TODO: implement
        // Native: dnf info <pkg> / apt show <pkg> / pacman -Qi <pkg> / zypper info <pkg>
        // Flatpak: flatpak info <app-id>
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // -------------------------------------------------------------------------

    private boolean matches(InstalledPackage pkg, String keyword) {
        return pkg.name().toLowerCase().contains(keyword)
                || pkg.id().toLowerCase().contains(keyword)
                || pkg.summary().toLowerCase().contains(keyword);
    }

    private String format(InstalledPackage pkg) {
        String tag = pkg.source() == InstalledPackage.Source.FLATPAK ? "[flatpak]" : "[native] ";
        String label = pkg.source() == InstalledPackage.Source.FLATPAK
                ? pkg.name() + " (" + pkg.id() + ")"
                : pkg.id();
        String summary = pkg.summary().isBlank() ? "" : " — " + pkg.summary();
        return "%s  %-45s %s%s".formatted(tag, label, pkg.version(), summary);
    }
}
