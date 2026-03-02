package com.linuxpkgmgr.ui;

import com.linuxpkgmgr.cli.RoutingChatClient;
import com.linuxpkgmgr.service.ShellOutputBus;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;

@Component
public class ChatController {

    private final RoutingChatClient routingClient;
    private final ShellOutputBus shellOutputBus;
    private final String sessionId;

    public ChatController(RoutingChatClient routingClient,
                          ShellOutputBus shellOutputBus,
                          @Value("${app.session-id}") String sessionId) {
        this.routingClient = routingClient;
        this.shellOutputBus = shellOutputBus;
        this.sessionId = sessionId;
    }

    public void buildScene(Stage stage) {
        // ── Header ────────────────────────────────────────────────────────────
        Label titleLabel = new Label("Linux Package Manager");
        titleLabel.getStyleClass().add("title-label");

        Label modelLabel = new Label("[local]");
        modelLabel.getStyleClass().add("model-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(titleLabel, spacer, modelLabel);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);

        // ── Chat area ─────────────────────────────────────────────────────────
        VBox chatBox = new VBox();
        chatBox.getStyleClass().add("chat-box");

        ScrollPane scrollPane = new ScrollPane(chatBox);
        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // ── Input bar ─────────────────────────────────────────────────────────
        TextField inputField = new TextField();
        inputField.getStyleClass().add("input-field");
        inputField.setPromptText("Ask me anything about your packages…");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("send-button");

        HBox inputBar = new HBox(inputField, sendButton);
        inputBar.getStyleClass().add("input-bar");
        inputBar.setAlignment(Pos.CENTER);

        // ── Shell output pane ─────────────────────────────────────────────────
        TextArea shellOutput = new TextArea();
        shellOutput.getStyleClass().add("shell-output");
        shellOutput.setEditable(false);
        shellOutput.setWrapText(false);
        shellOutput.setPrefHeight(150);
        shellOutput.setVisible(false);
        shellOutput.setManaged(false);

        Label toggleLabel = new Label("▶  Shell Output");
        toggleLabel.getStyleClass().add("shell-toggle");

        HBox shellHeader = new HBox(toggleLabel);
        shellHeader.getStyleClass().add("shell-header");
        shellHeader.setAlignment(Pos.CENTER_LEFT);
        shellHeader.setOnMouseClicked(e -> {
            boolean expanded = shellOutput.isVisible();
            shellOutput.setVisible(!expanded);
            shellOutput.setManaged(!expanded);
            toggleLabel.setText(expanded ? "▶  Shell Output" : "▼  Shell Output");
        });

        VBox bottomPane = new VBox(inputBar, shellHeader, shellOutput);

        // ── Shell bus → TextArea ──────────────────────────────────────────────
        shellOutputBus.addListener(line ->
                Platform.runLater(() -> shellOutput.appendText(line + "\n")));

        // ── Layout ────────────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(scrollPane);
        root.setBottom(bottomPane);

        // ── Send action ───────────────────────────────────────────────────────
        Runnable sendAction = () -> {
            String text = inputField.getText().trim();
            if (text.isBlank()) return;
            inputField.clear();

            chatBox.getChildren().add(userBubble(text));
            Node thinking = aiBubble("thinking…");
            chatBox.getChildren().add(thinking);
            scrollToBottom(scrollPane);

            new Thread(() -> {
                String response;
                try {
                    response = routingClient.chat(text, sessionId);
                } catch (Exception e) {
                    response = "[Error] " + e.getMessage();
                }
                final String finalResponse = response;
                Platform.runLater(() -> {
                    chatBox.getChildren().remove(thinking);
                    chatBox.getChildren().add(aiBubble(finalResponse));
                    scrollToBottom(scrollPane);
                });
            }).start();
        };

        sendButton.setOnAction(e -> sendAction.run());
        inputField.setOnAction(e -> sendAction.run());

        // ── Scene ─────────────────────────────────────────────────────────────
        Scene scene = new Scene(root, 800, 580);
        URL css = getClass().getResource("chat.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Linux Package Manager");
        stage.setMinWidth(500);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();
    }

    private Node userBubble(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bubble-user");
        label.setWrapText(true);
        label.setMaxWidth(560);

        HBox row = new HBox(label);
        row.setAlignment(Pos.CENTER_RIGHT);
        HBox.setMargin(label, new Insets(0, 4, 0, 80));
        return row;
    }

    private Node aiBubble(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bubble-ai");
        label.setWrapText(true);
        label.setMaxWidth(560);

        HBox row = new HBox(label);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setMargin(label, new Insets(0, 80, 0, 4));
        return row;
    }

    private void scrollToBottom(ScrollPane pane) {
        Platform.runLater(() -> pane.setVvalue(1.0));
    }
}
