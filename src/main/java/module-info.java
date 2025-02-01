module org.markproject.bills {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    opens org.markproject.bills to javafx.fxml;
    exports org.markproject.bills;
}
//java --module-path /Users/romsa/DevKit/JavaSet/javafx-sdk-23.0.2/lib --add-modules javafx.controls,javafx.fxml -jar target/utility-bills-1.0-SNAPSHOT-jar-with-dependencies.jar