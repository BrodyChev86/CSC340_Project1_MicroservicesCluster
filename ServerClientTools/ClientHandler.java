package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>(); //A static list that holds references to all active client handlers, allowing the server to manage and communicate with multiple clients simultaneously
    private ArrayList<FileHandler> fileHandlers = new ArrayList<>(); //A static list that holds FileHandler objects representing files available for entropy analysis, allowing the server to manage and access these files when clients request the File Entropy Analyzer service.
    private Socket socket; //Used to establish a connection between the server and a specific client
    private DataInputStream dataInputStream; //Used to read binary data from the client, such as files for entropy analysis
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
    private DataOutputStream dataOutputStream;
    private int fileId = 0; //A counter used to assign unique IDs to uploaded files, allowing the server to manage and reference these files
    private FileHandler currentFile = null;
    private volatile boolean isUploading = false; //A flag used to indicate whether a file upload is currently in progress, allowing the server to manage the file upload process and prevent conflicts with other operations

    public ClientHandler(Socket socket, String clientUsername) {
        try {
            this.socket = socket;
            this.dataInputStream = new DataInputStream(socket.getInputStream()); //Initializes the data input stream for reading binary data from the client
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream()); //Initializes the data output stream for sending binary data back to the client
            this.clientUsername = clientUsername;
            clientHandlers.add(this); //Adds the current client handler instance to the static list of client handlers, enabling the server to keep track of all connected clients and facilitate communication between them
            broadcastMessageToSender(serviceOptions); //Sends a message to the client that just connected asking them to choose a service
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream); 
        }
    }


    @Override
    //Everything that is run in this method will be run in a separate thread
    public void run() {
        String messageFromClient;
        while(socket.isConnected()){
            try {
                messageFromClient = dataInputStream.readUTF().trim(); //Waits for a message from a client, needs to be done on a separate thread to avoid blocking the main server thread and allowing the server to handle multiple clients concurrently
                if (messageFromClient.contains(":")) {
                    messageFromClient = messageFromClient.substring(messageFromClient.indexOf(":") + 1).trim();
                }

                if(messageFromClient.equals("upload")){
                    broadcastMessageToSender("FILE_UPLOAD");
                    isUploading = true;
                    fileUpload(); //If the client sends a message indicating that they want to upload a file, call the fileUpload method to handle the file upload process
                }else if(messageFromClient.equals("exit")){
                    removeClientHandler(); 
                    break; //If the client disconnects the loop will break and the thread will end 
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
                        // expect: DECODE_FILE png  (or just DECODE_FILE to fall back)
                        String[] parts = command.split("\\s+", 2);
                        String targetExt; //If the user specified an extension, use it. Otherwise, fall back to the original file's extension or "bin" if we can't determine it, ensuring that the decoded file is saved with an appropriate extension for easy identification and use by the client after decoding
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
                }else if (messageFromClient.startsWith("CSV")) {
                    if (!isNodeConnected("CSV_Stats")) {
                        broadcastMessageToSender("[ERROR] CSV_Stats service node not connected.");
                        continue;
                    }
                    sendToCSVNode();
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
                    // image transformer commands
                    sendFileToImageNode(messageFromClient);
                } else if(messageFromClient.equals("\n"))
                {
                    // ignore empty messages
                }
                else {
                    broadcastMessageToSender("Invalid input. Please enter a valid option or follow the instructions for encoding/decoding."); //Sends an error message back to the client if their input does not match any valid commands, guiding them towards correct usage of the services
                }
            } catch (Exception e) {
               closeEverything(socket, dataInputStream, dataOutputStream); 
               break; //If the client disconnects the loop will break and the thread will end 
            }
        }
    }
    // simple wrapper so callers don't have to reference ServiceNodeHandler directly
    private boolean isNodeConnected(String serviceName) {
        return ServiceNodeHandler.isNodeConnected(serviceName);
    }

    /**
     * Send a lightweight ping message to the given handler and expect a pong.
     * This allows us to detect a dropped or unresponsive node even if the
     * socket remains in the connected list.
     */
    private boolean verifyNode(ServiceNodeHandler handler) {
        // quick pre‑checks to avoid even sending a ping if the socket has
        // already closed or the heartbeat is stale.  The static helper will
        // also purge the handler from global maps.
        if (!ServiceNodeHandler.isNodeConnected(handler.getService())) {
            return false;
        }

        // do a brief handshake; mostly this catches the rare case where the
        // connection dropped between the pre‑check and now.
        try {
            return handler.handshake(200);
        } catch (Exception e) {
            return false;
        }
    }

    public void sendMessageToNode(String input) throws InterruptedException {
        // sanity check: caller should have already verified but be defensive
        if (!isNodeConnected("BASE64")) {
            broadcastMessageToSender("[ERROR] BASE64 service node not connected.");
            return;
        }

        String base64 = null;
        System.out.println("Available service nodes: " + 
        ServiceNodeHandler.getServiceNodeHandlers().size());
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("BASE64".equals(serviceNodeHandler.getService())) {
                if (!verifyNode(serviceNodeHandler)) {
                    broadcastMessageToSender("[ERROR] BASE64 service node is unresponsive.");
                    return;
                }
                base64 = serviceNodeHandler.requestService(input); 
                broadcastMessageToSender("Result: " + base64); //Sends the result of the Base64 operation back to the client
                return;
            }
        }
        broadcastMessageToSender("[ERROR] No appropriate service node found to handle the request.");
        return;
    }

    public void sendFileToNode(String messageFromClient) throws InterruptedException {
            // both BASE64 and ENTROPY use this helper, ensure at least one of them exists
            if (!isNodeConnected("BASE64") && !isNodeConnected("ENTROPY")) {
                broadcastMessageToSender("[ERROR] No appropriate service node (BASE64 or ENTROPY) is connected.");
                return;
            }
            for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
                if ("ENTROPY".equals(serviceNodeHandler.getService())) {
                    if (!verifyNode(serviceNodeHandler)) {
                        broadcastMessageToSender("[ERROR] ENTROPY service node is unresponsive.");
                        return;
                    }
                    if (currentFile == null){
                        broadcastMessageToSender("No file uploaded. Please upload a file to analyze its entropy.");
                        return;
                    }else{
                        String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
                        String payload = "ENTROPY|" + currentFile.getFileName() + "|" + fileAsString;
                        String response = serviceNodeHandler.requestService(payload);
                        broadcastMessageToSender("File Entropy: " + response);
                        return;
                    }
                }
                if("BASE64".equals(serviceNodeHandler.getService())){
                    if (!verifyNode(serviceNodeHandler)) {
                        broadcastMessageToSender("[ERROR] BASE64 service node is unresponsive.");
                        return;
                    }
                    if(currentFile == null){
                        broadcastMessageToSender("No file uploaded. Please upload a file to encode/decode.");
                        return;
                    }else{
                        String fileExtension = currentFile.getFileExtension();
                        String fileName = currentFile.getFileName();
                        String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());

                        if (messageFromClient.equals("ENCODE_FILE")) {
                            String payload = "ENCODE_FILE|" + fileName + "|" + fileAsString + "|" + fileExtension;
                            String responseStr = serviceNodeHandler.requestServiceFile(payload);
                            byte[] fileBytes = responseStr.getBytes();
                            fileDownload(fileName + "_encoded", "txt", fileBytes);

                        } else if (messageFromClient.startsWith("DECODE_FILE")) {
                            // pull out the target extension the user specified
                            String[] parts = messageFromClient.split("\\|", 2);
                            String targetExt; //If the user specified an extension, use it. Otherwise, fall back to the original file's extension or "bin" if we can't determine it, ensuring that the decoded file is saved with an appropriate extension for easy identification and use by the client after decoding
                            if (parts.length > 1) {
                                targetExt = parts[1].trim();
                            } else {
                                targetExt = fileExtension;
                            }
                            String baseName; //If the original file name contains an extension, remove it to create a base name for the decoded file. If there is no extension, use the entire file name as the base name, ensuring that the decoded file is named appropriately based on the original file name and the presence of an extension for clarity and organization in the client's file system after downloading
                            if (fileName.contains(".")) {
                                baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                            } else {
                                baseName = fileName;
                            }
                            String payload = "DECODE_FILE|" + fileName + "|" + fileAsString + "|" + fileExtension;
                            String responseStr = serviceNodeHandler.requestServiceFile(payload);
                            byte[] fileBytes = java.util.Base64.getDecoder().decode(responseStr);

                            // reconstruct with the user-specified extension
                            fileDownload(baseName + "_decoded", targetExt, fileBytes);
                        }

                        broadcastMessageToSender("FILE HAS BEEN RETURNED");
                        return;
                    }
                }
            }
            broadcastMessageToSender("[ERROR] No appropriate service node found to handle the request."); 
            return;
        }

    public void sendToCSVNode() throws InterruptedException {
        if (!isNodeConnected("CSV_Stats")) {
            broadcastMessageToSender("[ERROR] CSV_Stats service node not connected.");
            return;
        }
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("CSV_Stats".equals(serviceNodeHandler.getService())) {
                if (!verifyNode(serviceNodeHandler)) {
                    broadcastMessageToSender("[ERROR] CSV_Stats service node is unresponsive.");
                    return;
                }
                if (currentFile == null) {
                    broadcastMessageToSender("No file uploaded. Please upload a CSV file first.");
                    return;
                } else {
                    // ensure extension .csv
                    String ext = currentFile.getFileExtension().toLowerCase();
                    if (!ext.equals("csv")) {
                        broadcastMessageToSender("Please upload a file with .csv extension for CSV service.");
                        return;
                    }
                    String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
                    String payload = "CSV|" + currentFile.getFileName() + "|" + fileAsString;
                    String response = serviceNodeHandler.requestService(payload);
                    broadcastMessageToSender(response);
                    return;
                }
            }
        }
        // fallback though check above should have caught it
        broadcastMessageToSender("[ERROR] CSV service node not connected.");
        return;
    }

    // Sends the currently uploaded file to a TOPK node. optional k may follow the word FILE
    public void sendFileToTopKNode(String messageFromClient) throws InterruptedException {
        if (!isNodeConnected("TOPK")) {
            broadcastMessageToSender("[ERROR] TOPK service node not connected.");
            return;
        }
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("TOPK".equals(serviceNodeHandler.getService())) {
                if (!verifyNode(serviceNodeHandler)) {
                    broadcastMessageToSender("[ERROR] TOPK service node is unresponsive.");
                    return;
                }
                if (currentFile == null) {
                    broadcastMessageToSender("No file uploaded. Please upload a file before requesting top-k terms.");
                    return;
                } else {
                    // ensure file extension is .txt
                    String ext = currentFile.getFileExtension();
                    if (!"txt".equalsIgnoreCase(ext)) {
                        broadcastMessageToSender("TOPK service only supports .txt files. Please upload a .txt file.");
                        return;
                    }

                    // parse optional k after the word FILE
                    String[] tokens = messageFromClient.split("\\s+");
                    int k = 3;
                    if (tokens.length >= 3) {
                        try {
                            k = Integer.parseInt(tokens[2]);
                        } catch (NumberFormatException nfe) {
                            // ignore, keep default
                        }
                    }

                    String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
                    String payload = "TOPK|" + k + "|" + fileAsString;
                    String response = serviceNodeHandler.requestService(payload);
                    broadcastMessageToSender(response);
                    return;
                }
            }
        }
        broadcastMessageToSender("[ERROR] TOPK service node not connected.");
        return;
    }

    // send the current file to an ImageTransformer node
    public void sendFileToImageNode(String messageFromClient) throws InterruptedException {
        if (!isNodeConnected("IMGT")) {
            broadcastMessageToSender("[ERROR] IMGT service node not connected.");
            return;
        }
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("IMGT".equals(serviceNodeHandler.getService())) {
                if (!verifyNode(serviceNodeHandler)) {
                    broadcastMessageToSender("[ERROR] IMGT service node is unresponsive.");
                    return;
                }
                if (currentFile == null) {
                    broadcastMessageToSender("No file uploaded. Please upload an image to transform.");
                    return;
                }

                // only allow png or jpg (jpeg treated as jpg)
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

                String response = serviceNodeHandler.requestServiceFile(payload);
                byte[] fileBytes = java.util.Base64.getDecoder().decode(response);

                // choose a descriptive name
                String baseName = currentFile.getFileName();
                int dot = baseName.lastIndexOf('.');
                if (dot > 0) baseName = baseName.substring(0, dot);

                String suffix;
                if (messageFromClient.startsWith("IMGT ROTATE")) suffix = "_rot";
                else if (messageFromClient.startsWith("IMGT RESIZE")) suffix = "_res";
                else suffix = "_gray";

                fileDownload(baseName + suffix, ext, fileBytes);
                broadcastMessageToSender("Image transformation complete.");
                return;
            }
        }
        broadcastMessageToSender("[ERROR] ImageTransformer service node not connected.");
        return;
    }
    public static String getFileExtension(String fileName){
        int i = fileName.lastIndexOf('.'); //Finds the last occurrence of the '.' character in the file name, which is typically used to separate the file name from its extension

        if(i > 0){
            return fileName = fileName.substring(i + 1); //Extracts the substring of the file name that comes after the last '.', which is the file extension
        }else{
            return "No extension found"; //Returns a message indicating that no file extension was found if there is no '.' character in the file name
        }
    }

    // handle plain text TOPK requests (not files)
    public void sendTextToTopKNode(String command) throws InterruptedException {
        if (!isNodeConnected("TOPK")) {
            broadcastMessageToSender("[ERROR] TOPK service node not connected.");
            return;
        }
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("TOPK".equals(serviceNodeHandler.getService())) {
                if (!verifyNode(serviceNodeHandler)) {
                    broadcastMessageToSender("[ERROR] TOPK service node is unresponsive.");
                    return;
                }
                if (command == null || command.isEmpty()) {
                    broadcastMessageToSender("Please provide text to analyze or use 'TOPK [k] <text>'.");
                    return;
                }
                // parse optional k at beginning
                String[] parts = command.split("\\s+", 2);
                int k = 3;
                String text;
                if (parts.length == 2) {
                    // try interpret first token as k
                    try {
                        k = Integer.parseInt(parts[0]);
                        text = parts[1];
                    } catch (NumberFormatException nfe) {
                        text = command;
                    }
                } else {
                    // single token: could be a number or text
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
                String response = serviceNodeHandler.requestService(payload);
                broadcastMessageToSender(response);
                return;
            }
        }
        broadcastMessageToSender("[ERROR] TOPK service node not connected.");
        return;
    }

    public void fileDownload(String fileName, String fileExtension, byte[] fileContent) {
        try {
            String fileAsString = java.util.Base64.getEncoder().encodeToString(fileContent);
            int chunkSize = 30000; // safe size under the 65535 UTF limit
            int totalChunks = (int) Math.ceil((double) fileAsString.length() / chunkSize);

            // Tell the client how many chunks to expect
            dataOutputStream.writeUTF("FILE_DOWNLOAD_START|" + fileName + "|" + fileExtension + "|" + totalChunks);
            dataOutputStream.flush();

            // Send each chunk
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

    //Created following a tutorial on YouTube by WittCode (https://www.youtube.com/watch?v=GLrlwwyd1gY&t=97s) and modified by BrodyChev86 to fit the requirements of the project
    public void fileUpload(){
         try{
            while(isUploading){

                int fileNameLength = dataInputStream.readInt();
                if (fileNameLength == 0) { // sentinel value received — client is done uploading
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

                        fileHandlers.add(new FileHandler(fileId, fileName, fileDataBytes, getFileExtension(fileName))); //Creates a new FileHandler object with the received file data and adds it to the static list of file handlers, allowing the server to manage and access the uploaded file for entropy analysis when requested by clients
                        currentFile = fileHandlers.get(fileHandlers.size() - 1); //Sets the current file to the most recently uploaded file, allowing the server to reference this file for any immediate operations that may be requested by the client after uploading
                        fileId++; //Increments the file ID counter to ensure that the next uploaded file receives a unique ID
                        System.out.println("Received file: " + fileName + " with size: " + fileDataBytes.length + " bytes"); //Prints a message to the server console indicating that a file has been received, along with its name and size in bytes for verification and debugging purposes

                    }

                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Sends a message to all clients except the sender, allowing for communication between clients while preventing the sender from receiving their own message back
    public void broadcastMessage(String messageToSend){
        for (ClientHandler clientHandler : clientHandlers) {
            try {
               if(!clientHandler.clientUsername.equals(clientUsername)){ //Checks if the client handler is not the same as the one that sent the message, preventing the server from sending the message back to the sender
                    clientHandler.dataOutputStream.writeUTF(messageToSend);
                    clientHandler.dataOutputStream.flush();
               }
            } catch (IOException e) {
                closeEverything(socket, dataInputStream, dataOutputStream); 
            }
        }
    }


    //Sends a message only to the sender, allowing the server to communicate directly with the client that sent a message without broadcasting it to all clients
    public void broadcastMessageToSender(String messageToSend){
        for (ClientHandler clientHandler : clientHandlers) {
            try {
               if(clientHandler.clientUsername.equals(clientUsername)){ //Checks if the client handler is the same as the one that sent the message, allowing the server to send a message back to the sender
                    clientHandler.dataOutputStream.writeUTF(messageToSend);
                    clientHandler.dataOutputStream.flush();
               }
            } catch (IOException e) {
                closeEverything(socket, dataInputStream, dataOutputStream); 
            }
        }
    }
    
    public void removeClientHandler(){
        clientHandlers.remove(this); //Removes the current client handler instance from the static list of client handlers, allowing the server to stop tracking and communicating with the client that has disconnected
        broadcastMessage("SERVER: " + clientUsername + " has left"); //Sends a message to all connected clients notifying them that a client has left 
        System.out.println("A client has disconnected!"); //Prints a message to the server console indicating that a client has disconnected
    }

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream){
        removeClientHandler(); //Removes the client handler from the list of client handlers, ensuring that the server no longer tracks or communicates with the disconnected client
        try {
            if (dataInputStream != null) {
                dataInputStream.close(); //Closes the input stream reader, releasing any resources associated with it and preventing further reading from the client
            }
            if (dataOutputStream != null) {
                dataOutputStream.close(); //Closes the output stream writer, releasing any resources associated with it and preventing further writing to the client
            }
            if (socket != null) {
                socket.close(); //Closes the socket connection between the server and the client, releasing any resources associated with it and preventing further communication with the client
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

   
}