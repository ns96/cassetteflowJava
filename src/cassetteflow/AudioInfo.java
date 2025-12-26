package cassetteflow;

import java.io.File;
import java.io.Serializable;

/**
 * This class hold information about mp3 or flac files such as file or url
 * locations
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
    private String title; // the title from the mp3/flac tags
    private String genre;
    private String artist;
    private String album;
    private String year;
    private String streamId;
    private String imageUrl;
    private String relativePath; // the relative path of the file from the root directory
    private String sdCardFilename; // the filename to use on the SD card (handling collisions)

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
        if (file != null) {
            return file.getName() + " (" + lengthAsTime + ")";
        } else {
            // we should have a title then
            return title + " (" + lengthAsTime + ")";
        }
    }

    public String getBasicName() {
        if (file != null) {
            String fn = file.getName();
            return fn.substring(0, fn.lastIndexOf("."));
        } else {
            // we must have a spotify track so split artist from it
            String[] sa = title.split(" -- ");
            return sa[0];
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        if (!genre.isBlank()) {
            this.genre = genre;
        }
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (!artist.isBlank()) {
            this.artist = artist;
        }
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        if (!album.isBlank()) {
            this.album = album;
        }
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getImageUrl() {
        if (imageUrl == null) {
            imageUrl = "https://img.youtube.com/vi/MwsGULCvMBk/0.jpg";
        }

        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getSdCardFilename() {
        if (sdCardFilename != null && !sdCardFilename.isEmpty()) {
            return sdCardFilename;
        }
        return file != null ? file.getName() : "";
    }

    public void setSdCardFilename(String sdCardFilename) {
        this.sdCardFilename = sdCardFilename;
    }

    @Override
    public String toString() {
        if (!parentDirectoryName.isEmpty()) {
            return "[" + parentDirectoryName + "] " + file.getName() + " (" + lengthAsTime + ")";
        } else {
            return getName();
        }
    }

    /**
     * Return the full info for the tracks
     * 
     * @return
     */
    public String getFullInfo() {
        String info = "File: " + getName() + "\nTitle: " + title + "\nArtist: " + artist + "\nAlbum: " +
                album + "\nYear: " + year + "\nGenre: " + genre;

        return info;
    }
}
