package ServerClientTools;

import java.io.InputStream;
import java.util.Properties;

public class PropertyFileReader {
    public static String getIP() {
        Properties prop = new Properties();
        String ip = "";
        try (InputStream input = PropertyFileReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return ip;
            }

            prop.load(input);
            ip = prop.getProperty("microservices.ip");

            if (ip != null) {
                ip = ip.trim();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ip;
    }
}