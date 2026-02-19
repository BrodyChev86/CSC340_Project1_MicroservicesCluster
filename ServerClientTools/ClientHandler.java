package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;


import Base64EncodeDecode.Base64;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>(); //A static list that holds references to all active client handlers, allowing the server to manage and communicate with multiple clients simultaneously
    private Socket socket; //Used to establish a connection between the server and a specific client
    private BufferedReader bufferedReader; //Used to read incoming messages from the client
    private BufferedWriter bufferedWriter; //Used to send messages back to the client
    private String clientUsername;
    private final String serviceOptions = "Please indicate which service you would like to use: \n1. Base64 Encode/Decode";

    public ClientHandler(Socket socket){
        try {
            this.socket = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.clientUsername = bufferedReader.readLine(); //Reads the client's username from the input stream, allowing the server to identify and manage the client based on their username
            clientHandlers.add(this); //Adds the current client handler instance to the static list of client handlers, enabling the server to keep track of all connected clients and facilitate communication between them
            broadcastMessage("SERVER: " + clientUsername + " has connected"); //Sends a message to all connected clients notifying them that a new client has joined 
            broadcastMessageToSender(serviceOptions); //Sends a message to the client that just connected asking them to choose a service
        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter); 
        }
    }


    @Override
    //Everything that is run in this method will be run in a sperate thread
    public void run() {
        String messageFromClient;
        while(socket.isConnected()){
            try {
                messageFromClient = bufferedReader.readLine().trim(); //Waits for a message from a client, needs to be done on a seperate thread to avoid blocking the main server thread and allowing the server to handle multiple clients concurrently
                if (messageFromClient.contains(":")) {
                    messageFromClient = messageFromClient.substring(messageFromClient.indexOf(":") + 1).trim();
                }
                if(messageFromClient.equals("1")){ //Checks if the client wants to use the Base64 Encode/Decode service
                    broadcastMessageToSender("You have selected the Base64 Encode/Decode service! Please enter the text you would like to encode or decode in the following format: \nTo Encode: encode <text> \nTo Decode: decode <text>"); //Sends a message to the client with instructions on how to use the Base64 Encode/Decode service
                }else if(messageFromClient.equals("list")){
                    broadcastMessageToSender(serviceOptions);
                } else if (messageFromClient.startsWith("encode ")){ //Checks if the client wants to encode a message
                    String textToEncode = messageFromClient.substring(7); //Extracts the text to encode from the client's message by removing the "encode " prefix, allowing the server to process only the relevant text for encoding
                    String encodedText = Base64.encode(textToEncode.getBytes()); //Encodes the extracted text using Base64 encoding, converting it into a format that can be easily transmitted and decoded by clients that understand Base64
                    broadcastMessageToSender("Encoded Text: " + encodedText); //Sends the encoded text back to the client that requested it, allowing them to see the result of their encoding request
                } else if (messageFromClient.startsWith("decode ")){ //Checks if the client wants to decode a message
                    String textToDecode = messageFromClient.substring(7); //Extracts the text to decode from the client's message by removing the "decode " prefix, allowing the server to process only the relevant text for decoding
                    String decodedText = Base64.decode(textToDecode); //Decodes the extracted text using Base64 decoding, converting it back into its original format for clients that understand Base64
                    broadcastMessageToSender("Decoded Text: " + decodedText); //Sends the decoded text back to the client that requested it, allowing them to see the result of their decoding request
                } else {
                    broadcastMessageToSender("Invalid input. Please enter a valid option or follow the instructions for encoding/decoding."); //Sends an error message back to the client if their input does not match any valid commands, guiding them towards correct usage of the services
                }
            } catch (Exception e) {
               closeEverything(socket, bufferedReader, bufferedWriter); 
               break; //If the client disconects the loop will break and the thread will end 
            }
        }
        throw new UnsupportedOperationException("Unimplemented method 'run'");
    }

    //Sends a message to all clients except the sender, allowing for communication between clients while preventing the sender from receiving their own message back
    public void broadcastMessage(String messageToSend){
        for (ClientHandler clientHandler : clientHandlers) {
            try {
               if(!clientHandler.clientUsername.equals(clientUsername)){ //Checks if the client handler is not the same as the one that sent the message, preventing the server from sending the message back to the sender
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
               }
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter); 
            }
        }
    }

    //Sends a message only to the sender, allowing the server to communicate directly with the client that sent a message without broadcasting it to all clients
    public void broadcastMessageToSender(String messageToSend){
        for (ClientHandler clientHandler : clientHandlers) {
            try {
               if(clientHandler.clientUsername.equals(clientUsername)){ //Checks if the client handler is the same as the one that sent the message, allowing the server to send a message back to the sender
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
               }
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter); 
            }
        }
    }
    
    public void removeClientHandler(){
        clientHandlers.remove(this); //Removes the current client handler instance from the static list of client handlers, allowing the server to stop tracking and communicating with the client that has disconnected
        broadcastMessage("SERVER: " + clientUsername + " has left the chat!"); //Sends a message to all connected clients notifying them that a client has left 
    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter){
        removeClientHandler(); //Removes the client handler from the list of client handlers, ensuring that the server no longer tracks or communicates with the disconnected client
        try {
            if (bufferedReader != null) {
                bufferedReader.close(); //Closes the input stream reader, releasing any resources associated with it and preventing further reading from the client
            }
            if (bufferedWriter != null) {
                bufferedWriter.close(); //Closes the output stream writer, releasing any resources associated with it and preventing further writing to the client
            }
            if (socket != null) {
                socket.close(); //Closes the socket connection between the server and the client, releasing any resources associated with it and preventing further communication with the client
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}