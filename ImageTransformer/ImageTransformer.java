import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
import javax.imageio.ImageIO;

public class ImageTransformer {

    private BufferedImage image;
    private String format;
    private static final String SERVER_HOST = "10.111.134.253";
    private static final int SERVER_PORT_TCP = 1234;
    private static final int SERVER_PORT_UDP = 1235;

    public ImageTransformer(String inputPath) throws IOException {
        File inputFile = new File(inputPath);
        this.image = ImageIO.read(inputFile);
        if (this.image == null) {
            throw new IOException("Unsupported image format or file not found."); //Not a valid image type/file
        }
        
        //this gets type of image file
        String fileName = inputFile.getName();
        this.format = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!format.equals("png") && !format.equals("jpg")) {
            throw new IOException("Only PNG and JPG images are supported.");
        }
    }

    /**
     * Empty constructor used when the class is acting as a service node.  The
     * image and format fields will be populated when a request arrives.
     */
    public ImageTransformer() {
        // nothing to do yet
    }

    //Resizing method
    public void resize(int width, int height) {
        Image scaledImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        //New Image thumbnail
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        this.image = resizedImage;
    }

    //Rotates image
    public void rotate(double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        
        int w = image.getWidth();
        int h = image.getHeight();
        int newWidth = (int) Math.floor(w * cos + h * sin);
        int newHeight = (int) Math.floor(h * cos + w * sin);

        BufferedImage rotatedImage = new BufferedImage(newWidth, newHeight, image.getType()); //creates new image
        Graphics2D g2d = rotatedImage.createGraphics();

        // Better quality
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        AffineTransform at = new AffineTransform();
        at.translate((newWidth - w) / 2.0, (newHeight - h) / 2.0); //help from claude for transformation calculations
        at.rotate(radians, w / 2.0, h / 2.0);
        
        g2d.setTransform(at);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        this.image = rotatedImage;
    }

    //Converts image to grayscale
    public void toGrayscale() {
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        
        this.image = grayImage;
    }

   //saves new transformed image to file
    public void save(String outputPath) throws IOException {
        ImageIO.write(this.image, format, new File(outputPath));
    }

    // networking helpers
    private Socket socket;
    private DatagramSocket datagramSocket;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;

    private void initializeNetworking(Socket socket, DatagramSocket datagramSocket) throws IOException {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
    }

    /**
     * Send a heartbeat packet to the server so it knows we are still alive.
     */
    public void sendHeartbeat(String message, InetAddress address, int port) throws IOException {
        byte[] outgoingData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(outgoingData, outgoingData.length, address, port);
        datagramSocket.send(sendPacket);
    }

    /**
     * Send a possibly-large byte array back to the server using the same chunking
     * protocol that ServiceNodeHandler expects.
     */
    private void sendFile(byte[] fileBytes) {
        try {
            String encoded = java.util.Base64.getEncoder().encodeToString(fileBytes);
            int chunkSize = 60000; // safe under the 65535 limit
            int totalChunks = (encoded.length() + chunkSize - 1) / chunkSize;
            dataOutputStream.writeUTF("FILE_RESPONSE_START|" + totalChunks);
            dataOutputStream.flush();
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, encoded.length());
                dataOutputStream.writeUTF("FILE_RESPONSE_CHUNK|" + encoded.substring(start, end));
                dataOutputStream.flush();
            }
        } catch (IOException e) {
            closeNetworking();
        }
    }

    /**
     * Listen for requests from the server and process image transformation
     * commands.  The expected protocol is described in ClientHandler.
     */
    public void listenForMessage() {
        new Thread(() -> {
            while (socket.isConnected()) {
                try {
                    String msgFromServer = dataInputStream.readUTF();
                    // reassemble if chunked
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

                    if (msgFromServer.startsWith("ROTATE|")) {
                        String[] parts = msgFromServer.split("\\|");
                        double degrees = Double.parseDouble(parts[1]);
                        String ext = parts[2].toLowerCase();
                        if (!ext.equals("png") && !ext.equals("jpg")) {
                            dataOutputStream.writeUTF("[ERROR] Only PNG and JPG are supported.");
                            dataOutputStream.flush();
                            continue;
                        }
                        byte[] imgBytes = java.util.Base64.getDecoder().decode(parts[3]);
                        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imgBytes));
                        this.image = img;
                        this.format = ext;
                        rotate(degrees);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        ImageIO.write(this.image, format, baos);
                        sendFile(baos.toByteArray());
                    } else if (msgFromServer.startsWith("RESIZE|")) {
                        String[] parts = msgFromServer.split("\\|");
                        int w = Integer.parseInt(parts[1]);
                        int h = Integer.parseInt(parts[2]);
                        String ext = parts[3].toLowerCase();
                        if (!ext.equals("png") && !ext.equals("jpg")) {
                            dataOutputStream.writeUTF("[ERROR] Only PNG and JPG are supported.");
                            dataOutputStream.flush();
                            continue;
                        }
                        byte[] imgBytes = java.util.Base64.getDecoder().decode(parts[4]);
                        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imgBytes));
                        this.image = img;
                        this.format = ext;
                        resize(w, h);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        ImageIO.write(this.image, format, baos);
                        sendFile(baos.toByteArray());
                    } else if (msgFromServer.startsWith("TOGRAYSCALE|")) {
                        String[] parts = msgFromServer.split("\\|");
                        String ext = parts[1].toLowerCase();
                        if (!ext.equals("png") && !ext.equals("jpg")) {
                            dataOutputStream.writeUTF("[ERROR] Only PNG and JPG are supported.");
                            dataOutputStream.flush();
                            continue;
                        }
                        byte[] imgBytes = java.util.Base64.getDecoder().decode(parts[2]);
                        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imgBytes));
                        this.image = img;
                        this.format = ext;
                        toGrayscale();
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        ImageIO.write(this.image, format, baos);
                        sendFile(baos.toByteArray());
                    } else {
                        // unrecognized command
                        dataOutputStream.writeUTF("[ERROR] ImageTransformer received unknown command");
                        dataOutputStream.flush();
                    }
                } catch (IOException e) {
                    closeNetworking();
                    break;
                }
            }
        }).start();
    }

    private void closeNetworking() {
        try {
            if (dataInputStream != null) dataInputStream.close();
            if (dataOutputStream != null) dataOutputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //main method: start as a service node unless a filename is provided for a quick local test
    public static void main(String[] args) {
        if (args.length > 0) {
            // local test path provided
            try {
                ImageTransformer transformer = new ImageTransformer(args[0]);
                transformer.toGrayscale();
                transformer.rotate(180);
                transformer.resize(150, 150);
                transformer.save("transformed_image.jpg");
                System.out.println("Image processed successfully!");
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
            return;
        }

        // otherwise behave like a cluster service node
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT_TCP);
            DatagramSocket datagramSocket = new DatagramSocket();
            ImageTransformer node = new ImageTransformer(); // use empty ctor
            node.initializeNetworking(socket, datagramSocket);
            String nodeId = java.util.UUID.randomUUID().toString();

            node.dataOutputStream.writeUTF("NODE_HELLO");
            node.dataOutputStream.flush();

            node.dataOutputStream.writeUTF("IMGT"); // service name
            node.dataOutputStream.flush();

            node.dataOutputStream.writeUTF(nodeId);
            node.dataOutputStream.flush();

            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        node.sendHeartbeat("NODE_ALIVE|" + nodeId, InetAddress.getByName(SERVER_HOST), SERVER_PORT_UDP);
                        System.out.println("Sent heartbeat to server: NODE_ALIVE");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 15000);

            node.listenForMessage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}




