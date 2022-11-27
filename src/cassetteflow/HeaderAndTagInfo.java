package cassetteflow;

/**
 * This is a simple class for collecting and passing along mp3/flag header and
 * tag information to the AudioInfo Objects
 * 
 * @author Nathan
 */
public class HeaderAndTagInfo {
    public int length = -1; // length of the mp3/flack track in seconds
    public int bitrate = -1; // the bit rate
    public String artist; // the artist
    public String album; // the album
    public String genre; // the genre
    
    public HeaderAndTagInfo() {}
    
    /**
     * The main constructor
     * 
     * @param length
     * @param bitrate
     * @param artist
     * @param album
     * @param genre 
     */
    public HeaderAndTagInfo(int length, int bitrate, String artist, String album, String genre) {
        this.length = length;
        this.bitrate = bitrate;
        this.artist = artist;
        this.album = album;
        this.genre = genre;
    }
}
