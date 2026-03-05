package CSV_Stats;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class CSV_Reader {

    // Config
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;
    private static final String SERVICE_NAME = "CSV_Stats";
    private static final String NODE_ID = UUID.randomUUID().toString();
    private static final int HEARTBEAT_INTERVAL_MS = 30_000; // Heartbeat = 30 seconds
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
        socket = new Socket(SERVER_HOST, SERVER_PORT);
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
                        InetAddress.getByName(SERVER_HOST), 1235);
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    System.err.println("[WARN] Heartbeat failed, stopping.");
                    heartbeatTimer.cancel();
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
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

    // Reassembles large requests
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

    // Records the output of CSV file as a string and returns it to the server
    private static String handleRequest(String request) {
        try {
            // Split payload into parts (CSV|filename|base64data)
            String[] parts = request.split("\\|");
            byte[] fileBytes = java.util.Base64.getDecoder().decode(parts[2]);

            // Wraps decoded bytes so readCsv() can read them
            ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);

            // Capture filereader's output into a buffer
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true, "UTF-8");
            PrintStream orig = System.out;

            System.setOut(ps);
            try {
                readCsv(bais);
            } finally {
                System.setOut(orig); // restores console if error
            }

            return baos.toString("UTF-8");

        } catch (IOException e) {
            return "[ERROR] Could not process CSV: " + e.getMessage();
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

    // Closes all connections and heartbeat on exit 
    private static void shutdown() {
        System.out.println("[INFO] Shutting down CSV service node.");
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

    // FILE READER

    // Opens and reads a CSV file, calculates statistics, and prints a summary table
    public static void readCsv(InputStream inputStream) throws IOException {

        int rowCount = 0;
        String[] columns = null;

        // Stores values per column
        Map<String, List<Double>> columnData = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String header = reader.readLine();

            if (header != null) {
                columns = header.split(",");

                for (String column : columns) {
                    columnData.put(column.trim(), new ArrayList<>());
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                rowCount++;

                String[] values = line.split(",");

                // Parse values
                for (int i = 0; i < values.length && i < columns.length; i++) {
                    try {
                        double parsedValue = Double.parseDouble(values[i].trim());
                        columnData.get(columns[i].trim()).add(parsedValue);
                    } catch (NumberFormatException e) {
                        // Ignore non-numeric values
                    }
                }
            }
        }

        // Output Formatting
        System.out.println("----------------------------------------");
        System.out.println("CSV File Summary");
        System.out.println("----------------------------------------");

        if (columns != null) {
            System.out.println("Column Count: " + columns.length);
            System.out.println("Columns: " + String.join(", ", columns));
        } else {
            System.out.println("Column Count: 0");
            System.out.println("Columns: (none found)");
        }

        System.out.println("Data Row Count: " + rowCount);

        // Table Header
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-15s %12s %12s %12s %12s %12s%n",
                "Column", "Mean", "Min", "Max", "Median", "Std Dev");
        System.out.println("--------------------------------------------------------------------------------");

        // Table Rows
        for (Map.Entry<String, List<Double>> entry : columnData.entrySet()) {

            List<Double> values = entry.getValue();

            if (!values.isEmpty()) {

                double mean = calculateMean(values);
                double min = calculateMin(values);
                double max = calculateMax(values);
                double median = calculateMedian(values);
                double stdDev = calculateStdDev(values);

                System.out.printf("%-15s %12.2f %12.2f %12.2f %12.2f %12.2f%n",
                        entry.getKey(), mean, min, max, median, stdDev);
            }
        }
        System.out.println("----------------------------------------");
    }

    // STATISTIC CALCULATORS

    // Mean Calculator
    private static double calculateMean(List<Double> values) {

        double sum = 0.0;

        for (double value : values) {
            sum += value;
        }

        return sum / values.size();
    }

    // Min Calculator
    private static double calculateMin(List<Double> values) {

        double min = values.get(0);

        for (double value : values) {
            if (value < min) {
                min = value;
            }
        }

        return min;
    }

    // Max Calculator
    private static double calculateMax(List<Double> values) {

        double max = values.get(0);

        for (double value : values) {
            if (value > max) {
                max = value;
            }
        }

        return max;
    }

    // Median Calculator
    private static double calculateMedian(List<Double> values) {

        // Copy list
        List<Double> sorted = new ArrayList<>(values);

        Collections.sort(sorted);

        int size = sorted.size();

        // Even
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }

        // Odd
        return sorted.get(size / 2);
    }

    // Standard Deviation Calculator
    private static double calculateStdDev(List<Double> values) {

        double mean = calculateMean(values);
        double sumSquaredDiffs = 0.0;

        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiffs += diff * diff;
        }

        return Math.sqrt(sumSquaredDiffs / values.size());
    }
}