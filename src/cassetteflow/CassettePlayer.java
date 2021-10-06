/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cassetteflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javazoom.jl.player.Player;

/**
 * This class processes data on cassette tape for playback
 * 
 * @author Nathan
 */
public class CassettePlayer implements LogFileTailerListener {
    private CassetteFlow cassetteFlow;
    private CassetteFlowFrame cassetteFlowFrame;
    
    private Player player = null;
    
    private Thread playerThread = null;
        
    // used to read the log file outputted by the minimodem program
    // "minimodem -r 1200 &> >(tee -a tape.log)"
    public LogFileTailer tailer;
    
    private String logfile;
    
    // variables used to keep track of current playing mp3
    private String currentTapeId = "";
    private String currentMp3Id = "";
    private String mp3Filename = "";
    private int startTime = -1;
    private int mp3PlayTime = -1;
    private int currentPlayTime = -1;
    
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
    
    private Process process = null;
    
    // used to indicate if the minimodem program is running
    private boolean decoding;
    
    public CassettePlayer(CassetteFlowFrame cassetteFlowFrame, CassetteFlow cassetteFlow, String logfile) {
        this(cassetteFlow, logfile);
        this.cassetteFlowFrame = cassetteFlowFrame;
    }
    
    public CassettePlayer(CassetteFlow cassetteFlow, String logfile) {
        this.cassetteFlow = cassetteFlow;
        this.logfile = logfile;
        
        DOWNLOAD_DIR = CassetteFlow.MP3_DIR_NAME + File.separator + "downloads";
    }
    
    /**
     * Grab data directly from minimodem
     * 
     * @throws IOException 
     */
    public void startMinimodem(final int delay) throws IOException {
        // call minimodem to do encoding
        String command = "minimodem -r 1200";
        
        // kill any previous process
        if(process != null) process.destroy();
        
        // start new process
        process = Runtime.getRuntime().exec(command);
        
        String message = "\nReading data from minimodem ...";
        System.out.println(message);
        
        if(cassetteFlowFrame != null) {
            cassetteFlowFrame.printToConsole(message, false);
        }
        
        decoding = true;
        
        // start thread to read from csssette tape
        Thread soutThread = new Thread("Standard Output Reader") {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                try {
                    while (true) {
                        line = reader.readLine();
                        
                        if (line != null) {
                            newLogFileLine(line);
                            
                            if(cassetteFlowFrame != null) {
                                cassetteFlowFrame.printToConsole(line, true);
                            }
                        }
                        
                        if(!decoding) {
                            break;
                        }
                        
                        // Take a pause to keep timing of tape inline with mp3 playback time
                        Thread.sleep(delay);
                    }
                    
                    reader.close();
                } catch (Exception ex) {
                    Logger.getLogger(CassettePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        soutThread.start();
        
        Thread serrThread = new Thread("Standard Error Reader") {
            @Override
            public void run() {
                BufferedReader readerErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                try {
                    while (true) {
                        line = readerErr.readLine();
                        
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
                    
                    readerErr.close();
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
                    processRecord(line);
                } else {
                    if(logLineCount%10 == 0) {
                        String message = logLineCount + " -- MP3 Downloads in progress. \nPlease stop cassette playback ...";
                        
                        if(cassetteFlowFrame != null) {
                            cassetteFlowFrame.setPlayingMP3Info(message);
                        }
                        
                        System.out.println(message);
                    }
                }
                
                logLineCount++;
                stopRecords = 0;
            } else if(line.contains("### NOCARRIER")) {
                String stopMessage = "Playback Stopped {# errors " + dataErrors +  "/" + logLineCount + "} ...";
                
                // make sure we stop any previous players
                if (player != null) {
                    player.close();
                    player = null;
                    
                    if(cassetteFlowFrame != null) {
                        cassetteFlowFrame.setPlaybackInfo(stopMessage + "\n", false);
                    } 
                }

                currentMp3Id = "";
                currentPlayTime = -1;
                
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.setPlaybackInfo(stopMessage, true);
                } else {
                    if(stopRecords == 0) {
                        System.out.println("\n");
                    }
                    
                    System.out.println(stopMessage);
                }
                
                stopRecords++;
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
     */
    public void processRecord(String line) {        
        String[] sa = line.split("_");
        String tapeId = sa[0];
        String track = sa[1];
        String mp3Id = sa[2];
        String playTimeS = sa[3];
        
        // get the total time on the tape
        int totalTime = 0;
        try {
            totalTime = Integer.parseInt(sa[4]);
        } catch(Exception nfe) {
            System.out.println("Invalid Record @ Total Time");
            dataErrors++;
            return;
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
                        startMP3Download(mp3Id);
                    }
                };
                thread.start();
                
                return;
            }
        }
        
        if(!currentMp3Id.equals(mp3Id)) {
            if(!playTimeS.equals("000M")) {
                muteRecords = 0;
                currentMp3Id = mp3Id;
                startTime = Integer.parseInt(playTimeS);
                
                MP3Info mp3Info = cassetteFlow.mp3sMap.get(mp3Id);
                String message;
                
                if(mp3Info != null) {
                    File mp3File = mp3Info.getFile();
                    mp3Filename = mp3File.getName();
                    mp3PlayTime = mp3Info.getLength();
                
                    /*** start thread to begin music playback ***/
                    playMP3(mp3File);
            
                    /*** start thread to track playback ***/
                    message = "MP3 ID: " + mp3Id + "\n" + 
                        mp3Info.toString() + "\n" + 
                        "Start Time @ " + startTime + " | Track Number: " + track;
                } else {
                    message = "Playback Error.  Unknown MP3 ID: " + mp3Id; 
                }
                
                // print out the message
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.setPlayingMP3Info(message);
                }
                
                System.out.println("\n" + message);
            } else {
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
                return;
            }
        }
        
        int playTime = 0;
        try {
            playTime = Integer.parseInt(playTimeS);
        } catch(NumberFormatException nfe) {
            System.out.println("Invalid play time: " + playTimeS);
            dataErrors++;
            return;
        }
        
        //System.out.println("Line Data: " + playTime + " >> " + line);
        if(currentPlayTime != playTime && player != null) {
            currentPlayTime = playTime;
            
            // get the actual playback time from the mp3 player
            int mp3Time = player.getPosition()/1000 + startTime + 1;
            
            String timeFromMp3 = String.format("%04d", mp3Time);
            
            if(cassetteFlowFrame != null) {
                String message = mp3Filename + " [" + track + "]\n"
                        + "Playtime From Tape: " + String.format("%04d", currentPlayTime) + " / " + String.format("%04d", mp3PlayTime) + "\n"
                        + "Playtime From MP3 : " + timeFromMp3 + "\n"
                        + "Tape Counter: " + totalTime;
                cassetteFlowFrame.setPlaybackInfo(message, false);
            } else {
                //String message = "[ " + mp3Filename + " {" + track + "} Time: " + currentPlayTime + "/" + 
                //    mp3PlayTime + " | MP3 Time: " + timeFromMp3 + " | Tape Counter: " + totalTime + " ]";
                
                String message = "Tape Time: " + currentPlayTime + "/" + 
                    mp3PlayTime + " | MP3 Time: " + timeFromMp3 + " | Tape Counter: " + totalTime + " ]";
                System.out.print(message + "\r");
            }
        }                       
    }
    
    /**
     * Print out the tracks for a particular cassette
     * 
     * @param tapeId 
     */
    private void printTracks(String tapeId) {
        ArrayList<String> mp3Ids = cassetteFlow.cassetteDB.get(tapeId);
                
        if (mp3Ids != null) {
            for (int i = 0; i < mp3Ids.size(); i++) {
                MP3Info mp3Info = cassetteFlow.mp3sMap.get(mp3Ids.get(i));
                String trackCount = String.format("%02d", (i + 1));
                System.out.println("[" + trackCount + "] " + mp3Info);
            }
            
            System.out.println("");
        } else {
            System.out.println("No record in database ...\n");
        }
    }
    
    /**
     * Play mp3 indicated by the file object in another thread
     * @param file 
     */
    private void playMP3(File file) {
        // make sure we stop any previous threads
        if (player != null) {
            player.close();
            player = null;
        }

        playerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // the millisconds to skip
                    int skipMS = startTime * 1000;

                    FileInputStream mp3Stream = new FileInputStream(file);
                    player = new Player(mp3Stream);
                    
                    if(skipMS > 0) {
                        System.out.println("Milliseconds Skipped: " + skipMS);
                        player.skipMilliSeconds(skipMS);
                    }
                    
                    player.play();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        );
        playerThread.start();
    }
     
    // stop reading logfile and playing
    public void stop() {
        // make sure we stop any previous threads
        if (player != null) {
            player.close();
            player = null;
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
     * Download mp3 from a server and add to the mp3 and tape database
     * 
     * @param indexFileId 
     */
    public void startMP3Download(String indexFileId) {
         String message;
         
        // first check to see the files have already been downloaded by checking an
        // entry in the cassette database
        if(cassetteFlow.cassetteDB.containsKey(indexFileId)) {
            downloading = false;
            
            if(cassetteFlowFrame != null) {
                message = "All files already downloaded\nWaiting for play data ...";
                cassetteFlowFrame.setPlayingMP3Info(message);
            }
            
            return;
        }
        
        try {
            // first download the index file which lets us know which files to actual download
            String indexUrl = cassetteFlow.downloadServerRoot + "tapes/Tape_" + indexFileId + ".tsv";
            URL url = new URL(indexUrl);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));

            String inputLine;
            int mp3Count = 0;
            
            message = "Starting MP3 Downloads for " + indexUrl;
            System.out.println("\n" + message);
            
            if(cassetteFlowFrame != null) {
                cassetteFlowFrame.setPlaybackInfo(message, false);
            }
            
            String tapeID = "";
            ArrayList<String> mp3List = new ArrayList<>();
            
            while ((inputLine = in.readLine()) != null) {                
                String[] sa = inputLine.split("\t");
                String mp3Id = sa[0];
                String filename = sa[1];
                String fileUrl = cassetteFlow.downloadServerRoot + encodeValue(filename);
                
                // download the file now
                if(mp3Id.startsWith("Tape ID")) {
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
                    
                    mp3List.add(mp3Id);
                    mp3Count++;
                } 
            }
            
            in.close();
            
            // store two entries in the tape database
            cassetteFlow.cassetteDB.put(tapeID, mp3List);
            cassetteFlow.cassetteDB.put(indexFileId, mp3List);
            
            message = "\n" + mp3Count + " Files Downloaded ...";
            
            if (cassetteFlowFrame != null) {
                cassetteFlowFrame.setPlaybackInfo(message, true);
                cassetteFlowFrame.setPlayingMP3Info("Resume cassette playback ...");
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
}
