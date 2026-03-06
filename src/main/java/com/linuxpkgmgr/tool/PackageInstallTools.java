package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SudoService;
import com.linuxpkgmgr.service.SystemPackageService;
import lombok.extern.slf4j.Slf4j;
import com.linuxpkgmgr.tool.IntentRole;
import com.linuxpkgmgr.tool.PkgTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools for installing and removing packages.
 * The agent MUST confirm with the user before calling these tools.
 *
 * Flatpak operations run as the current user; polkit handles privilege
 * elevation internally and may show its own native dialog.
 *
 * Native package manager operations use SudoService, which shows a
 * graphical password popup (zenity or kdialog) when sudo credentials
 * are not already cached.
 */
@Slf4j
@Component
public class PackageInstallTools implements ToolBean {

    private final SystemPackageService packageService;
    private final CommandExecutor executor;
    private final SudoService sudoService;

    public PackageInstallTools(SystemPackageService packageService,
                               CommandExecutor executor,
                               SudoService sudoService) {
        this.packageService = packageService;
        this.executor = executor;
        this.sudoService = sudoService;
    }

    // -------------------------------------------------------------------------
    // Flatpak — polkit handles auth; no explicit sudo needed
    // -------------------------------------------------------------------------

    @PkgTool(name = "install_flatpak", role = IntentRole.END, description = """
            Installs a Flatpak application from Flathub.
            Call this only after the user has explicitly confirmed the installation.
            appId: the Flatpak application ID (e.g. 'org.videolan.VLC').
            This may take several minutes while the package downloads.
            Returns installation output or an error message.
            """)
    public String installFlatpak(String appId) {
        if (!packageService.isFlatpakAvailable()) {
            return "Flatpak is not installed on this system.";
        }
        log.info("Installing Flatpak: {}", appId);
        try {
            String output = executor.executeChecked(List.of("flatpak", "install", "--user", "flathub", appId, "-y"));
            packageService.invalidateCache();
            return "Successfully installed " + appId + ".\n" + output.strip();
        } catch (Exception e) {
            log.error("installFlatpak failed for {}", appId, e);
            return "Failed to install " + appId + ": " + e.getMessage();
        }
    }

    @PkgTool(name = "remove_flatpak", role = IntentRole.END, description = """
            Removes a Flatpak application from the system.
            Call this only after the user has explicitly confirmed the removal.
            appId: the Flatpak application ID (e.g. 'org.videolan.VLC').
            Returns removal output or an error message.
            """)
    public String removeFlatpak(String appId) {
        if (!packageService.isFlatpakAvailable()) {
            return "Flatpak is not installed on this system.";
        }
        log.info("Removing Flatpak: {}", appId);
        try {
            String output = executor.executeChecked(List.of("flatpak", "uninstall", "--user", appId, "-y"));
            packageService.invalidateCache();
            return "Successfully removed " + appId + ".\n" + output.strip();
        } catch (Exception e) {
            log.error("removeFlatpak failed for {}", appId, e);
            return "Failed to remove " + appId + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Native package manager — requires sudo (password popup shown as needed)
    // -------------------------------------------------------------------------

    @PkgTool(name = "install_native_package", role = IntentRole.END, description = """
            Installs a package using the native system package manager (dnf/apt/pacman/zypper).
            Use only when the package is not available as a Flatpak or is a system-level package.
            Call this only after the user has explicitly confirmed the installation.
            Requires sudo/root privileges — a password popup will appear if needed.
            packageName: the native package name (e.g. 'vlc', 'cups').
            Returns installation output or an error message.
            """)
    public String installNativePackage(String packageName) {
        List<String> cmd = nativeInstallCommand(packageName);
        if (cmd == null) return "No supported native package manager found.";
        log.info("Installing native package: {}", packageName);
        try {
            String output = sudoService.runWithSudo(cmd);
            packageService.invalidateCache();
            return "Successfully installed " + packageName + ".\n" + output.strip();
        } catch (Exception e) {
            log.error("installNativePackage failed for {}", packageName, e);
            return "Failed to install " + packageName + ": " + e.getMessage();
        }
    }

    @PkgTool(name = "remove_native_package", role = IntentRole.END, description = """
            Removes a package using the native system package manager (dnf/apt/pacman/zypper).
            Call this only after the user has explicitly confirmed the removal.
            Requires sudo/root privileges — a password popup will appear if needed.
            packageName: the native package name.
            Returns removal output or an error message.
            """)
    public String removeNativePackage(String packageName) {
        List<String> cmd = nativeRemoveCommand(packageName);
        if (cmd == null) return "No supported native package manager found.";
        log.info("Removing native package: {}", packageName);
        try {
            String output = sudoService.runWithSudo(cmd);
            packageService.invalidateCache();
            return "Successfully removed " + packageName + ".\n" + output.strip();
        } catch (Exception e) {
            log.error("removeNativePackage failed for {}", packageName, e);
            return "Failed to remove " + packageName + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> nativeInstallCommand(String pkg) {
        return switch (packageService.getNativePackageManager()) {
            case DNF    -> List.of("dnf",     "install",              "-y",           pkg);
            case APT    -> List.of("apt-get", "install",              "-y",           pkg);
            case PACMAN -> List.of("pacman",  "-S",                   "--noconfirm",  pkg);
            case ZYPPER -> List.of("zypper",  "--non-interactive",    "install",      pkg);
            default     -> null;
        };
    }

    private List<String> nativeRemoveCommand(String pkg) {
        return switch (packageService.getNativePackageManager()) {
            case DNF    -> List.of("dnf",     "remove",           "-y",           pkg);
            case APT    -> List.of("apt-get", "remove",           "-y",           pkg);
            case PACMAN -> List.of("pacman",  "-R",               "--noconfirm",  pkg);
            case ZYPPER -> List.of("zypper",  "--non-interactive","remove",       pkg);
            default     -> null;
        };
    }
}
