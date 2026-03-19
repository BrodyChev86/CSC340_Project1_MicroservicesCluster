package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable {

    public static java.util.List<ClientHandler> clientHandlers = new java.util.concurrent.CopyOnWriteArrayList<>();;
    private ArrayList<FileHandler> fileHandlers = new ArrayList<>();
    private Socket socket;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private String clientUsername;
    private final String serviceOptions =
        "Available commands:\n" +
        "  upload                 * send a file to the server; upload a file before using services that require one.\n" +
        "  list                   * display this help message again.\n" +
        "  exit                   * disconnect from the server.\n\n" +
        "BASE64 commands (text or uploaded file):\n" +
        "  BASE64 ENCODE_FILE        * encode the currently uploaded file; result returned as base64 text.\n" +
        "  BASE64 DECODE_FILE [ext]  * decode base64 data from uploaded file; optional extension for output (uses original ext or .bin if unspecified).\n" +
        "  BASE64 ENCODE_TEXT <text> * send arbitrary text and receive its Base64 representation.\n" +
        "  BASE64 DECODE_TEXT <text> * send Base64 text and receive decoded text.\n\n" +
        "ENTROPY (file only):\n" +
        "  ENTROPY               * compute entropy of the uploaded file.\n\n" +
        "CSV statistics (file only):\n" +
        "  CSV                   * analyze the uploaded CSV file (must have .csv extension).\n\n" +
        "TOP-K terms:\n" +
        "  TOPK FILE             * find top k terms in uploaded .txt file (default k=3).\n" +
        "  TOPK  <text>          * find top k terms in provided text (default k=3).\n\n" +
        "Image transformations (PNG/JPG, file upload required):\n" +
        "  IMGT ROTATE <degrees>\n" +
        "  IMGT RESIZE <width> <height>\n" +
        "  IMGT TOGRAYSCALE\n\n" +
        "Other:\n" +
        "  NODE_LIST             * list connected service nodes.\n" +
        "  upload                * start a file upload.\n" +
        "  list                  * show this help text anytime.";
    private int fileId = 0;
    private FileHandler currentFile = null;
    private volatile boolean isUploading = false;

    public ClientHandler(Socket socket, String clientUsername) {
        try {
            this.socket = socket;
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.clientUsername = clientUsername;
            clientHandlers.add(this);
            broadcastMessageToSender(serviceOptions);
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    // ------------------------------------------------------------------
    // Helper: returns true if the response string is a sentinel error
    // written by ServiceNodeHandler when a node disconnects mid-request.
    // ------------------------------------------------------------------
    private boolean isNodeError(String response) {
        return response != null && response.startsWith(ServiceNodeHandler.NODE_ERROR_SENTINEL);
    }

    // Strips the sentinel prefix to give the client a readable message.
    private String nodeErrorMessage(String response) {
        return "[ERROR] " + response.substring(ServiceNodeHandler.NODE_ERROR_SENTINEL.length());
    }

    @Override
    public void run() {
        String messageFromClient;
        while(socket.isConnected()){
            try {
                messageFromClient = dataInputStream.readUTF().trim();
                if (messageFromClient.contains(":")) {
                    messageFromClient = messageFromClient.substring(messageFromClient.indexOf(":") + 1).trim();
                }

                if(messageFromClient.equals("upload")){
                    broadcastMessageToSender("FILE_UPLOAD");
                    isUploading = true;
                    fileUpload(); // back to blocking on the run() thread
                }else if(messageFromClient.equals("exit")){
                    removeClientHandler();
                    break;
                }else if(messageFromClient.equals("list")){
                    broadcastMessageToSender(serviceOptions);
                } else if (messageFromClient.startsWith("BASE64")) {
                    if (!isNodeConnected("BASE64")) {
                        broadcastMessageToSender("[ERROR] BASE64 service node not connected.");
                        continue;
                    }
                    String command = messageFromClient.substring("BASE64".length()).trim();
                    if (command.startsWith("ENCODE_FILE")) {
                        sendFileToNode("ENCODE_FILE");
                    } else if (command.startsWith("DECODE_FILE")) {
                        String[] parts = command.split("\\s+", 2);
                        String targetExt;
                        if (parts.length > 1) {
                            targetExt = parts[1].trim();
                        } else {
                            if (currentFile != null) {
                                targetExt = currentFile.getFileExtension();
                            } else {
                                targetExt = "bin";
                            }
                        }
                        sendFileToNode("DECODE_FILE|" + targetExt);
                    } else {
                        sendMessageToNode(command);
                    }
                }
                else if(messageFromClient.startsWith("ENTROPY")) {
                    if (!isNodeConnected("ENTROPY")) {
                        broadcastMessageToSender("[ERROR] ENTROPY service node not connected.");
                        continue;
                    }
                    sendFileToNode(messageFromClient);
                } else if (messageFromClient.startsWith("CSV_BATCH")) {
                    if (!isNodeConnected("CSV_Stats")) {
                        broadcastMessageToSender("[ERROR] CSV_Stats service node not connected.");
                        continue;
                    }
                    String[] parts = messageFromClient.split("\\s+");
                    int parallelRequests = 3;
                    if (parts.length > 1) {
                        try {
                            parallelRequests = Math.max(1, Integer.parseInt(parts[1]));
                        } catch (NumberFormatException nfe) {
                            broadcastMessageToSender("[ERROR] CSV_BATCH usage: CSV_BATCH <num>");
                            continue;
                        }
                    }
                    for (int i = 0; i < parallelRequests; i++) {
                        int requestId = i + 1;
                        new Thread(() -> {
                            try {
                                broadcastMessageToSender("[INFO] CSV_BATCH request " + requestId + " started.");
                                sendToCSVNode();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                broadcastMessageToSender("[ERROR] CSV_BATCH request " + requestId + " interrupted.");
                            }
                        }, "CSV_BATCH-" + requestId).start();
                    }
                } else if (messageFromClient.startsWith("CSV")) {
                    if (!isNodeConnected("CSV_Stats")) {
                        broadcastMessageToSender("[ERROR] CSV_Stats service node not connected.");
                        continue;
                    }
                    new Thread(() -> {
                    try {
                        sendToCSVNode();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        broadcastMessageToSender("[ERROR] CSV request interrupted.");
                    }
                    }, "csv-" + clientUsername).start();
                }else if (messageFromClient.startsWith("TOPK")) {
                    if (!isNodeConnected("TOPK")) {
                        broadcastMessageToSender("[ERROR] TOPK service node not connected.");
                        continue;
                    }
                    String command = messageFromClient.substring("TOPK".length()).trim();
                    if (command.toUpperCase().startsWith("FILE")) {
                        sendFileToTopKNode(messageFromClient);
                    } else {
                        sendTextToTopKNode(command);
                    }
                }else if(messageFromClient.equals("NODE_LIST")){
                    StringBuilder nodeList = new StringBuilder("Connected Nodes:\n");
                    for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
                        nodeList.append("- ").append(serviceNodeHandler.getService()).append("\n");
                    }
                    broadcastMessageToSender(nodeList.toString());
                } else if (messageFromClient.startsWith("IMGT")) {
                    if (!isNodeConnected("IMGT")) {
                        broadcastMessageToSender("[ERROR] IMGT service node not connected.");
                        continue;
                    }
                    sendFileToImageNode(messageFromClient);
                } else if(messageFromClient.equals("\n")) {
                    // ignore empty messages
                } else {
                    broadcastMessageToSender("Invalid input. Please enter a valid option or follow the instructions for encoding/decoding.");
                }
            } catch (Exception e) {
               closeEverything(socket, dataInputStream, dataOutputStream);
               break;
            }
        }
    }

    private boolean isNodeConnected(String serviceName) {
        return ServiceNodeHandler.isNodeConnected(serviceName);
    }

    // ---------------------------------------------------------------
    // sendMessageToNode — BASE64 text commands (ENCODE_TEXT/DECODE_TEXT)
    // ---------------------------------------------------------------
    public void sendMessageToNode(String input) throws InterruptedException {
        if (!isNodeConnected("BASE64")) {
            broadcastMessageToSender("[ERROR] BASE64 service node not connected.");
            return;
        }

        RequestQueue.PendingRequest ticket = RequestQueue.submit("BASE64", input);
        String base64 = ticket.waitForResponse();

        if (isNodeError(base64)) {
            broadcastMessageToSender(nodeErrorMessage(base64));
            return;
        }
        broadcastMessageToSender("Result: " + base64);
    }

    // ---------------------------------------------------------------
    // sendFileToNode — BASE64 file encode/decode and ENTROPY
    // ---------------------------------------------------------------
    public void sendFileToNode(String messageFromClient) throws InterruptedException {

        // ENTROPY branch
        if (messageFromClient.startsWith("ENTROPY")) {
            if (!isNodeConnected("ENTROPY")) {
                broadcastMessageToSender("[ERROR] ENTROPY service node not connected.");
                return;
            }
            if (currentFile == null) {
                broadcastMessageToSender("No file uploaded. Please upload a file to analyze its entropy.");
                return;
            }
            String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
            String payload = "ENTROPY|" + currentFile.getFileName() + "|" + fileAsString;

            RequestQueue.PendingRequest ticket = RequestQueue.submit("ENTROPY", payload);
            String response = ticket.waitForResponse();

            if (isNodeError(response)) {
                broadcastMessageToSender(nodeErrorMessage(response));
                return;
            }
            broadcastMessageToSender("File Entropy: " + response);
            return;
        }

        // BASE64 file branch
        if (!isNodeConnected("BASE64")) {
            broadcastMessageToSender("[ERROR] BASE64 service node not connected.");
            return;
        }
        if (currentFile == null) {
            broadcastMessageToSender("No file uploaded. Please upload a file to encode/decode.");
            return;
        }

        String fileExtension = currentFile.getFileExtension();
        String fileName = currentFile.getFileName();
        String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());

        if (messageFromClient.equals("ENCODE_FILE")) {
            String payload = "ENCODE_FILE|" + fileName + "|" + fileAsString + "|" + fileExtension;

            RequestQueue.PendingRequest ticket = RequestQueue.submit("BASE64", payload);
            String responseStr = ticket.waitForResponse();

            if (isNodeError(responseStr)) {
                broadcastMessageToSender(nodeErrorMessage(responseStr));
                return;
            }
            byte[] fileBytes = responseStr.getBytes();
            fileDownload(fileName + "_encoded", "txt", fileBytes);

        } else if (messageFromClient.startsWith("DECODE_FILE")) {
            String[] parts = messageFromClient.split("\\|", 2);
            String targetExt = parts.length > 1 ? parts[1].trim() : fileExtension;
            String baseName = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;

            String payload = "DECODE_FILE|" + fileName + "|" + fileAsString + "|" + fileExtension;

            RequestQueue.PendingRequest ticket = RequestQueue.submit("BASE64", payload);
            String responseStr = ticket.waitForResponse();

            if (isNodeError(responseStr)) {
                broadcastMessageToSender(nodeErrorMessage(responseStr));
                return;
            }
            byte[] fileBytes = java.util.Base64.getDecoder().decode(responseStr);
            fileDownload(baseName + "_decoded", targetExt, fileBytes);
        }

        broadcastMessageToSender("FILE HAS BEEN RETURNED");
    }

    // ---------------------------------------------------------------
    // sendToCSVNode — CSV_Stats
    // ---------------------------------------------------------------
    public void sendToCSVNode() throws InterruptedException {
        ServiceNodeHandler serviceNodeHandler = ServiceNodeHandler.getNode("CSV_Stats");
        if (serviceNodeHandler == null || !isNodeConnected("CSV_Stats")) {
            broadcastMessageToSender("[ERROR] CSV_Stats service node not connected.");
            return;
        }
        if (currentFile == null) {
            broadcastMessageToSender("No file uploaded. Please upload a CSV file first.");
            return;
        }
        String ext = currentFile.getFileExtension().toLowerCase();
        if (!ext.equals("csv")) {
            broadcastMessageToSender("Please upload a file with .csv extension for CSV service.");
            return;
        }

        String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
        String payload = "CSV|" + currentFile.getFileName() + "|" + fileAsString;

        System.out.println("[DEBUG] Submitting CSV request to RequestQueue");
        RequestQueue.PendingRequest ticket = RequestQueue.submit("CSV_Stats", payload);
        System.out.println("[DEBUG] Ticket submitted, waiting for response...");
        String response = ticket.waitForResponse();
        System.out.println("[DEBUG] Got response: " + response);

        if (isNodeError(response)) {
            broadcastMessageToSender(nodeErrorMessage(response));
            return;
        }
        broadcastMessageToSender(response);
    }

    // ---------------------------------------------------------------
    // sendFileToTopKNode — TOPK file
    // ---------------------------------------------------------------
    public void sendFileToTopKNode(String messageFromClient) throws InterruptedException {
        if (!isNodeConnected("TOPK")) {
            broadcastMessageToSender("[ERROR] TOPK service node not connected.");
            return;
        }
        if (currentFile == null) {
            broadcastMessageToSender("No file uploaded. Please upload a file before requesting top-k terms.");
            return;
        }
        String ext = currentFile.getFileExtension();
        if (!"txt".equalsIgnoreCase(ext)) {
            broadcastMessageToSender("TOPK service only supports .txt files. Please upload a .txt file.");
            return;
        }

        String[] tokens = messageFromClient.split("\\s+");
        int k = 3;
        if (tokens.length >= 3) {
            try {
                k = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException nfe) {
                // keep default
            }
        }

        String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
        String payload = "TOPK|" + k + "|" + fileAsString;

        RequestQueue.PendingRequest ticket = RequestQueue.submit("TOPK", payload);
        String response = ticket.waitForResponse();

        if (isNodeError(response)) {
            broadcastMessageToSender(nodeErrorMessage(response));
            return;
        }
        broadcastMessageToSender(response);
    }

    // ---------------------------------------------------------------
    // sendTextToTopKNode — TOPK inline text
    // ---------------------------------------------------------------
    public void sendTextToTopKNode(String command) throws InterruptedException {
        if (!isNodeConnected("TOPK")) {
            broadcastMessageToSender("[ERROR] TOPK service node not connected.");
            return;
        }
        if (command == null || command.isEmpty()) {
            broadcastMessageToSender("Please provide text to analyze or use 'TOPK [k] <text>'.");
            return;
        }

        String[] parts = command.split("\\s+", 2);
        int k = 3;
        String text;
        if (parts.length == 2) {
            try {
                k = Integer.parseInt(parts[0]);
                text = parts[1];
            } catch (NumberFormatException nfe) {
                text = command;
            }
        } else {
            try {
                k = Integer.parseInt(command);
                broadcastMessageToSender("Please specify text after the k value.");
                return;
            } catch (NumberFormatException nfe) {
                text = command;
            }
        }

        String base64 = java.util.Base64.getEncoder()
                .encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = "TOPK|" + k + "|" + base64;

        RequestQueue.PendingRequest ticket = RequestQueue.submit("TOPK", payload);
        String response = ticket.waitForResponse();

        if (isNodeError(response)) {
            broadcastMessageToSender(nodeErrorMessage(response));
            return;
        }
        broadcastMessageToSender(response);
    }

    // ---------------------------------------------------------------
    // sendFileToImageNode — IMGT
    // ---------------------------------------------------------------
    public void sendFileToImageNode(String messageFromClient) throws InterruptedException {
        if (!isNodeConnected("IMGT")) {
            broadcastMessageToSender("[ERROR] IMGT service node not connected.");
            return;
        }
        if (currentFile == null) {
            broadcastMessageToSender("No file uploaded. Please upload an image to transform.");
            return;
        }

        String ext = currentFile.getFileExtension().toLowerCase();
        if (!(ext.equals("jpg") || ext.equals("png"))) {
            broadcastMessageToSender("ImageTransformer only supports PNG and JPG files.");
            return;
        }

        String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
        String payload;

        if (messageFromClient.startsWith("IMGT ROTATE")) {
            String[] parts = messageFromClient.split("\\s+");
            if (parts.length < 3) {
                broadcastMessageToSender("Usage: IMGT ROTATE <degrees>");
                return;
            }
            double degrees;
            try {
                degrees = Double.parseDouble(parts[2]);
            } catch (NumberFormatException nfe) {
                broadcastMessageToSender("Invalid degrees value.");
                return;
            }
            payload = "ROTATE|" + degrees + "|" + ext + "|" + fileAsString;
        } else if (messageFromClient.startsWith("IMGT RESIZE")) {
            String[] parts = messageFromClient.split("\\s+");
            if (parts.length < 4) {
                broadcastMessageToSender("Usage: IMGT RESIZE <width> <height>");
                return;
            }
            int w, h;
            try {
                w = Integer.parseInt(parts[2]);
                h = Integer.parseInt(parts[3]);
            } catch (NumberFormatException nfe) {
                broadcastMessageToSender("Width and height must be integers.");
                return;
            }
            payload = "RESIZE|" + w + "|" + h + "|" + ext + "|" + fileAsString;
        } else if (messageFromClient.equals("IMGT TOGRAYSCALE")) {
            payload = "TOGRAYSCALE|" + ext + "|" + fileAsString;
        } else {
            broadcastMessageToSender("Unknown IMGT command.");
            return;
        }

        RequestQueue.PendingRequest ticket = RequestQueue.submit("IMGT", payload);
        String response = ticket.waitForResponse();

        if (isNodeError(response)) {
            broadcastMessageToSender(nodeErrorMessage(response));
            return;
        }
        byte[] fileBytes = java.util.Base64.getDecoder().decode(response);

        String baseName = currentFile.getFileName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);

        String suffix;
        if (messageFromClient.startsWith("IMGT ROTATE")) suffix = "_rot";
        else if (messageFromClient.startsWith("IMGT RESIZE")) suffix = "_res";
        else suffix = "_gray";

        fileDownload(baseName + suffix, ext, fileBytes);
        broadcastMessageToSender("Image transformation complete.");
    }

    public static String getFileExtension(String fileName){
        int i = fileName.lastIndexOf('.');
        if(i > 0){
            return fileName = fileName.substring(i + 1);
        }else{
            return "No extension found";
        }
    }

    public void fileDownload(String fileName, String fileExtension, byte[] fileContent) {
        try {
            String fileAsString = java.util.Base64.getEncoder().encodeToString(fileContent);
            int chunkSize = 30000;
            int totalChunks = (int) Math.ceil((double) fileAsString.length() / chunkSize);

            dataOutputStream.writeUTF("FILE_DOWNLOAD_START|" + fileName + "|" + fileExtension + "|" + totalChunks);
            dataOutputStream.flush();

            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileAsString.length());
                String chunk = fileAsString.substring(start, end);
                dataOutputStream.writeUTF("FILE_CHUNK|" + chunk);
                dataOutputStream.flush();
            }
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void fileUpload(){
         try{
            while(isUploading){

                int fileNameLength = dataInputStream.readInt();
                if (fileNameLength == 0) {
                    isUploading = false;
                    broadcastMessageToSender("File upload complete.");
                    break;
                }
                if(fileNameLength > 0){
                    byte[] fileNameBytes = new byte[fileNameLength];
                    dataInputStream.readFully(fileNameBytes, 0 , fileNameBytes.length);
                    String fileName = new String(fileNameBytes);

                    int fileDataLength = dataInputStream.readInt();

                    if(fileDataLength > 0){
                        byte[] fileDataBytes = new byte[fileDataLength];
                        dataInputStream.readFully(fileDataBytes, 0, fileDataBytes.length);

                        fileHandlers.add(new FileHandler(fileId, fileName, fileDataBytes, getFileExtension(fileName)));
                        currentFile = fileHandlers.get(fileHandlers.size() - 1);
                        fileId++;
                        System.out.println("Received file: " + fileName + " with size: " + fileDataBytes.length + " bytes");
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void broadcastMessage(String messageToSend){
        for (ClientHandler clientHandler : clientHandlers) {
            try {
               if(!clientHandler.clientUsername.equals(clientUsername)){
                    clientHandler.dataOutputStream.writeUTF(messageToSend);
                    clientHandler.dataOutputStream.flush();
               }
            } catch (IOException e) {
                closeEverything(socket, dataInputStream, dataOutputStream);
            }
        }
    }

    public void broadcastMessageToSender(String messageToSend){
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                if (clientHandler.clientUsername.equals(clientUsername)) {
                    byte[] msgBytes = messageToSend.getBytes("UTF-8");
                    if (msgBytes.length <= 65535) {
                        clientHandler.dataOutputStream.writeUTF(messageToSend);
                        clientHandler.dataOutputStream.flush();
                    } else {
                        int chunkSize = 30000;
                        int total = (int) Math.ceil((double) messageToSend.length() / chunkSize);
                        clientHandler.dataOutputStream.writeUTF("TEXT_RESPONSE_START|" + total);
                        clientHandler.dataOutputStream.flush();
                        for (int i = 0; i < total; i++) {
                            int start = i * chunkSize;
                            int end = Math.min(start + chunkSize, messageToSend.length());
                            clientHandler.dataOutputStream.writeUTF("TEXT_RESPONSE_CHUNK|" + messageToSend.substring(start, end));
                            clientHandler.dataOutputStream.flush();
                        }
                    }
                }
            } catch (IOException e) {
                closeEverything(socket, dataInputStream, dataOutputStream);
            }
        }
    }

    public void removeClientHandler(){
        clientHandlers.remove(this);
        broadcastMessage("SERVER: " + clientUsername + " has left");
        System.out.println("A client has disconnected!");
    }

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream){
        removeClientHandler();
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
}