package lab4.snake.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import lab4.snake.network.controller.NetworkManager;

import java.io.IOException;

public class AppController {
    private final Stage primaryStage;
    private NetworkManager networkManager;
    private MainMenuController mainMenuController;

    private String playerName;

    public AppController(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void start() {
        networkManager = new NetworkManager();
        try {
            networkManager.start();
        } catch (IOException e) {
            System.err.println("Failed to start network: " + e.getMessage());
            showError("Не удалось запустить сеть: " + e.getMessage());
            Platform.exit();
            return;
        }

        primaryStage.setTitle("Сетевая Змейка");
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            shutdown();
            Platform.exit();
        });

        showMainMenu();
        primaryStage.show();
    }

    public void shutdown() {
        System.out.println("Application shutting down...");

        if (mainMenuController != null) {
            mainMenuController.stopDiscovery();
        }

        if (networkManager != null) {
            if (networkManager.isInGame()) {
                networkManager.leaveGame();
            }
            networkManager.stop();
        }

        System.out.println("Application shutdown complete");
    }


    public void showMainMenu() {
        if (mainMenuController != null) {
            mainMenuController.stopDiscovery();
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
            Parent root = loader.load();

            mainMenuController = loader.getController();
            mainMenuController.setApp(this);
            mainMenuController.setNetworkManager(networkManager);

            if (playerName != null && !playerName.isEmpty()) {
                mainMenuController.setPlayerName(playerName);
            }

            primaryStage.setScene(new Scene(root, 800, 600));
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить главное меню");
        }
    }


    public void showNewGame() {
        if (mainMenuController != null) {
            mainMenuController.stopDiscovery();
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/NewGame.fxml"));
            Parent root = loader.load();

            NewGameController controller = loader.getController();
            controller.setApp(this);
            controller.setNetworkManager(networkManager);
            controller.setPlayerName(playerName);

            primaryStage.setScene(new Scene(root, 800, 600));
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить экран создания игры");
        }
    }


    public void showGame() {
        if (mainMenuController != null) {
            mainMenuController.stopDiscovery();
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Game.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            controller.setApp(this);
            controller.setNetworkManager(networkManager);
            controller.initNetwork();

            Scene scene = new Scene(root, 1000, 700);
            primaryStage.setScene(scene);

            controller.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить игровой экран");
        }
    }


    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    public void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}