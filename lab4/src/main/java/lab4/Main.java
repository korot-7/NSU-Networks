package lab4;

import javafx.application.Application;
import javafx.stage.Stage;
import lab4.snake.controller.AppController;


public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        AppController app = new AppController(primaryStage);
        app.start();
    }
}