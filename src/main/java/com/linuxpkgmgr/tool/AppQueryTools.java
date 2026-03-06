package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.model.DesktopEntry;
import com.linuxpkgmgr.model.PackageInfo;
import com.linuxpkgmgr.service.DesktopFileService;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import com.linuxpkgmgr.tool.IntentRole;
import com.linuxpkgmgr.tool.PkgTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool for listing installed GUI applications filtered by freedesktop.org category.
 * Unlike {@link PackageQueryTools}, this only surfaces apps that appear in the
 * desktop app menu (those with a valid .desktop entry).
 */
@Slf4j
@Component
public class AppQueryTools implements ToolBean {

    /** Maps common user-facing aliases to freedesktop.org Main Category names. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("media",        "AudioVideo"),
            Map.entry("mediaplayer",  "AudioVideo"),
            Map.entry("audio",        "AudioVideo"),
            Map.entry("video",        "AudioVideo"),
            Map.entry("music",        "AudioVideo"),
            Map.entry("dev",          "Development"),
            Map.entry("ide",          "Development"),
            Map.entry("editor",       "Development"),
            Map.entry("coding",       "Development"),
            Map.entry("image",        "Graphics"),
            Map.entry("photo",        "Graphics"),
            Map.entry("drawing",      "Graphics"),
            Map.entry("browser",      "WebBrowser"),
            Map.entry("internet",     "Network"),
            Map.entry("email",        "Email"),
            Map.entry("chat",         "InstantMessaging"),
            Map.entry("productivity", "Office"),
            Map.entry("word",         "Office"),
            Map.entry("spreadsheet",  "Office"),
            Map.entry("games",        "Game"),
            Map.entry("gaming",       "Game"),
            Map.entry("tools",        "Utility"),
            Map.entry("utilities",    "Utility"),
            Map.entry("learning",     "Education"),
            Map.entry("sysadmin",     "System"),
            Map.entry("preferences",  "Settings")
    );

    private final SystemPackageService packageService;
    private final DesktopFileService desktopFileService;

    @Value("${pkg-mgr.list.max-results:50}")
    private int maxResults;

    public AppQueryTools(SystemPackageService packageService, DesktopFileService desktopFileService) {
        this.packageService = packageService;
        this.desktopFileService = desktopFileService;
    }

    @PkgTool(name = "list_installed_apps", role = IntentRole.START, description = """
            Lists installed GUI applications visible in the app menu, optionally filtered by
            category. The category parameter MUST be a single word — resolve the user's natural
            language request to one of these canonical values before calling this tool:
              AudioVideo, Development, Graphics, Network, Office, Game, Utility,
              Education, Science, System, Settings.
            Examples: "media player" → AudioVideo, "web browser" → WebBrowser,
            "text editor" or "IDE" → Development, "image editor" → Graphics,
            "email client" → Email, "chat" → InstantMessaging,
            "games" → Game, "office suite" → Office.
            Leave category blank to list all installed apps.
            """)
    public String listInstalledApps(String category) {
        log.debug("listInstalledApps called — category: '{}'", category);

        // Guard: multi-word input — instruct the LLM to resolve it to a single word
        if (category != null && category.trim().contains(" ")) {
            log.debug("listInstalledApps — multi-word category rejected: '{}'", category);
            return """
                    The category must be a single word. Please map the request to one of:
                    AudioVideo, Development, Graphics, Network, Office, Game, Utility,
                    Education, Science, System, Settings.
                    Then call listInstalledApps again with that single-word category.
                    """;
        }

        String raw      = (category == null) ? "" : category.trim();
        String resolved = raw.isEmpty() ? "" : ALIASES.getOrDefault(raw.toLowerCase(), raw);
        log.debug("listInstalledApps — resolved category: '{}'", resolved.isEmpty() ? "(all)" : resolved);

        // Build id → PackageInfo map for version lookup and Flatpak confirmation
        Map<String, PackageInfo> installedById;
        try {
            installedById = packageService.listInstalled().stream()
                    .collect(Collectors.toMap(
                            p -> p.id().toLowerCase(),
                            p -> p,
                            (a, b) -> a   // keep first on collision
                    ));
        } catch (Exception e) {
            return "Error fetching installed packages: " + e.getMessage();
        }

        List<DesktopEntry> entries = desktopFileService.getDesktopEntries();
        log.debug("Total desktop entries found: {}", entries.size());

        List<AppRow> rows = new ArrayList<>();
        Set<String> seen  = new HashSet<>();

        for (DesktopEntry entry : entries) {
            // Deduplicate by source + appId
            String dedupeKey = entry.source() + ":" + entry.appId().toLowerCase();
            if (!seen.add(dedupeKey)) continue;

            PackageInfo pkg = lookupPackage(entry, installedById);
            if (pkg == null) continue; // Flatpak desktop file with no matching install

            if (!resolved.isEmpty()) {
                boolean categoryMatches = entry.categories().stream()
                        .anyMatch(c -> c.equalsIgnoreCase(resolved));
                if (!categoryMatches) continue;
            }

            rows.add(new AppRow(entry, pkg));
        }

        rows.sort(Comparator.comparing(r -> r.entry().name().toLowerCase()));

        if (rows.isEmpty()) {
            return resolved.isEmpty()
                    ? "No installed GUI applications found."
                    : "No installed applications found in category \"" + resolved + "\".";
        }

        boolean truncated  = rows.size() > maxResults;
        List<AppRow> shown = truncated ? rows.subList(0, maxResults) : rows;

        StringBuilder sb = new StringBuilder();
        sb.append("Installed applications");
        if (!resolved.isEmpty()) sb.append(" [").append(resolved).append("]");
        sb.append(" — ").append(rows.size()).append(" found:\n\n");

        for (AppRow row : shown) {
            sb.append(format(row)).append("\n");
        }

        if (truncated) {
            sb.append("\n(Showing ").append(maxResults).append(" of ").append(rows.size())
              .append(". Specify a category to narrow results.)");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------

    /**
     * Finds the installed {@link PackageInfo} that corresponds to a desktop entry.
     *
     * <ul>
     *   <li>Flatpak: exact app-id match required; null if not found (= not installed).</li>
     *   <li>Native: tries exact basename, then last reverse-domain segment.
     *       Falls back to a synthetic entry for unowned .desktop files.</li>
     * </ul>
     */
    private PackageInfo lookupPackage(DesktopEntry entry, Map<String, PackageInfo> installedById) {
        String id = entry.appId().toLowerCase();

        // Exact match: works for flatpak app-ids and simple native package names
        PackageInfo pkg = installedById.get(id);
        if (pkg != null) return pkg;

        // Last segment of reverse-domain name: org.kde.kate → kate
        int lastDot = id.lastIndexOf('.');
        if (lastDot >= 0) {
            pkg = installedById.get(id.substring(lastDot + 1));
            if (pkg != null) return pkg;
        }

        // Flatpak: no match = not installed → exclude
        if (entry.source() == PackageInfo.Source.FLATPAK) return null;

        // Native: unowned .desktop file (manually placed) → include with no version
        return new PackageInfo(
                entry.name(), entry.appId(), "", entry.comment(),
                PackageInfo.Source.NATIVE, PackageInfo.Installed.YES
        );
    }

    private String format(AppRow row) {
        DesktopEntry entry = row.entry();
        PackageInfo  pkg   = row.pkg();

        String tag   = pkg.source() == PackageInfo.Source.FLATPAK ? "[flatpak]" : "[native ]";
        String label = "%-45s".formatted(entry.name() + " (" + entry.appId() + ")");
        String ver   = pkg.version().isBlank() ? "-" : pkg.version();
        String desc  = entry.comment().isBlank()
                ? String.join(";", entry.categories())
                : entry.comment();

        return "%s  %s  %-22s  —  %s".formatted(tag, label, ver, desc);
    }

    private record AppRow(DesktopEntry entry, PackageInfo pkg) {}
}
