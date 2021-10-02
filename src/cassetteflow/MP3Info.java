package cassetteflow;

import java.io.File;

/**
 * This class hold information about mp3 files such as file or url locations
 * 
 * @author Nathan
 */
public class MP3Info {
    private String hash10C;
    private File file;
    private String Url;
    private int length;
    private String lengthAsTime;
    
    public MP3Info(File file, String hash10C, int length, String lengthAsTime) {
        this.file = file;
        this.hash10C = hash10C;
        this.length = length;
        this.lengthAsTime = lengthAsTime; 
    }

    public String getUrl() {
        return Url;
    }

    public void setUrl(String Url) {
        this.Url = Url;
    }

    public String getHash10C() {
        return hash10C;
    }

    public File getFile() {
        return file;
    }

    public int getLength() {
        return length;
    }
    
    public String getLengthAsTime() {
        return lengthAsTime;
    }

    @Override
    public String toString() {
        return file.getName() + " (" + lengthAsTime + ")";
    }
}
