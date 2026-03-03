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

            // If a node reconnects, replace the old stale handler
            ServiceNodeHandler previous = connectedNodes.put(nodeName, this);
            if (previous != null) {
                System.out.println("[WARN] Node " + nodeName + " reconnected, replacing stale handler.");
            } else {
                System.out.println("[INFO] Node connected: " + nodeName);
            }

        } catch (IOException e) {
            e.printStackTrace();
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

    // Called internally when the heartbeat arrives
    public void refreshHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    private void disconnect() {
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
        // Move timer outside the while loop — you only need one
        Timer timer = new Timer(true); // daemon=true so it doesn't block JVM shutdown
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendUDPMessage("NODE_ALIVE", InetAddress.getByName("localhost"), 1235);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5000);

        try {
            String messageFromNode;
            while (socket.isConnected()) {
                messageFromNode = dataInputStream.readUTF().trim();

                if (messageFromNode.equals("NODE_ALIVE")) {
                    refreshHeartbeat(); // Update last seen time on heartbeat
                } else {
                    processRequest(messageFromNode);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            timer.cancel();
            disconnect(); 
        }
    }
}