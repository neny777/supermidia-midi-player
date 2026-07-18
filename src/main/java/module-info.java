module br.com.supermidia {
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;

    exports br.com.supermidia.app;
    exports br.com.supermidia.core;
    exports br.com.supermidia.midi;
    exports br.com.supermidia.lyrics;
    exports br.com.supermidia.mixer;
    exports br.com.supermidia.playlist;

    opens br.com.supermidia.app to javafx.fxml;
}
