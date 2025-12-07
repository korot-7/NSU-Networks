import controller.ServerController;

import java.io.IOException;

public class Main {
    public static void main(String[] args){
        try {
            ServerController serverController = new ServerController(args);
            serverController.start();
        } catch (IOException e){
            System.err.println(e);
        }
    }
}
