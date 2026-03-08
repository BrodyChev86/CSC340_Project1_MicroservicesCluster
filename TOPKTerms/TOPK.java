package TOPKTerms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class TOPK {

    private final List<List<String>> corpus; // tokenized corpus: list of documents
    private final int totalDocs; // number of documents in the corpus
    // Basic list of filler words to ignore
    private final Set<String> stopWords = new HashSet<>(Arrays.asList(
        "is", "the", "a", "an", "and", "or", "it", "to", "of", "i", "in", "with", "for"
    ));
    // reference documents used to compute IDF
    private static final List<String> REFERENCE_CORPUS = Arrays.asList(
        "Java is a high-level, class-based, object-oriented programming language.",
        "Python is an interpreted high-level general-purpose programming language.",
        "JavaScript is a language that is one of the core technologies of the World Wide Web.",
        "C++ is an extension of the C programming language.",
        "The coffee bean comes from the Java region and is very famous worldwide."
    );
    // Config
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT_TCP = 1234;
    private static final int SERVER_PORT_UDP = 1235;
    private static final String SERVICE_NAME = "TOPK";
    private static final String NODE_ID = UUID.randomUUID().toString();
    private static final int HEARTBEAT_INTERVAL_MS = 10_000; // Heartbeat = 10 seconds
    private static final int CHUNK_SIZE = 60_000; // 60,000 Characters Chunk Max

    // Socket I/O
    private static Socket socket;
    private static DataInputStream dataInputStream;
    private static DataOutputStream dataOutputStream;
    private static Timer heartbeatTimer;
    private static DatagramSocket datagramSocket; // Heartbeat Socket

    // Server Connector, Heartbeat, and Requests
    public static void main(String[] args) {
        try {
            connect();
            startHeartbeat();
            listenForRequests();
        } catch (IOException e) {
            System.err.println("[ERROR] Service node error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    // Opens a TCP socket and sends service name and node ID
    private static void connect() throws IOException {
        socket = new Socket(SERVER_HOST, SERVER_PORT_TCP);
        dataInputStream = new DataInputStream(socket.getInputStream());
        dataOutputStream = new DataOutputStream(socket.getOutputStream());
        datagramSocket = new DatagramSocket();

        dataOutputStream.writeUTF("NODE_HELLO");
        dataOutputStream.writeUTF(SERVICE_NAME);
        dataOutputStream.writeUTF(NODE_ID);
        dataOutputStream.flush();

        System.out.println("[INFO] Connected as " + SERVICE_NAME + " (id=" + NODE_ID + ")");
    }

    // Sends a UDP heartbeat
    private static void startHeartbeat() {
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    String heartbeatMsg = "NODE_ALIVE|" + NODE_ID;
                    byte[] data = heartbeatMsg.getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(SERVER_HOST), SERVER_PORT_UDP);
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    System.err.println("[WARN] Heartbeat failed, stopping.");
                    heartbeatTimer.cancel();
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    public static void shutdown(){
        System.out.println("[INFO] Shutting down TOPK service node.");
        if (heartbeatTimer != null) heartbeatTimer.cancel();
        try {
            if (dataInputStream != null) dataInputStream.close();
            if (dataOutputStream != null) dataOutputStream.close();
            if (datagramSocket != null) datagramSocket.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("[WARN] Shutdown error: " + e.getMessage());
        }
    }

    // Reads requests from server
    private static void listenForRequests() throws IOException {
        System.out.println("[INFO] Listening for requests...");
        while (socket.isConnected()) {
            String request = dataInputStream.readUTF();

            // Recieve large requests in chunks
            if (request.startsWith("FILE_REQUEST_START|")) {
                request = receiveChunkedRequest(request);
            }

            // If heartbeat repeat loop
            if (request.equals("HEARTBEAT")) continue;

            System.out.println("[INFO] Request received: " + request);
            String result = handleRequest(request);
            sendResponse(result);
        }
    }
    // Handle a TOPK request of the form TOPK|k|base64text or TOPK|base64text.
    private static String handleRequest(String request) {
        try {
            String[] parts = request.split("\\|");
            int k = 3;
            String base64;

            if (parts.length == 3) {
                // TOPK|k|base64
                try {
                    k = Integer.parseInt(parts[1]);
                } catch (NumberFormatException nfe) {
                    return "[ERROR] invalid k value: " + parts[1];
                }
                base64 = parts[2];
            } else if (parts.length == 2) {
                // TOPK|base64
                base64 = parts[1];
            } else {
                return "[ERROR] malformed TOPK request";
            }

            byte[] fileBytes = java.util.Base64.getDecoder().decode(base64);
            String text = new String(fileBytes, StandardCharsets.UTF_8);

            // create a TOPK instance to process the request
            TOPK extractor = new TOPK(REFERENCE_CORPUS);
            List<Map.Entry<String, Double>> results = extractor.getTopKTerms(text, k);
            if (results.isEmpty()) {
                return "No significant terms found.";
            }

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> e : results) {
                sb.append(e.getKey())
                  .append(": ")
                  .append(String.format("%.4f", e.getValue()))
                  .append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] processing TOPK: " + e.getMessage();
        }
    }

    // Sends in chunks if too large
    private static void sendResponse(String response) throws IOException {
        if (response.length() <= CHUNK_SIZE) {
            dataOutputStream.writeUTF(response);
            dataOutputStream.flush();
            return;
        }

        int total = (response.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        dataOutputStream.writeUTF("FILE_RESPONSE_START|" + total);
        dataOutputStream.flush();

        for (int i = 0; i < total; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, response.length());
            dataOutputStream.writeUTF("FILE_RESPONSE_CHUNK|" + response.substring(start, end));
            dataOutputStream.flush();
        }
    }

    // Reassembles large requests (unchanged, retained for chunk support)
    private static String receiveChunkedRequest(String startMessage) throws IOException {
        int chunks = Integer.parseInt(startMessage.split("\\|")[1]);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks; i++) {
            String chunk = dataInputStream.readUTF();
            if (chunk.startsWith("FILE_REQUEST_CHUNK|")) {
                sb.append(chunk.substring("FILE_REQUEST_CHUNK|".length()));
            } else {
                sb.append(chunk);
            }
        }
        return sb.toString();
    }



    public TOPK(List<String> documents) { // build the internal tokenized corpus
        this.corpus = documents.stream()
                .map(this::tokenize)
                .collect(Collectors.toList());
        this.totalDocs = corpus.size();
    }

    public List<Map.Entry<String, Double>> getTopKTerms(String userRequest, int k) { // compute top-k by TF-IDF
        List<String> tokens = tokenize(userRequest); // tokens from the user request
        Map<String, Double> tfIdfMap = new HashMap<>(); // term -> tf-idf score

        for (String term : new HashSet<>(tokens)) {
            // Skip common stop words to keep results meaningful
            if (stopWords.contains(term)) continue;

            double tf = calculateTF(tokens, term);
            double idf = calculateIDF(term);
            tfIdfMap.put(term, tf * idf);
        }

        return tfIdfMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .collect(Collectors.toList());
    }

    private double calculateTF(List<String> tokens, String term) { //calculates term frequency (TF) formula
        double count = 0;
        for (String s : tokens) {
            if (s.equalsIgnoreCase(term)) count++;
        }
        return count / tokens.size();
    }

    private double calculateIDF(String term) { //calculates inverse document frequency (IDF) formula
        double docsWithTerm = 0;
        for (List<String> doc : corpus) {
            if (doc.contains(term.toLowerCase())) docsWithTerm++;
        }
        // log(Total / (Found + 1)) helps balance the weight
        return Math.log((double) totalDocs / (1.0 + docsWithTerm));
    }

    private List<String> tokenize(String text) {
        return Arrays.asList(text.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").split("\\s+")); // lowercase, remove punctuation, split on whitespace
    }
}