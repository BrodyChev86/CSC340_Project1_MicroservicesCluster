package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    public Client(Socket socket, String username){
        try{
            this.socket = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = username;
        }catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter); 
        }
    }

    public void sendMessage(){
        try {
            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            Scanner scanner = new Scanner(System.in);
            while (socket.isConnected()) {
                String messageToSend = scanner.nextLine();
                bufferedWriter.write(username + ": " + messageToSend);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }
            scanner.close();
        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter); 
        }
    }

    public void listenForMessage(){
        new Thread(new Runnable(){ //Creates a new thread to listen for incoming messages from the server since this is a blocking operation
            @Override
            public void run() {
                String msgFromGroupChat;

                while (socket.isConnected()) {
                    try {
                        msgFromGroupChat = bufferedReader.readLine();
                        System.out.println(msgFromGroupChat);
                    } catch (IOException e) {
                        closeEverything(socket, bufferedReader, bufferedWriter); 
                    }
                }
            }
        }).start();  
    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter){
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
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
