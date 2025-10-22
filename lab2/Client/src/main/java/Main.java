import client.ClientController;

public class Main {
    public static void main(String[] args){
        if (args.length != 3) {
            System.err.println("Usage: java -jar Client.jar <serverHost> <serverPort> <filePath>");
            System.exit(1);
        }

        ClientController controller = new ClientController(args);
        controller.start();
    }
}
