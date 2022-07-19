package cassetteflow;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerEvent;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;
import com.goxr3plus.streamplayer.stream.StreamPlayerListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

/**
 * This class processes data on cassette tape for playback
 * it uses the https://github.com/goxr3plus/java-stream-player
 * for all playback functionality
 * 
 * @author Nathan
 */
public class CassettePlayer implements LogFileTailerListener, StreamPlayerListener {
    private CassetteFlow cassetteFlow;
    private CassetteFlowFrame cassetteFlowFrame;
    
    private StreamPlayer player = null;
        
    // used to read the log file outputted by the minimodem program
    // "minimodem -r 1200 &> >(tee -a tape.log)"
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
    
    // variables used for calling minimodem
    private final String COMMAND_MINIMODEM = "minimodem -r " + cassetteFlow.BAUDE_RATE;
    private final String COMMAND_PULSEAUDIO = "pulseaudio";
    private Process process;
    private BufferedReader miniModemReader;
    private BufferedReader miniModemErrReader;
    private int readDelay = 0;
    
    // used to indicate if the minimodem program is running
    private boolean decoding;
    
    // variable to track when we are paused to allowing clearing the buffer
    private boolean paused = false;
    
    // the current line record
    private String currentLineRecord;
    
    // keeps track of the current audio progress
    private int audioProgress;
    
    // the name of the output mixer to redirect the sound to other speakers
    private String outputMixerName;
    
    // the speed factor to increase or decrease playback speed incase
    // the tape deck is running slow/fast
    private double speedFactor = 1.0;
    
    // used to display tracks from long youtube mix
    private TrackListInfo trackListInfo = null;
    
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
     * Set the output mixer name to redirect audio to the selected speaker
     * 
     * @param outputMixerName 
     */
    void setMixerName(String outputMixerName) {
        this.outputMixerName = outputMixerName;
    }
    
    /**
     * Set the speed factor for playback
     * 
     * @param speedFactor 
     */
    public void setSpeedFactor(double speedFactor) {
       this.speedFactor = speedFactor; 
    }
    
    /**
     * Return the current line record
     * @return 
     */
    public String getCurrentLineRecord() {
        return currentLineRecord;
    }
    
    /**
     * Gets the decoding stats such as total errors
     * @return 
     */
    public String getStats() {
        return "Test ...";
    }
    
    /**
     * Grab data directly from minimodem
     * 
     * @param delay
     * @throws IOException 
     */
    public void startMinimodem(int delay) throws IOException {
        readDelay = delay;
        
        // kill any previous process
        if(process != null) process.destroy();
        
        // if we running on mac os then we need to stat pulseaudio as well
        if(CassetteFlow.isMacOs) {
            try {
                Runtime.getRuntime().exec(COMMAND_PULSEAUDIO);
                Thread.sleep(1000);
                System.out.println("Starting pulseaudio ...");
            } catch (InterruptedException ex) { }
        }
        
        // start new process
        process = Runtime.getRuntime().exec(COMMAND_MINIMODEM);
        
        String message = "\nReading data from minimodem ...";
        System.out.println(message);
        
        if(cassetteFlowFrame != null) {
            cassetteFlowFrame.printToConsole(message, false);
        }
        
        decoding = true;
        
        // start thread to read from tape
        Thread soutThread = new Thread("Standard Output Reader") {
            @Override
            public void run() {
                miniModemReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                try {
                    while (true) {
                        if(!paused) {
                            line = miniModemReader.readLine();

                            if (line != null && !paused) { //check for pause again
                                if (cassetteFlowFrame != null) {
                                    cassetteFlowFrame.printToConsole(line, true);
                                }
                                
                                newLogFileLine(line);
                            }
                        }
                        
                        if(!decoding) {
                            break;
                        }
                        
                        // Take a pause to keep timing of tape inline with mp3 
                        // playback time
                        if(delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                    
                    miniModemReader.close();
                } catch (Exception ex) {
                    Logger.getLogger(CassettePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        soutThread.start();
        
        Thread serrThread = new Thread("Standard Error Reader") {
            @Override
            public void run() {
                miniModemErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                try {
                    while (true) {
                        if(paused) {
                            Thread.sleep(50);
                            continue;
                        }
                        
                        line = miniModemErrReader.readLine();
                        
                        if (line != null) {
                            newLogFileLine(line);
                            if(cassetteFlowFrame != null) {
                                cassetteFlowFrame.printToConsole(line, true);
                            }
                        }
                        
                        if(!decoding) {
                            break;
                        }
                    }
                    
                    miniModemErrReader.close();
                } catch (Exception ex) {
                    Logger.getLogger(CassettePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        serrThread.start();
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
     * Process a line from the log file tailer class.
     * @param line 
     */
    @Override
    public synchronized void newLogFileLine(String line) {
        if(line != null) {
            line = line.trim();
            
            if(line.length() == 29 && validCharacters(line)) {
                //System.out.println("Line record: " + line);
                
                if(!downloading) {
                    // check to see if this line is a DCT line record
                    // in which case it's needs to be translated
                    if(line.startsWith("DCT0")) {
                        String dctLine = cassetteFlow.getDCTLine(line);
                        
                        if(dctLine != null) {
                            if (cassetteFlowFrame != null) {
                                cassetteFlowFrame.printToConsole(" -->" + dctLine, true);
                            }
                            
                            currentLineRecord = processRecord(dctLine);
                        } else {
                            String stopMessage = "DCT Lookup Error {# errors " + dataErrors + "/" + logLineCount + "} ...";

                            // make sure we stop any previous players
                            if (player != null) {
                                player.stop();

                                if (cassetteFlowFrame != null) {
                                    cassetteFlowFrame.setPlaybackInfo(stopMessage, false);
                                }
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
                
                logLineCount++;
                stopRecords = 0;
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.setStopRecords(0, currentTapeTime);
                }
            } else if(line.contains("### NOCARRIER")) {                
                String stopMessage = "Playback Stopped {# errors " + dataErrors +  "/" + logLineCount + "} ...";
                
                // make sure we stop any previous players
                if (player != null) {
                    player.stop();
                    
                    if(cassetteFlowFrame != null) {
                        cassetteFlowFrame.setPlaybackInfo(stopMessage, false);
                    } 
                }
                
                // check to make sure we close the minimodem read
                if(miniModemReader != null && stopRecords == 0 && readDelay > 0) {
                    try {
                        paused = true;
                        
                        if(miniModemReader != null) miniModemReader.close();
                        if(miniModemErrReader != null) miniModemErrReader.close();
                        if(process != null) process.destroyForcibly();
                        
                        process = Runtime.getRuntime().exec(COMMAND_MINIMODEM);
                        miniModemReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        miniModemErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        
                        // take a quick pause
                        Thread.sleep(1000);
                        
                        paused = false;
                    } catch (IOException | InterruptedException ex) {
                        Logger.getLogger(CassettePlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                currentAudioId = "";
                currentPlayTime = -1;
                trackListInfo = null;
                playtimeDiff = 0;
                
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.setPlaybackInfo(stopMessage, true);
                } else {
                    if(stopRecords == 0) {
                        System.out.println("\n");
                    }
                    
                    System.out.println(stopMessage);
                }
                
                stopRecords++;
                
                currentLineRecord = "PLAYBACK STOPPED # " + stopRecords; 
                
                // tell the UI the stop records so we can estimate the current
                // track on the tape. This works for R2R
                if(cassetteFlowFrame != null) {
                    // check to see if there is actual audio data
                    String sa[] = line.split(" ");
                    
                    if(!sa[4].equals("ampl=0.000")) {
                        cassetteFlowFrame.setStopRecords(stopRecords, currentTapeTime);
                    } else {
                        //System.out.println("Line Record: " + line);
                        cassetteFlowFrame.setStopRecords(0, currentTapeTime);
                    }
                }
            }
        }
    }
    
    /**
     * Make sure we only processing ascii characters
     * 
     * @param input
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
     * 
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
        
        try {
            totalTime = Integer.parseInt(sa[4]);
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
                // TO-DO 3/15/2022 -- Need to calculate mute time correctly!
                if(cassetteFlowFrame != null) {
                    if(muteRecords == 0) {
                        cassetteFlowFrame.setPlaybackInfo("Mute Section ...", false);
                    } else {
                        cassetteFlowFrame.setPlaybackInfo("Mute Section ...", true);
                    }
                } 
                if(muteRecords == 0) {
                    System.out.println("\n");
                }
                
                System.out.println("Mute Section ...");
                muteRecords++;
                return line;
            }
        }
        
        int playTime = 0;
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
            
            // get the track time
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
     * 
     * @param tapeId 
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
     * 
     * @param file 
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
        
        // stop the minimodem program
        if(process != null) {
            process.destroy();
        }
    }
    
    /**
     * Download mp3/flac from a server and add to the audio and tape database
     * 1/30/2022 -- This is a work in progress
     * 
     * @param indexFileId 
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
     * 
     * @param value
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
