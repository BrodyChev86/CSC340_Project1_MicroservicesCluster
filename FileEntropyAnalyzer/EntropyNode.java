package FileEntropyAnalyzer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

import ServerClientTools.PropertyFileReader;


public class EntropyNode {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private byte[] outgoingData = new byte[1024];
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
     private static final String SERVER_HOST = PropertyFileReader.getIP();
    private static final int SERVER_PORT_TCP = PropertyFileReader.getServiceNodeTCPPort();
    private static final int SERVER_PORT_UDP = PropertyFileReader.getServiceNodeUDPPort();

    public EntropyNode(Socket socket, DatagramSocket datagramSocket) {
        try {
            this.socket = socket;
            this.datagramSocket = datagramSocket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void listenForMessage() {
        new Thread(() -> {
            while (socket.isConnected()) {
                try {
                    String msgFromServer = dataInputStream.readUTF();

                    // reassemble if server split a large request into chunks
                    if (msgFromServer.startsWith("FILE_REQUEST_START|")) {
                        String[] hdr = msgFromServer.split("\\|");
                        int chunks = Integer.parseInt(hdr[1]);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < chunks; i++) {
                            String chunkMsg = dataInputStream.readUTF();
                            if (chunkMsg.startsWith("FILE_REQUEST_CHUNK|")) {
                                sb.append(chunkMsg.substring("FILE_REQUEST_CHUNK|".length()));
                            } else {
                                sb.append(chunkMsg);
                            }
                        }
                        msgFromServer = sb.toString();
                    }

                    String[] parts = msgFromServer.split("\\|");
                    if (parts.length < 3) {
                        // malformed request; ignore or notify server
                        System.err.println("[WARN] EntropyNode received bad payload: " + msgFromServer);
                        continue;
                    }
                    // decode once to get original file bytes
                    byte[] fileBytes = java.util.Base64.getDecoder().decode(parts[2]);
                    // calculate entropy
                    double entropy = FileEntropyAnalyzer.EntropyAnalyzer
                            .calculateEntropy(fileBytes);
                    // Send result back to server
                    dataOutputStream.writeUTF(String.valueOf(entropy));
                    dataOutputStream.flush();

                } catch (IOException e) {
                    closeEverything(socket, dataInputStream, dataOutputStream);
                    break;
                }
            }
        }).start();

    }

    public void sendHeartbeat(String message, InetAddress address, int port) throws IOException {
        outgoingData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(outgoingData, outgoingData.length, address, port);
        datagramSocket.send(sendPacket);
    }

    /**
     * Create and display a small Swing window that lets the user choose one or more
     * files and upload them to the server. When the window closes a special message
     * is
     * sent to indicate completion.
     */

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        try {
            if (dataInputStream != null) {
                dataInputStream.close();
            }
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket(SERVER_HOST, SERVER_PORT_TCP); // Creates a socket that connects to the server running on
                                                       // localhost at port 1234, allowing the client to communicate
                                                       // with the server and other clients connected to it
        DatagramSocket datagramSocket = new DatagramSocket();
        EntropyNode entropyNode = new EntropyNode(socket, datagramSocket);
        String nodeId = java.util.UUID.randomUUID().toString();

        entropyNode.dataOutputStream.writeUTF("NODE_HELLO"); // Sends an initial message to the server to identify
                                                                // itself as a node, allowing the server to manage the
                                                                // connection appropriately
        entropyNode.dataOutputStream.flush();

        entropyNode.dataOutputStream.writeUTF("ENTROPY"); // Sends a message to the server to specify that this node
                                                            // provides entropy calculation services, allowing the server
                                                            // to route relevant client requests to this node
        entropyNode.dataOutputStream.flush();

        entropyNode.dataOutputStream.writeUTF(nodeId); // Sends a unique node ID to the server, allowing the server to
                                                        // track and manage this node's status and heartbeats effectively
        entropyNode.dataOutputStream.flush();

        Timer timer = new Timer(true); // daemon=true so it doesn't block JVM shutdown
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    entropyNode.sendHeartbeat("NODE_ALIVE|" + nodeId, InetAddress.getByName(SERVER_HOST), SERVER_PORT_UDP);
                    System.out.println("Sent heartbeat to server: NODE_ALIVE");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 15000);
        
        entropyNode.listenForMessage(); // Starts a thread to listen for incoming messages from the server, enabling the
        // client to receive messages from other clients in real-time
        // entropyNode.sendMessage(); // Starts sending messages to the server, allowing
        // the client to participate in
        // the group chat by sending messages to other clients through the server
    }
}
