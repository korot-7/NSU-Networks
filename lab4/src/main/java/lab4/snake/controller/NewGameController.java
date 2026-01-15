package lab4.snake.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import lab4.snake.model.GameConfig;
import lab4.snake.network.controller.NetworkManager;


public class NewGameController {

    @FXML private TextField gameNameField;

    @FXML private Spinner<Integer> widthSpinner;
    @FXML private Spinner<Integer> heightSpinner;
    @FXML private Spinner<Integer> foodSpinner;
    @FXML private Spinner<Integer> delaySpinner;


    private AppController app;
    private NetworkManager networkManager;
    private String playerName;


    @FXML
    public void initialize() {
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                GameConfig.MIN_WIDTH, GameConfig.MAX_WIDTH, 40));

        heightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                GameConfig.MIN_HEIGHT, GameConfig.MAX_HEIGHT, 30));

        foodSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                GameConfig.MIN_FOOD_STATIC, GameConfig.MAX_FOOD_STATIC, 1));

        delaySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                GameConfig.MIN_STATE_DELAY_MS, GameConfig.MAX_STATE_DELAY_MS, 500, 50));

        gameNameField.setText("Game_" + System.currentTimeMillis() % 1000);
    }

    public void setApp(AppController app) {
        this.app = app;
    }

    public void setNetworkManager(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }


    public void setPlayerName(String name) {
        this.playerName = name;
    }

    @FXML
    private void onStartGame() {
        String gameName = gameNameField.getText().trim();

        if (gameName.isEmpty()) {
            app.showError("Введите название игры");
            return;
        }

        if (playerName == null || playerName.isEmpty()) {
            app.showError("Имя игрока не задано. Вернитесь в главное меню.");
            return;
        }

        GameConfig config = new GameConfig(
                widthSpinner.getValue(),
                heightSpinner.getValue(),
                foodSpinner.getValue(),
                delaySpinner.getValue()
        );

        networkManager.createGame(config, playerName, gameName);

        app.showGame();
    }

    @FXML
    private void onBack() {
        app.showMainMenu();
    }
}