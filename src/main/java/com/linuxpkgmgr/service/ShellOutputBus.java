package com.linuxpkgmgr.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Pub/sub bridge between CommandExecutor (service layer) and the JavaFX shell pane.
 *
 * CommandExecutor emits one line at a time as the subprocess streams output.
 * ChatController registers a listener that appends each line to the TextArea
 * via Platform.runLater().
 */
@Component
public class ShellOutputBus {

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    /** Called from background threads — must be thread-safe. */
    public void emit(String line) {
        for (Consumer<String> l : listeners) l.accept(line);
    }
}
