package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.SystemPackageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Tools for updating packages — individual packages or full system upgrade.
 * The agent MUST confirm with the user before calling mutating tools.
 */
@Component
public class PackageUpdateTools {

    private final SystemPackageService packageService;

    public PackageUpdateTools(SystemPackageService packageService) {
        this.packageService = packageService;
    }

    @Tool(description = """
            Checks for available updates for all installed Flatpak applications.
            Safe to call without confirmation — read-only check.
            Returns a list of apps with pending updates and their new versions.
            """)
    public String checkFlatpakUpdates() {
        // TODO: implement
        // flatpak remote-ls --updates --columns=name,application,version
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Checks for available updates via the native system package manager (dnf/apt/pacman/zypper).
            Safe to call without confirmation — read-only check.
            Returns a list of packages with pending updates.
            """)
    public String checkNativeUpdates() {
        // TODO: implement
        // dnf check-update / apt list --upgradable / pacman -Qu / zypper list-updates
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Updates a specific Flatpak application to the latest version.
            Call this only after the user has explicitly confirmed the update.
            Pass 'ALL' as appId to update all installed Flatpak apps.
            appId: the Flatpak application ID, or 'ALL'.
            """)
    public String updateFlatpak(String appId) {
        // TODO: implement
        // flatpak update <appId> -y  /  flatpak update -y
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Tool(description = """
            Updates a specific native package, or performs a full system upgrade.
            Call this only after the user has explicitly confirmed the update.
            Requires sudo/root privileges.
            Pass 'ALL' as packageName to upgrade all native packages.
            packageName: the native package name, or 'ALL'.
            """)
    public String updateNativePackage(String packageName) {
        // TODO: implement
        // sudo dnf update <pkg> -y / sudo apt upgrade <pkg> -y / etc.
        // For ALL: sudo dnf upgrade -y / sudo apt upgrade -y / etc.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
