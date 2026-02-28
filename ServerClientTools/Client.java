package ServerClientTools;
import java.awt.Dimension;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class Client {
    
    private Socket socket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private String username;
    private volatile boolean isUploading = false;
    final File[] fileToSend = new File[1];

    public Client(Socket socket, String username){
        try{
            this.socket = socket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.username = username;
        }catch (IOException e) {
            closeEverything(socket, dataInputStream, dataOutputStream); 
        }
    }

    public void sendMessage(){
        try {
            dataOutputStream.writeUTF(username);
            dataOutputStream.flush();

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

    public void listenForMessage(){
        new Thread(new Runnable(){ //Creates a new thread to listen for incoming messages from the server since this is a blocking operation
            @Override
            public void run() {
                String msgFromGroupChat;

                while (socket.isConnected()) {
                    try {
                        msgFromGroupChat = dataInputStream.readUTF();
                        if(msgFromGroupChat.equals("FILE_UPLOAD")){
                            isUploading = true;
                            fileUpload(); //If the server sends a message indicating that the client should select a file to upload, call the fileUpload method to handle the file upload process

                            continue;
                        }
                        System.out.println(msgFromGroupChat);
                    } catch (IOException e) {
                        closeEverything(socket, dataInputStream, dataOutputStream); 
                    }
                }
            }

            //Created following a tutorial on YouTube by WittCode (https://www.youtube.com/watch?v=GLrlwwyd1gY&t=97s) and modified by BrodyChev86 to fit the requirements of the project
            private void fileUpload() {
                JFrame jFrame = new JFrame("File Upload");
                jFrame.setSize(450, 450);
                jFrame.setLayout(new BoxLayout(jFrame.getContentPane(), BoxLayout.Y_AXIS));
                jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                JLabel jLabelTitle = new JLabel("File Upload");
                jLabelTitle.setFont(new Font("Arial", Font.BOLD, 25));
                jLabelTitle.setBorder(new EmptyBorder(20,0,10,0));
                jLabelTitle.setAlignmentX(jFrame.CENTER_ALIGNMENT);

                JLabel jLabelFileName =  new JLabel("Choose a file to send");
                jLabelFileName.setFont(new Font("Arial", Font.BOLD, 20));
                jLabelFileName.setBorder(new EmptyBorder(50,0,0,0));
                jLabelFileName.setAlignmentX(jFrame.CENTER_ALIGNMENT);

                JPanel jPanelButtons = new JPanel();
                jPanelButtons.setBorder(new EmptyBorder(75,0,10,0));

                JButton jButtonSendFile = new JButton("Send File");
                jButtonSendFile.setPreferredSize(new Dimension(150, 75));
                jButtonSendFile.setFont(new Font("Arial", Font.BOLD, 20));

                JButton jButtonChooseFile = new JButton("Choose File");
                jButtonChooseFile.setPreferredSize(new Dimension(150, 75));
                jButtonChooseFile.setFont(new Font("Arial", Font.BOLD, 20));

                jPanelButtons.add(jButtonChooseFile);
                jPanelButtons.add(jButtonSendFile);

                jButtonChooseFile.addActionListener(e -> {
                    JFileChooser jFileChooser = new JFileChooser();
                    jFileChooser.setDialogTitle("Chose a file to send");

                    if(jFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION){
                        fileToSend[0] = jFileChooser.getSelectedFile();
                        jLabelFileName.setText("The file you want to send is: " +fileToSend[0].getName());
                    }
                });

                jButtonSendFile.addActionListener(e -> {
                    if(fileToSend[0] == null){
                        jLabelFileName.setText("Please choose a file before sending");
                        isUploading = false;
                    }else{
                        try {
                            FileInputStream fileInputStream = new FileInputStream(fileToSend[0].getAbsolutePath());

                            String fileName = fileToSend[0].getName();
                            byte[] fileNameBytes = fileName.getBytes();

                            byte[] fileContentBytes = new byte[(int) fileToSend[0].length()];
                            fileInputStream.read(fileContentBytes);

                            dataOutputStream.writeInt(fileNameBytes.length);
                            dataOutputStream.write(fileNameBytes);

                            dataOutputStream.writeInt(fileContentBytes.length);
                            dataOutputStream.write(fileContentBytes);

                            fileInputStream.close();
                            isUploading = false;
                        } catch (IOException error) {
                            error.printStackTrace();
                        }
                        
                }});

                jFrame.add(jLabelTitle);
                jFrame.add(jLabelFileName);
                jFrame.add(jPanelButtons);
                jFrame.setVisible(true);

            }
        }).start();  
    }

    public void closeEverything(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream){
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
        String username = scanner.nextLine();
        Socket socket = new Socket("localhost", 1234); //Creates a socket that connects to the server running on localhost at port 1234, allowing the client to communicate with the server and other clients connected to it
        Client client = new Client(socket, username);
        client.listenForMessage(); //Starts a thread to listen for incoming messages from the server, enabling the client to receive messages from other clients in real-time
        client.sendMessage(); //Starts sending messages to the server, allowing the client to participate in the group chat by sending messages to other clients through the server
        scanner.close();
    }
}