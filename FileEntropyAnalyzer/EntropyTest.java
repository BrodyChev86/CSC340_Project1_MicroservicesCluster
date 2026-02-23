package FileEntropyAnalyzer;

import java.io.IOException;

public class EntropyTest {
    public static void main(String[] args) {
        try {
            double entropy = EntropyAnalyzer.calculateEntropy("C://Users//hayde//OneDrive//Desktop//Classes//CSC340//Project 1//CSC340_Project1_MicroservicesCluster//FileEntropyAnalyzer//lowEntropy.txt");
            System.out.println("Entropy of lowEntropy.txt: " + entropy);
            double entropy2 = EntropyAnalyzer.calculateEntropy("C://Users//hayde//OneDrive//Desktop//Classes//CSC340//Project 1//CSC340_Project1_MicroservicesCluster//FileEntropyAnalyzer//mediumEntropy.txt");
            System.out.println("Entropy of mediumEntropy.txt: " + entropy2);
            double entropy3 = EntropyAnalyzer.calculateEntropy("C://Users//hayde//OneDrive//Desktop//Classes//CSC340//Project 1//CSC340_Project1_MicroservicesCluster//FileEntropyAnalyzer//highEntropy.txt");
            System.out.println("Entropy of highEntropy.txt: " + entropy3);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
