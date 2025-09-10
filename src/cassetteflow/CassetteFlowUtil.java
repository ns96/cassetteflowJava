package cassetteflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * A class containing utility methods
 * @author Nathan
 */
public class CassetteFlowUtil {
    /**
     * Get a file as a string
     * 
     * @param file
     * @return Return the string content in the file
     * @throws IOException 
     */
    public static String getContentAsString(File file) throws IOException {
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, StandardCharsets.UTF_8);
    }
    
    /**
     * Reads given resource file as a string.
     * https://stackoverflow.com/questions/6068197/read-resource-text-file-to-string-in-java
     * 
     * @param fileName path to the resource file
     * @return the file's contents
     * @throws IOException if read fails for any reason
     */
    public static String getResourceFileAsString(String fileName) throws IOException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try ( InputStream is = classLoader.getResourceAsStream("cassetteflow/" + fileName)) {
            if (is == null) {
                return null;
            }
            try ( InputStreamReader isr = new InputStreamReader(is);  BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }
    
    
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
     * Get the parent directory
     * 
     * @param file
     * @return 
     */
    public static String getParentDirectoryName(File file) {
        String parentName = "";
        
        String parentPath = file.getParent();
        if(parentPath != null) {
            String pattern = Pattern.quote(System.getProperty("file.separator"));
            String[] sa = parentPath.split(pattern);
            return sa[sa.length - 1];
        }
        
        return parentName;
    }
    
    /**
     * Return an int array containing the approximate total time a track
     * should be playing/encoding at
     * 
     * @param sideList
     * @return 
     */
    public static int[] getTimeForTracks(ArrayList<AudioInfo> sideList, int mute) {
        int[] timeForTracks = new int[sideList.size()];
        int totalTime = 0;
        
        for(int i = 0; i < sideList.size(); i++) {
            AudioInfo info = sideList.get(i);
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
    
    /**
     * Check to see if the length of an audio file is within
     * 
     * @param audioLength
     * @param filterRange
     * @return 
     */
    public static boolean withinFilterRange(int audioLength, String filterRange) {        
        try {
            // get the filter limit and convert to minutes
            String[] sa = filterRange.split("-");
            int filterLimitMin = Integer.parseInt(sa[0].trim())*60;
            int filterLimitMax = Integer.parseInt(sa[1].trim())*60;
            
            if(audioLength >= filterLimitMin && audioLength <= filterLimitMax) {
                return true;
            } else {
                return false;
            }
        } catch(NumberFormatException nfe) {
            return true;
        }
    }
    
    /**
     * Find all audio files in the path and all subdirectories
     * 
     * @param path
     * @return
     * @throws IOException 
     */
    public static List<Path> findAllAudioFiles(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path must be a directory!");
        }

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk
                    .filter(Files::isRegularFile)   // is a file
                    .filter(p -> {
                        String filename = p.getFileName().toString().toLowerCase();
                        return filename.endsWith(".mp3") || filename.endsWith(".flac");
                    })
                    .collect(Collectors.toList());
        }
        
        return result;
    }
    
    /**
     * Method to return the type of audio object we have based on the filename ending, or url 
     * of audio info object
     * 
     * @param audioInfo The audio info object we are inspecting
     * @return The format of the audio object
     */
    public static String audioInfoFormat(AudioInfo audioInfo) {
        if(audioInfo.getName().toLowerCase().contains(".mp3")) {
            return "MP3";
        } else if(audioInfo.getName().toLowerCase().contains(".flac")) {
            return "FLAC";
        } else if(audioInfo.getUrl().toLowerCase().contains("youtube")) {
            return "YTB";
        } else if(audioInfo.getUrl().toLowerCase().contains("spotify")) {
            return "SPO";
        } else {
            return "UNK";
        }
    }
    
    // test main method
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("C:\\mp3files\\");
        List<Path> paths = findAllAudioFiles(path);
        paths.forEach(x -> System.out.println(x));
    }
}
