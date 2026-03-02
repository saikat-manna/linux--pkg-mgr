package com.linuxpkgmgr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;


@SpringBootApplication
public class LinuxPkgMgrApplication {

    public static void main(String[] args) throws IOException {
        String sessionId = UUID.randomUUID().toString();

        // Must be a system property — Logback reads it before Spring context starts
        Path logDir = Path.of("temp/logs");
        Files.createDirectories(logDir);
        System.setProperty("APP_SESSION_ID", sessionId);

        if (Arrays.asList(args).contains("--dev")) {
            Path logFile = logDir.resolve("session-" + sessionId + ".log");
            Files.createFile(logFile); // create now so tail -f has a file to open
            launchLogTail(logFile.toString(), ProcessHandle.current().pid());
        }

        SpringApplication app = new SpringApplication(LinuxPkgMgrApplication.class);
        app.setDefaultProperties(Map.of("app.session-id", sessionId));
        app.run(args);
    }

    /**
     * Opens a secondary terminal window tailing the session log file.
     * Uses {@code tail --pid} so the window closes automatically when the JVM exits.
     * Tries common terminal emulators in order of preference.
     */
    private static void launchLogTail(String logFile, long parentPid) {
        String tailCmd = "tail --pid=" + parentPid + " -f " + logFile;
        String[][] candidates = {
            {"konsole",        "-e", "tail", "--pid=" + parentPid, "-f", logFile},
            {"gnome-terminal", "--",  "tail", "--pid=" + parentPid, "-f", logFile},
            {"xfce4-terminal", "-e", tailCmd},
            {"xterm",          "-e", tailCmd},
        };

        for (String[] cmd : candidates) {
            try {
                Process check = new ProcessBuilder("which", cmd[0])
                        .redirectErrorStream(true).start();
                if (check.waitFor() != 0) continue;

                new ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start();
                System.out.println("[dev] Log tail opened in " + cmd[0] + " → " + logFile);
                return;
            } catch (Exception e) {
                System.err.println("[dev] Failed to launch " + cmd[0] + ": " + e.getMessage());
            }
        }

        System.err.println("[dev] No terminal emulator found — run manually: tail -f " + logFile);
    }
}
