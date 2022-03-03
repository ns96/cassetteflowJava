/*
 * 
 */
package cassetteflow;

import java.util.ArrayList;

/**
 * Class to store track list information for long YouTube Mixes
 * Unfortunately this track information needs to be manually obtained
 * with the help of Shazam
 * @author Nathan
 */
public class TrackListInfo {
    private String audioID;
    private String fileName;
    private ArrayList<Integer> trackNumbers;
    private ArrayList<Integer> trackTimes;
    private ArrayList<String> trackTitles;
    
    // variables used to look up track for a particular time
    private int lastIndex;
    private int maxTime;
    private int[] lookUpTable;
    
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
            trackTimes.add(trackTime);
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
            tracks += "  " + trackNumbers.get(i) + ". " + trackTitles.get(i) + "\n";
        }
        
        return tracks;
    }
    
    /**
     * Create the look up table to make finding the track and a certain time
     * much more efficient
     */
    public void createLookUpTable() {
        lastIndex = trackTimes.size() - 1;
        maxTime = trackTimes.get(lastIndex);
        
        lookUpTable = new int[maxTime+1]; 
        int timeIndex = 0;
        int nextTrackTime = 0;
        
        for(int i = 0; i < lookUpTable.length; i++) {
            if(timeIndex < lastIndex) {
                nextTrackTime = trackTimes.get(timeIndex+1);
                
                if(i < nextTrackTime) {
                    lookUpTable[i] = timeIndex;
                } else {
                    timeIndex++;
                    lookUpTable[i] = timeIndex;
                }
            } else {
                lookUpTable[i] = lastIndex;
            }
        }
    }
    
    /**
     * Get the track at a particular time
     * 
     * @param atTime
     * @return 
     */
    public String getTrackAtTime(int atTime, String parentTrack) {
        int index;
        
        if(atTime < maxTime) {
            index = lookUpTable[atTime];
        } else {
            index = lastIndex;
        }
        
        return trackTitles.get(index) + " [" + parentTrack + "." + trackNumbers.get(index) + "]";
    }
}
