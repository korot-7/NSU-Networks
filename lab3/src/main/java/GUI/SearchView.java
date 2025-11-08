package GUI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SearchView {
    private final VBox root;
    private final Scene scene;
    private final TextField addressField;
    private final Button searchButton;
    private final Label statusLabel;
    private Consumer<String> onSearch;

    public SearchView() {
        Label prompt = new Label("Enter address or place:");
        addressField = new TextField();
        addressField.setPrefWidth(420);

        searchButton = new Button("Search");
        searchButton.setDefaultButton(true);
        statusLabel = new Label();

        HBox top = new HBox(10, prompt, addressField, searchButton);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(12));

        root = new VBox(12, top, statusLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(16));

        scene = new Scene(root);

        searchButton.setOnAction(_ -> acceptSearch());
    }


    public Scene getScene() { return scene; }

    private void acceptSearch() {
        onSearch.accept(addressField.getText());
    }

    public void setOnSearch(Consumer<String> cb) { this.onSearch = cb; }

    public void setBusy(boolean busy, String message) {
        addressField.setDisable(busy);
        searchButton.setDisable(busy);
        statusLabel.setText(message);
    }
}
