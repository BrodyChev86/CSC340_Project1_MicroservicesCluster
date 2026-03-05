package ServerClientTools;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.net.*;
import java.time.Instant;

public class ServiceNodeHandler implements Runnable{
    private Socket socket;
    private static ArrayList<ServiceNodeHandler> serviceNodeHandlers = new ArrayList<>();
    private final LinkedBlockingQueue<String> requestQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private DataInputStream dataInputStream; // Used to read binary data from the client, such as files for entropy
                                             // analysis
    private String service;
    private DataOutputStream dataOutputStream;
    private DatagramSocket datagramSocket;
    private byte[] outgoingData = new byte[1024];
    private Instant lastHeartbeat;
    private String nodeId;
    private static final ConcurrentHashMap<String, ServiceNodeHandler> connectedNodes = new ConcurrentHashMap<>();

    public ServiceNodeHandler(Socket socket, DatagramSocket datagramSocket) {
        try {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        service = dataInputStream.readUTF().trim(); // First message from node should be its service type
        nodeId = dataInputStream.readUTF().trim(); // Second message is a unique node ID (can be random or based on IP/port)
        this.lastHeartbeat = Instant.now();
        serviceNodeHandlers.add(this);

        ServiceNodeHandler previous = connectedNodes.put(service, this);
        if (previous != null) {
            System.out.println("[WARN] Node " + service + " reconnected, replacing stale handler.");
        } else {
            System.out.println("[INFO] Node connected: " + service);
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
    //This is where communication with the service node happens
    //The client handler sends a request to the service node handler
    //when done this way we don't have to worry about the distinction between different types of service nodes, 
    //we just send a request to the node and it processes it and sends back a response
    public String requestService(String input) throws InterruptedException {
        requestQueue.put(input);
        return responseQueue.take(); // Wait for the service node to process the request and put a response in the queue
    }

    public String getNodeId() {
        return nodeId;
    }

    /**
     * Sends the given request string to the node and waits for its reply.
     * The reply is returned verbatim; callers decide whether to treat it as
     * raw bytes or a Base64 string.
     */
    public String requestServiceFile(String input) throws InterruptedException {
        requestQueue.put(input);
        return responseQueue.take(); // return the raw response string from node
    }
    public String getService() {
        return this.service;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public DataInputStream getDataInputStream() {
        return this.dataInputStream;
    }

    public DataOutputStream getDataOutputStream() {
        return this.dataOutputStream;
    }

    public static ArrayList<ServiceNodeHandler> getServiceNodeHandlers() {
        return serviceNodeHandlers;
    }

   

    public void removeServiceNodeHandler() {
        serviceNodeHandlers.remove(this);
        connectedNodes.remove(service, this);
        // broadcastMessage(service + " node has disconnected");
        //System.out.println("A service has disconnected!");
    }

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        removeServiceNodeHandler();
        try {
            if (dataInputStream != null) {
                dataInputStream.close(); // Closes the input stream reader, releasing any resources associated with it
                                         // and preventing further reading from the client
            }
            if (dataOutputStream != null) {
                dataOutputStream.close(); // Closes the output stream writer, releasing any resources associated with it
                                          // and preventing further writing to the client
            }
            if (socket != null) {
                socket.close(); // Closes the socket connection between the server and the client, releasing any
                                // resources associated with it and preventing further communication with the
                                // client
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

/* 
    public String getNodeName() {
        return nodeName;
    }
 */

    // Called internally when the heartbeat arrives
    public void refreshHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void disconnect() {
        removeServiceNodeHandler();
        System.out.println("[INFO] Node disconnected: " + service);
        closeSocket();
    }

    public void timeoutDisconnect() {
        removeServiceNodeHandler();
        System.out.println("[TIMEOUT] Node timed out and disconnected: " + service);
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close(); // Closes the socket connection between the server and the client, releasing any
                                // resources associated with it and preventing further communication with the
                                // client
            }
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

    /**
     * Write a potentially large request string to the node using the same
     * chunking protocol the node uses for responses.  This avoids the 64‑KB
     * limitation of DataOutputStream.writeUTF.
     */
    private void sendRequest(String request) throws IOException {
        int chunkSize = 60000;
        if (request.length() <= chunkSize) {
            dataOutputStream.writeUTF(request);
            dataOutputStream.flush();
        } else {
            int total = (request.length() + chunkSize - 1) / chunkSize;
            dataOutputStream.writeUTF("FILE_REQUEST_START|" + total);
            dataOutputStream.flush();
            for (int i = 0; i < total; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, request.length());
                dataOutputStream.writeUTF("FILE_REQUEST_CHUNK|" + request.substring(start, end));
                dataOutputStream.flush();
            }
        }
    }

    public void run() {
        try {
            while (socket.isConnected()) {
                String request = requestQueue.take();  // waits for work
                sendRequest(request);

                String response = dataInputStream.readUTF();
                // if the node is sending back a large file, it will chunk it
                if (response.startsWith("FILE_RESPONSE_START|")) {
                    String[] parts = response.split("\\|");
                    int chunks = Integer.parseInt(parts[1]);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < chunks; i++) {
                        String chunkMsg = dataInputStream.readUTF();
                        if (chunkMsg.startsWith("FILE_RESPONSE_CHUNK|")) {
                            sb.append(chunkMsg.substring("FILE_RESPONSE_CHUNK|".length()));
                        } else {
                            // unexpected message, still append raw
                            sb.append(chunkMsg);
                        }
                    }
                    response = sb.toString();
                }

                responseQueue.put(response);           // hand result back
            }
        }catch (EOFException e) {
            System.out.println("[INFO] Node " + service + " closed the connection.");
        }catch (InterruptedException e) {
            System.out.println("[ERROR] ServiceNodeHandler interrupted: " + e.getMessage());
        }catch(IOException e){
            System.out.println("[WARN] Connection lost with node: " + service);
        }
    }
}

    
