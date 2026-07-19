package br.com.supermidia.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

public final class SuperMidiaApplication extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        loadApplicationFonts();
        FXMLLoader loader = new FXMLLoader(
                SuperMidiaApplication.class.getResource("main-view.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root, 1366, 768);
        scene.getStylesheets().add(
                SuperMidiaApplication.class.getResource("theme.css").toExternalForm());

        stage.setTitle("SuperMídia MIDI Player");
        stage.setMinWidth(1180);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmClose()) {
                event.consume();
            }
        });
        stage.centerOnScreen();
        stage.show();
    }

    private void loadApplicationFonts() {
        Font.loadFont(SuperMidiaApplication.class
                .getResource("fonts/SourceSans3-Regular.ttf").toExternalForm(), 14);
        Font.loadFont(SuperMidiaApplication.class
                .getResource("fonts/SourceSans3-Bold.ttf").toExternalForm(), 14);
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
