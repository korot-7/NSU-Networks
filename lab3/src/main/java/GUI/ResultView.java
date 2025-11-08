package GUI;

import model.ClosestObject;
import model.FinalObject;
import model.ObjectWithDescription;
import model.Weather;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultView {
    private final VBox root;
    private final Scene scene;
    private final Label locationLabel;
    private final Label weatherLabel;
    private final ListView<ClosestObject> placesListView;
    private final TextArea descriptionArea;
    private final Button backButton;


    private final Map<String, String> descriptionsMap = new HashMap<>();
    private Runnable onBack;

    public ResultView() {
        locationLabel = new Label("Location:");
        locationLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        weatherLabel = new Label("Weather: ");
        weatherLabel.setWrapText(true);
        weatherLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 600;");

        VBox weatherCard = new VBox(8);
        weatherCard.setPadding(new Insets(12));
        weatherCard.setStyle("-fx-background-color: linear-gradient(#ffffff, #eef6ff); -fx-border-color: #cbd7e6; -fx-border-radius: 8; -fx-background-radius: 8;");
        weatherCard.getChildren().addAll(weatherLabel);

        HBox header = new HBox(12);
        header.setPadding(new Insets(12));
        header.getChildren().addAll(locationLabel, weatherCard);
        HBox.setHgrow(weatherCard, Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        placesListView = new ListView<>();
        placesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ClosestObject item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getName() + " — " + item.getType());
            }
        });
        Label noPlacesLabel = new Label("Нет мест поблизости");
        noPlacesLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic;");
        placesListView.setPlaceholder(noPlacesLabel);
        placesListView.setPrefWidth(380);

        VBox leftColumn = new VBox(6, new Label("Nearby places:"), placesListView);
        leftColumn.setPadding(new Insets(8));
        leftColumn.setPrefWidth(380);
        VBox.setVgrow(placesListView, Priority.ALWAYS);

        descriptionArea = new TextArea();
        descriptionArea.setWrapText(true);
        descriptionArea.setEditable(false);
        descriptionArea.setPrefRowCount(12);
        descriptionArea.setMinWidth(320);

        ScrollPane descScroll = new ScrollPane(descriptionArea);
        descScroll.setFitToWidth(true);
        descScroll.setFitToHeight(true);
        descScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        descScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox descCard = new VBox(6, new Label("Description:"), descScroll);
        descCard.setPadding(new Insets(12));
        descCard.setStyle("-fx-background-color: linear-gradient(#ffffff, #fbfcff); -fx-border-color: #e0e7f0; -fx-border-radius: 8; -fx-background-radius: 8;");
        VBox.setVgrow(descScroll, Priority.ALWAYS);

        HBox center = new HBox(12, leftColumn, descCard);
        center.setPadding(new Insets(12));
        HBox.setHgrow(descCard, Priority.ALWAYS);

        backButton = new Button("Back");
        HBox bottom = new HBox(10, backButton);
        bottom.setPadding(new Insets(8));
        bottom.setAlignment(Pos.CENTER_RIGHT);

        root = new VBox(12, header, center, bottom);
        root.setPadding(new Insets(10));
        scene = new Scene(root);

        placesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) descriptionArea.clear();
            else {
                String desc = descriptionsMap.get(newV.getName());
                descriptionArea.setText(desc);
                descriptionArea.positionCaret(0);
                descScroll.setVvalue(0);
            }
        });

        backButton.setOnAction(e -> { if (onBack != null) onBack.run(); });
    }


    public Scene getScene() { return scene; }


    public void setOnBack(Runnable r) { this.onBack = r; }


    public void showLoading(model.Location location) {
        descriptionsMap.clear();
        placesListView.setItems(FXCollections.emptyObservableList());
        descriptionArea.clear();
        locationLabel.setText(location.getName());
        weatherLabel.setText("Weather: loading...");
    }


    public void showResult(FinalObject finalObject) {
        Weather w = finalObject.getWeather();
            weatherLabel.setText(String.format("%s — %.2f°C (feels like %.2f°C), Humidity: %d%%, Pressure: %.2f mm",
                    w.getMainWeatherDescription(), w.getTemp(), w.getFeelsLike(), w.getHumidity(), w.getPressure()));

        List<ObjectWithDescription> places = finalObject.getPlacesWithDescription();
        if (places != null && !places.isEmpty()) {
            ObservableList<ClosestObject> items = FXCollections.observableArrayList();
            for (ObjectWithDescription o : places) {
                ClosestObject c = o.getClosestObject();
                items.add(c);
                descriptionsMap.put(c.getName(), o.getDescription());
            }
            placesListView.setItems(items);
        } else {
            placesListView.setItems(FXCollections.emptyObservableList());
            descriptionArea.clear();
        }
    }
}