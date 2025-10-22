import server.ServerController;

public class Main {
    public static void main(String[] args){
        if (args.length != 1) {
            System.err.println("Usage: java -jar Server.jar <port>");
            System.exit(1);
        }

        ServerController controller = new ServerController(args);
        controller.start();
    }
}
