import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ImageTransformer {

    private BufferedImage image;
    private String format;

    public ImageTransformer(String inputPath) throws IOException {
        File inputFile = new File(inputPath);
        this.image = ImageIO.read(inputFile);
        if (this.image == null) {
            throw new IOException("Unsupported image format or file not found."); //Not a valid image type/file
        }
        
        //this gets type of image file
        String fileName = inputFile.getName();
        this.format = fileName.substring(fileName.lastIndexOf('.') + 1);
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
        at.translate((newWidth - w) / 2.0, (newHeight - h) / 2.0);
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


    //testing main method
    public static void main(String[] args) {
    try {
        ImageTransformer transformer = new ImageTransformer("/Users/abner/Desktop/baby.jpeg");

        transformer.toGrayscale();        // Make it BW
        transformer.rotate(180);         // Flip it
        transformer.resize(150, 150);   // Create thumbnail size

        transformer.save("transformed_image.jpg");
        System.out.println("Image processed successfully!");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}


