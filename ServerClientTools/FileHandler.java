package ServerClientTools;
//Created following a tutorial on YouTube by WittCode (https://www.youtube.com/watch?v=GLrlwwyd1gY&t=97s) and modified by BrodyChev86 to fit the requirements of the project
public class FileHandler {
    private int id;
    private String fileName;
    private byte[] data;
    private String fileExtension;

    public FileHandler(int id, String fileName, byte[] data, String fileExtension) {
        this.id = id;
        this.fileName = fileName;
        this.data = data;
        this.fileExtension = fileExtension;
    }

    public int getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }
}