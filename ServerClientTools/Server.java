package ServerClientTools;
import java.io.DataInputStream;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket;
    private byte[] incomingData = new byte[1024];
    private Set<InetAddress> nodeAddresses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final int SERVER_PORT_TCP = 1234;
    private static final int SERVER_PORT_UDP = 1235;


    public Server(ServerSocket serverSocket, DatagramSocket datagramSocket) {
        this.serverSocket = serverSocket;
        this.datagramSocket = datagramSocket;
    }

    public void startServer() throws IOException {

        Timer cleanupTimer = new Timer(true);
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Instant now = Instant.now();
                for (java.util.List<ServiceNodeHandler> nodeList : ServiceNodeHandler.getAllNodes().values()) {
                    for (ServiceNodeHandler node : nodeList) {
                        if (Duration.between(node.getLastHeartbeat(), now).getSeconds() > 120) {
                            System.out.println("[TIMEOUT] Removing stale node: " + node.getService());
                            node.timeoutDisconnect(); // Close its socket
                        }
                    }
                }
            }
        }, 5000, 5000);
        
        // UDP listener thread — identifies nodes
        Thread udpThread = new Thread(() -> {
        byte[] buffer = new byte[1024];
        while (!datagramSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(packet); // Blocks until a heartbeat arrives
                
                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                InetAddress nodeIp = packet.getAddress();

                if (message.startsWith("NODE_ALIVE")) {
                    System.out.println("[UDP] packet from " + nodeIp + " payload='" + message + "'");
                    updateNodeStatus(nodeIp.getHostAddress(), message);
                }
            } catch (IOException e) {
                if (!datagramSocket.isClosed()) e.printStackTrace();
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
                        new Thread(new ClientHandler(socket, identity)).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        udpThread.start();
        tcpThread.start();
    }

    private void updateNodeStatus(String hostAddress,  String messageFromNode) {
        if (messageFromNode.startsWith("NODE_ALIVE")) {
            String[] parts = messageFromNode.split("\\|");
            if (parts.length < 2) {
                System.out.println("[WARN] Malformed heartbeat from " + hostAddress + ": " + messageFromNode);
                return;
            }
            String incomingId = parts[1].trim();
            for (java.util.List<ServiceNodeHandler> handlers : ServiceNodeHandler.getAllNodes().values()) {
                for (ServiceNodeHandler handler : handlers) {
                    if (incomingId.equals(handler.getNodeId())) {
                        handler.refreshHeartbeat();
                        System.out.println("[DEBUG] Heartbeat refreshed for node: " + handler.getService());
                        return;
                    }
                }
            }
            System.out.println("[WARN] No handler found for heartbeat id=" + incomingId + " from " + hostAddress);
        }
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
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT_TCP); //Creates a server socket that listens on port 1234 for incoming client connections.
            DatagramSocket datagramSocket = new DatagramSocket(SERVER_PORT_UDP); //Creates a datagram socket that listens on port 1235 for incoming UDP packets.
            Server server = new Server(serverSocket, datagramSocket);
            System.out.println("Server is listening on port 1234 for TCP connections and port 1235 for UDP packets...");
            server.startServer(); //Starts the server to accept and handle client connections.
    }
}
