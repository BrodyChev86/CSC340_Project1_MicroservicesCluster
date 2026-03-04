package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import Base64EncodeDecode.Base64;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>(); //A static list that holds references to all active client handlers, allowing the server to manage and communicate with multiple clients simultaneously
    private ArrayList<FileHandler> fileHandlers = new ArrayList<>(); //A static list that holds FileHandler objects representing files available for entropy analysis, allowing the server to manage and access these files when clients request the File Entropy Analyzer service.
    private Socket socket; //Used to establish a connection between the server and a specific client
    private DataInputStream dataInputStream; //Used to read binary data from the client, such as files for entropy analysis
    private String clientUsername;
    private final String serviceOptions = "To Encode a file: BASE64 ENCODE_FILE\nTo Decode a file: BASE64 DECODE_FILE\nTo Encode text: BASE64 ENCODE_TEXT <text>\nTo Decode text: BASE64 DECODE_TEXT <text>\nTo analyze Entropy: ENTROPY <file>\nTo upload a file please enter the 'upload' command \nType 'list' to see these options again at any time!"; //A string that contains the options for the services offered by the server, which is sent to clients to guide them in choosing a service
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
    //Everything that is run in this method will be run in a sperate thread
    public void run() {
        String messageFromClient;
        while(socket.isConnected()){
            try {
                messageFromClient = dataInputStream.readUTF().trim(); //Waits for a message from a client, needs to be done on a seperate thread to avoid blocking the main server thread and allowing the server to handle multiple clients concurrently
                if (messageFromClient.contains(":")) {
                    messageFromClient = messageFromClient.substring(messageFromClient.indexOf(":") + 1).trim();
                }

                if(messageFromClient.equals("upload")){
                    broadcastMessageToSender("FILE_UPLOAD");
                    isUploading = true;
                    fileUpload(); //If the client sends a message indicating that they want to upload a file, call the fileUpload method to handle the file upload process
                }else if(messageFromClient.equals("exit")){
                    removeClientHandler(); 
                    break; //If the client disconects the loop will break and the thread will end 
                }else if(messageFromClient.equals("list")){
                    broadcastMessageToSender(serviceOptions);
                } else if (messageFromClient.startsWith("BASE64")) {
                    String command = messageFromClient.substring("BASE64".length()).trim();
                    if (command.equals("ENCODE_FILE") || command.equals("DECODE_FILE")) {
                        sendFileToNode(command); // file operation
                    } else {
                        sendMessageToNode(command); // text operation
                    }
                }
                else if(messageFromClient.startsWith("ENTROPY")) {
                    sendFileToNode(messageFromClient);
                }else {
                    broadcastMessageToSender("Invalid input. Please enter a valid option or follow the instructions for encoding/decoding."); //Sends an error message back to the client if their input does not match any valid commands, guiding them towards correct usage of the services
                }
            } catch (Exception e) {
               closeEverything(socket, dataInputStream, dataOutputStream); 
               break; //If the client disconects the loop will break and the thread will end 
            }
        }
    }
    public void sendMessageToNode(String input) throws InterruptedException {
        String base64 = null;
        System.out.println("Available service nodes: " + 
        ServiceNodeHandler.getServiceNodeHandlers().size());
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("BASE64".equals(serviceNodeHandler.getService())) {
                base64 = serviceNodeHandler.requestService(input); //Sends a request to the service node handler for the entropy service, passing the input string and waiting for a response, which is then parsed as a double representing the calculated entropy
                broadcastMessageToSender("Result: " + base64); //Sends the result of the Base64 operation back to the client
                return;
            }
        }
        throw new RuntimeException("[ERROR] NODE NOT FOUND"); //Throws an exception if no entropy service node is available to handle the request, indicating that the requested service cannot be performed at this time
    }

    public void sendFileToNode(String messageFromClient) throws InterruptedException {
        for (ServiceNodeHandler serviceNodeHandler : ServiceNodeHandler.getServiceNodeHandlers()) {
            if ("ENTROPY".equals(serviceNodeHandler.getService())) {
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
                if(currentFile == null){
                    broadcastMessageToSender("No file uploaded. Please upload a file to encode/decode.");
                    return;
                }else{
                    String fileExtension = currentFile.getFileExtension();
                    String fileName = currentFile.getFileName();
                    String fileAsString = java.util.Base64.getEncoder().encodeToString(currentFile.getData());
                    String payload = ""+ messageFromClient + "|" + currentFile.getFileName() + "|" + fileAsString + "|" + fileExtension;
                    String responseStr = serviceNodeHandler.requestServiceFile(payload);

                    // handle encode vs decode differently
                    if (messageFromClient.equals("ENCODE_FILE")) {
                        // responseStr itself is a Base64 representation of the original bytes
                        // convert to bytes so fileDownload will wrap it again for client
                        byte[] fileBytes = responseStr.getBytes();
                        // change extension to text to avoid confusion
                        String outExt = "txt";
                        String outName = fileName + "_encoded";
                        fileDownload(outName, outExt, fileBytes);
                    } else {
                        // DECODE_FILE: node returned Base64 of the decoded bytes; decode it now
                        byte[] fileBytes = java.util.Base64.getDecoder().decode(responseStr);
                        fileDownload(fileName, fileExtension, fileBytes);
                    }

                    broadcastMessageToSender("FILE HAS BEEN RETURNED");
                    return;
                }
            }
        }
        throw new RuntimeException("[ERROR] NODE NOT FOUND"); //Throws an exception if no entropy service node is available to handle the request, indicating that the requested service cannot be performed at this time
    }

    public static String getFileExtension(String fileName){
        int i = fileName.lastIndexOf('.'); //Finds the last occurrence of the '.' character in the file name, which is typically used to separate the file name from its extension

        if(i > 0){
            return fileName = fileName.substring(i + 1); //Extracts the substring of the file name that comes after the last '.', which is the file extension
        }else{
            return "No extension found"; //Returns a message indicating that no file extension was found if there is no '.' character in the file name
        }
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