package ServerClientTools;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
import java.time.Instant;

public class ServiceNodeHandler implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private byte[] outgoingData = new byte[1024];
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private String nodeName;
    private Instant lastHeartbeat;
    private static final ConcurrentHashMap<String, ServiceNodeHandler> connectedNodes = new ConcurrentHashMap<>();

    public ServiceNodeHandler(Socket socket, DatagramSocket datagramSocket) {
        try {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        this.nodeName = dataInputStream.readUTF();
        this.lastHeartbeat = Instant.now();

        ServiceNodeHandler previous = connectedNodes.put(nodeName, this);
        if (previous != null) {
            System.out.println("[WARN] Node " + nodeName + " reconnected, replacing stale handler.");
        } else {
            System.out.println("[INFO] Node connected: " + nodeName);
        }

    } catch (IOException e) {
        // Instead of e.printStackTrace(), print a clean message
        System.err.println("[ERROR] Failed to initialize Node Handler: " + e.getMessage());
        try {
            if (socket != null) socket.close();
        } catch (IOException ex) {
            // Ignore secondary close errors
        }
    }
    }

    public static boolean isNodeConnected(String name) {
        return connectedNodes.containsKey(name);
    }

    public static ServiceNodeHandler getNode(String name) {
        return connectedNodes.get(name);
    }

    public static ConcurrentHashMap<String, ServiceNodeHandler> getAllNodes() {
        return connectedNodes;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getNodeName() {
        return nodeName;
    }

    // Called internally when the heartbeat arrives
    public void refreshHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void disconnect() {
        connectedNodes.remove(nodeName);
        System.out.println("[INFO] Node disconnected: " + nodeName);
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendUDPMessage(String message, InetAddress address, int port) throws IOException {
        outgoingData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(outgoingData, outgoingData.length, address, port);
        datagramSocket.send(sendPacket);
    }

    public void sendTCPMessage(String message) throws IOException {
        dataOutputStream.writeUTF(message);
    }

    public void sendTCPFiles(byte[] data) throws IOException {
        dataOutputStream.writeInt(data.length); // First send the length of the data
        dataOutputStream.write(data); // Then send the actual data
    }

    public String processRequest(String request) {
        return " ";
    }

    @Override
    public void run() {
        try {
            String messageFromNode;
            while (socket.isConnected()) {
                messageFromNode = dataInputStream.readUTF().trim();
                System.out.println("Message from node " + nodeName + ": " + messageFromNode);
            }
        } catch (EOFException e) {
            System.out.println("[INFO] Node " + nodeName + " closed the connection.");
        } catch(IOException e){
            System.out.println("[WARN] Connection lost with node: " + nodeName);
        }finally {
        disconnect();
        }
    }
}