package com.linuxpkgmgr.service;

import com.linuxpkgmgr.model.DesktopEntry;
import com.linuxpkgmgr.model.PackageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Scans .desktop files from standard freedesktop.org locations and parses them
 * into {@link DesktopEntry} records.
 *
 * Only entries with Type=Application and NoDisplay != true are returned,
 * matching what desktop environments show in app menus.
 */
@Slf4j
@Service
public class DesktopFileService {

    private static final List<Path> FLATPAK_DIRS = List.of(
            Path.of("/var/lib/flatpak/exports/share/applications"),
            Path.of(System.getProperty("user.home"), ".local/share/flatpak/exports/share/applications")
    );

    private static final List<Path> NATIVE_DIRS = List.of(
            Path.of("/usr/share/applications"),
            Path.of("/usr/local/share/applications"),
            Path.of(System.getProperty("user.home"), ".local/share/applications")
    );

    public List<DesktopEntry> getDesktopEntries() {
        List<DesktopEntry> entries = new ArrayList<>();
        for (Path dir : FLATPAK_DIRS) {
            entries.addAll(scanDir(dir, PackageInfo.Source.FLATPAK));
        }
        for (Path dir : NATIVE_DIRS) {
            entries.addAll(scanDir(dir, PackageInfo.Source.NATIVE));
        }
        log.debug("Found {} desktop entries", entries.size());
        return entries;
    }

    // -------------------------------------------------------------------------

    private List<DesktopEntry> scanDir(Path dir, PackageInfo.Source source) {
        if (!Files.isDirectory(dir)) return List.of();
        log.debug("Scanning {} desktop files in: {}", source, dir);
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".desktop"))
                    .map(p -> parseDesktopFile(p, source))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to scan {}: {}", dir, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses only the [Desktop Entry] section of a .desktop file.
     * Returns null if the entry should be excluded (wrong Type or NoDisplay=true).
     */
    private DesktopEntry parseDesktopFile(Path path, PackageInfo.Source source) {
        try {
            Map<String, String> fields = new HashMap<>();
            boolean inDesktopEntry = false;

            for (String raw : Files.readAllLines(path)) {
                String line = raw.trim();
                if (line.equals("[Desktop Entry]")) {
                    inDesktopEntry = true;
                    continue;
                }
                if (line.startsWith("[") && inDesktopEntry) break; // left [Desktop Entry] section
                if (!inDesktopEntry || line.startsWith("#") || line.isBlank()) continue;

                int eq = line.indexOf('=');
                if (eq > 0) {
                    // putIfAbsent so the first occurrence wins (spec compliant)
                    fields.putIfAbsent(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }

            if (!"Application".equals(fields.get("Type"))) return null;
            if ("true".equalsIgnoreCase(fields.get("NoDisplay"))) return null;

            String filename = path.getFileName().toString();
            String appId = filename.substring(0, filename.length() - ".desktop".length());

            String name       = fields.getOrDefault("Name", appId);
            String comment    = fields.getOrDefault("Comment", "");
            String catsRaw    = fields.getOrDefault("Categories", "");
            List<String> cats = Arrays.stream(catsRaw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            return new DesktopEntry(appId, name, cats, comment, source);

        } catch (Exception e) {
            log.debug("Failed to parse {}: {}", path, e.getMessage());
            return null;
        }
    }
}
