package CSV_Stats;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CSV_Reader {

    // Opens and reads CSV
    public static void main(String[] args) {

        String filePath = "CSV_Stats/StressTest.csv"; // Temp

        try (FileInputStream fis = new FileInputStream(filePath)) {
            readCsv(fis);

        } catch (IOException e) {
            System.out.println("Failed to open or read file.");
            e.printStackTrace();
        }
    }

    // Prints header and counts rows in CSV file
    public static void readCsv(InputStream inputStream) throws IOException {

        int rowCount = 0;
        String[] columns = null;

        // Stores values per column
        Map<String, List<Double>> columnData = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String header = reader.readLine();

            if (header != null) {
                columns = header.split(",");

                for (String column : columns) {
                    columnData.put(column.trim(), new ArrayList<>());
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                rowCount++;

                String[] values = line.split(",");

                // Parse values
                for (int i = 0; i < values.length && i < columns.length; i++) {
                    try {
                        double parsedValue = Double.parseDouble(values[i].trim());
                        columnData.get(columns[i].trim()).add(parsedValue);
                    } catch (NumberFormatException e) {
                        // Ignore non-numeric values
                    }
                }
            }
        }

        // Output Formatting
        System.out.println("----------------------------------------");
        System.out.println("CSV File Summary");
        System.out.println("----------------------------------------");

        if (columns != null) {
            System.out.println("Column Count: " + columns.length);
            System.out.println("Columns: " + String.join(", ", columns));
        } else {
            System.out.println("Column Count: 0");
            System.out.println("Columns: (none found)");
        }

        System.out.println("Data Row Count: " + rowCount);

        // Table Header
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-15s %12s %12s %12s %12s %12s%n",
                "Column", "Mean", "Min", "Max", "Median", "Std Dev");
        System.out.println("--------------------------------------------------------------------------------");

        // Table Rows
        for (Map.Entry<String, List<Double>> entry : columnData.entrySet()) {

            List<Double> values = entry.getValue();

            if (!values.isEmpty()) {

                double mean = calculateMean(values);
                double min = calculateMin(values);
                double max = calculateMax(values);
                double median = calculateMedian(values);
                double stdDev = calculateStdDev(values);

                System.out.printf("%-15s %12.2f %12.2f %12.2f %12.2f %12.2f%n",
                        entry.getKey(), mean, min, max, median, stdDev);
            }
        }
        System.out.println("----------------------------------------");
    }

    // STATISTIC CALCULATORS

    // Mean Calculator
    private static double calculateMean(List<Double> values) {

        double sum = 0.0;

        for (double value : values) {
            sum += value;
        }

        return sum / values.size();
    }

    // Min Calculator
    private static double calculateMin(List<Double> values) {

        double min = values.get(0);

        for (double value : values) {
            if (value < min) {
                min = value;
            }
        }

        return min;
    }

    // Max calculator
    private static double calculateMax(List<Double> values) {

        double max = values.get(0);

        for (double value : values) {
            if (value > max) {
                max = value;
            }
        }

        return max;
    }

    // Median calculator
    private static double calculateMedian(List<Double> values) {

        // Copy list
        List<Double> sorted = new ArrayList<>(values);

        Collections.sort(sorted);

        int size = sorted.size();

        // Even
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }

        // Odd
        return sorted.get(size / 2);
    }

    // Standard deviation calculator
    private static double calculateStdDev(List<Double> values) {

        double mean = calculateMean(values);
        double sumSquaredDiffs = 0.0;

        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiffs += diff * diff;
        }

        return Math.sqrt(sumSquaredDiffs / values.size());
    }
}