package com.linuxpkgmgr.ui;

import com.linuxpkgmgr.LinuxPkgMgrApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

public class ChatApp extends Application {

    private ConfigurableApplicationContext context;

    /** Called on the JavaFX launcher thread before start(). */
    @Override
    public void init() {
        String sessionId = getParameters().getNamed().getOrDefault(
                "session-id", System.getProperty("APP_SESSION_ID", ""));

        SpringApplication app = new SpringApplication(LinuxPkgMgrApplication.class);
        app.setAdditionalProfiles("gui");
        app.setDefaultProperties(Map.of("app.session-id", sessionId));
        context = app.run();
    }

    @Override
    public void start(Stage primaryStage) {
        context.getBean(ChatController.class).buildScene(primaryStage);
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
