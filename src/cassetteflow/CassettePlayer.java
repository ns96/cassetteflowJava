package cassetteflow;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerEvent;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;
import com.goxr3plus.streamplayer.stream.StreamPlayerListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * This class processes data on cassette tape for playback
 * it uses the https://github.com/goxr3plus/java-stream-player
 * for all playback functionality.
 * * UPDATED: Uses JMinimodem for internal FSK decoding.
 * * @author Nathan
 */
public class CassettePlayer implements LogFileTailerListener, StreamPlayerListener {
    private CassetteFlow cassetteFlow;
    private CassetteFlowFrame cassetteFlowFrame;
    
    private StreamPlayer player = null;
    
    private DeckCastConnector deckCastConnector;
    
    private DeckCastConnector deckCastConnectorDisplay;
    
    private SpotifyConnector spotifyConnector;
        
    // used to read the log file outputted by the minimodem program
    public LogFileTailer tailer;
    
    private String logfile;
    
    // variables used to keep track of current playing mp3
    private String currentTapeId = "";
    private String currentAudioId = "";
    private String audioFilename = "";
    private int startTime = -1;
    private int audioTotalPlayTime = -1;
    private int currentPlayTime = -1;
    private int currentTapeTime = -1;
    
    private int playtimeDiff = 0; // track the difference between the mp3 playtime and current PlayTime
    
    // keep track of the total log lines which have been process so far
    private int logLineCount = 0;
    
    // variable used to track if we are currently downloading the mp3 files
    private boolean downloading = false;
    
    // location of download directory
    private String DOWNLOAD_DIR;
    
    // keep track off the number of stop and mute records
    private int muteRecords = 0;
    private int stopRecords = 0;
    
    // keep track of the number of read errors after a complete line record 
    // has been read in.
    private int dataErrors = 0;
    
    // JMinimodem & Audio Control
    private Thread decoderThread;
    private TargetDataLine microphoneLine;
    
    // used to indicate if the minimodem program is running
    private volatile boolean decoding = false;
    
    // variable to track when we are paused to allowing clearing the buffer
    private boolean paused = false;
    
    // the current line record
    private String currentLineRecord;
    
    // raw line record returned from minimodem
    private String rawLineRecord;
    
    // indicate if we only interested in getting the raw line record minimodem and not processing it
    private boolean rawLineRecordOnly = false;
    // variable to keep track of all of the raw data lines we have seen.  Useful for debugging
   
    // keeps track of the current audio progress
    private int audioProgress;
    
    // the name of the output mixer to redirect the sound to other speakers
    private String outputMixerName;
    
    // the speed factor to increase or decrease playback speed incase
    // the tape deck is running slow/fast
    private double speedFactor = 1.0;
    
    // used to display tracks from long youtube mix
    private TrackListInfo trackListInfo = null;
    
    private final int STOP_RECORD_LIMIT = 0;
    
    public CassettePlayer(CassetteFlowFrame cassetteFlowFrame, CassetteFlow cassetteFlow, String logfile) {
        this(cassetteFlow, logfile);
        this.cassetteFlowFrame = cassetteFlowFrame;
    }
    
    public CassettePlayer(CassetteFlow cassetteFlow, String logfile) {
        this.cassetteFlow = cassetteFlow;
        cassetteFlow.setCassettePlayer(this);
        
        this.logfile = logfile;
        
        DOWNLOAD_DIR = CassetteFlow.AUDIO_DIR_NAME + File.separator + "downloads";
    }
    
    /**
     * Set the raw line record flag
     *
     * @param rawLineRecordOnly
     */
    public void setRawLineRecordOnly(boolean rawLineRecordOnly) {
        this.rawLineRecordOnly = rawLineRecordOnly;
    }
    
    /**
     * Set the output mixer name to redirect audio to the selected speaker
     * * @param outputMixerName 
     */
    void setMixerName(String outputMixerName) {
        this.outputMixerName = outputMixerName;
    }
    
    /**
     * Set the speed factor for playback
     * * @param speedFactor 
     */
    public void setSpeedFactor(double speedFactor) {
       this.speedFactor = speedFactor; 
    }
    
    /**
     * Set the deckcast connector object
     * * @param deckCastConnector 
     */
    public void setDeckCastConnector(DeckCastConnector deckCastConnector) {
       this.deckCastConnector = deckCastConnector; 
    }
    
    /**
     * Set the deckcast connector object used to display information through browser
     * * @param deckCastConnectorDisplay 
     */
    public void setDeckCastConnectorDisplay(DeckCastConnector deckCastConnectorDisplay) {
       this.deckCastConnectorDisplay = deckCastConnectorDisplay; 
    }
    
    /**
     * Set the Spotify connector object
     * * @param spotifyConnector  
     */
    public void setSpotifyConnector(SpotifyConnector spotifyConnector) {
        this.spotifyConnector = spotifyConnector; 
        System.out.println("\n\nSpotifyConnector Set ...\n");
    }
    
    /**
     * Return the current line record
     * @return 
     */
    public String getCurrentLineRecord() {
        return currentLineRecord;
    }
    
    /**
     * Method to return the raw minimodem line returned
     * @return 
     */
    public String getRawLineRecord() {
        return rawLineRecord;
    }
    
    /**
     * Gets the decoding stats such as total errors
     * @return 
     */
    public String getStats() {
        return "Test ...";
    }
    
    /**
     * Grab data directly from JMinimodem (Internal Library)
     * * @param delay (Deprecated in JMinimodem implementation, kept for signature compatibility)
     * @throws IOException 
     */
    public void startMinimodem(int delay) throws IOException {
        // Kill any previous session
        stop();
        
        String message = "\nListening for data via JMinimodem ...";
        System.out.println(message);
        
        if(cassetteFlowFrame != null) {
            cassetteFlowFrame.printToConsole(message, false);
        }

        // 1. Setup Audio Format (48kHz, 16-bit, Mono)
        float sampleRate = 48000.0f;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        
        try {
            // Open Microphone
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                throw new IOException("Microphone line not supported.");
            }
            microphoneLine = (TargetDataLine) AudioSystem.getLine(info);
            microphoneLine.open(format);
            microphoneLine.start();
        } catch (Exception e) {
            throw new IOException("Failed to open microphone", e);
        }

        decoding = true;
        
        // 2. Start the Decoder Thread
        decoderThread = new Thread(() -> {
            // Create Config
            JMinimodem.Config config = new JMinimodem.Config();
            config.rxMode = true;
            try {
                config.baudRate = Double.parseDouble(cassetteFlow.BAUDE_RATE);
            } catch (NumberFormatException e) {
                config.baudRate = 1200.0; // Default
            }
            config.sampleRate = sampleRate;
            config.quiet = false; // Must be false to generate "### NOCARRIER"
            
            // Wrap the Mic Line in a Stream
            AudioInputStream audioStream = new AudioInputStream(microphoneLine);

            // Setup Custom OutputStream to capture decoded text and feed it to newLineRecord
            LineAccumulator dataOutput = new LineAccumulator();
            
            // Setup System.err Interceptor to capture "### NOCARRIER"
            PrintStream originalErr = System.err;
            StatusInterceptor statusInterceptor = new StatusInterceptor(originalErr);
            System.setErr(statusInterceptor);

            try {
                // *** BLOCKING CALL - Runs until stop() closes the line ***
                JMinimodem.receive(config, audioStream, dataOutput);
            } catch (Exception ex) {
                // If we stopped manually, an IO exception is expected when the line closes
                if (decoding) {
                    Logger.getLogger(CassettePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } finally {
                // Restore System.err
                System.setErr(originalErr);
            }
        }, "JMinimodem Decoder Thread");
        
        decoderThread.start();
    }
    
    /**
     * Helper Class: Captures decoded bytes, builds strings, and calls newLineRecord.
     */
    private class LineAccumulator extends OutputStream {
        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws IOException {
            if (paused) return; // Skip if paused

            if (b == '\n' || b == '\r') {
                if (buffer.size() > 0) {
                    String line = buffer.toString("UTF-8");
                    processLine(line);
                    buffer.reset();
                }
            } else {
                buffer.write(b);
                
                // check the buffer length if more than 50 characters then it's noise
                // process it quickly stop playback
                if(buffer.size() > 40) {
                    String line = buffer.toString("UTF-8");
                    if(isNoisyData(line)) {
                        line = "### NOCARRIER (Noisy Data) ###";
                        processLine(line);
                        buffer.reset();
                    }
                }
            }
        }
        
        private void processLine(String line) {
            if (!paused && line != null) {                
                if (cassetteFlowFrame != null) {
                    cassetteFlowFrame.printToConsole(line, true);
                }
                newLineRecord(line);
            }
        }
        
        /**
         * Returns true if the line is considered noise. Valid lines must
         * either: 1. Start with "###" (Status messages) 2. Contain ONLY
         * letters, numbers, and underscores (Data IDs)
         */
        private boolean isNoisyData(String input) {
            if (input == null || input.trim().isEmpty()) {
                return true; // Treat empty lines as noise/skippable
            }

            // 1. ALLOW LIST: Status Messages
            // If the line starts with "###", we trust it (it contains spaces/parens, which are valid here)
            if (input.startsWith("###")) {
                return false;
            }

            // 2. STRICT CHECK: Data IDs
            // If it's not a status message, it MUST be a data ID.
            // Data IDs cannot have spaces, brackets, or weird symbols.
            // They can ONLY have letters, digits, and underscores.
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return true; // Found a bad symbol (like '?', ' ', '~') -> It's NOISE
                }
            }

            // If we passed the loop, it's a valid Data ID
            return false;
        }
    }

    /**
     * Helper Class: Intercepts System.err to detect JMinimodem status messages (NOCARRIER).
     */
    private class StatusInterceptor extends PrintStream {
        public StatusInterceptor(OutputStream out) {
            super(out);
        }
        @Override
        public void println(String x) {
            // super.println(x); // Uncomment to debug raw status messages to console
            checkTrigger(x);
        }
        @Override
        public void print(String x) {
            // super.print(x);
            checkTrigger(x);
        }
        
        private void checkTrigger(String x) {
             if (x != null && x.contains("###")) {
                 if(paused) return;
                 
                 // Pass status messages (like NOCARRIER) to the main logic
                 if (cassetteFlowFrame != null) {
                    cassetteFlowFrame.printToConsole(x, true);
                 }
                 newLineRecord(x);
            }
        }
    }
    
    /**
     * Start the tailing a log file
     */
    public void startLogTailer() {
        tailer = new LogFileTailer( new File(logfile),800, false );
        tailer.addLogFileTailerListener( this );
        tailer.start();
    } 
    
    /**
     * Process a line from the JMinimodem or log file.
     * @param line 
     */
    @Override
    public synchronized void newLineRecord(String line) {
        if(line != null) {
            line = line.trim();            
            rawLineRecord = line;
            
            // if we only reading raw line records just return here
            if(rawLineRecordOnly) return;
            
            if(line.length() == 29 && validCharacters(line)) {              
                //System.out.println("Line record: " + line);
                logLineCount++;
                
                if(!downloading) {
                    // check to see if this line is a DCT line record
                    // in which case it's needs to be translated
                    if(line.startsWith("DCT0")) {
                        String dctLine = cassetteFlow.getDCTLine(line);
                        
                        if(dctLine != null) {
                            if(!dctLine.contains("TAPE TIME:")) { 
                                if (cassetteFlowFrame != null) {
                                    cassetteFlowFrame.printToConsole(" -->" + dctLine, true);
                                }
                                
                                currentLineRecord = processRecord(dctLine);
                            } else {
                                // we have a good dct record, but no DCT lookup array so let's see
                                // if we are controlling a stream player
                                if(deckCastConnector != null) {
                                    int tapeTime = Integer.parseInt(dctLine.split(" ")[2]);
                                    deckCastConnector.playStream(tapeTime);
                                    deckCastConnector.setDataErrors(dataErrors, logLineCount);
                                }
                                
                                if(spotifyConnector != null) {
                                    int tapeTime = Integer.parseInt(dctLine.split(" ")[2]);
                                    int startAt = spotifyConnector.playStream(tapeTime);
                                    
                                    if(spotifyConnector.isPlaying() && startAt != -1) {
                                        if (deckCastConnectorDisplay != null) {
                                            deckCastConnectorDisplay.displayPlayingAudioInfo(spotifyConnector.getCurrentAudioInfo(), 
                                                    startAt, "spotify", spotifyConnector.getCurrentTrack());
                                        }
                                    }
                                    
                                    spotifyConnector.setDataErrors(dataErrors, logLineCount);
                                }
                                
                                currentLineRecord = dctLine;
                            }
                        } else {
                            String stopMessage = "DCT Lookup Error {# errors " + dataErrors + "/" + logLineCount + "} ...";

                            // make sure we stop any previous players
                            if (player != null) {
                                player.stop();

                                if (cassetteFlowFrame != null) {
                                    cassetteFlowFrame.setPlaybackInfo(stopMessage, false);
                                }
                            }
                            
                            // check to see if we have deckcast object and stop it's playback as well
                            if(deckCastConnector != null) {
                                // TO-DO 11/18/2022 -- See how best to handle this? 
                                //deckCastConnector.stopStream();
                            }
                        }
                    } else {
                        currentLineRecord = processRecord(line);
                    }
                } else {
                    if(logLineCount%10 == 0) {
                        String message = logLineCount + " -- File Downloads in progress. \nPlease stop cassette playback ...";
                        
                        if(cassetteFlowFrame != null) {
                            cassetteFlowFrame.setPlayingAudioInfo(message);
                        }
                        
                        System.out.println(message);
                    }
                }
                
                stopRecords = 0;
            } else if(line.contains("### NOCARRIER")) {
                stopRecords++; // increment the stop records
                
                String stopMessage = "Playback Stopped {# errors " + dataErrors +  "/" + logLineCount + "} ...";
                
                // make sure we stop any previous players after receiving a few stop records
                if (player != null && stopRecords > STOP_RECORD_LIMIT) {
                    player.stop();
                    
                    if(cassetteFlowFrame != null) {
                        cassetteFlowFrame.setPlaybackInfo(stopMessage, false);
                    } 
                }
                
                // check to see if we have  deckcast object and stop it's playback as well
                if (deckCastConnector != null) {
                    deckCastConnector.stopStream();
                }
                
                // check to see if we have  spotify object and stop it's playback as well
                if (spotifyConnector != null) {
                    spotifyConnector.stopStream();
                }
                
                // RESTART LOGIC REMOVED: JMinimodem is a continuous stream, we don't need to restart the process
                // on stopRecords == 1 like the external process version did. 
                // However, if logic requires a "reset", we can just toggle paused.
                if(stopRecords == 1) {
                     // Optionally clear internal buffers here if needed.
                }

                currentAudioId = "";
                currentPlayTime = -1;
                trackListInfo = null;
                playtimeDiff = 0;
                
                if(cassetteFlowFrame != null && stopRecords > (STOP_RECORD_LIMIT + 1)) {
                    cassetteFlowFrame.setPlaybackInfo(stopMessage, true);
                } else {
                    if(stopRecords == 1) {
                        System.out.println("\n");
                    }
                    
                    System.out.println(stopMessage);
                }
                
                currentLineRecord = "PLAYBACK STOPPED # " + stopRecords; 
            }
        }
    }
    
    /**
     * Make sure we only processing ascii characters
     * * @param input
     * @return 
     */
    private boolean validCharacters(String input) {
        boolean result = true;
        
        for (int i = 0; i < input.length(); i++) {
            int test = (int) input.charAt(i);
            
            if (test > 127) {
                result = false;
                System.out.println("Invalid data: " + input);
                dataErrors++;
                break;
            }
        }
        
        return result;
    }
    
    /**
     * Process a record from the minimodem log file in order to playback the
     * correct mp3 file
     * @param line 
     * @return  
     */
    public String processRecord(String line) {        
        String[] sa = line.split("_");
        String tapeId = sa[0];
        String track = sa[1];
        String audioId = sa[2];
        String playTimeS = sa[3];
        
        // get the total time from the tape data
        int totalTime;
        int trackNum;
        
        try {
            totalTime = Integer.parseInt(sa[4]);
            trackNum = Integer.parseInt(track);
        } catch(Exception nfe) {
            System.out.println("Invalid Record @ Total Time: " + line);
            dataErrors++;
            return "DATA ERROR";
        }
        
        // check to see what tape is playing
        if(!tapeId.equals(currentTapeId)) {
            if(!tapeId.equals("HTTPS")) {
                currentTapeId = tapeId;
            
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.setPlayingCassetteID(tapeId);
                }
                
                System.out.println("\nPlaying Tape: " + tapeId);
            
                printTracks(tapeId);
            } else if(tapeId.equals("HTTPS") && !downloading) {
                downloading = true;
                
                // start a thread to download the mp3 here
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        startAudioDownload(audioId);
                    }
                };
                thread.start();
                
                return "DOWNLOADING";
            }
        }
        
        if(!currentAudioId.equals(audioId)) {
            if(!playTimeS.contains("M")) {
                muteRecords = 0;
                currentAudioId = audioId;
                
                try {
                    startTime = Integer.parseInt(playTimeS);
                } catch (NumberFormatException nfe) {
                    System.out.println("Invalid Record @ Start Time");
                    dataErrors++;
                    return "DATA ERROR";
                }
                
                // see if we have additional track information for long youtube mixes
                if(cassetteFlow.tracklistDB.containsKey(audioId)) {
                    trackListInfo = cassetteFlow.tracklistDB.get(audioId);
                    // update the tracks being displayed
                } else {
                    trackListInfo = null;
                }
                
                AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(audioId);
                String message;
                
                if(audioInfo != null) {
                    File audioFile = audioInfo.getFile();
                    audioFilename = audioFile.getName();
                    audioTotalPlayTime = audioInfo.getLength();
                
                    /*** start thread to begin music playback ***/
                    audioProgress = 0;
                    playAudio(audioFile, audioInfo.getLength());
            
                    /*** start thread to track playback ***/
                    message = "Audio ID: " + audioId + "\n" + 
                        audioInfo.getName() + "\n" + 
                        "Start Time @ " + startTime + " | Track Number: " + track;
                    
                    if (deckCastConnectorDisplay != null) {
                        deckCastConnectorDisplay.displayPlayingAudioInfo(audioInfo, startTime, "mp3/FLAC", trackNum);
                    }
                } else {
                    message = "Playback Error.  Unknown Audio ID: " + audioId; 
                }
                
                // print out the message
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.setPlayingAudioInfo(message);
                    cassetteFlowFrame.setPlayingAudioTrack(track);
                }
                
                System.out.println("\n" + message);
            } else {
                String muteInfo = "Mute Section ...";
                boolean incrementOffset = false;
                
                if(playTimeS.contains("MM")) {
                    muteInfo = "Mute Section (Padding) ...";
                    incrementOffset = true;
                } 
                
                // TO-DO 3/15/2022 -- Need to calculate mute time correctly!
                if(cassetteFlowFrame != null) {
                    if(muteRecords == 0) {
                        cassetteFlowFrame.setPlaybackInfo(muteInfo, false);
                        System.out.println("\n");
                        
                        // check that we have not incremented the offset before
                        if(incrementOffset) {
                           cassetteFlowFrame.incrementDCTDecodeOffset();
                        }
                    } else {
                        cassetteFlowFrame.setPlaybackInfo(muteInfo, true);
                    }
                }
                
                System.out.println(muteInfo);
                muteRecords++;
                return line;
            }
        }
        
        int playTime;
        try {
            playTime = Integer.parseInt(playTimeS);
        } catch(NumberFormatException nfe) {
            System.out.println("Invalid play time: " + playTimeS);
            dataErrors++;
            return "DATA ERROR";
        }
        
        //System.out.println("Line Data: " + playTime + " >> " + line);
        if(currentPlayTime != playTime && player != null) {
            currentPlayTime = playTime;
            currentTapeTime = totalTime;
            
            // get the actual playback time from the mp3 player
            //int mp3Time = player.get/1000 + startTime;
            int mp3Time = audioProgress + startTime;
            playtimeDiff = mp3Time - currentPlayTime;
            
            String timeFromFile = String.format("%04d", mp3Time);
            
            // get the track name
            String trackName = audioFilename + " [" + track + "]";
            if (trackListInfo != null) {
                trackName = trackListInfo.getTrackAtTime(mp3Time, track);
            }
                
            if(cassetteFlowFrame != null) {
                String message = trackName + "\n"
                        + "Playtime From Tape: " + String.format("%04d", currentPlayTime) + " / " + String.format("%04d", audioTotalPlayTime) + "\n"
                        + "Playtime From File:  " + timeFromFile + "\n"
                        + "Tape Counter: " + totalTime + " (" + CassetteFlowUtil.getTimeString(totalTime) + ")\n"
                        + "Data Errors: " + dataErrors +  "/" + logLineCount;
                
                cassetteFlowFrame.setPlaybackInfo(message, false, "");
            } else {
                //String message = "[ " + mp3Filename + " {" + track + "} Time: " + currentPlayTime + "/" + 
                //    mp3PlayTime + " | MP3 Time: " + timeFromFile + " | Tape Counter: " + totalTime + " ]";
                
                String message = "Tape Time: " + currentPlayTime + "/" + 
                    audioTotalPlayTime + " | File Time:  " + timeFromFile + " | Tape Counter: " + totalTime + " ]";
                System.out.print(message + "\r");
            }
        }
        
        return line;
    }
    
    /**
     * Print out the tracks for a particular cassette
     * * @param tapeId 
     */
    private void printTracks(String tapeId) {
        ArrayList<String> audioIds = cassetteFlow.tapeDB.get(tapeId);
                
        if (audioIds != null) {
            for (int i = 0; i < audioIds.size(); i++) {
                AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(audioIds.get(i));
                String trackCount = String.format("%02d", (i + 1));
                System.out.println("[" + trackCount + "] " + audioInfo);
            }
            
            System.out.println("");
        } else {
            System.out.println("No record in database ...\n");
        }
    }
    
    /**
     * Play mp3 or flac indicated by the file object in another thread
     * * @param file 
     */
    private void playAudio(File file, int duration) {
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
        } else {
            player = new StreamPlayer();
            player.addStreamPlayerListener(this);
        }
        
        try {
            player.setMixerName(outputMixerName);
            player.setSpeedFactor(speedFactor);
            player.open(file);

            if (startTime > 0) {
                System.out.println("Seconds Skipped: " + startTime);
                
                if(file.getName().toLowerCase().contains(".mp3")) {
                    player.seekTo(startTime);
                } else {
                    /** 12/20/2021 -- BUG WITH StreamPlayer Library
                     * must be FLAC so calling seekTo causes everything to crash!
                     * Unfortunately this workaround doesn't work either
                     */
                    long totalBytes = player.getTotalBytes();
                    double percentage = (startTime * 100) / duration;
                    long bytes = (long) (totalBytes * (percentage / 100));
                    long bytesSkipped = player.seekBytes(bytes);
                    System.out.println("Bytes Skipped: " + bytesSkipped + " / Requested: " + bytes);         
                }             
            }

            player.play();
        } catch(StreamPlayerException ex) {
            ex.printStackTrace();
        }
    }
     
    // stop reading logfile and playing
    public void stop() {
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
        }
        
        if(tailer != null) {
            tailer.stopTailing();
        }
        
        decoding  = false;
        
        // Stop JMinimodem by closing the line
        if (microphoneLine != null && microphoneLine.isOpen()) {
            microphoneLine.close();
        }
    }
    
    /**
     * Download mp3/flac from a server and add to the audio and tape database
     * 1/30/2022 -- This is a work in progress
     * * @param indexFileId 
     */
    public void startAudioDownload(String indexFileId) {
         String message;
         
        // first check to see the files have already been downloaded by checking an
        // entry in the cassette database
        if(cassetteFlow.tapeDB.containsKey(indexFileId)) {
            downloading = false;
            
            if(cassetteFlowFrame != null) {
                message = "All files already downloaded\nWaiting for play data ...";
                cassetteFlowFrame.setPlayingAudioInfo(message);
            }
            
            return;
        }
        
        try {
            // first download the index file which lets us know which files to actual download
            String indexUrl = cassetteFlow.DOWNLOAD_SERVER + "tapes/Tape_" + indexFileId + ".tsv";
            URL url = new URL(indexUrl);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));

            String inputLine;
            int fileCount = 0;
            
            message = "Starting Audio File Downloads for " + indexUrl;
            System.out.println("\n" + message);
            
            if(cassetteFlowFrame != null) {
                cassetteFlowFrame.setPlaybackInfo(message, false);
            }
            
            String tapeID = "";
            ArrayList<String> audioList = new ArrayList<>();
            
            while ((inputLine = in.readLine()) != null) {                
                String[] sa = inputLine.split("\t");
                String audioId = sa[0];
                String filename = sa[1];
                String fileUrl = cassetteFlow.DOWNLOAD_SERVER + encodeValue(filename);
                
                // download the file now
                if(audioId.startsWith("Tape ID")) {
                    tapeID = filename;
                } else  {
                    String localFilename = DOWNLOAD_DIR + File.separator + filename;
                    
                    message = "Downloading file: " + fileUrl;
                    
                    if(cassetteFlowFrame != null) {
                        cassetteFlowFrame.setPlaybackInfo(message, true);
                    }
                    
                    System.out.println(message);
                    
                    InputStream ins = new URL(fileUrl).openStream();
                    Files.copy(ins, Paths.get(localFilename), StandardCopyOption.REPLACE_EXISTING);
                    
                    audioList.add(audioId);
                    fileCount++;
                } 
            }
            
            in.close();
            
            // store two entries in the tape database
            cassetteFlow.tapeDB.put(tapeID, audioList);
            cassetteFlow.tapeDB.put(indexFileId, audioList);
            
            message = "\n" + fileCount + " Files Downloaded ...";
            
            if (cassetteFlowFrame != null) {
                cassetteFlowFrame.setPlaybackInfo(message, true);
                cassetteFlowFrame.setPlayingAudioInfo("Resume cassette playback ...");
            }
            
            System.out.println(message);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        
        downloading = false;
    }
    
    /**
     * Method to encode a url string
     * * @param value
     * @return 
     */
    private String encodeValue(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20");
    }

    @Override
    public void opened(Object o, Map<String, Object> map) { }

    @Override
    public void progress(int nEncodedBytes, long microsecondPosition, byte[] pcmData, Map<String, Object> map) {
        audioProgress = (int) (microsecondPosition / 1000000);
    }

    @Override
    public void statusUpdated(StreamPlayerEvent spe) { } 
}