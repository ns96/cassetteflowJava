/*
 * 
 */
package cassetteflow;

import java.util.ArrayList;

/**
 * Class to store track list information for Long YouTube Mixes
 * 
 * @author Nathan
 */
public class TrackListInfo {
    private String audioID;
    private String fileName;
    private ArrayList<Integer> trackNumbers;
    private ArrayList<Integer> trackTimes;
    private ArrayList<String> trackTitles;
    
    /**
     * Main constructor
     * 
     * @param audioID
     * @param fileName 
     */
    public TrackListInfo(String audioID, String fileName) {
        this.audioID = audioID;
        this.fileName = fileName;
        
        trackNumbers = new ArrayList<>();
        trackTimes = new ArrayList<>();
        trackTitles = new ArrayList<>();
    }
    
    /**
     * Add track information
     * 
     * @param track
     * @param time
     * @param title 
     */
    public void addTrack(String track, String time, String title) {
        try {
            int trackNumber = Integer.parseInt(track);
            int trackTime = Integer.parseInt(time);
            trackNumbers.add(trackNumber);
            trackTimes.add(trackNumber);
            trackTitles.add(title);
        } catch(NumberFormatException nfe) {}
    }
    
    /**
     * Return the list of tracks
     * 
     * @return 
     */
    public String toString() {
        String tracks = "";
        
        for(int i = 0; i < trackNumbers.size(); i++) {
            tracks += trackNumbers.get(i) + ". " + trackTitles.get(i) + "\n";
        }
        
        return tracks.trim();
    }
    
}
