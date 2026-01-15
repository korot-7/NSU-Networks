package lab4.snake.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lab4.snake.model.GameAnnouncement;
import lab4.snake.network.controller.NetworkManager;

import java.util.List;
import java.util.concurrent.*;

public class MainMenuController {
    @FXML private TableView<GameAnnouncementRow> gamesTable;
    @FXML private TableColumn<GameAnnouncementRow, String> nameColumn;
    @FXML private TableColumn<GameAnnouncementRow, String> sizeColumn;
    @FXML private TableColumn<GameAnnouncementRow, Integer> playersColumn;
    @FXML private TableColumn<GameAnnouncementRow, String> canJoinColumn;

    @FXML private TextField playerNameField;
    @FXML private Button joinButton;
    @FXML private Button viewButton;

    private AppController app;
    private NetworkManager networkManager;
    private final ObservableList<GameAnnouncementRow> gamesList = FXCollections.observableArrayList();

    private ScheduledExecutorService discoveryExecutor;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        playersColumn.setCellValueFactory(new PropertyValueFactory<>("players"));
        canJoinColumn.setCellValueFactory(new PropertyValueFactory<>("canJoin"));

        gamesTable.setItems(gamesList);

        gamesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        joinButton.setDisable(!newVal.canJoinBoolean());
                        viewButton.setDisable(false);
                    } else {
                        joinButton.setDisable(true);
                        viewButton.setDisable(true);
                    }
                });

        joinButton.setDisable(true);
        viewButton.setDisable(true);

        playerNameField.setText("Misha_" + System.currentTimeMillis() % 1000);

        playerNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (app != null && newVal != null && !newVal.trim().isEmpty()) {
                app.setPlayerName(newVal.trim());
            }
        });
    }

    public void setApp(AppController app) {
        this.app = app;
    }

    public void setNetworkManager(NetworkManager networkManager) {
        this.networkManager = networkManager;
        networkManager.setOnGamesListUpdate(this::updateGamesList);
        startPeriodicDiscovery();
    }


    public void setPlayerName(String name) {
        if (name != null && !name.isEmpty()) {
            playerNameField.setText(name);
        }
    }

    private void startPeriodicDiscovery() {
        if (discoveryExecutor != null) {
            discoveryExecutor.shutdown();
        }
        discoveryExecutor = Executors.newSingleThreadScheduledExecutor();
        discoveryExecutor.scheduleAtFixedRate(() -> {
            if (networkManager != null && networkManager.isRunning() && !networkManager.isInGame()) {
                networkManager.discoverGames();
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public void stopDiscovery() {
        if (discoveryExecutor != null) {
            discoveryExecutor.shutdown();
            discoveryExecutor = null;
        }
    }

    private void updateGamesList(List<GameAnnouncement> games) {
        Platform.runLater(() -> {
            GameAnnouncementRow currentSelection = gamesTable.getSelectionModel().getSelectedItem();
            String currentSelectedName = currentSelection != null ? currentSelection.getName() : null;

            gamesList.clear();
            int indexToSelect = -1;

            for (int i = 0; i < games.size(); i++) {
                GameAnnouncement game = games.get(i);
                gamesList.add(new GameAnnouncementRow(game));

                if (game.getGameName().equals(currentSelectedName)) {
                    indexToSelect = i;
                }
            }

            if (indexToSelect >= 0) {
                gamesTable.getSelectionModel().select(indexToSelect);
            }
        });
    }

    @FXML
    private void onJoinGame() {
        GameAnnouncementRow selected = gamesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            app.showError("Выберите игру из списка");
            return;
        }

        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            app.showError("Введите имя игрока");
            return;
        }

        app.setPlayerName(playerName);

        networkManager.joinGame(selected.announcement(), playerName, false);
        app.showGame();
    }

    @FXML
    private void onViewGame() {
        GameAnnouncementRow selected = gamesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            app.showError("Выберите игру из списка");
            return;
        }

        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            playerName = "Viewer_" + System.currentTimeMillis() % 1000;
        }

        app.setPlayerName(playerName);
        networkManager.joinGame(selected.announcement(), playerName, true);
        app.showGame();
    }

    @FXML
    private void onNewGame() {
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            app.showError("Введите имя игрока");
            return;
        }

        app.setPlayerName(playerName);
        app.showNewGame();
    }

    @FXML
    private void onExit() {
        stopDiscovery();
        if (app != null && app.getNetworkManager() != null) {
            app.getNetworkManager().stop();
        }
        Platform.exit();
        System.exit(0);
    }

    public record GameAnnouncementRow(GameAnnouncement announcement) {

        public String getName() {
            return announcement.getGameName();
        }

        public String getSize() {
            return announcement.getFieldSize();
        }

        public int getPlayers() {
            return announcement.getPlayingCount();
        }

        public String getCanJoin() {
            return announcement.canJoin() ? "Да" : "Нет";
        }

        public boolean canJoinBoolean() {
            return announcement.canJoin();
        }
    }
}