package ServerClientTools;
//Code created with the help of a tutorial on YouTube by WittCode (https://youtu.be/gLfuZrrfKes?si=r0TVgY7UQkRsKLtl) and modified by BrodyChev86 to fit the requirements of the project
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private ServerSocket serverSocket;

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void startServer() {
        try {
            while (!serverSocket.isClosed()) {
                
                Socket socket = serverSocket.accept(); //Waits for a client to connect and accepts the connection, returning a Socket object for communication with the client.
                System.out.println("A new client has connected!");
                ClientHandler clientHandler = new ClientHandler(socket);

                Thread thread = new Thread(clientHandler); //Creates a new thread to handle the client's requests concurrently, allowing multiple clients to connect and interact with the server simultaneously.
                thread.start(); //Runs ClientHandler 
            }
        }catch (Exception e) {
        
        }
    }

    public void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close(); //Closes the server socket, releasing any resources associated with it and preventing new client connections if any errors are present.
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException{
            ServerSocket serverSocket = new ServerSocket(1234); //Creates a server socket that listens on port 1234 for incoming client connections.
            Server server = new Server(serverSocket);
            System.out.println("Server is listening on port 1234...");
            server.startServer(); //Starts the server to accept and handle client connections.
    }
}
