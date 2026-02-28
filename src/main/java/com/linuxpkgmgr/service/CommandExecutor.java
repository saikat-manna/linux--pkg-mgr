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

    /**
     * Executes a command and returns combined stdout + stderr as a single string.
     *
     * @param command the command and its arguments
     * @return raw output from the process
     * @throws IOException          if the process cannot be started
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public String execute(List<String> command) throws IOException, InterruptedException {
        log.debug("Executing: {}", command);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        log.debug("Exit code: {}, output length: {} chars", exitCode, output.length());
        return output;
    }
}
