package br.com.supermidia.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class SuperMidiaApplication extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                SuperMidiaApplication.class.getResource("main-view.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(
                SuperMidiaApplication.class.getResource("theme.css").toExternalForm());

        stage.setTitle("SuperMidia Live");
        stage.setMinWidth(1060);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmClose()) {
                event.consume();
            }
        });
        stage.centerOnScreen();
        stage.show();
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
