package ServerClientTools;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

import FileEntropyAnalyzer.EntropyAnalyzer;

import java.net.*;

public class ServiceNodeHandler implements Runnable{
    private Socket socket;
    private static ArrayList<ServiceNodeHandler> serviceNodeHandlers = new ArrayList<>();
    private DataInputStream dataInputStream; // Used to read binary data from the client, such as files for entropy
                                             // analysis
    private String service;
    private DataOutputStream dataOutputStream;

    public ServiceNodeHandler(Socket socket, String service) {
        try {
            this.socket = socket;
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.service = service;
            serviceNodeHandlers.add(this);
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }
    //This is where 
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
    @Override
    public void run() {
        // TODO Auto-generated method stub
    }
}
