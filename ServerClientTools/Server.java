package ServerClientTools;
import java.io.BufferedReader;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket = null;

    public Server(ServerSocket serverSocket, DatagramSocket datagramSocket) {
        this.serverSocket = serverSocket;
        this.datagramSocket = datagramSocket;
    }

    public void startServer() {
        Set<String> nodeAddresses = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // UDP listener thread — identifies nodes
        Thread udpThread = new Thread(() -> {
            byte[] incomingData = new byte[1024];
            while (!datagramSocket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(incomingData, incomingData.length);
                try {
                    datagramSocket.receive(packet);
                    String ip = packet.getAddress().getHostAddress();
                    nodeAddresses.add(ip);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // TCP listener thread — accepts both clients and nodes
        Thread tcpThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    // Read the first message the connector sends to identify itself
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                    String identity = reader.readLine();

                    if (identity != null && identity.equals("NODE_HELLO")) {
                        String ip = socket.getInetAddress().getHostAddress();
                        nodeAddresses.add(ip); // Register node IP for reference
                        System.out.println("Node connected: " + ip);
                        new Thread(new ServiceNodeHandler(socket)).start();
                    } else {
                        String ip = socket.getInetAddress().getHostAddress();
                        System.out.println("Client connected: " + ip);
                        new Thread(new ClientHandler(socket)).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        udpThread.start();
        tcpThread.start();
    }

    public void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close(); //Closes the server socket, releasing any resources associated with it and preventing new client connections if any errors are present.
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException{
            ServerSocket serverSocket = new ServerSocket(1234); //Creates a server socket that listens on port 1234 for incoming client connections.
            DatagramSocket datagramSocket = new DatagramSocket(1235); //Creates a datagram socket that listens on port 1235 for incoming UDP packets.
            Server server = new Server(serverSocket, datagramSocket);
            System.out.println("Server is listening on port 1234...");
            server.startServer(); //Starts the server to accept and handle client connections.
    }
}
