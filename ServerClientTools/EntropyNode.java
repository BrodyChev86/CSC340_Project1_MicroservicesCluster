package ServerClientTools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.Scanner;


public class EntropyNode {
    private String service = "entropy";
    private Socket socket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;

    public EntropyNode(Socket socket, String service) {
        try {
            this.socket = socket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.service = service;
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void listenForMessage() {
        new Thread(() -> {
            while (socket.isConnected()) {
                try {
                    String msgFromServer = dataInputStream.readUTF();

                    // Calculate entropy
                    double entropy = FileEntropyAnalyzer.EntropyAnalyzer
                            .calculateEntropy(msgFromServer);

                    // Send result back to server
                    dataOutputStream.writeUTF(String.valueOf(entropy));
                    dataOutputStream.flush();

                } catch (IOException e) {
                    closeEverything(socket, dataInputStream, dataOutputStream);
                    break;
                }
            }
        }).start();

    }

    /**
     * Create and display a small Swing window that lets the user choose one or more
     * files and upload them to the server. When the window closes a special message
     * is
     * sent to indicate completion.
     */

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

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your username for the server: ");
        String service = "entropy";
        Socket socket = new Socket("localhost", 1234); // Creates a socket that connects to the server running on
                                                       // localhost at port 1234, allowing the client to communicate
                                                       // with the server and other clients connected to it
        EntropyNode entropyNode = new EntropyNode(socket, service);

        entropyNode.dataOutputStream.writeUTF("NODE_HELLO"); // Sends an initial message to the server to identify
                                                                // itself as a node, allowing the server to manage the
                                                                // connection appropriately
        entropyNode.dataOutputStream.flush();

        entropyNode.listenForMessage(); // Starts a thread to listen for incoming messages from the server, enabling the
        // client to receive messages from other clients in real-time
        // entropyNode.sendMessage(); // Starts sending messages to the server, allowing
        // the client to participate in
        // the group chat by sending messages to other clients through the server
        scanner.close();
    }
}
