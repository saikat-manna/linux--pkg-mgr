package com.linuxpkgmgr.model;

/**
 * Normalised representation of an installed package,
 * regardless of whether it came from a native PM or Flatpak.
 *
 * @param name    human-readable name (e.g. "VLC media player" or "vlc")
 * @param id      unique identifier — native package name or Flatpak app-id
 * @param version installed version string
 * @param summary one-line description
 * @param source  NATIVE or FLATPAK
 */
public record InstalledPackage(
        String name,
        String id,
        String version,
        String summary,
        Source source) {

    public enum Source { NATIVE, FLATPAK }
}
