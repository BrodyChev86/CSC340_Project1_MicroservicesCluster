package FileEntropyAnalyzer;
//Used code from https://www.cs.usfca.edu/~mmalensek/cs677/schedule/materials/Entropy.java.html as a reference to create the EntropyAnalyzer
//Also used Stack Overflow to help understand how to calculate file entropy
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class EntropyAnalyzer {
    public static double calculateEntropy(byte[] fileData) throws IOException {
        //byte[] fileData = Files.readAllBytes(Paths.get(filePath));
        if (fileData.length == 0){
            return 0.0;
        }
        int [] characterCount = new int[256];
        for (byte b : fileData) {
            //Increment the count for the corresponding byte value
            //0xFF is used to ensure index is between 0 and 255
            characterCount[b & 0xFF]++;
        }

        double entropy = 0.0;
        for (int i=0; i < 256; i++) {
            if (characterCount[i] == 0.0) {
                continue; //Skips unused characters
            }
            if (characterCount[i]>0) {
                double frequency = (double) characterCount[i]/fileData.length;
                //Uses the formula: H = -Σ(p(x) * log2(p(x))) where p(x) is the frequency of each character to calculae the entropy
                entropy -= frequency * (Math.log(frequency)/Math.log(2));
            }
        }
        return entropy;
    }
}
