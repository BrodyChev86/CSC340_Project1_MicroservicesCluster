package ServerClientTools;
import java.io.DataInputStream;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket;
    private byte[] incomingData = new byte[1024];
    private Set<InetAddress> nodeAddresses = Collections.newSetFromMap(new ConcurrentHashMap<>());


    public Server(ServerSocket serverSocket, DatagramSocket datagramSocket) {
        this.serverSocket = serverSocket;
        this.datagramSocket = datagramSocket;
    }

    public void startServer() throws IOException {
        // UDP listener thread — identifies nodes
        Thread udpThread = new Thread(() -> {
            while (!datagramSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(incomingData, incomingData.length);
                    receiveUDPMessages(packet);
                    new Thread(new ServiceNodeHandler(serverSocket.accept(), datagramSocket)).start();
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        });

        // TCP listener thread — accepts both clients and nodes
        Thread tcpThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    // Read the first message the connector sends to identify itself
                    DataInputStream reader = new DataInputStream(socket.getInputStream());
                    String identity = reader.readUTF();

                    if (identity != null && identity.equals("NODE_HELLO")) {
                        InetAddress ip = socket.getInetAddress();
                        nodeAddresses.add(ip); // Register node IP for reference
                        System.out.println("Node connected: " + ip);
                        new Thread(new ServiceNodeHandler(socket, datagramSocket)).start();
                    } else {
                        InetAddress ip = socket.getInetAddress();
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

    public void receiveUDPMessages(DatagramPacket packet) {
        try {
            datagramSocket.receive(packet);
            InetAddress ip = packet.getAddress();
            nodeAddresses.add(ip);
            int port = packet.getPort();
            String messageFromNode = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received UDP message from " + ip.getHostAddress() + ":" + port + " - " + messageFromNode);
            updateNodeStatus(ip.getHostAddress(), messageFromNode);
        } catch (IOException e) {
            e.printStackTrace();
                
        }
    }

    private void updateNodeStatus(String hostAddress, String messageFromNode) {
        System.out.println("Updating status for node " + hostAddress + ": " + messageFromNode);
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
            System.out.println("Server is listening on port 1234 for TCP connections and port 1235 for UDP packets...");
            server.startServer(); //Starts the server to accept and handle client connections.
    }
}
