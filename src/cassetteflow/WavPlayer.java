package cassetteflow;


import java.io.File;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;


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
    
    // The sound clip object
    private Clip sound;
    
    // size of the byte buffer used to read/write the audio stream
    private static final int BUFFER_SIZE = 4096;
    
    // used to stop the playback of long wav file
    private boolean stopPlay;
    
    /**
     * Method to play a wave file uning the default out
     * 
     * @param filename
     * @throws LineUnavailableException
     * @throws IOException
     * @throws InterruptedException
     * @throws UnsupportedAudioFileException 
     */
    public void playWithDefaultOutput(String filename) throws LineUnavailableException, IOException, InterruptedException, UnsupportedAudioFileException {
        CountDownLatch syncLatch = new CountDownLatch(1);

        AudioInputStream inputStream = AudioSystem.getAudioInputStream(new File(filename));
        AudioFormat format = inputStream.getFormat();
        DataLine.Info info = new DataLine.Info(Clip.class, format);

        sound = (Clip) AudioSystem.getLine(info);

        // Listener which allow method return once sound is completed
        sound.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP) {
                sound.close();
                syncLatch.countDown();
            }
        });

        sound.open(inputStream);
        sound.start();

        syncLatch.await();
    }
    
    /**
     * Play a sound using the specific output ad wait for the sound to finish playing
     * https://stackoverflow.com/questions/557903/how-can-i-wait-for-a-java-sound-clip-to-finish-playing-back
     * 
     * @param filename
     * @param info
     * @throws UnsupportedAudioFileException
     * @throws IOException
     * @throws LineUnavailableException
     * @throws InterruptedException 
     */
    public void play(String filename, Mixer.Info info) throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {
        CountDownLatch syncLatch = new CountDownLatch(1);
        
        AudioInputStream inputStream = AudioSystem.getAudioInputStream(new File(filename));
        System.out.println(String.format("Playing through [%s] \nDescription [%s]\n\n", info.getName(), info.getDescription()));
    
        sound = AudioSystem.getClip(info);
        
        // Listener which allow method return once sound is completed
        sound.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP) {
                sound.close();
                syncLatch.countDown();
            }
        });
        
        sound.open(inputStream);
        sound.start();
        
        syncLatch.await();   
    }
    
    /**
     * Plays a big wav by streaming it from the disk instead of using Clip
     * instead of using clip
     * 
     * https://www.codejava.net/coding/how-to-play-back-audio-in-java-with-examples
     * 
     * @param wavFile
     * @param info 
     * @throws javax.sound.sampled.UnsupportedAudioFileException 
     * @throws java.io.IOException 
     * @throws javax.sound.sampled.LineUnavailableException 
     */
    public void playBigWav(File wavFile, Mixer.Info info) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        stopPlay = false;
        
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = audioStream.getFormat();

        SourceDataLine audioLine = AudioSystem.getSourceDataLine(format, info);

        audioLine.open(format);
        audioLine.start();

        System.out.println("Playback started ...");

        byte[] bytesBuffer = new byte[BUFFER_SIZE];
        int bytesRead = -1;

        while ((bytesRead = audioStream.read(bytesBuffer)) != -1) {
            audioLine.write(bytesBuffer, 0, bytesRead);
            
            if(stopPlay) {
                System.out.println("Playback stopped.");
                break;
            }
        }

        audioLine.drain();
        audioLine.close();
        audioStream.close();

        System.out.println("Playback completed.");
    }
    
    /**
     * Get the audio output devices
     * 
     * @return 
     */
    public static HashMap<String, Mixer.Info> getOutputDevices() {
        return getDevices(playbackLine);
    }
    
    /**
     * Get either the playback or listening devices 
     * @param supportedLine
     * @return 
     */
    public static HashMap<String, Mixer.Info> getDevices(final Line.Info supportedLine) {
        HashMap<String, Mixer.Info> result = new HashMap<>();
        //result.put("Default", null);
        
        Info[] infoList = AudioSystem.getMixerInfo();
        for (Mixer.Info info : infoList) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(supportedLine)) {
                String key = info.getName();
                result.put(key, info);
                System.out.println(String.format("Name [%s] \nDescription [%s]\n", info.getName(), info.getDescription()));
            }
        }

        return result;
    }
    
    /**
     * Stop the wav playback
     */
    public void stop() {
        stopPlay = true;
        
        if(sound != null) {
            sound.stop();
        }
    }
    
    /**
     * Merge all the files in the array list into a single wave file
     * 
     * @param mergeWavFile
     * @param wavFiles 
     */
    public static void mergeWavFiles(File mergeWavFile, ArrayList<File> wavFiles) {
        ArrayList<File> deleteFiles = new ArrayList<>();
        ArrayList<AudioInputStream> clips = new ArrayList<>();
        
        AudioInputStream appendedAIS = null;
        
        try {
            for (int i = 0; i < wavFiles.size() - 1; i++) {
                File wavFile2 = wavFiles.get(i + 1);
                AudioInputStream clip2 = AudioSystem.getAudioInputStream(wavFile2);
                
                if(wavFile2.getName().contains("track_")) {
                    deleteFiles.add(wavFile2);
                    clips.add(clip2);
                }
                
                if (i == 0) {
                    File wavFile1 = wavFiles.get(i);
                    
                    AudioInputStream clip1 = AudioSystem.getAudioInputStream(wavFile1);
                    appendedAIS = new AudioInputStream(new SequenceInputStream(clip1, clip2),
                            clip1.getFormat(), clip1.getFrameLength() + clip2.getFrameLength());

                    if(wavFile1.getName().contains("track_")) {
                        deleteFiles.add(wavFile1);
                        clips.add(clip1);
                    }
                    continue;
                }

                appendedAIS = new AudioInputStream(
                        new SequenceInputStream(appendedAIS, clip2),
                        appendedAIS.getFormat(), appendedAIS.getFrameLength() + clip2.getFrameLength());
            }

            AudioSystem.write(appendedAIS, AudioFileFormat.Type.WAVE, mergeWavFile);
            appendedAIS.close();
            
            // sleep for 4 seconds to allow files to become unlocked so they can
            // deleted -- 1/30/2022 -- Doesn't work on Windows
            //Thread.sleep(4000);
            
            // try to delete the track files now. Doesn't work on windows!
            for(int i = 0; i < deleteFiles.size(); i++) {
                clips.get(i).close();
                deleteFiles.get(i).delete();
            }
        } catch (IOException | UnsupportedAudioFileException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Main method to testing functionality of the class
     * 
     * @param args 
     */
    public static void main(String[] args) {
        try {
            WavPlayer wavPlayer = new WavPlayer();
            HashMap<String, Mixer.Info> mixerOutput = WavPlayer.getDevices(playbackLine);
            
            //String wavFile = "‪C:\\mp3files\\TapeFiles\\track_1-1200.wav";
            String wavFilename = "C:\\mp3files\\TapeFiles\\Tape_0U90A-1200.wav";
            wavPlayer.playBigWav(new File(wavFilename), mixerOutput.get("Speakers (2- USB Audio Device)"));
        } catch (Exception ex) {
            Logger.getLogger(WavPlayer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
