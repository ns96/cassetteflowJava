package cassetteflow;


import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;


/**
 * A simple class for playing wav files using a specific output device 
 * 
 * https://stackoverflow.com/questions/37609430/play-sound-on-specific-sound-device-java
 * 
 * @author Nathan
 */
public class WavPlayer {
    // Param for playback (input) device.
    public static Line.Info playbackLine = new Line.Info(SourceDataLine.class);
    
    // Param for capture (output) device.
    public static Line.Info captureLine = new Line.Info(TargetDataLine.class);
    
    /**
     * Get either the playback or listening devices 
     * @param supportedLine
     * @return 
     */
    public List<Info> getDevices(final Line.Info supportedLine) {
        List<Info> result = new ArrayList();

        Info[] infoList = AudioSystem.getMixerInfo();
        for (Info info : infoList) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(supportedLine)) {
                result.add(info);
                System.out.println(String.format("Name [%s] \n Description [%s]\n\n", info.getName(), info.getDescription()));
            }
        }

        return result;
    }
    
    /**
     * Main method to testing functionality of the class
     * 
     * @param args 
     */
    public static void main(String[] args) {
       WavPlayer wavPlayer = new WavPlayer();
       wavPlayer.getDevices(playbackLine);
    }
}
