package com.linuxpkgmgr.model;

/**
 * Normalised representation of a package,
 * regardless of whether it came from a native PM or Flatpak.
 *
 * @param name      human-readable name (e.g. "VLC media player" or "vlc")
 * @param id        unique identifier — native package name or Flatpak app-id
 * @param version   version string
 * @param summary   one-line description
 * @param source    NATIVE or FLATPAK
 * @param installed YES if currently installed on the system, NO otherwise
 */
public record PackageInfo(
        String name,
        String id,
        String version,
        String summary,
        Source source,
        Installed installed) {

    public enum Source    { NATIVE, FLATPAK }
    public enum Installed { YES, NO }
}
