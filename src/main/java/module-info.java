module br.com.supermidia {
    requires javafx.controls;
    requires javafx.fxml;

    exports br.com.supermidia.app;
    exports br.com.supermidia.core;

    opens br.com.supermidia.app to javafx.fxml;
}
