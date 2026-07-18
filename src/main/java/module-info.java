module br.com.supermidia {
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;

    exports br.com.supermidia.app;
    exports br.com.supermidia.core;
    exports br.com.supermidia.midi;

    opens br.com.supermidia.app to javafx.fxml;
}
