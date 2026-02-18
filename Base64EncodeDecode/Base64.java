package CSC340_Project1_MicroservicesCluster.Base64EncodeDecode;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

import javax.imageio.ImageIO;

public class Base64{
    public static void main(String[] args) {
        String inputFilePath = "C:\\Users\\brody\\CSC340\\Project1\\CSC340_Project1_MicroservicesCluster\\Base64EncodeDecode\\TestImage.jpg"; // Update this path to your input file
        try{
            byte[] fileData = readFile(inputFilePath);
            String encoded = encode(fileData);
            System.out.println("Encoded: " + encoded);
            String decoded = decode(encoded);
            System.out.println("Decoded: " + decoded);
            
            BufferedImage image = new Base64().decodeToImage(decodeToBytes(encoded));
            if (image != null) {
                // Save the BufferedImage to a file (PNG format)
                File outputfile = new File("saved_image.png");
                ImageIO.write(image, "png", outputfile);
                System.out.println("Image successfully saved to " + outputfile.getAbsolutePath());
            } else {
                System.out.println("Could not decode the image, possibly an unsupported format or invalid data.");
            }

        } catch (IOException e) {
            e.printStackTrace();
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

    public static byte[] readFile(String filePath) throws IOException {
        return Files.readAllBytes(Paths.get(filePath));
    }

    public BufferedImage decodeToImage(byte[] decodedBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(decodedBytes));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
