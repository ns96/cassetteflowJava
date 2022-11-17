 package cassetteflow;

import com.formdev.flatlaf.FlatDarculaLaf;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.SwingUtilities;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp3.MP3File;

/**
 * A simple program for creating input files for the cassette flow encoding
 * program/method. It make use of the following libraries for dealing with 
 * MP3/flac playback and meta data extraction
 * 
 * Playing of MP3s/FLAC: https://github.com/goxr3plus/java-stream-player
 * 
 * Getting meta data: http://www.jthink.net/jaudiotagger/examples_id3.jsp 
 * 
 * Generating hash of filenames (Apache Commons Codec): https://www.baeldung.com/sha-256-hashing-java
 * 
 * Reading the last line of a file: https://web.archive.org/web/20160510001134/http://www.informit.com/guides/content.aspx?g=java&seqNum=226
 * 
 * @author Nathan Stevens 09/05/2021
 */
public class CassetteFlow {
    // The default mp3 directory name
    public static String AUDIO_DIR_NAME = "c:\\mp3files";
    
    // store the mp3/flac id hashmap to a tab delimitted file for use on the LyraT board
    public static String AUDIO_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.txt";
    
    // store the audio db file in binary form to make it more efficient to load audio information
    public static String AUDIO_INDEX_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.bin";
    
    // default directory where the text files to be encoded
    public static final String TAPE_FILE_DIR_NAME = "TapeFiles";
    
    public static String LOG_FILE_NAME = AUDIO_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tape.log"; 
    
    // stores the mp3info object keyed by the 10 character hash
    public HashMap<String, AudioInfo> audioInfoDB = new HashMap<>();
    
    // also store the AudioInfo object in a list for convinience
    public ArrayList<AudioInfo> audioInfoList = new ArrayList<>();
    
    // stores the cassette ID to the mp3ids
    public TreeMap<String, ArrayList<String>> tapeDB = new TreeMap<>();
    
    public HashMap<String, TrackListInfo> tracklistDB = new HashMap<>();
    
    public static String TAPE_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "tapedb.txt";   
    
    public static String TRACK_LIST_FILENAME = AUDIO_DIR_NAME + File.separator + "tracklist.txt";
    
    // the location of mp3 files on the server
    public static String DOWNLOAD_SERVER = "http://192.168.1.14/~pi/mp3/";
    
    // The IP address of the LyraT host
    public static String LYRA_T_HOST = "http://127.0.0.1:8192/";
    
    public static String BAUDE_RATE = "1200";
    
    // used when running in desktop mode
    private static CassetteFlowFrame cassetteFlowFrame;
    
    private CassettePlayer cassettePlayer;
    
    // store program properties
    private Properties properties = new Properties();
    
    private final String propertiesFilename = "cassetteFlow.properties";
    
    // used to stop realtime encoding
    private boolean stopEncoding = false;
    
    // the number of times to replicate a data line in the input files
    private final int replicate = 4;
    
    // used when doing realtime encoding to keep track of progress
    public int currentTimeTotal = 0;
    public int currentAudioCount = 1;
    public String currentTapeID = "";
    public String currentAudioID = "";
    
    // The wav file player
    private WavPlayer wavPlayer;
    
    // String arrays use by the dynamic content track
    private ArrayList<String> sideADCTList;
    private ArrayList<String> sideBDCTList;
    
    // offset used by dynamic content track
    private int dctOffset = 0;
    
    // debug flag
    private static final boolean DEBUG = false;
    
    // indicates if we are running on mac so we start pusle audio in addition
    // to minimodem
    public static boolean isMacOs = false;
    
    /**
     * Default constructor that just loads the mp3/flac files and cassette 
     * map database
     */
    public CassetteFlow() {
        // see if we running on mac os so we run minimodem correctly
        String osName = System.getProperty("os.name").toLowerCase();
        isMacOs = osName.startsWith("mac os");
        System.out.println("\nRunning On: " + osName);
        
        init();
    }
    
    /**
     * Do initial loading of audio, tape, and track database if needed
     */
    public void init() {
        loadProperties();
        
        // load the audio file index to make decoding much easier
        loadAudioFileIndex();
        
        // load audio files which is displayed in the GUI, and overwrites the
        // records in the audio file index.
        loadAudioFiles(AUDIO_DIR_NAME, false);
        
        File file = new File(TAPE_DB_FILENAME);
        tapeDB = loadTapeDB(file);
        
        file = new File(TRACK_LIST_FILENAME);
        tracklistDB = loadTrackListDB(file);
    }
    
    /**
     * Load the default properties
     */
    private void loadProperties() {
        // try loading the properties
        try (FileReader fileReader = new FileReader(propertiesFilename)) {
            properties.load(fileReader);
            
            DOWNLOAD_SERVER = properties.getProperty("download.server");
            LYRA_T_HOST = properties.getProperty("lyraT.host");
            BAUDE_RATE = properties.getProperty("baude.rate", "1200");
            LOG_FILE_NAME = properties.getProperty("minimodem.log.file", "");
            
            setDefaultAudioDirectory(properties.getProperty("audio.directory"));
        } catch (IOException e) {
            String audioDirectory = System.getProperty("user.home");
            setDefaultAudioDirectory(audioDirectory);
        }
    }
    
    /**
     * Save the default properties file
     */
    public void saveProperties() {
        try (FileWriter output = new FileWriter(propertiesFilename)) {
            properties.put("audio.directory", AUDIO_DIR_NAME);
            properties.put("download.server", DOWNLOAD_SERVER);
            properties.put("lyraT.host", LYRA_T_HOST);
            properties.put("baud.rate", BAUDE_RATE);
            properties.put("minimodem.log.file", LOG_FILE_NAME);
            
            properties.store(output, "CassetteFlow Defaults");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Set the current cassette player
     * 
     * @param cassettePlayer 
     */
    public void setCassettePlayer(CassettePlayer cassettePlayer) {
        this.cassettePlayer = cassettePlayer;
    }
    
    public String getCurrentLineRecord() {
        if(cassettePlayer != null) {
            return cassettePlayer.getCurrentLineRecord();
        } else {
            return "NO PLAYER";
        }
    } 
    
    /**
     * Save the MP3/Flac map as a tab delimited file. Not currently used 
     * other than to provide examples of how this should look
     */
    private void saveAudioInfoDB() {
        try {
            FileWriter writer = new FileWriter(AUDIO_DB_FILENAME);
            
            for(String key: audioInfoDB.keySet()) {
                AudioInfo audioInfo = audioInfoDB.get(key);
                
                // add "/sdcard/" to the filename so they match those on the LyraT
                String line = key + "\t" + audioInfo.getLength() + "\t" +  audioInfo.getBitRate() + "\t/sdcard/" + audioInfo.getFile().getName() + "\n";
                
                writer.write(line);
            }
            
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Get the audio info database has a string. Used for testing the 
     * cassette flow server
     * 
     * @return audio data as a single string 
     */
    public String getAudioInfoDBAsString() {
        StringBuilder sb = new StringBuilder();
        
        for (String key : audioInfoDB.keySet()) {
            AudioInfo audioInfo = audioInfoDB.get(key);
            String line = key + "\t" + audioInfo.getLength() + "\t" +  audioInfo.getBitRate() + "\t" + audioInfo.getFile().getName() + "\n";
            sb.append(line);
        }
        
        return sb.toString();
    }
    
    /**
     * Method to create the audio db as a String
     * 
     * @param data 
     */
    public void createAudioInfoDBFromString(String data) throws Exception {
        HashMap<String, AudioInfo> remoteDB = new HashMap<>();
        ArrayList<AudioInfo> remoteList = new ArrayList<>();

        for (String line : data.split("\n")) {
            String[] sa = line.split("\t");

            String id = sa[0];
            int playtime = Integer.parseInt(sa[1]);
            String playtimeString = CassetteFlowUtil.getTimeString(playtime);
            int bitRate = Integer.parseInt(sa[2]);
            File file = new File(sa[3]);

            AudioInfo audioInfo = new AudioInfo(file, id, playtime, playtimeString, bitRate);
            remoteDB.put(id, audioInfo);
            remoteList.add(audioInfo);
        }

        // update the objects in the cassett flow frame
        audioInfoDB = remoteDB;
        audioInfoList = remoteList;
    }
    
    /**
     * Saves the cassette data which stores info on tape IDs and their associated mp3 ids
     * 
     * @param file
     * @throws IOException 
     */
    public void saveTapeDB(File file) throws IOException {
        try {
            /**ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(cassetteDB);
            oos.close();
            */
            FileWriter writer = new FileWriter(file);
            
            for(String key: tapeDB.keySet()) {
                String line = key + "\t" + String.join("\t", tapeDB.get(key)) + "\n";
                writer.write(line);
            }
            
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Loads the cassette data which stores info on tape IDs and their associated mp3 ids
     * 
     * @param file 
     */
    private TreeMap<String, ArrayList<String>> loadTapeDB(File file) {
        TreeMap<String, ArrayList<String>> localTapeDB = new TreeMap<>();
        
        try {
            if(file.exists()) {
                /*FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis);
                cassetteDB = (HashMap<String, ArrayList<String>>) ois.readObject();
                ois.close();*/
                
                FileReader reader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(reader);
 
                String line;
 
                while ((line = bufferedReader.readLine()) != null) {
                    String[] sa = line.split("\t");
                    String key = sa[0];
                    
                    ArrayList<String> audioIds = new ArrayList<>();
                    for(int i = 1; i < sa.length; i++) {
                        audioIds.add(sa[i]);
                    }
                    
                    localTapeDB.put(key, audioIds);
                }
                reader.close();
                
                
                System.out.println("\nCassette database file loaded ... ");
                
                for(String key: localTapeDB.keySet()) {
                    ArrayList<String> audioIds = localTapeDB.get(key);
                    System.out.println(key + " >> " + audioIds);
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        
        return localTapeDB;
    }
    
    /**
     * Load track list information for YouTube mixes from a file
     * 
     * @param file 
     * @return  
     */
    public HashMap<String, TrackListInfo> loadTrackListDB(File file) {
        HashMap<String, TrackListInfo> tracks = new HashMap<>();
        
        try {
            System.out.println("\nLoading Track List File: " + file);
            
            if(file.exists()) {
                FileReader reader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(reader);
 
                String line;
 
                while ((line = bufferedReader.readLine()) != null) {
                    // check to see if line is not empty
                    if(line.trim().isEmpty() || line.startsWith("File")) {
                        continue;
                    }
                    
                    String[] sa = line.split("\t");
                    String key = sa[0];
                    
                    if(!key.isEmpty()) {
                        if(!tracks.containsKey(key)) {
                            TrackListInfo trackListInfo = new TrackListInfo(sa[0], sa[1]);
                            trackListInfo.addTrack(sa[2], sa[3], sa[4]);
                            tracks.put(key, trackListInfo);
                        } else {
                            TrackListInfo trackListInfo = tracks.get(key);
                            trackListInfo.addTrack(sa[2], sa[3], sa[4]);
                        }
                    }
                }
                reader.close();
            } else {
                System.out.println("Missing Track List File: " + file);
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        
        // generate the lookup table for each of the track list
        tracks.forEach((k,v) -> {
            v.createLookUpTable();
            System.out.println("Tracklist Key: " + k + " Tracks:\n" + v);
        });
        
        return tracks;
    }
    
    /**
     * Method to create the tape db from a String
     * 
     * @param data 
     */
    public void createTapeDBFromString(String data) throws Exception {
        TreeMap<String, ArrayList<String>> remoteDB = new TreeMap<>();
        
        for (String line : data.split("\n")) {
            try {
                String[] sa = line.split("\t");
                String key = sa[0];

                ArrayList<String> audioIds = new ArrayList<>();
                for (int i = 1; i < sa.length; i++) {
                    if(!sa[i].isEmpty()) {
                        audioIds.add(sa[i]);
                    }
                }

                remoteDB.put(key, audioIds);
            } catch(Exception e) {
                System.out.println("Error With TapeDB Line: " + line);
            }
        }
        
        tapeDB = remoteDB;
    }
    
    /**
     * Merges the currently loaded tapedb to the version stored on the local disk
     */
    public void mergeCurrentTapeDBToLocal() {
        try {
            File file = new File(TAPE_DB_FILENAME);
            TreeMap<String, ArrayList<String>> localTapeDB = loadTapeDB(file);
            
            for(String key: tapeDB.keySet()) {
                if(!localTapeDB.containsKey(key)) {
                    localTapeDB.put(key, tapeDB.get(key));
                }
            }
            
            // save the local db file now
            FileWriter writer = new FileWriter(file);
            
            for(String key: localTapeDB.keySet()) {
                String line = key + "\t" + String.join("\t", tapeDB.get(key)) + "\n";
                writer.write(line);
            }
            
            writer.close();
        } catch(IOException ex) {
           ex.printStackTrace();
        }
    }
    
    /**
     * Add an entry to the tape database
     * 
     * @param tapeID
     * @param sideAList
     * @param sideBList
     * @param save 
     */
    public void addToTapeDB(String tapeID, ArrayList<AudioInfo> sideAList, ArrayList<AudioInfo> sideBList, boolean save) {
        // save information for side A
        ArrayList<String> audioIds = new ArrayList<>();
        
        if(sideAList != null && !sideAList.isEmpty()) {
            // store track for side A
            for(AudioInfo audioInfo: sideAList) {
                audioIds.add(audioInfo.getHash10C());
            }
        
            tapeDB.put(tapeID + "A", audioIds);
        }
        
        if(sideBList != null && !sideBList.isEmpty()) {
            audioIds = new ArrayList<>();
            
            // store track for side B
            for(AudioInfo audioInfo: sideBList) {
                audioIds.add(audioInfo.getHash10C());
            }
        
            tapeDB.put(tapeID + "B", audioIds);
        }
        
        // save the database file if required
        if(save) {
            try {
                File file = new File(TAPE_DB_FILENAME);
                saveTapeDB(file);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    
    /**
     * Return the tape database as a string. Used my the cassetteflow server
     * 
     * @return 
     */
    public String getTapeDBAsString() {
        StringBuilder sb = new StringBuilder();

        for (String key : tapeDB.keySet()) {
            String line = key + "\t" + String.join("\t", tapeDB.get(key)) + "\n";
            sb.append(line);
        }
        
        return sb.toString();
    }
    
    /**
     * This generates the text files to be using for encoding
     * 
     * @param directoryName
     * @param tapeID
     * @param sideA
     * @param sideB
     * @param muteTime
     * @param forDownload specifies if this is for mp3/flac which are to be downloaded
     * 
     * @return indicates if the input file(s) were successfully created
     */
    public boolean createInputFiles(String directoryName, String tapeID, ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB, int muteTime, boolean forDownload) {
        // check to make sure directory exist
        File directory = new File(directoryName);
        if (!directory.exists()){
            directory.mkdir();
        }
        
        try {
            if (sideA != null && sideA.size() >= 1) {
                File file = new File(directoryName + File.separator + "Tape_" + tapeID + "A" + ".txt");
                createInputFileForSide(file, tapeID + "A", sideA, muteTime, forDownload);
            }

            if (sideB != null && sideB.size() >= 1) {
                File file = new File(directoryName + File.separator + "Tape_" + tapeID + "B" + ".txt");
                createInputFileForSide(file, tapeID + "B", sideB, muteTime, forDownload);
            }
            
            // save to the tape data base
            addToTapeDB(tapeID, sideA, sideB, true);
            
            return true;
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create the input files for the audio tracks
     * 
     * @param inputFile
     * @param tapeID
     * @param sideN
     * @param muteTime
     * @param forDownload
     * @return
     * @throws IOException 
     */
    public String createInputFileForSide(File inputFile, String tapeID, ArrayList<AudioInfo> sideN, int muteTime, boolean forDownload) throws IOException {
        System.out.println("Creating Cassette Tape Input: " + inputFile + ", " + tapeID + ", " +  muteTime);
        System.out.println(sideN);
        
        FileWriter myWriter = new FileWriter(inputFile);
        StringBuilder builder = new StringBuilder();
        
        // used for creating an input file which will download mp3s from the server
        String tapeHashCode;
        FileWriter myWriter2 = null;

        currentTimeTotal = 0;
        int fileCount = 0;
        
        // if this is for a download file, specify that in the first 10 seconds of data
        // when this text is decoded the mp3s will be downloaded
        if(forDownload) {
            tapeHashCode = CassetteFlowUtil.get10CharacterHash(tapeID + sideN.toString());
            String filename = inputFile.getParent() + File.separator + "Tape_" + tapeHashCode + ".tsv";
            
            // add the header to the download file indicating the TapeID
            myWriter2 = new FileWriter(filename);
            myWriter2.write("Tape ID   \t" + tapeID + "\n");
            
            System.out.println("Download File Name: " + filename);
            
            // add special line records to indicate the mp3 files need to be downloaded
            String line;
            for(int j = 0; j < replicate*10; j++) { // replicate record N times ~ 10 seconds of time
                String count = String.format("%04d", j);
                line = "HTTPS_00_" + tapeHashCode + "_000M_" + count + "\n";
                myWriter.write(line);
                builder.append(line);
            }
        }
        
        for(AudioInfo audioInfo: sideN) {
            String trackS = String.format("%02d", fileCount+1);
            String audioId = tapeID + "_" + trackS + "_" + audioInfo.getHash10C();
            
            // add this entry to the download file
            if(forDownload) {
                String line = audioInfo.getHash10C() + "\t" + audioInfo.getFile().getName() + "\n";
                myWriter2.write(line);
            }
            
            // add line records to create a N second muted section before next song
            if(fileCount >= 1) {
                currentTimeTotal += muteTime;
                String timeTotalString = String.format("%04d", currentTimeTotal);
                String line = audioId + "_00" + muteTime + "M_" + timeTotalString + "\n";
                myWriter.write(line);
                builder.append(line);
                
                /*for(int i = 0; i < muteTime; i++) {
                    currentTimeTotal += 1;
                    String timeTotalString = String.format("%04d", currentTimeTotal);
                    String line = mp3Id + "_000M_" + timeTotalString + "\n";
                    
                    for(int j = 0; j < replicate; j++) { // replicate record N times
                        myWriter.write(line);
                        builder.append(line);
                    }
                }*/
            }
        
            for(int i = 0; i < audioInfo.getLength(); i++) {
                String timeString = String.format("%04d", i);
                String timeTotalString = String.format("%04d", currentTimeTotal);
                String line = audioId + "_" + timeString + "_" + timeTotalString + "\n";
            
                for(int j = 0; j < replicate; j++) { // replicate record N times
                    myWriter.write(line);
                    builder.append(line);
                }
                
                currentTimeTotal += 1;
            }
    
            fileCount += 1;
        }
    
        // close the file writter
        myWriter.close();
        
        if(myWriter2 != null) {
            myWriter2.close();
        }
        
        return builder.toString();
    }
    
    /**
     * Create a Dynamic Content Track Array for a particular tape
     * 
     * @param tapeID
     * @param sideA
     * @param sideB
     * @param muteTime 
     */
    public void createDCTArrayList(String tapeID, ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB, int muteTime) {
        if (sideA != null && sideA.size() >= 1) {
            sideADCTList = createDCTArrayListForSide(tapeID + "A", sideA, muteTime);
        }

        if (sideB != null && sideB.size() >= 1) {
            sideBDCTList = createDCTArrayListForSide(tapeID + "B", sideB, muteTime);
        }

        // save to the tape database
        addToTapeDB(tapeID, sideA, sideB, true);        
    }
    
    /**
     * Create a array list containing line records for the audio tracks on side
     * A or side B
     * 
     * @param tapeID
     * @param sideN
     * @param muteTime
     * @return 
     */
    public ArrayList<String> createDCTArrayListForSide(String tapeID, ArrayList<AudioInfo> sideN, int muteTime) {
        System.out.println("Creating DCT Array: " + tapeID + ", Mute Time: " +  muteTime);
        System.out.println(sideN);
        
        ArrayList<String> dctList = new ArrayList<>();
        currentTimeTotal = 0;
        int fileCount = 0;
        
        for(AudioInfo audioInfo: sideN) {
            String trackS = String.format("%02d", fileCount+1);
            String audioId = tapeID + "_" + trackS + "_" + audioInfo.getHash10C();
            
            // add line records to create a N second muted section before next song
            if(fileCount >= 1) {                
                for(int i = 0; i < muteTime; i++) {
                    currentTimeTotal += 1;
                    String timeTotalString = String.format("%04d", currentTimeTotal);
                    String line = audioId + "_000M_" + timeTotalString;
                    dctList.add(line);
                }
            }
        
            for(int i = 0; i < audioInfo.getLength(); i++) {
                String timeString = String.format("%04d", i);
                String timeTotalString = String.format("%04d", currentTimeTotal);
                String line = audioId + "_" + timeString + "_" + timeTotalString;
                dctList.add(line);
                
                currentTimeTotal += 1;
            }
    
            fileCount += 1;
        }
    
        return dctList;
    }
    
    /**
     * Set the offset for used when mapping dynamic content track
     * 
     * @param dctOffset Offset in minutes
     */
    public void setDCTOffset(int dctOffset) {
        this.dctOffset = dctOffset*60; // convert to seconds
        System.out.println("DCT Offset In Seconds: " + this.dctOffset);
    }
    
    /**
     * Get a line record from the DCT array in order to playback the correct
     * audio file
     * 
     * @param line
     * @return 
     */
    public String getDCTLine(String line) {
        try {
            String[] sa = line.split("_");
            String tapeId = sa[0];
            
            // try reading the totalTime twice incase one is bad
            int totalTime;
            try {
                totalTime = Integer.parseInt(sa[4]);
            } catch(NumberFormatException nfe) {
                totalTime = Integer.parseInt(sa[3]);
                System.out.println("Using backup for DCT TotalTime: " + line);
            }
            
            // add the dct offset
            //System.out.println("Tape Total Time: " + totalTime + " / Offset " + dctOffset);
            totalTime = totalTime + dctOffset;
            
            ArrayList<String> dctList;
            
            // based on total time and side get the line record. Any exception
            // will result in a null being returned
            if(tapeId.endsWith("A")) {
                dctList = sideADCTList;
            } else {
                dctList = sideBDCTList;
            }
            
            if(dctList != null && totalTime < dctList.size()) {
                return dctList.get(totalTime);
            } else {
                //System.out.println("Invalid Time Code Index: " + line);
                return "TAPE TIME: " + totalTime;
            }
        } catch(NumberFormatException ex) {
            System.out.println("Invalid DCT Record, or Missing DCT Loopkup Array: " + line);
            return null;
        }
    }
    
    /**
     * Directly encode the data using minimodem
     * 
     * @param side
     * @param saveDirectoryName
     * @param tapeID
     * @param sideA
     * @param sideB
     * @param muteTime
     * @param forDownload 
     * @return  The encoded file objects
     */
    public File[] directEncode(String saveDirectoryName, String tapeID, ArrayList<AudioInfo> sideA, 
            ArrayList<AudioInfo> sideB, int muteTime, boolean forDownload) throws IOException {
        
        // an array to store the wave file
        File[] wavFiles = new File[2];
        wavFiles[0] = null;
        wavFiles[1] = null;
        
        File file;
        File wavFile;
        String data;
        
        // if we running on mac os then we need to stat pulseaudio as well
        if(CassetteFlow.isMacOs) {
            try {
                Runtime.getRuntime().exec("pulseaudio");
                Thread.sleep(1000);
                System.out.println("Starting pulseaudio ...");
            } catch (InterruptedException ex) { }
        }
        
        if(sideA != null && !sideA.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A" + ".txt");
            wavFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A-" + BAUDE_RATE + ".wav");
            data = createInputFileForSide(file, tapeID + "A", sideA, muteTime, forDownload);
            runMiniModemAndMergeWav(wavFile, data, muteTime);
            //runMinimodem(wavFile, data);
            wavFiles[0] = wavFile;
        }
        
        if(sideB != null && !sideB.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B" + ".txt");
            wavFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B-" + BAUDE_RATE + ".wav");
            data = createInputFileForSide(file, tapeID + "B", sideB, muteTime, forDownload);
            runMiniModemAndMergeWav(wavFile, data, muteTime);
            //runMinimodem(wavFile, data);
            wavFiles[1] = wavFile;
        }
        
        // save to the tape data base
        addToTapeDB(tapeID, sideA, sideB, true);
        
        cassetteFlowFrame.setEncodingDone();
        
        return wavFiles;
    }
    
    /**
     * Run mini modem and merge the resulting wav file into a single wav file
     * 
     * @param wavFile
     * @param data
     * @param muteTime 
     */
    public void runMiniModemAndMergeWav(File mergedWavFile, String data, int muteTime) throws IOException {
        ArrayList<File> wavFiles = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        
        // copy the 1 second blank wav if needed
        String blankFilename = mergedWavFile.getParent() + File.separator + "blank_1s.wav";
        File blankWavFile = new File(blankFilename);
        if(!blankWavFile.exists()) {
            InputStream source = getClass().getClassLoader().getResourceAsStream("sounds/blank_1s.wav");
            Files.copy(source, Paths.get(blankFilename), StandardCopyOption.REPLACE_EXISTING);
        }
        
        String[] sa = data.split("\n");
        int track = 1;
        String trackData;
        File trackFile = new File(mergedWavFile.getParent() + File.separator + "track_" + track + ".wav");
        
        for(String line: sa) {
            if(!line.contains("M_")) {
                sb.append(line).append("\n");
            } else {
                trackData = sb.toString();
                runMinimodem(trackFile, trackData);
                wavFiles.add(trackFile);
                
                // add a blank files
                for(int i = 0; i < muteTime; i++) {
                    wavFiles.add(blankWavFile);
                }
                
                track++;
                trackFile = new File(mergedWavFile.getParent() + File.separator + "track_" + track + ".wav");
                sb = new StringBuilder();
            }
        }
        
        // save the last set of data
        trackData = sb.toString();
        runMinimodem(trackFile, trackData);
        wavFiles.add(trackFile);
        
        // add a blank record if only one file in the list
        if(wavFiles.size() == 1) {
            wavFiles.add(blankWavFile);
        }
        
        // merge the wave files now. This takes a lot of memory since the all the waves need
        // to be loaded into memory
        WavPlayer.mergeWavFiles(mergedWavFile, wavFiles);
    }
    
    /**
     * Run minimodem to encode data
     * 
     * @param wavFile
     * @param data
     * @throws IOException 
     */
    private void runMinimodem(File wavFile, String data) throws IOException {
        // call minimodem to do encoding
        String command = "minimodem --tx " + BAUDE_RATE + " -f " + wavFile.toString().replace("\\", "/");
        Process process = Runtime.getRuntime().exec(command);
                
        System.out.println("\nSending data to for encoding minimodem ...");
        
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream()));
        writer.write(data);
        writer.close();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
 
        reader.close();
        
        try {
            process.waitFor();
        } catch (InterruptedException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        process.destroy();
        
        System.out.println("\nDone Encoding Command: " + command);
    }
    
    /**
     * Encode directly using minimodem without first creating a wave file
     * 
     * @param tapeID
     * @param sideN
     * @param muteTime
     * @param forDownload Currently not used
     * @param saveDirectoryName
     * @param soundOutput
     * @return Indicate if encode completed or was stopped
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public boolean realTimeEncode(String tapeID, ArrayList<AudioInfo> sideN, int muteTime, 
            boolean forDownload, String saveDirectoryName, Mixer.Info soundOutput) throws IOException, InterruptedException {
        
        boolean completed = true;
        stopEncoding = false;
        currentTapeID = tapeID;
        currentTimeTotal = 0;
        currentAudioCount = 1;
        
        String message;
        
        wavPlayer = new WavPlayer();
        
        // if we running on mac os then we need to stat pulseaudio as well
        if(CassetteFlow.isMacOs) {
            try {
                Runtime.getRuntime().exec("pulseaudio");
                Thread.sleep(1000);
                System.out.println("Started pulseaudio ...");
            } catch (InterruptedException ex) { }
        }
        
        for(AudioInfo audioInfo: sideN) {
            long startTime = System.currentTimeMillis();
            
            currentAudioID = audioInfo.getHash10C();
            String data = createInputDataForAudio(tapeID, audioInfo, currentAudioCount, 1);
            
            // indicate the current track being procecessed 
            if(cassetteFlowFrame != null)
                cassetteFlowFrame.setSelectedIndexForSideJList(currentAudioCount - 1);
            
            message = "Minimodem Encoding: " + tapeID + " Track [ " + currentAudioCount + " ] ( " + audioInfo.getLengthAsTime() + " )";
            printToGUIConsole(message, false);
            System.out.println("\n" + message);
                        
            String filename = saveDirectoryName + File.separator + "track_" + currentAudioCount + "-" + BAUDE_RATE + ".wav";
            String command = "minimodem --tx " + BAUDE_RATE + " -f " + filename;
            
            Process process = Runtime.getRuntime().exec(command);
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            writer.write(data, 0, data.length());
            writer.close();
            process.waitFor();
            process.destroy();
            
            long endTime = System.currentTimeMillis();
            long encodeTime = endTime - startTime;
            
            message = "Minimodem Encode Time: " + encodeTime + " milliseconds";
            printToGUIConsole(message, true);
            System.out.println(message);
            
            message = "\nDone Encoding Track [ " + currentAudioCount + " ] Total Tape Time: " + currentTimeTotal + " seconds";
            printToGUIConsole(message, true);
            System.out.println(message); 
            
            if(currentAudioCount > 1) {
                // sleep for the desired mute time to allow for blank on the tape
                // a blank on the tape allows for track skipping on decks that
                // supported it
                int encodeTimeSeconds = Math.round(encodeTime / 1000);
                int delay = muteTime * 1000 - (int) encodeTime;
                
                int sleepTime = (encodeTimeSeconds > muteTime)?encodeTimeSeconds : muteTime;
                
                message = "\nMute for " + sleepTime + " seconds ...";
                printToGUIConsole(message, true);
                System.out.println(message);
                
                if (delay > 0) {
                    currentTimeTotal += muteTime;
                    Thread.sleep(delay);
                } else {
                    currentTimeTotal += encodeTimeSeconds;
                }
            }
            // playback the wav file and wait for it to be done
            message = "\nPlaying Wav File: " + filename;
            printToGUIConsole(message, true);
            System.out.println(message);
            
            try {
                if(wavPlayer != null) { // check for null here since we could have stop encoding
                    File wavFile = new File(filename);
                    wavPlayer.playBigWav(wavFile, soundOutput);
                }
            } catch(IOException | LineUnavailableException | UnsupportedAudioFileException e) {
                message = "Error Playing Wav: " + filename + "\n" + e.getMessage();
                printToGUIConsole(message, true);
                System.out.println(message);
                
                e.printStackTrace();
                
                completed = false;
                break;
            }
            
            // TO-DO -- Delete the Wav file here
            
            if(stopEncoding) {
                message = "\nReal Time Encoding Stopped ...";
                printToGUIConsole(message, true);
                System.out.println(message);
                completed = false;
                break;
            }
            
            // increment fileCount
            currentAudioCount++;
        }
        
        // indicate that the encoding is done
        message = "\nEncoding of  " + (currentAudioCount - 1) + " Tracks Done ...";
        printToGUIConsole(message, true);
        System.out.println(message);
        
        if(cassetteFlowFrame != null)
            cassetteFlowFrame.setEncodingDone();
        
        return completed;
    }
    
    /**
     * Play the encoded wav file
     * 
     * @param wavFile
     * @param soundOutput
     * @return indicate if we stopped it prematurely
     * 
     * @throws IOException
     * @throws InterruptedException 
     */
    public boolean playEncodedWav(File wavFile, Mixer.Info soundOutput) throws IOException, InterruptedException {
        boolean completed = true;
        stopEncoding = false;
        
        String message = "Playing Encoded Wav: " + wavFile.getName();
        printToGUIConsole(message, false);
        System.out.println("\n" + message);
        
        wavPlayer = new WavPlayer();
        try {
            wavPlayer.playBigWav(wavFile, soundOutput);
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException e) {
            message = "Error Playing Wav: " + wavFile.getName() + "\n" + e.getMessage();
            printToGUIConsole(message, true);
            System.out.println(message);
            
            e.printStackTrace();
            completed = false;
        }

        if (stopEncoding) {
            message = "\nPlaying of Encoded Wav Stopped ...";
            printToGUIConsole(message, true);
            System.out.println(message);
            completed = false;
        }

        // indicate that the encoding is done
        message = "\nPlaying of Encoded of Wav Done ...";
        printToGUIConsole(message, true);
        System.out.println(message);
        
        if(cassetteFlowFrame != null)
            cassetteFlowFrame.setEncodingDone();
        
        return completed;
    }
    
    /**
     * Create the input data for an mp3 track
     * @param tapeID
     * @param audioInfo
     * @param audioCount
     * @param muteTime
     * @return
     */
    public String createInputDataForAudio(String tapeID, AudioInfo audioInfo, int audioCount, int muteTime) {
        StringBuilder builder = new StringBuilder();

        String trackString = String.format("%02d", audioCount);
        String audioId = tapeID + "_" + trackString + "_" + audioInfo.getHash10C();
        
        // add a mute record to allow loading of mp3 correctly?
        for (int i = 0; i < muteTime; i++) {
            currentTimeTotal++;
            String timeTotalString = String.format("%04d", currentTimeTotal);
            String line = audioId + "_000M_" + timeTotalString + "\n";

            for (int j = 0; j < replicate; j++) { // replicate record N times
                builder.append(line);
            }
        }
        
        // add line records for each second of sound
        for (int i = 0; i < audioInfo.getLength(); i++) {
            String timeString = String.format("%04d", i);
            String timeTotalString = String.format("%04d", currentTimeTotal);
            String line = audioId + "_" + timeString + "_" + timeTotalString + "\n";

            for (int j = 0; j < replicate; j++) { // replicate record N times
                builder.append(line);
            }

            currentTimeTotal++;
        }

        return builder.toString();
    }
    
    /**
     * Stop real time encoding
     */
    void stopEncoding() {
        stopEncoding = true;
        
        if(wavPlayer != null) {
            wavPlayer.stop();
            wavPlayer = null;
        }
    }
    
    public ArrayList<AudioInfo> getRandomAudioList(int maxTime, int muteTime) {
        // get a shuffle list of mp3s or flac
        ArrayList<AudioInfo> shuffledAudio = shuffleAudioList();
        ArrayList<AudioInfo> tapeList = new ArrayList<>();
        
        System.out.println("\nGenerating Ramdom List Of MP3/Flac: (" + maxTime + "s / " + muteTime + "s)");
        
        int currentTime = 0;
        int totalTime = 0;
        
        for(int i = 0; i < shuffledAudio.size(); i++) {
            AudioInfo audioInfo = shuffledAudio.get(i);
            currentTime += audioInfo.getLength();
            
            int timeWithMute = currentTime + muteTime*i;
            if(timeWithMute <= maxTime) {
                tapeList.add(audioInfo);
                totalTime = timeWithMute;
                String trackCount = String.format("%02d", (i + 1));
                String trackName = "[" + trackCount + "] " + audioInfo;
                System.out.println(trackName);
            } else {
                break;
            }
        }
        
        System.out.println("\n" + tapeList.size() + " Tracks -- Play Time: " + CassetteFlowUtil.getTimeString(totalTime));
        
        return tapeList;
    }
    
    /**
     * Copy and shuffle the audioList Array
     * 
     * @return shuffle list containing the shuffled audioInfo objects
     */
    public ArrayList<AudioInfo> shuffleAudioList() {
         ArrayList<AudioInfo> audioListCopy = (ArrayList<AudioInfo>) audioInfoList.clone();
         Collections.shuffle(audioListCopy);
         return audioListCopy;
    }
    
    // copy and shuffle and audio list
    public ArrayList<AudioInfo> shuffleAudioList(ArrayList<AudioInfo> audioList) {
        ArrayList<AudioInfo> audioListCopy = (ArrayList<AudioInfo>) audioList.clone();
        Collections.shuffle(audioListCopy);
        return audioListCopy;
    }
    
    /**
     * Method to return the length in seconds of the mp3/flac file
     * 
     * @param file
     * @return the file length and bit rate
     */
    public int[] getAudioLengthAndBitRate(File file) {
        int[] info = {-1,-1};
        
        try {
            if(file.getName().toLowerCase().endsWith("mp3")) { 
                MP3File mp3 = (MP3File) AudioFileIO.read(file);
                MP3AudioHeader audioHeader = mp3.getMP3AudioHeader();
                info[0] = audioHeader.getTrackLength();
                info[1] = (int)audioHeader.getBitRateAsNumber()*1000;
                //System.out.println("Length/Rate " + info[0] + " | " + info[1]);
            } else {
                // flac file?
                AudioFile af = AudioFileIO.read(file);
                AudioHeader audioHeader = af.getAudioHeader();
                info[0] = audioHeader.getTrackLength();
                info[1] = (int)audioHeader.getBitRateAsNumber()*1000;
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        
        return info;
    }
    
    /**
     * Method to get the mp3 of flac files is a directory
     * 
     * @param directory
     * @param storeParentDirectory 
     */
    public final void loadAudioFiles(String directory, boolean storeParentDirectory) {
        try {
            File dir = new File(directory);

            FilenameFilter filter = (File f, String name) -> {
                name = name.toLowerCase();
                return name.endsWith(".mp3") || name.endsWith(".flac");
            };

            // Note that this time we are using a File class as an array,
            File[] files = dir.listFiles(filter);

            // add the files in the root directory
            for (File file : files) {
                addAudioFileToDatabase(file, storeParentDirectory, true);
            }
            
            // save the database as a tab delimited text file
            saveAudioInfoDB();
            
            // see if to set this the audio directory as the default
            if(files.length > 0 && !properties.containsKey("audio.directory")) {
                setDefaultAudioDirectory(directory);
                saveProperties();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Loads all audio files in the specified directory and subdirectories.
     * This is just a convenient way to not to have to manually load a 
     * directory containing the correct audio files when using the decoding
     * functionality
     * 
     * @param directory 
     */
    public final void buildAudioFileIndex(String directory) {
        try {
            System.out.println("Building Audio File Index Starting @ " + directory);
            
            Path rootPath = Paths.get(directory);
            List<Path> audioFiles = CassetteFlowUtil.findAllAudioFiles(rootPath);
            
            for(Path path: audioFiles) {
                File file = path.toFile();
                addAudioFileToDatabase(file, false, false);
                
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.printToConsole(path.toString(), true);
                }
            }
            
            // save the audiodb db as a binary file
            FileOutputStream fos = new FileOutputStream(new File(AUDIO_INDEX_FILENAME));
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(audioInfoDB);

            oos.close();
            fos.close();
            
            String message = "\n" + audioFiles.size() +  " Audio Files Indexed ...";
            System.out.println(message);
            
            if(cassetteFlowFrame != null) {
                cassetteFlowFrame.printToConsole(message, true);
            }
        } catch (IOException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Load the audio file index if the file exist
     */
    public void loadAudioFileIndex() {
        File file = new File(AUDIO_INDEX_FILENAME);
        
        if(file.canRead()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fis);
                audioInfoDB = (HashMap<String, AudioInfo>) in.readObject();
                in.close();
                fis.close();
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Add the MP3 or flac files to the database
     * 
     * @param file 
     * @param storeParentDirectory 
     * @param addToList Weather to add to the jlist for main GUI display
     */
    public void addAudioFileToDatabase(File file, boolean storeParentDirectory, boolean addToList) {
        String filename = file.getName();
        String sha10hex = CassetteFlowUtil.get10CharacterHash(filename);
        int[] ia = getAudioLengthAndBitRate(file);
        int length = ia[0];
        int bitRate = ia[1];
        String lengthAsTime = CassetteFlowUtil.getTimeString(length);

        AudioInfo audioInfo = new AudioInfo(file, sha10hex, length, lengthAsTime, bitRate);
        
        if(storeParentDirectory) {
            String parentDirecotryName = CassetteFlowUtil.getParentDirectoryName(file);
            audioInfo.setParentDirectoryName(parentDirecotryName);
        }
        
        if(addToList) {
            audioInfoList.add(audioInfo);
        }
        
        audioInfoDB.put(sha10hex, audioInfo);
        
        System.out.println(sha10hex + " -- " + audioInfo);
    }
        
    /**
     * Method to set the download server where the mp3 or flac files for 
     * download are stored
     * 
     * This is predominantly for testing purposes
     * @param url 
     */
    public void setDownloadServer(String url) {
        this.DOWNLOAD_SERVER = url;
        saveProperties();
    }
    
    /**
     * Set the default MP3 directory
     * 
     * @param audioDirectory
     */
    public void setDefaultAudioDirectory(String audioDirectory) {
        AUDIO_DIR_NAME = audioDirectory;
        AUDIO_INDEX_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.bin";
        AUDIO_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.txt";
        TAPE_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "tapedb.txt";
        TRACK_LIST_FILENAME = AUDIO_DIR_NAME + File.separator + "tracklist.txt";
        LOG_FILE_NAME = AUDIO_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tape.log";
    }
    
    /**
     * Set the IP address of the lyraT host
     * @param host 
     */
    public void setLyraTHost(String host) {
        LYRA_T_HOST = host;
        saveProperties();
    }
    
    /**
     * Set the minimodem log file
     * 
     * @param logfileName 
     */
    public void setMinimodemLogFile(String logfileName) {
        LOG_FILE_NAME = logfileName;
        saveProperties();
    }
    
    /**
     * If cassette flow GUI is available print to the GUI console
     * 
     * @param message
     * @param append 
     */
    public void printToGUIConsole(String message, boolean append) {
        if(cassetteFlowFrame != null) {
            cassetteFlowFrame.printToConsole(message, append);
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {        
        // get any command line arguments
        final String cla;
        if(args.length > 0) {
            cla = args[0];
        } else {
            cla = "";
        }
        
        CassetteFlow cassetteFlow = new CassetteFlow();
        
        // check to see if to load other directories
        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) {
                String dirName = args[i];
                File dir = new File(dirName);

                System.out.println("Loading MP3s from " + dirName);

                if (dir.isDirectory()) {
                    cassetteFlow.loadAudioFiles(dirName, true);
                    System.out.println("Done with " + dirName);
                }
            }
        }
        
        if(DEBUG || (args.length > 0 && cla.equals("cli"))) {
            try {
                CassettePlayer cassettePlayer = new CassettePlayer(cassetteFlow, LOG_FILE_NAME);
                //cassettePlayer.startLogTailer();
                cassettePlayer.startMinimodem(0);
            } catch(IOException ex) {
                ex.printStackTrace();
            }
            
            // TEST CODE
            //cassettePlayer.startMP3Download("2ff38e2276");
        } else {
            // set the look and feel
            FlatDarculaLaf.setup();
            
            //** Start main application UI here
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    cassetteFlowFrame = new CassetteFlowFrame();
                    cassetteFlowFrame.setCassetteFlow(cassetteFlow);
                    
                    if(cla.equals("fsm")) {
                        cassetteFlowFrame.initFullScreen();
                    } else {
                        cassetteFlowFrame.pack();
                    }
                    
                    cassetteFlowFrame.setVisible(true);
                }
            });
        }
    }
}
