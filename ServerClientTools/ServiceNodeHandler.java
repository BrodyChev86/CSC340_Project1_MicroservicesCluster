package ServerClientTools;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.net.*;

public class ServiceNodeHandler implements Runnable {
    private Socket socket;

    public ServiceNodeHandler(Socket socket) {
        this.socket = socket;
    }
    public String processRequest(String request) {
        //Still need to do this part
        return " "; // Placeholder response, replace with actual processing logic
    }

    @Override
    public void run() {
        try {
            // Handle communication with the client
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request;
            while ((request = in.readLine()) != null) {
                // Process the request and generate a response
                String response = processRequest(request);
                out.println(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
    }
    
}
}
