package com.linuxpkgmgr.model;

import java.util.List;

/**
 * Parsed representation of a single .desktop file entry.
 *
 * @param appId      basename of the .desktop file without extension
 *                   (e.g. "org.videolan.VLC" or "vlc")
 * @param name       human-readable display name from Name=
 * @param categories list of categories from Categories= (semicolon-separated in file)
 * @param comment    one-line description from Comment=
 * @param source     NATIVE or FLATPAK depending on which directory it was found in
 */
public record DesktopEntry(
        String appId,
        String name,
        List<String> categories,
        String comment,
        PackageInfo.Source source
) {}
