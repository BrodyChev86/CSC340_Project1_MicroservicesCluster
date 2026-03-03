package Base64EncodeDecode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

public class Base64{

    private static byte[] fileData;
    private Socket socket;
    private DatagramSocket datagramSocket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private String username;
    private java.util.List<File> filesToSend = new java.util.ArrayList<>();

    public Base64(Socket socket, DatagramSocket datagramSocket, String username) {
        try {
            this.socket = socket;
            this.datagramSocket = datagramSocket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.username = username;
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }
    
    public static String encode(byte[] data) {
            return java.util.Base64.getEncoder().encodeToString(data);
    }

    public static String decode(String input) {
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(input);
        return new String(decodedBytes);
    }

    public static byte[] decodeToBytes(String input) {
        return java.util.Base64.getDecoder().decode(input);
    }

    public void setFileData(byte[] fileData) {
        Base64.fileData = fileData;
    }

    public BufferedImage decodeToImage(byte[] decodedBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(decodedBytes));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendEncodedText(byte[] data) {
        try {
            dataOutputStream.writeUTF(username);
            dataOutputStream.flush();


            while (socket.isConnected()) {
                String messageToSend = encode(data);
                dataOutputStream.writeUTF(username + ": " + messageToSend);
                dataOutputStream.flush();
            }
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendDecodedText(String text) {
        try {
            dataOutputStream.writeUTF(username);
            dataOutputStream.flush();


            while (socket.isConnected()) {
                String messageToSend = decode(text);
                dataOutputStream.writeUTF(username + ": " + messageToSend);
                dataOutputStream.flush();
            }
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendErrorMessage() {
        try {
            dataOutputStream.writeUTF(username);
            dataOutputStream.flush();
            dataOutputStream.writeUTF("ERROR: " + "Invalid input for Base64 encoding/decoding. Please provide valid text or file data.");
            dataOutputStream.flush();
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendHeartbeat() {
        try {
            String heartbeatMessage = "NODE_ALIVE";
            dataOutputStream.writeUTF(heartbeatMessage);
            dataOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void listenForMessage(){
        new Thread(new Runnable(){
            @Override
            public void run() {
                String msgFromServer;

                while (socket.isConnected()) {
                    try {
                        msgFromServer = dataInputStream.readUTF();
                        if(msgFromServer.startsWith("ENCODE_TEXT")){
                            String textToEncode = msgFromServer.substring("ENCODE_TEXT".length()).trim();
                            sendEncodedText(textToEncode.getBytes());
                        } else if(msgFromServer.startsWith("DECODE_TEXT")){
                            String textToDecode = msgFromServer.substring("DECODE_TEXT".length()).trim();
                            sendDecodedText(textToDecode);
                        } else {
                            sendErrorMessage();
                        }
                    } catch (IOException e) {
                        closeEverything(socket, dataInputStream, dataOutputStream);
                    }
                }
            }
        }).start();

    }

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
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

   


     public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", 1234);
        DatagramSocket datagramSocket = new DatagramSocket();
        Base64 base64 = new Base64(socket, datagramSocket, "Base64Node");
        
        base64.dataOutputStream.writeUTF("NODE_HELLO"); 
        base64.dataOutputStream.flush();

        Timer timer = new Timer(true); // daemon=true so it doesn't block JVM shutdown
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                base64.sendHeartbeat();
                System.out.println("Sent heartbeat to server: NODE_ALIVE");
            }
        }, 0, 5000);

        base64.listenForMessage();
    }

}
