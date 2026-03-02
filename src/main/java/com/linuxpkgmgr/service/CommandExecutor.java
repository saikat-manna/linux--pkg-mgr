package com.linuxpkgmgr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Runs shell commands as subprocesses and returns their output.
 * Extracted as a dedicated service so all tool and service classes
 * share one place for process execution and logging.
 *
 * Commands requiring privilege elevation (sudo) must be constructed by the caller.
 */
@Slf4j
@Service
public class CommandExecutor {

    private record Result(String output, int exitCode) {}

    /**
     * Executes a command and returns combined stdout + stderr as a single string.
     * Does not throw on non-zero exit — use {@link #executeChecked} if you need that.
     */
    public String execute(List<String> command) throws IOException, InterruptedException {
        return run(command).output;
    }

    /**
     * Like {@link #execute} but throws {@link RuntimeException} if the process exits non-zero.
     */
    public String executeChecked(List<String> command) throws IOException, InterruptedException {
        Result result = run(command);
        if (result.exitCode != 0) {
            throw new RuntimeException(
                    "Command failed (exit " + result.exitCode + "): " + result.output.strip());
        }
        return result.output;
    }

    private Result run(List<String> command) throws IOException, InterruptedException {
        log.debug("Executing: {}", command);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        log.debug("Exit code: {}, output length: {} chars", exitCode, output.length());
        return new Result(output, exitCode);
    }
}
