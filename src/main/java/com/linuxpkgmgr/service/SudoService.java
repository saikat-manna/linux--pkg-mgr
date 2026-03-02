package com.linuxpkgmgr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs commands with sudo privilege elevation.
 *
 * Elevation strategy (in order):
 *   1. Passwordless sudo — sudo cache is warm or NOPASSWD is configured.
 *   2. Graphical password popup — zenity (GTK/GNOME) then kdialog (KDE).
 *   3. Terminal fallback — System.console().readPassword().
 *
 * The password is held in a local String only for the duration of the subprocess
 * launch and is never logged.
 */
@Slf4j
@Service
public class SudoService {

    private static final List<String> POPUP_DIRS = List.of("/usr/bin", "/bin", "/usr/local/bin");

    /**
     * Runs {@code command} with sudo.
     * Throws {@link RuntimeException} if the command exits non-zero or auth is cancelled.
     */
    public String runWithSudo(List<String> command) throws IOException, InterruptedException {
        log.debug("Running with sudo: {}", command);

        if (isSudoCacheWarm()) {
            log.debug("Sudo cache warm — running without password prompt");
            return executeSudo(command, null);
        }

        String password = promptPassword(command.get(0));
        return executeSudo(command, password);
    }

    // -------------------------------------------------------------------------
    // Sudo execution
    // -------------------------------------------------------------------------

    private boolean isSudoCacheWarm() {
        try {
            Process p = new ProcessBuilder("sudo", "-n", "true")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes(); // drain so the process can exit
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String executeSudo(List<String> command, String password)
            throws IOException, InterruptedException {

        List<String> sudoCmd = new ArrayList<>();
        sudoCmd.add("sudo");
        if (password != null) sudoCmd.add("-S"); // read password from stdin
        sudoCmd.add("--");
        sudoCmd.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(sudoCmd).redirectErrorStream(true);
        Process process = pb.start();

        if (password != null) {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write((password + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        log.debug("sudo exit code: {}", exitCode);

        if (exitCode != 0) {
            throw new RuntimeException("Command failed (exit " + exitCode + "): " + output.strip());
        }
        return output;
    }

    // -------------------------------------------------------------------------
    // Password popup
    // -------------------------------------------------------------------------

    /**
     * Prompts for a password using the best available input method.
     * zenity → kdialog → System.console()
     */
    private String promptPassword(String cmdName) throws IOException, InterruptedException {
        String message = "Enter password to run: " + cmdName;

        String zenity = findExecutable("zenity");
        if (zenity != null) {
            log.debug("Using zenity for password prompt");
            Process p = new ProcessBuilder(
                    zenity,
                    "--password",
                    "--title=Authentication Required",
                    "--text=" + message
            ).start();
            String pw = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int rc = p.waitFor();
            if (rc != 0) throw new RuntimeException("Authentication cancelled.");
            if (!pw.isEmpty()) return pw;
        }

        String kdialog = findExecutable("kdialog");
        if (kdialog != null) {
            log.debug("Using kdialog for password prompt");
            Process p = new ProcessBuilder(
                    kdialog,
                    "--password", message,
                    "--title", "Authentication Required"
            ).start();
            String pw = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int rc = p.waitFor();
            if (rc != 0) throw new RuntimeException("Authentication cancelled.");
            if (!pw.isEmpty()) return pw;
        }

        // Console fallback (works in plain terminal sessions)
        java.io.Console console = System.console();
        if (console != null) {
            log.debug("Using console for password prompt");
            char[] pw = console.readPassword("Enter sudo password: ");
            if (pw != null && pw.length > 0) return new String(pw);
        }

        throw new RuntimeException(
                "No password input method available. " +
                "Install zenity or kdialog, or configure passwordless sudo (NOPASSWD).");
    }

    private String findExecutable(String name) {
        return POPUP_DIRS.stream()
                .map(dir -> Path.of(dir, name))
                .filter(Files::isExecutable)
                .map(Path::toString)
                .findFirst()
                .orElse(null);
    }
}
