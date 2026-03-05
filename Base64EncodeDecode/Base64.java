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

    private byte[] outgoingData = new byte[1024];
    private Socket socket;
    private DatagramSocket datagramSocket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    final static String base64Text = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final int[] DECODE_TABLE = new int[256];

    static {
        // Default all to invalid
        for (int i = 0; i < 256; i++) {
            DECODE_TABLE[i] = -1;
        }
        // Map each Base64 character to its 6-bit value
        for (int i = 0; i < base64Text.length(); i++) {
            DECODE_TABLE[base64Text.charAt(i)] = i;
        }
        // Mark padding character
        DECODE_TABLE['='] = -2;
    }

    public Base64(Socket socket, DatagramSocket datagramSocket) {
        try {
            this.socket = socket;
            this.datagramSocket = datagramSocket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }
    
    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 3) { // process 3 bytes at a time
            int b1 = data[i] & 0xFF;
            int b2 = (i + 1 < data.length) ? (data[i + 1] & 0xFF) : 0;
            int b3 = (i + 2 < data.length) ? (data[i + 2] & 0xFF) : 0;

            int combined = (b1 << 16) | (b2 << 8) | b3; // combine into 24 bits

            // extract 6-bit segments and map to Base64 chars
            sb.append(base64Text.charAt((combined >> 18) & 0x3F));
            sb.append(base64Text.charAt((combined >> 12) & 0x3F));
            sb.append((i + 1 < data.length) ? base64Text.charAt((combined >> 6) & 0x3F) : '=');
            sb.append((i + 2 < data.length) ? base64Text.charAt(combined & 0x3F) : '=');
        }
        return sb.toString();
    }

    public static String decode(String input) {
        byte[] decodedBytes = decodeToBytes(input);
        return new String(decodedBytes);
    }

    public static byte[] decodeToBytes(String input) {
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }

        // Strip whitespace (mirrors real-world tolerance)
        input = input.replaceAll("\\s", "");

        int length = input.length();
        // Add missing padding to make length a multiple of 4
        int mod = length % 4;
        if (mod != 0) {
            int paddingNeeded = 4 - mod;
            for (int i = 0; i < paddingNeeded; i++) {
                input += "=";
            }
            length = input.length();
        }

        // Count how many '=' padding characters are at the end (0, 1, or 2)
        int padding = 0;
        if (length > 0 && input.charAt(length - 1) == '=') padding++;
        if (length > 1 && input.charAt(length - 2) == '=') padding++;
        if (padding > 2) padding = 2;

        // Each group of 4 Base64 chars decodes to 3 bytes, minus padding bytes
        int outputLen = (length / 4) * 3 - padding;
        byte[] output = new byte[outputLen];
        int outIndex = 0;

        // Process every 4-character block
        for (int i = 0; i < length; i += 4) {
            int b0 = getBase64Value(input, i);
            int b1 = getBase64Value(input, i + 1);
            int b2 = getBase64Value(input, i + 2);
            int b3 = getBase64Value(input, i + 3);

            // Combine 4x 6-bit values into 3 bytes:
            //
            //  [ b0: 6 bits ][ b1: 6 bits ][ b2: 6 bits ][ b3: 6 bits ]
            //  = [ byte 1: 8 bits ][ byte 2: 8 bits ][ byte 3: 8 bits ]
            //
            // byte 1: all 6 bits of b0  +  top 2 bits of b1
            // byte 2: bottom 4 bits of b1  +  top 4 bits of b2
            // byte 3: bottom 2 bits of b2  +  all 6 bits of b3

            int combined = (b0 << 18) | (b1 << 12) | (b2 << 6) | b3;

            // Always write byte 1
            if (outIndex < output.length)
                output[outIndex++] = (byte) ((combined >> 16) & 0xFF);

            // Only write byte 2 if not padding
            if (b2 != -2 && outIndex < output.length) {
                output[outIndex++] = (byte) ((combined >> 8) & 0xFF);
            }

            // Only write byte 3 if not padding
            if (b3 != -2 && outIndex < output.length) {
                output[outIndex++] = (byte) (combined & 0xFF);
            }
        }

        return output;
    }

    private static int getBase64Value(String input, int i) {
        char c = input.charAt(i);
        int value = (c < 256) ? DECODE_TABLE[c] : -1;

        if (value == -1) {
            throw new IllegalArgumentException(
                "Invalid Base64 character '" + c + "' at position " + i
            );
        }
        // Return 0 for padding so bit-shifting still works cleanly
        return (value == -2) ? 0 : value;
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
            String encoded = encode(fileBytes);
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
                            byte[] fileBytes = decodeToBytes(parts[2]);
                            // encode again and send back; server will treat the returned string as the encoded text
                            sendFile(fileBytes);
                        } else if(msgFromServer.startsWith("DECODE_FILE")){
                            // payload: DECODE_FILE|name|base64-of-base64-text|ext
                            String[] parts = msgFromServer.split("\\|");
                            byte[] intermediate = decodeToBytes(parts[2]);
                            byte[] fileBytes;
                            try {
                                String ascii = new String(intermediate);
                                fileBytes = decodeToBytes(ascii);
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
        Base64 base64 = new Base64(socket, datagramSocket);
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
