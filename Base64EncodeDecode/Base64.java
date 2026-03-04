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
    private byte[] outgoingData = new byte[1024];
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
            String messageToSend = encode(data);
            dataOutputStream.writeUTF(messageToSend);
            dataOutputStream.flush();
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendDecodedText(String text) {
        try {
            String messageToSend = decode(text);
            dataOutputStream.writeUTF(messageToSend);
            dataOutputStream.flush();
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    /**
     * Send the supplied raw bytes back to the server but avoid writeUTF size limits.
     * The data is first Base64 encoded (the protocol expected by the server) and
     * then split into manageable chunks (< 64K) which are transmitted as a sequence
     * of UTF messages.  The server-side handler understands the start/ chunk markers
     * and will reassemble the full string before returning it to the client.
     */
    private void sendFile(byte[] fileBytes) {
        try {
            String encoded = java.util.Base64.getEncoder().encodeToString(fileBytes);
            int chunkSize = 60000; // safe under the 65535 byte limit of writeUTF
            int totalChunks = (encoded.length() + chunkSize - 1) / chunkSize;
            // first send header telling the server how many pieces to expect
            dataOutputStream.writeUTF("FILE_RESPONSE_START|" + totalChunks);
            dataOutputStream.flush();
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, encoded.length());
                String chunk = encoded.substring(start, end);
                dataOutputStream.writeUTF("FILE_RESPONSE_CHUNK|" + chunk);
                dataOutputStream.flush();
            }
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendErrorMessage() {
        try {
            dataOutputStream.writeUTF("[ERROR] Invalid input for Base64 encoding/decoding. Please provide valid text or file data.");
            dataOutputStream.flush();
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendHeartbeat(String message, InetAddress address, int port) throws IOException {
        outgoingData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(outgoingData, outgoingData.length, address, port);
        datagramSocket.send(sendPacket);
    }
    

    public void listenForMessage(){
        new Thread(new Runnable(){
            @Override
            public void run() {
                while (socket.isConnected()) {
                    try {
                        String msgFromServer = dataInputStream.readUTF();
                        // reassemble chunked request if server split a large payload
                        if (msgFromServer.startsWith("FILE_REQUEST_START|")) {
                            String[] hdr = msgFromServer.split("\\|");
                            int chunks = Integer.parseInt(hdr[1]);
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < chunks; i++) {
                                String chunkMsg = dataInputStream.readUTF();
                                if (chunkMsg.startsWith("FILE_REQUEST_CHUNK|")) {
                                    sb.append(chunkMsg.substring("FILE_REQUEST_CHUNK|".length()));
                                } else {
                                    sb.append(chunkMsg);
                                }
                            }
                            msgFromServer = sb.toString();
                        }
                        if(msgFromServer.startsWith("ENCODE_TEXT")){
                            String textToEncode = msgFromServer.substring("ENCODE_TEXT".length()).trim();
                            sendEncodedText(textToEncode.getBytes());
                        } else if(msgFromServer.startsWith("DECODE_TEXT")){
                            String textToDecode = msgFromServer.substring("DECODE_TEXT".length()).trim();
                            sendDecodedText(textToDecode);
                        } else if(msgFromServer.startsWith("ENCODE_FILE")){
                            // payload: ENCODE_FILE|name|base64-of-original-bytes|ext
                            String[] parts = msgFromServer.split("\\|");
                            // decode once to get original file bytes
                            byte[] fileBytes = java.util.Base64.getDecoder().decode(parts[2]);
                            // encode again and send back; server will treat the returned string as the encoded text
                            sendFile(fileBytes);
                        } else if(msgFromServer.startsWith("DECODE_FILE")){
                            // payload: DECODE_FILE|name|base64-of-base64-text|ext
                            String[] parts = msgFromServer.split("\\|");
                            byte[] intermediate = java.util.Base64.getDecoder().decode(parts[2]);
                            byte[] fileBytes;
                            try {
                                String ascii = new String(intermediate);
                                fileBytes = java.util.Base64.getDecoder().decode(ascii);
                            } catch (IllegalArgumentException e) {
                                // not valid base64, just return what we got
                                fileBytes = intermediate;
                            }
                            sendFile(fileBytes);
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
        String nodeId = java.util.UUID.randomUUID().toString();
        
        base64.dataOutputStream.writeUTF("NODE_HELLO"); 
        base64.dataOutputStream.flush();

        base64.dataOutputStream.writeUTF("BASE64");
        base64.dataOutputStream.flush();

        base64.dataOutputStream.writeUTF(nodeId);
        base64.dataOutputStream.flush();

        Timer timer = new Timer(true); // daemon=true so it doesn't block JVM shutdown
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    base64.sendHeartbeat("NODE_ALIVE|" + nodeId, InetAddress.getByName("localhost"), 1235);
                    System.out.println("Sent heartbeat to server: NODE_ALIVE");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5000);

        base64.listenForMessage();
    }

}
