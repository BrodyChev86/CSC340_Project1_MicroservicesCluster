package ServerClientTools;
import java.net.*;

public class EntropyNode {
    public static void main(String[] args) throws Exception {
        registerWithClientHandler();
        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ServiceNodeHandler(socket)).start();
            }
        }
    }
    private static void registerWithClientHandler() throws Exception {
        Socket socket = new Socket("Localhost", 1234); //Localhost will need to be changed to the IP address of the machine running the ClientHandler when testing on multiple machines
        socket.getOutputStream().write("REGISTER ENTROPY".getBytes());
        socket.close();
    }
}
