package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.SystemPackageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Tools for installing and removing packages.
 * The agent MUST confirm with the user before calling these tools.
 */
@Component
public class PackageInstallTools {

    private final SystemPackageService packageService;

    public PackageInstallTools(SystemPackageService packageService) {
        this.packageService = packageService;
    }

    @Tool(description = """
            Installs a Flatpak application from Flathub.
            Call this only after the user has explicitly confirmed the installation.
            appId: the Flatpak application ID (e.g. 'org.videolan.VLC').
            Returns installation output or an error message.
            """)
    public String installFlatpak(String appId) {
        // TODO: implement
        // flatpak install flathub <appId> -y
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Installs a package using the native system package manager (dnf/apt/pacman/zypper).
            Use only when the package is not available as a Flatpak or is a system-level package.
            Call this only after the user has explicitly confirmed the installation.
            Requires sudo/root privileges.
            packageName: the native package name (e.g. 'vlc', 'cups').
            Returns installation output or an error message.
            """)
    public String installNativePackage(String packageName) {
        // TODO: implement
        // sudo dnf install <pkg> -y / sudo apt install <pkg> -y / etc.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Removes a Flatpak application from the system.
            Call this only after the user has explicitly confirmed the removal.
            appId: the Flatpak application ID (e.g. 'org.videolan.VLC').
            Returns removal output or an error message.
            """)
    public String removeFlatpak(String appId) {
        // TODO: implement
        // flatpak uninstall <appId> -y
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Removes a package using the native system package manager (dnf/apt/pacman/zypper).
            Call this only after the user has explicitly confirmed the removal.
            Requires sudo/root privileges.
            packageName: the native package name.
            Returns removal output or an error message.
            """)
    public String removeNativePackage(String packageName) {
        // TODO: implement
        // sudo dnf remove <pkg> -y / sudo apt remove <pkg> -y / etc.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
