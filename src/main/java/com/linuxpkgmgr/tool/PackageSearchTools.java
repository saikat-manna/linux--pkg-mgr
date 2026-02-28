package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.SystemPackageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Tools for searching available packages in repositories and Flathub.
 * Search order: Flathub first, then native repo.
 */
@Component
public class PackageSearchTools {

    private final SystemPackageService packageService;

    public PackageSearchTools(SystemPackageService packageService) {
        this.packageService = packageService;
    }

    @Tool(description = """
            Searches Flathub for Flatpak applications NOT YET INSTALLED on the system.
            Use this ONLY when the user wants to discover or install new packages.
            Do NOT use this to list or query already-installed packages — use listInstalledPackages for that.
            Use this before searchNativeRepo; check Flathub first.
            query: keyword, app name, or description term (e.g. 'media player', 'vlc', 'scanner').
            Returns application name, ID, description, and version.
            """)
    public String searchFlathub(String query) {
        // TODO: implement
        // flatpak search <query> --columns=name,application,description,version
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Searches the native system repository (dnf/apt/pacman/zypper) for packages NOT YET INSTALLED.
            Use this ONLY when the user wants to discover or install new packages from the native repo.
            Do NOT use this to list or query already-installed packages — use listInstalledPackages for that.
            Use this after searchFlathub when the package is not on Flathub, or for system-level packages
            (drivers, libraries, CLI tools) that should not be Flatpaks.
            query: keyword or package name.
            """)
    public String searchNativeRepo(String query) {
        // TODO: implement
        // dnf search <query> / apt search <query> / pacman -Ss <query> / zypper se <query>
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
