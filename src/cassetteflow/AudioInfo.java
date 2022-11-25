package cassetteflow;

import java.io.File;
import java.io.Serializable;

/**
 * This class hold information about mp3 or flac files such as file or url locations
 * 
 * @author Nathan
 */
public class AudioInfo implements Serializable {
    private String hash10C;
    private File file;
    private String Url;
    private int length;
    private String lengthAsTime;
    private int bitRate;
    private String parentDirectoryName = "";
    private String genre;
    private String artist;
    
    public AudioInfo(File file, String hash10C, int length, String lengthAsTime, int bitRate) {
        this.file = file;
        this.hash10C = hash10C;
        this.length = length;
        this.lengthAsTime = lengthAsTime;
        this.bitRate = bitRate;
    }
    
    public void setParentDirectoryName(String parentDirectoryName) {
        this.parentDirectoryName = parentDirectoryName;
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
    
    public int getBitRate() {
        return bitRate;
    }
    
    public String getName() {
        return file.getName() + " (" + lengthAsTime + ")";
    }
    
    public String getBasicName() {
        String fn = file.getName();
        return fn.substring(0, fn.lastIndexOf("."));
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }
    
    @Override
    public String toString() {
        if(!parentDirectoryName.isEmpty()) {
            return "[" + parentDirectoryName + "] " + file.getName() + " (" + lengthAsTime + ")";
        } else {
            return file.getName() + " (" + lengthAsTime + ")";
        }
    }
}
