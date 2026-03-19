package ServerClientTools;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.net.*;
import java.time.Instant;

public class ServiceNodeHandler implements Runnable{
    private Socket socket;
    private static final java.util.List<ServiceNodeHandler> serviceNodeHandlers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final LinkedBlockingQueue<String> requestQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private DataInputStream dataInputStream;
    private String service;
    private DataOutputStream dataOutputStream;
    private DatagramSocket datagramSocket;
    private byte[] outgoingData = new byte[1024];
    private Instant lastHeartbeat;
    private String nodeId;
    private static final ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<ServiceNodeHandler>> connectedNodes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> rrIndexes = new ConcurrentHashMap<>();

    // Sentinel value written to responseQueue when the node dies mid-request,
    // so any thread blocked on requestService/requestServiceFile unblocks immediately
    // instead of hanging forever.
    public static final String NODE_ERROR_SENTINEL = "__NODE_ERROR__";

    // How long requestService / requestServiceFile will wait before declaring
    // the node unresponsive and returning an error to the client.
    private static final long RESPONSE_TIMEOUT_MS = 100_000; // 100 seconds

    public ServiceNodeHandler(Socket socket, DatagramSocket datagramSocket) {
        try {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        service = dataInputStream.readUTF().trim(); // First message from node should be its service type
        nodeId = dataInputStream.readUTF().trim(); // Second message is a unique node ID
        this.lastHeartbeat = Instant.now();
        serviceNodeHandlers.add(this);

        connectedNodes.computeIfAbsent(service, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(this);
        System.out.println("[INFO] Node connected: " + service);

    } catch (IOException e) {
        System.err.println("[ERROR] Failed to initialize Node Handler: " + e.getMessage());
        try {
            if (socket != null) socket.close();
        } catch (IOException ex) {
            // Ignore secondary close errors
        }
    }
    }

    /**
     * Send a request to the node and wait up to RESPONSE_TIMEOUT_MS for a reply.
     * If the node disconnects mid-request, run() will poison the responseQueue with
     * NODE_ERROR_SENTINEL so this method unblocks and returns an error string
     * rather than hanging forever.
     */
    public String requestService(String input) throws InterruptedException {
        requestQueue.put(input);
        String response = responseQueue.poll(RESPONSE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (response == null) {
            removeServiceNodeHandler();
            return NODE_ERROR_SENTINEL + "Request timed out — node may have disconnected.";
        }
        return response;
    }

    /**
     * Same as requestService but used for file payloads.  Shares the same
     * timeout-and-sentinel safeguard.
     */
    public String requestServiceFile(String input) throws InterruptedException {
        requestQueue.put(input);
        String response = responseQueue.poll(RESPONSE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (response == null) {
            removeServiceNodeHandler();
            return NODE_ERROR_SENTINEL + "Request timed out — node may have disconnected.";
        }
        return response;
    }

    /**
     * Send a ping request via the normal queueing mechanism and wait up to
     * {@code timeoutMs} milliseconds for a response.  If the node fails to
     * respond or an interruption/IO error occurs we remove it from the list
     * and return false.
     */
    public boolean handshake(long timeoutMs) {
        if (socket == null || socket.isClosed()) {
            removeServiceNodeHandler();
            return false;
        }
        try {
            requestQueue.put("PING");
            String resp = responseQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (resp == null || resp.startsWith(NODE_ERROR_SENTINEL)) {
                removeServiceNodeHandler();
                return false;
            }
            return "PONG".equals(resp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            removeServiceNodeHandler();
            return false;
        }
    }

    public String getNodeId() {
        return nodeId;
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

    public static java.util.List<ServiceNodeHandler> getServiceNodeHandlers() {
        return serviceNodeHandlers;
    }

    public void removeServiceNodeHandler() {
        serviceNodeHandlers.remove(this);
        java.util.List<ServiceNodeHandler> list = connectedNodes.get(service);
        if (list != null) {
            list.remove(this);
            if (list.isEmpty()) {
                connectedNodes.remove(service);
                rrIndexes.remove(service);
            }
        }
    }

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        removeServiceNodeHandler();
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

    public static boolean isNodeConnected(String name) {
        java.util.List<ServiceNodeHandler> handlers = connectedNodes.get(name);
        if (handlers == null || handlers.isEmpty()) return false;

        handlers.removeIf(h -> {
            boolean dead = false;
            try {
                if (h.socket == null || h.socket.isClosed()) {
                    dead = true;
                } else if (h.lastHeartbeat != null && h.lastHeartbeat.isBefore(Instant.now().minusSeconds(30))) {
                    dead = true;
                }
            } catch (Exception e) {
                dead = true;
            }
            if (dead) {
                h.removeServiceNodeHandler();
            }
            return dead;
        });

        return !handlers.isEmpty();
    }

    public static ServiceNodeHandler getNode(String name) {
        java.util.List<ServiceNodeHandler> handlers = connectedNodes.get(name);
        if (handlers == null || handlers.isEmpty()) return null;
        int idx = rrIndexes.computeIfAbsent(name, key -> new java.util.concurrent.atomic.AtomicInteger(0)).getAndIncrement();
        return handlers.get(Math.floorMod(idx, handlers.size()));
    }

    public static ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<ServiceNodeHandler>> getAllNodes() {
        return connectedNodes;
    }

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
                socket.close();
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
        dataOutputStream.writeInt(data.length);
        dataOutputStream.write(data);
    }

    public String processRequest(String request) {
        return " ";
    }

    /**
     * Write a potentially large request string to the node using chunking to
     * avoid the 64-KB limitation of DataOutputStream.writeUTF.
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

        // --- NEW: Dispatcher thread ---
        // Pulls jobs from the shared RequestQueue for this service,
        // forwards them through the existing requestService() machinery,
        // and completes the ticket so the waiting ClientHandler unblocks.
        Thread dispatcher = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    RequestQueue.PendingRequest job = RequestQueue.take(service);
                    String response = requestService(job.payload);
                    job.complete(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "dispatcher-" + service);
        dispatcher.setDaemon(true);
        dispatcher.start();
        // --- END NEW ---

        try {
            while (socket.isConnected()) {
                String request = requestQueue.take(); // waits for work

                // respond immediately to ping requests without involving the node process
                if ("PING".equals(request)) {
                    responseQueue.put("PONG");
                    continue;
                }

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
                            sb.append(chunkMsg);
                        }
                    }
                    response = sb.toString();
                }

                responseQueue.put(response); // hand result back
            }
        } catch (EOFException e) {
            System.out.println("[INFO] Node " + service + " closed the connection.");
        } catch (InterruptedException e) {
            System.out.println("[ERROR] ServiceNodeHandler interrupted: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[WARN] Connection lost with node: " + service);
        } finally {
            // Always poison the responseQueue on the way out so any thread currently
            // blocked in requestService / requestServiceFile wakes up immediately.
            removeServiceNodeHandler();
            responseQueue.offer(NODE_ERROR_SENTINEL + "Node " + service + " disconnected unexpectedly.");
        }
    }
}