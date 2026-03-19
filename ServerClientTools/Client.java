package ServerClientTools;
import java.awt.Dimension;
import java.awt.Font;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.UUID;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project

public class Client {

    private Socket socket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private String username;
    private volatile boolean isUploading = false;
    private StringBuilder fileBuffer = new StringBuilder();
    private String pendingFileName = null;
    private String pendingFileExtension = null;
    private int expectedChunks = 0;
    private int receivedChunks = 0;
    private java.util.List<File> filesToSend = new java.util.ArrayList<>();
    private static final String SERVER_HOST = PropertyFileReader.getIP();
    private static final int SERVER_PORT_TCP = 1234;

    public Client(Socket socket, String username) {
        try {
            this.socket = socket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.username = username;
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendMessage() {
        try {
            Scanner scanner = new Scanner(System.in);
            while (socket.isConnected()) {
                String messageToSend = scanner.nextLine();
                dataOutputStream.writeUTF(username + ": " + messageToSend);
                dataOutputStream.flush();
            }
            scanner.close();
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
        }
    }

    public void sendFileUploadMessage() {
        try {
            dataOutputStream.writeInt(0); // sentinel: signals end of upload to server
            dataOutputStream.flush();
        } catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream);
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
                        if(msgFromServer.equals("FILE_UPLOAD")){
                            isUploading = true;
                            fileUpload(); // prompt user to pick files
                            continue;
                        }
                        if (msgFromServer.startsWith("FILE_DOWNLOAD_START|")) {
                            String[] parts = msgFromServer.split("\\|");
                            pendingFileName = parts[1];
                            pendingFileExtension = parts[2];
                            expectedChunks = Integer.parseInt(parts[3]);
                            receivedChunks = 0;
                            fileBuffer = new StringBuilder();
                            continue;
                        }

                        if (msgFromServer.startsWith("FILE_CHUNK|")) {
                            fileBuffer.append(msgFromServer.substring("FILE_CHUNK|".length()));
                            receivedChunks++;
                            if (receivedChunks == expectedChunks) {
                                byte[] fileContent = java.util.Base64.getDecoder().decode(fileBuffer.toString());
                                fileDownload(pendingFileName, pendingFileExtension, fileContent);
                                fileBuffer = new StringBuilder();
                            }
                            continue;
                        }
                        System.out.println(msgFromServer);
                    } catch (IOException e) {
                        closeEverything(socket, dataInputStream, dataOutputStream);
                    }
                }
            }
        }).start();

    }

    /**
     * Create and display a small Swing window that lets the user choose one or more
     * files and upload them to the server.  When the window closes a special message is
     * sent to indicate completion.
     */
    private void fileUpload() {
        JFrame jFrame = new JFrame("File Upload");
        jFrame.setSize(450, 500);
        jFrame.setLayout(new BoxLayout(jFrame.getContentPane(), BoxLayout.Y_AXIS));
        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        jFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                sendFileUploadMessage();
                isUploading = false;
            }
        });

        JLabel jLabelTitle = new JLabel("File Upload");
        jLabelTitle.setFont(new Font("Arial", Font.BOLD, 25));
        jLabelTitle.setBorder(new EmptyBorder(20, 0, 10, 0));
        jLabelTitle.setAlignmentX(jFrame.CENTER_ALIGNMENT);

        JLabel jLabelFileName = new JLabel("No files selected");
        jLabelFileName.setFont(new Font("Arial", Font.PLAIN, 16));
        jLabelFileName.setBorder(new EmptyBorder(20, 10, 0, 10));
        jLabelFileName.setAlignmentX(jFrame.CENTER_ALIGNMENT);

        JPanel jPanelButtons = new JPanel();
        jPanelButtons.setBorder(new EmptyBorder(30, 0, 10, 0));

        JButton jButtonChooseFile = new JButton("Choose Files");
        jButtonChooseFile.setPreferredSize(new Dimension(150, 75));
        jButtonChooseFile.setFont(new Font("Arial", Font.BOLD, 18));

        JButton jButtonSendFile = new JButton("Send Files");
        jButtonSendFile.setPreferredSize(new Dimension(150, 75));
        jButtonSendFile.setFont(new Font("Arial", Font.BOLD, 18));

        jPanelButtons.add(jButtonChooseFile);
        jPanelButtons.add(jButtonSendFile);

        jButtonChooseFile.addActionListener(e -> {
            JFileChooser jFileChooser = new JFileChooser();
            jFileChooser.setDialogTitle("Choose files to send");
            jFileChooser.setMultiSelectionEnabled(true);

            if (jFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                filesToSend.clear();
                for (File f : jFileChooser.getSelectedFiles()) {
                    filesToSend.add(f);
                }
                jLabelFileName.setText(filesToSend.size() + " file(s) selected");
            }
        });

        jButtonSendFile.addActionListener(e -> {
            if (filesToSend.isEmpty()) {
                jLabelFileName.setText("Please choose at least one file");
            } else {
                int successCount = 0;
                for (File file : filesToSend) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(file.getAbsolutePath());
                        byte[] fileNameBytes = file.getName().getBytes();
                        byte[] fileContentBytes = new byte[(int) file.length()];
                        fileInputStream.read(fileContentBytes);

                        dataOutputStream.writeInt(fileNameBytes.length);
                        dataOutputStream.write(fileNameBytes);
                        dataOutputStream.writeInt(fileContentBytes.length);
                        dataOutputStream.write(fileContentBytes);

                        fileInputStream.close();
                        successCount++;
                    } catch (IOException error) {
                        error.printStackTrace();
                        jLabelFileName.setText("Error sending: " + file.getName());
                    }
                }
                jLabelFileName.setText(successCount + " file(s) sent successfully!");
                filesToSend.clear();
                isUploading = false;
            }
        });

        jFrame.add(jLabelTitle);
        jFrame.add(jLabelFileName);
        jFrame.add(jPanelButtons);
        jFrame.setVisible(true);
    }

    private void fileDownload(String fileName, String fileExtension, byte[] fileContent) {
        try {
            String userHome = System.getProperty("user.home");
            File downloadDir = new File(userHome, "Downloads");
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            String fullFileName;
            if (fileName.contains(".")) {
                fullFileName = fileName; 
            } else {
                fullFileName = fileName + "." + fileExtension;
            }
            File downloadedFile = new File(downloadDir, fullFileName);
            java.nio.file.Files.write(downloadedFile.toPath(), fileContent);
            System.out.println("File downloaded to: " + downloadedFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        String username = UUID.randomUUID().toString();
        Socket socket = new Socket(SERVER_HOST, SERVER_PORT_TCP); // Creates a socket that connects to the server running on
                                                       // localhost at port 1234, allowing the client to communicate
                                                       // with the server and other clients connected to it
        Client client = new Client(socket, username);

        // The username is sent as the initial message to identify the client
        client.dataOutputStream.writeUTF(username);
        client.dataOutputStream.flush();
        
        client.listenForMessage(); // Starts a thread to listen for incoming messages from the server, enabling the
                                   // client to receive messages from other clients in real-time
        client.sendMessage(); // Starts sending messages to the server, allowing the client to participate in
                              // the group chat by sending messages to other clients through the server
        scanner.close();
    }
}