package cassetteflow;

import java.util.ArrayList;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * A class containing utility methods
 * @author Nathan
 */
public class CassetteFlowUtil {
    /**
     * Method to get the time in seconds as a string
     * 
     * @param totalSecs
     * @return 
     */
    public static String getTimeString(int totalSecs) {
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Get a 10 character hash
     * 
     * @param string
     * @return 
     */
    public static String get10CharacterHash(String string) {
        String sha256hex = DigestUtils.sha256Hex(string);
        return sha256hex.substring(0, 10);
    }
    
    /**
     * Return an int array containing the approximate total time a track
     * should be playing/encoding at
     * 
     * @param sideList
     * @return 
     */
    public static int[] getTimeForTracks(ArrayList<MP3Info> sideList, int mute) {
        int[] timeForTracks = new int[sideList.size()];
        int totalTime = 0;
        
        for(int i = 0; i < sideList.size(); i++) {
            MP3Info info = sideList.get(i);
            totalTime += info.getLength() + mute;
            timeForTracks[i] = totalTime;
        }
        
        return timeForTracks;
    }
    
    /**
     * Get the track number given a certain time
     * 
     * @param timeForTracks
     * @param time
     * @return 
     */
    public static int getTrackFromTime(int[] timeForTracks, int time) {
        int track = 1;
        for(int i = 0; i < timeForTracks.length; i++) {
            if(time < timeForTracks[i]) {
                break;
            } else {
                track++;
            }
        }
        
        return track;
    }
}
