package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;


import Base64EncodeDecode.Base64;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>(); //A static list that holds references to all active client handlers, allowing the server to manage and communicate with multiple clients simultaneously
    static ArrayList<FileHandler> fileHandlers = new ArrayList<>(); //A static list that holds FileHandler objects representing files available for entropy analysis, allowing the server to manage and access these files when clients request the File Entropy Analyzer service.
    private Socket socket; //Used to establish a connection between the server and a specific client
    private DataInputStream dataInputStream; //Used to read binary data from the client, such as files for entropy analysis
    private String clientUsername;
    private final String serviceOptions = "Please indicate which service you would like to use: \n1. Calculate File Entropy\n2. Base64 Encode/Decode\nType 'list' to see these options again at any time!"; //A string that contains the options for the services offered by the server, which is sent to clients to guide them in choosing a service
    private DataOutputStream dataOutputStream;
    private int fileId = 0; //A counter used to assign unique IDs to uploaded files, allowing the server to manage and reference these files
    private FileHandler currentFile = null;
    private volatile boolean isUploading = false; //A flag used to indicate whether a file upload is currently in progress, allowing the server to manage the file upload process and prevent conflicts with other operations

    public ClientHandler(Socket socket){
        try {
            this.socket = socket;
            this.dataInputStream = new DataInputStream(socket.getInputStream()); //Initializes the data input stream for reading binary data from the client
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream()); //Initializes the data output stream for sending binary data back to the client
            this.clientUsername = dataInputStream.readUTF(); //Reads the client's username from the input stream, allowing the server to identify and manage the client based on their username
            clientHandlers.add(this); //Adds the current client handler instance to the static list of client handlers, enabling the server to keep track of all connected clients and facilitate communication between them
            broadcastMessage("SERVER: " + clientUsername + " has connected"); //Sends a message to all connected clients notifying them that a new client has joined 
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
                }else if(messageFromClient.equals("1")){ //Checks if the client wants to use the Base64 Encode/Decode service
                    broadcastMessageToSender("You have selected the File Entopy Analyzer service! Please upload the file you would like to analyze using the 'upload' command"); //Sends a message to the client with instructions on how to use the File Entropy Analyzer service
                }else if(messageFromClient.equals("2")){
                    broadcastMessageToSender("You have selected the Base64 Encode/Decode service! Please enter the text you would like to encode or decode in the following format: \nTo Encode a file: encode file\nTo Decode a file: decode file\nTo Encode text: encode <text>\nTo Decode text: decode <text>\nTo upload a file please enter the 'upload' command"); //Sends a message to the client with instructions on how to use the Base64 Encode/Decode service
                }else if(messageFromClient.equals("list")){
                    broadcastMessageToSender(serviceOptions);
                } else if (messageFromClient.startsWith("encode ")){ //Checks if the client wants to encode a message
                    if(messageFromClient.equals("encode file")){
                        if(currentFile == null){
                            broadcastMessageToSender("No file uploaded. Please upload a file first by typing 'upload'.");
                        } else {
                            String encodedText = Base64.encode(currentFile.getData()); 
                            broadcastMessageToSender("Encoded File (" + currentFile.getFileName() + "): " + encodedText);
                        }
                    } else {
                        String textToEncode = messageFromClient.substring(7);
                        String encodedText = Base64.encode(textToEncode.getBytes());
                        broadcastMessageToSender("Encoded Text: " + encodedText);
                    }
                } else if (messageFromClient.startsWith("decode ")){ //Checks if the client wants to decode a message
                   if(messageFromClient.equals("decode file")){
                        if(currentFile == null){
                            broadcastMessageToSender("No file uploaded. Please upload a file first by typing 'upload'.");
                        } else {
                            String decodedText = Base64.decode(new String(currentFile.getData()));
                            broadcastMessageToSender("Decoded File (" + currentFile.getFileName() + "): " + decodedText);
                        }
                    } else {
                        String textToDecode = messageFromClient.substring(7);
                        String decodedText = Base64.decode(textToDecode);
                        broadcastMessageToSender("Decoded Text: " + decodedText);
                    }
                } else if(messageFromClient.startsWith("entropy")) {

                }else {
                    broadcastMessageToSender("Invalid input. Please enter a valid option or follow the instructions for encoding/decoding."); //Sends an error message back to the client if their input does not match any valid commands, guiding them towards correct usage of the services
                }
            } catch (Exception e) {
               closeEverything(socket, dataInputStream, dataOutputStream); 
               break; //If the client disconects the loop will break and the thread will end 
            }
        }
    }

    public static String getFileExtension(String fileName){
        int i = fileName.lastIndexOf('.'); //Finds the last occurrence of the '.' character in the file name, which is typically used to separate the file name from its extension

        if(i > 0){
            return fileName = fileName.substring(i + 1); //Extracts the substring of the file name that comes after the last '.', which is the file extension
        }else{
            return "No extension found"; //Returns a message indicating that no file extension was found if there is no '.' character in the file name
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