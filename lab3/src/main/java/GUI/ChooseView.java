package GUI;

import model.Location;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;


import java.util.List;
import java.util.function.Consumer;


public class ChooseView {
    private final VBox root;
    private final Scene scene;
    private final ListView<Location> listView;
    private final Button selectButton;
    private final Button backButton;
    private Consumer<Location> onSelect;
    private Runnable onBack;


    public ChooseView() {
        Label header = new Label("Choose the correct location:");
        listView = new ListView<>();
        listView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(Location item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label name = new Label(item.getName());
                    Label first = new Label(item.getFirstObject());
                    Label second = new Label(item.getSecondObject());
                    Label state = new Label(item.getState());
                    Label country = new Label(item.getCountry());
                    Label coords = new Label(String.format("(%.6f, %.6f)", item.getLat(), item.getLon()));

                    name.setPrefWidth(150);
                    first.setPrefWidth(120);
                    second.setPrefWidth(120);
                    state.setPrefWidth(150);
                    country.setPrefWidth(120);
                    coords.setPrefWidth(180);

                    HBox row = new HBox(10, name, first, second, state, country, coords);
                    row.setAlignment(Pos.CENTER_LEFT);

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        selectButton = new Button("Select");
        selectButton.setDisable(true);
        backButton = new Button("Back");

        HBox buttons = new HBox(10, backButton, selectButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root = new VBox(10, header, listView, buttons);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.CENTER);
        scene = new Scene(root);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> selectButton.setDisable(newV == null));

        listView.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Location sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null && onSelect != null) onSelect.accept(sel);
            }
        });

        selectButton.setOnAction(e -> {
            Location sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null && onSelect != null) onSelect.accept(sel);
        });
        backButton.setOnAction(e -> { if (onBack != null) onBack.run(); });
    }


    public Scene getScene() { return scene; }

    public void setLocations(List<Location> locations) {
        listView.setItems(FXCollections.observableArrayList(locations));
    }

    public void setOnSelect(Consumer<Location> c) { this.onSelect = c; }

    public void setOnBack(Runnable r) { this.onBack = r; }

}
