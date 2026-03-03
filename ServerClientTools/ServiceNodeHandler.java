package ServerClientTools;
import java.io.*;
import java.util.ArrayList;

import FileEntropyAnalyzer.EntropyAnalyzer;

import java.util.concurrent.ConcurrentHashMap;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
import java.time.Instant;

public class ServiceNodeHandler implements Runnable{
    private Socket socket;
    private static ArrayList<ServiceNodeHandler> serviceNodeHandlers = new ArrayList<>();
    private DataInputStream dataInputStream; // Used to read binary data from the client, such as files for entropy
                                             // analysis
    private String service;
    private DataOutputStream dataOutputStream;
    private DatagramSocket datagramSocket;
    private byte[] outgoingData = new byte[1024];
    //private String nodeName;
    private Instant lastHeartbeat;
    private static final ConcurrentHashMap<String, ServiceNodeHandler> connectedNodes = new ConcurrentHashMap<>();

    public ServiceNodeHandler(Socket socket, DatagramSocket datagramSocket) {
        try {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        service = dataInputStream.readUTF().trim(); // First message from node should be its service type
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
    public synchronized String requestService(String input) throws IOException {
    try {
            dataOutputStream.writeUTF(input);
            dataOutputStream.flush();
            return dataInputStream.readUTF(); // blocks until response
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
            throw e;
        }
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
        connectedNodes.remove(service);
        System.out.println("[INFO] Node disconnected: " + service);
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
                System.out.println("Message from node " + service + ": " + messageFromNode);
            }
        } catch (EOFException e) {
            System.out.println("[INFO] Node " + service + " closed the connection.");
        } catch(IOException e){
            System.out.println("[WARN] Connection lost with node: " + service);
        }finally {
        disconnect();
        }
    }
}

    
