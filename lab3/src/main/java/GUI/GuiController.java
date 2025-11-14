package GUI;

import javafx.application.Platform;
import workApi.baseApi.FindLocationAPI;
import workApi.WorkAPI;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.Location;
import javafx.stage.Stage;
import java.nio.file.Path;
import java.nio.file.Paths;


public class GuiController {
    private final Stage stage;

    private final double WIDTH = 1000;
    private final double HEIGHT = 700;

    private final SearchView searchView = new SearchView();
    private final ChooseView chooseView = new ChooseView();
    private final ResultView resultView = new ResultView();

    private final String apiKeyGraphHopper;
    private final String apiKeyOpenWeather;
    private final String apiKey2GIS;

    public GuiController(Stage stage) {
        this.stage = stage;

        Map<String, String> env = loadEnv(Paths.get(".env"));
        apiKeyGraphHopper = env.get("GRAPHHOPPER_API_KEY");
        apiKeyOpenWeather = env.get("OPENWEATHER_API_KEY");
        apiKey2GIS = env.get("2GIS_API_KEY");

        searchView.setOnSearch(query -> doSearch(query));
        chooseView.setOnBack(() -> stage.setScene(searchView.getScene()));
        chooseView.setOnSelect(location -> openResultFor(location));
        resultView.setOnBack(() -> stage.setScene(chooseView.getScene()));

        stage.setTitle("Korotkov lab3");
        stage.setWidth(WIDTH);
        stage.setHeight(HEIGHT);
    }


    public void showSearchView() {
        stage.setScene(searchView.getScene());
        stage.show();
    }


    private void doSearch(String query) {
        searchView.setBusy(true, "Searching...");
        FindLocationAPI.findLocationsAsync(query, apiKeyGraphHopper)
                .thenAccept(list -> Platform.runLater(() -> {
                    searchView.setBusy(false, "");
                    chooseView.setLocations(list);
                    stage.setScene(chooseView.getScene());
                }));
    }


    private void openResultFor(Location location) {
        resultView.showLoading(location);
        stage.setScene(resultView.getScene());
        WorkAPI.placeInfoAsync(location, apiKeyOpenWeather, apiKey2GIS)
                .thenAccept(finalObject -> Platform.runLater(() ->
                        resultView.showResult(finalObject)
                ));
    }


    private Map<String, String> loadEnv(Path path) {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(path)) return map;
        try {
            List<String> lines = Files.readAllLines(path);
            for (String raw : lines) {
                if (raw.isEmpty()) continue;

                int equal = raw.indexOf('=');
                if (equal <= 0) continue;

                String key = raw.substring(0, equal).trim();
                String value = raw.substring(equal + 1).trim();
                map.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("Failed to read .env: " + e.getMessage());
        }
        return map;
    }
}