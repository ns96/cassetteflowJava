package cassetteflow;

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
}
