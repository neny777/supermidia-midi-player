package br.com.supermidia.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.prefs.Preferences;

public final class SuperMidiaApplication extends Application {
    private static final String WINDOW_X_KEY = "windowX";
    private static final String WINDOW_Y_KEY = "windowY";
    private static final String WINDOW_WIDTH_KEY = "windowWidth";
    private static final String WINDOW_HEIGHT_KEY = "windowHeight";
    private static final String WINDOW_MAXIMIZED_KEY = "windowMaximized";
    private static final double DEFAULT_WIDTH = 1366;
    private static final double DEFAULT_HEIGHT = 768;

    private final Preferences preferences =
            Preferences.userNodeForPackage(SuperMidiaApplication.class);
    private MainController controller;
    private Stage primaryStage;

    // Tamanho e posição da janela restaurada (não maximizada). Guardados à parte
    // porque, com a janela maximizada, o Stage informa as medidas da tela cheia —
    // salvar aquilo faria a janela nunca mais voltar ao tamanho que o usuário usava.
    private double restoredX = Double.NaN;
    private double restoredY = Double.NaN;
    private double restoredWidth = DEFAULT_WIDTH;
    private double restoredHeight = DEFAULT_HEIGHT;

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

        var iconUrl = SuperMidiaApplication.class.getResource("supermidia-logo.png");
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
        stage.setTitle("SuperMídia MIDI Player");
        stage.setMinWidth(1180);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmClose()) {
                event.consume();
            }
        });

        primaryStage = stage;
        restoreWindowBounds(stage);
        trackRestoredBounds(stage);
        stage.show();

        // Depois do show: abrir um arquivo mexe na interface, e a janela ja esta montada.
        controller.openAtStartup(getParameters().getUnnamed());
        SingleInstance.listen(parameter -> Platform.runLater(() -> {
            controller.openFromOtherInstance(parameter);
            bringToFront(stage);
        }));
    }

    /**
     * Traz a janela para a frente quando outra cópia entrega um arquivo.
     *
     * <p>Sem isto o arquivo abriria numa janela atrás das outras, e pareceria que o
     * clique não fez nada.</p>
     */
    private void bringToFront(Stage stage) {
        if (stage.isIconified()) {
            stage.setIconified(false);
        }
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * Devolve a janela ao tamanho, posição e estado da última sessão.
     *
     * <p>A posição só é aceita se ainda cair sobre alguma tela ligada agora: quem
     * usa o player em apresentação troca de monitor e projetor, e uma coordenada
     * salva num monitor ausente abriria a janela fora do campo de visão.</p>
     */
    private void restoreWindowBounds(Stage stage) {
        double width = readDouble(WINDOW_WIDTH_KEY, DEFAULT_WIDTH);
        double height = readDouble(WINDOW_HEIGHT_KEY, DEFAULT_HEIGHT);
        double x = readDouble(WINDOW_X_KEY, Double.NaN);
        double y = readDouble(WINDOW_Y_KEY, Double.NaN);

        if (width > 0 && height > 0) {
            stage.setWidth(width);
            stage.setHeight(height);
            restoredWidth = width;
            restoredHeight = height;
        }

        if (!Double.isNaN(x) && !Double.isNaN(y)
                && !Screen.getScreensForRectangle(x, y, width, height).isEmpty()) {
            stage.setX(x);
            stage.setY(y);
            restoredX = x;
            restoredY = y;
        } else {
            stage.centerOnScreen();
        }

        if (readBoolean(WINDOW_MAXIMIZED_KEY)) {
            stage.setMaximized(true);
        }
    }

    /** Registra as medidas apenas enquanto a janela não está maximizada. */
    private void trackRestoredBounds(Stage stage) {
        stage.widthProperty().addListener((ignored, oldValue, newValue) -> {
            if (!stage.isMaximized()) {
                restoredWidth = newValue.doubleValue();
            }
        });
        stage.heightProperty().addListener((ignored, oldValue, newValue) -> {
            if (!stage.isMaximized()) {
                restoredHeight = newValue.doubleValue();
            }
        });
        stage.xProperty().addListener((ignored, oldValue, newValue) -> {
            if (!stage.isMaximized()) {
                restoredX = newValue.doubleValue();
            }
        });
        stage.yProperty().addListener((ignored, oldValue, newValue) -> {
            if (!stage.isMaximized()) {
                restoredY = newValue.doubleValue();
            }
        });
    }

    private void saveWindowBounds() {
        if (primaryStage == null) {
            return;
        }
        try {
            preferences.putBoolean(WINDOW_MAXIMIZED_KEY, primaryStage.isMaximized());
            preferences.putDouble(WINDOW_WIDTH_KEY, restoredWidth);
            preferences.putDouble(WINDOW_HEIGHT_KEY, restoredHeight);
            if (!Double.isNaN(restoredX) && !Double.isNaN(restoredY)) {
                preferences.putDouble(WINDOW_X_KEY, restoredX);
                preferences.putDouble(WINDOW_Y_KEY, restoredY);
            }
        } catch (SecurityException ignored) {
            // A preferência é opcional; o player continua funcionando sem persistência.
        }
    }

    private double readDouble(String key, double fallback) {
        try {
            return preferences.getDouble(key, fallback);
        } catch (SecurityException exception) {
            return fallback;
        }
    }

    private boolean readBoolean(String key) {
        try {
            return preferences.getBoolean(key, false);
        } catch (SecurityException exception) {
            return false;
        }
    }

    private void loadApplicationFonts() {
        Font.loadFont(SuperMidiaApplication.class
                .getResource("fonts/SourceSans3-Regular.ttf").toExternalForm(), 14);
        Font.loadFont(SuperMidiaApplication.class
                .getResource("fonts/SourceSans3-Bold.ttf").toExternalForm(), 14);
    }

    @Override
    public void stop() {
        SingleInstance.release();
        saveWindowBounds();
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        // Uma cópia só: duas disputam o dispositivo de saída e a segunda fica muda.
        // Se a entrega falhar, abrir duas janelas incomoda menos do que não abrir nenhuma.
        if (!SingleInstance.acquire() && SingleInstance.handOff(args)) {
            // Encerrar na marra, e não apenas voltar daqui. O lançador do Java sobe a
            // thread de eventos do JavaFX antes de chamar este main; sem launch(), ela
            // fica girando num laço vazio e o processo nunca morre — um fantasma sem
            // janela, segurando porta e memória.
            System.exit(0);
        }
        launch(args);
    }
}
