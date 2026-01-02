package cassetteflow;

import com.formdev.flatlaf.FlatDarculaLaf;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A simple program for creating input files for the cassette flow encoding
 * program/method. It make use of the following libraries for dealing with
 * MP3/flac playback and meta data extraction
 * 
 * Playing of MP3s/FLAC: https://github.com/goxr3plus/java-stream-player
 * 
 * Getting meta data: http://www.jthink.net/jaudiotagger/examples_id3.jsp
 * 
 * Generating hash of filenames (Apache Commons Codec):
 * https://www.baeldung.com/sha-256-hashing-java
 * 
 * Reading the last line of a file:
 * https://web.archive.org/web/20160510001134/http://www.informit.com/guides/content.aspx?g=java&seqNum=226
 * 
 * @author Nathan Stevens 09/05/2021
 */
public class CassetteFlow {
    // The default mp3 directory name
    public static String AUDIO_DIR_NAME = "c:\\mp3files";

    // store the mp3/flac id hashmap to a tab delimitted file for use on the LyraT
    // board
    public static String AUDIO_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.txt";

    // store the audio db file in binary form to make it more efficient to load
    // audio information
    public static String AUDIO_INDEX_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.bin";

    // store the loaded stream audio files object in a seperate db file
    public static String STREAM_AUDIO_INDEX_FILENAME = AUDIO_DIR_NAME + File.separator + "streamAudiodb.bin";

    // default directory where the text files to be encoded
    public static final String TAPE_FILE_DIR_NAME = "TapeFiles";

    // store the DCT info in a binary file
    public static String DCT_INFO_FILENAME = AUDIO_DIR_NAME + File.separator + "dctinfo.bin";

    public static String LOG_FILE_NAME = AUDIO_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator
            + "tape.log";

    // stores the audioInfo object keyed by the 10 character hash
    public HashMap<String, AudioInfo> audioInfoDB = new HashMap<>();

    // stores the stream audioInfo object keyed by the 10 character hash
    public HashMap<String, AudioInfo> streamAudioInfoDB = new HashMap<>();

    // also store the AudioInfo object in a list for convinience when
    // displaying in the UI
    public ArrayList<AudioInfo> audioInfoList = new ArrayList<>();

    // stores the cassette ID to the mp3ids
    public TreeMap<String, ArrayList<String>> tapeDB = new TreeMap<>();

    public HashMap<String, TrackListInfo> tracklistDB = new HashMap<>();

    public static String TAPE_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "tapedb.txt";

    public static String TRACK_LIST_FILENAME = AUDIO_DIR_NAME + File.separator + "tracklist.txt";

    // the location of mp3 files on the server
    public static String DOWNLOAD_SERVER = "http://192.168.1.45/~pi/mp3/";

    // The IP address of the LyraT host
    public static String LYRA_T_HOST = "http://127.0.0.1:8192/";

    public static String BAUDE_RATE = "1200";

    // the url for the jcard site
    public static String JCARD_SITE = "https://ed7n.github.io/jcard-template/";

    // used when running in desktop mode
    private static CassetteFlowFrame cassetteFlowFrame;

    private CassettePlayer cassettePlayer;

    // store program properties
    private Properties properties = new Properties();

    private final String PROPERTIES_FILENAME = "cassetteFlow.properties";

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

    // indicate if the index has been loaded
    private boolean indexLoaded = false;

    // indicates if we are running on mac so we start pusle audio in addition
    // to minimodem
    public static boolean isMacOs = false;

    public static long startTime = 0;

    // debug flag
    private static final boolean DEBUG = false;

    // object to create dummy audio files from a spotify dataset for testing the UI
    // with large number
    // of records
    private SpotifyDatasetLoader spotifyDatasetLoader;

    // create a json object which is used to capture the state of the decode process
    private final JSONObject currentDeocdeState = new JSONObject();

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
    public final void init() {
        loadProperties();

        // keep track of start time so we can se how long it take to load files
        startTime = System.currentTimeMillis();

        // load the audio file and stream audio file index to make decoding much easier
        loadAudioFileIndex();

        // load audio files which is displayed in the GUI, and overwrites the
        // records in the audio file index.
        if (!indexLoaded) {
            loadAudioFiles(AUDIO_DIR_NAME, false);
        } else {
            loadAudioFilesFromIndex(AUDIO_DIR_NAME);
            loadStreamAudioFileIndex();
        }

        File file = new File(TAPE_DB_FILENAME);
        tapeDB = loadTapeDB(file);

        file = new File(TRACK_LIST_FILENAME);
        tracklistDB = loadTrackListDB(file);

        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("\nTotal time to load sound files: " + elapsedTime + " Milliseconds\n");

        // iniate the state java script
        try {
            currentDeocdeState.put("tracks", new JSONArray());
            currentDeocdeState.put("currentlyPlaying", "None");
            currentDeocdeState.put("currentlyPlayingId", 0);
            currentDeocdeState.put("isPlaying", false);
            currentDeocdeState.put("newTracks", false); // are the tracks new?
        } catch (JSONException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Load the default properties
     */
    private void loadProperties() {
        // try loading the properties
        try (FileReader fileReader = new FileReader(PROPERTIES_FILENAME)) {
            properties.load(fileReader);

            DOWNLOAD_SERVER = properties.getProperty("download.server");
            LYRA_T_HOST = properties.getProperty("lyraT.host");
            BAUDE_RATE = properties.getProperty("baude.rate", "1200");
            LOG_FILE_NAME = properties.getProperty("minimodem.log.file", "");
            JCARD_SITE = properties.getProperty("jcard.site", "https://ed7n.github.io/jcard-template/");

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
        try (FileWriter output = new FileWriter(PROPERTIES_FILENAME)) {
            properties.put("audio.directory", AUDIO_DIR_NAME);
            properties.put("download.server", DOWNLOAD_SERVER);
            properties.put("lyraT.host", LYRA_T_HOST);
            properties.put("baud.rate", BAUDE_RATE);
            properties.put("minimodem.log.file", LOG_FILE_NAME);
            properties.put("jcard.site", JCARD_SITE);

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

    /**
     * Return Javascript object from the currently playing information
     *
     * @param currentlyPlaying
     * @return
     */
    private JSONObject getCurrentPlayingAsJsonObject(String currentlyPlaying) {
        JSONObject infoObject = new JSONObject();
        try {
            // split the currently playing string into an string array by newline character
            infoObject.put("version", 1.0);
            infoObject.put("message", currentlyPlaying);

            String[] sa = currentlyPlaying.split("\n");

            if (sa.length > 1) {
                infoObject.put("title", sa[0]);
                infoObject.put("playTime", sa[1]);

                if (sa.length == 5) {
                    infoObject.put("tapeCount", sa[3]);
                    infoObject.put("dataErrors", sa[4]);
                } else {
                    infoObject.put("tapeCount", sa[2]);
                    infoObject.put("dataErrors", sa[3]);
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }

        return infoObject;
    }

    /**
     * Set the current state of the decode process to be served to the web view
     *
     * @param currentlyPlaying
     * @param currentlyPlayingId
     * @param isPlaying
     */
    public synchronized void setCurrentDecodeState(String currentlyPlaying,
            int currentlyPlayingId, boolean isPlaying) {

        try {
            currentDeocdeState.put("currentlyPlaying", getCurrentPlayingAsJsonObject(currentlyPlaying));
            currentDeocdeState.put("currentlyPlayingId", currentlyPlayingId);
            currentDeocdeState.put("isPlaying", isPlaying);
            currentDeocdeState.put("newTracks", false);
        } catch (JSONException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Set the current state of the decode process to be served to the web view
     *
     * @param tracks
     * @param currentlyPlaying
     * @param currentlyPlayingId
     * @param isPlaying
     */
    public synchronized void setCurrentDecodeState(JSONArray tracks,
            String currentlyPlaying, int currentlyPlayingId, boolean isPlaying) {
        try {
            currentDeocdeState.put("tracks", tracks);
            currentDeocdeState.put("currentlyPlaying", getCurrentPlayingAsJsonObject(currentlyPlaying));
            currentDeocdeState.put("currentlyPlayingId", currentlyPlayingId);
            currentDeocdeState.put("isPlaying", isPlaying);
            currentDeocdeState.put("newTracks", true); // are the tracks new?
        } catch (JSONException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Get the current state of the decode process
     *
     * @return JSONObject containing the state of the decode process
     */
    public synchronized JSONObject getCurrentDecodeState() {
        try {
            currentDeocdeState.put("rawLineRecord", getRawLineRecord());
            currentDeocdeState.put("currentLineRecord", getCurrentLineRecord());
        } catch (JSONException ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }

        return currentDeocdeState;
    }

    /**
     * Run a decode command, either start, stop, offset, reset
     * 
     * @param command
     */
    public void runDecodeCommand(String command) {
        if (cassetteFlowFrame != null) {
            cassetteFlowFrame.runDecodeCommand(command);
        }

        System.out.println("Decode Command Received: " + command);
    }

    /**
     * Get the raw line record from the minimodem program. That's the record
     * read from the tape
     * 
     * @return
     */
    public synchronized String getRawLineRecord() {
        if (cassettePlayer != null) {
            return cassettePlayer.getRawLineRecord();
        } else {
            return "NO PLAYER ...";
        }
    }

    /**
     * Get the raw line record from the minimodem program. That's the record
     * read from the tape then processed. So if it was a DCT record from the
     * tape it's then translated to the actual line record used to control
     * playback
     * 
     * @return
     */
    public synchronized String getCurrentLineRecord() {
        if (cassettePlayer != null) {
            return cassettePlayer.getCurrentLineRecord();
        } else {
            return "NO PLAYER ...";
        }
    }

    /**
     * Save the MP3/Flac map as a tab delimited file. Not currently used
     * other than to provide examples of how this should look
     */
    private void saveAudioInfoDB() {
        try {
            FileWriter writer = new FileWriter(AUDIO_DB_FILENAME);

            for (String key : audioInfoDB.keySet()) {
                AudioInfo audioInfo = audioInfoDB.get(key);

                if (audioInfo.getFile() != null) {
                    // add "/sdcard/" to the designated sdCardFilename
                    // Also include relativePath as 5th column for sync script
                    String filenameToCheck = audioInfo.getSdCardFilename();
                    String relativePath = audioInfo.getRelativePath();

                    // Fallbacks just in case
                    if (filenameToCheck == null || filenameToCheck.isEmpty()) {
                        filenameToCheck = audioInfo.getFile().getName();
                    }
                    if (relativePath == null) {
                        relativePath = audioInfo.getFile().getName();
                    }

                    String line = key + "\t" + audioInfo.getLength() + "\t" + audioInfo.getBitRate()
                            + "\t/sdcard/" + filenameToCheck
                            + "\t" + relativePath + "\n";

                    writer.write(line);
                }
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
            String line = key + "\t" + audioInfo.getLength() + "\t" + audioInfo.getBitRate() + "\t"
                    + audioInfo.getFile().getName() + "\n";
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
     * Saves the cassette data which stores info on tape IDs and their associated
     * mp3 ids
     * 
     * @param file
     * @throws IOException
     */
    public void saveTapeDB(File file) throws IOException {
        try {
            FileWriter writer = new FileWriter(file);

            for (String key : tapeDB.keySet()) {
                String line = key + "\t" + String.join("\t", tapeDB.get(key)) + "\n";
                writer.write(line);
            }

            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Loads the cassette data which stores info on tape IDs and their associated
     * mp3 ids
     * 
     * @param file
     */
    private TreeMap<String, ArrayList<String>> loadTapeDB(File file) {
        TreeMap<String, ArrayList<String>> localTapeDB = new TreeMap<>();

        try {
            if (file.exists()) {
                /*
                 * FileInputStream fis = new FileInputStream(file);
                 * ObjectInputStream ois = new ObjectInputStream(fis);
                 * cassetteDB = (HashMap<String, ArrayList<String>>) ois.readObject();
                 * ois.close();
                 */

                FileReader reader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(reader);

                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    String[] sa = line.split("\t");
                    String key = sa[0];

                    ArrayList<String> audioIds = new ArrayList<>();
                    for (int i = 1; i < sa.length; i++) {
                        audioIds.add(sa[i]);
                    }

                    localTapeDB.put(key, audioIds);
                }
                reader.close();

                System.out.println("\nCassette database file loaded ... ");

                for (String key : localTapeDB.keySet()) {
                    ArrayList<String> audioIds = localTapeDB.get(key);
                    System.out.println(key + " >> " + audioIds);
                }
            }
        } catch (Exception ex) {
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

            if (file.exists()) {
                FileReader reader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(reader);

                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    // check to see if line is not empty
                    if (line.trim().isEmpty() || line.startsWith("File")) {
                        continue;
                    }

                    String[] sa = line.split("\t");
                    String key = sa[0];

                    if (!key.isEmpty()) {
                        if (!tracks.containsKey(key)) {
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // generate the lookup table for each of the track list
        tracks.forEach((k, v) -> {
            v.createLookUpTable();
            // System.out.println("Tracklist Key: " + k + " Tracks:\n" + v);
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
                    if (!sa[i].isEmpty()) {
                        audioIds.add(sa[i]);
                    }
                }

                remoteDB.put(key, audioIds);
            } catch (Exception e) {
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

            for (String key : tapeDB.keySet()) {
                if (!localTapeDB.containsKey(key)) {
                    localTapeDB.put(key, tapeDB.get(key));
                }
            }

            // save the local db file now
            FileWriter writer = new FileWriter(file);

            for (String key : localTapeDB.keySet()) {
                String line = key + "\t" + String.join("\t", tapeDB.get(key)) + "\n";
                writer.write(line);
            }

            writer.close();
        } catch (IOException ex) {
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
    public void addToTapeDB(String tapeID, ArrayList<AudioInfo> sideAList, ArrayList<AudioInfo> sideBList,
            boolean save) {
        // save information for side A
        ArrayList<String> audioIds = new ArrayList<>();

        if (sideAList != null && !sideAList.isEmpty()) {
            // store track for side A
            for (AudioInfo audioInfo : sideAList) {
                audioIds.add(audioInfo.getHash10C());
            }

            tapeDB.put(tapeID + "A", audioIds);
        }

        if (sideBList != null && !sideBList.isEmpty()) {
            audioIds = new ArrayList<>();

            // store track for side B
            for (AudioInfo audioInfo : sideBList) {
                audioIds.add(audioInfo.getHash10C());
            }

            tapeDB.put(tapeID + "B", audioIds);
        }

        // save the database file if required
        if (save) {
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
     * @param forDownload   specifies if this is for mp3/flac which are to be
     *                      downloaded
     * 
     * @return indicates if the input file(s) were successfully created
     */
    public boolean createInputFiles(String directoryName, String tapeID, ArrayList<AudioInfo> sideA,
            ArrayList<AudioInfo> sideB, int muteTime, boolean forDownload) {
        // check to make sure directory exist
        File directory = new File(directoryName);
        if (!directory.exists()) {
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
        } catch (IOException ioe) {
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
    public String createInputFileForSide(File inputFile, String tapeID, ArrayList<AudioInfo> sideN, int muteTime,
            boolean forDownload) throws IOException {
        System.out.println("Creating Cassette Tape Input: " + inputFile + ", " + tapeID + ", " + muteTime);
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
        if (forDownload) {
            tapeHashCode = CassetteFlowUtil.get10CharacterHash(tapeID + sideN.toString());
            String filename = inputFile.getParent() + File.separator + "Tape_" + tapeHashCode + ".tsv";

            // add the header to the download file indicating the TapeID
            myWriter2 = new FileWriter(filename);
            myWriter2.write("Tape ID   \t" + tapeID + "\n");

            System.out.println("Download File Name: " + filename);

            // add special line records to indicate the mp3 files need to be downloaded
            String line;
            for (int j = 0; j < replicate * 10; j++) { // replicate record N times ~ 10 seconds of time
                String count = String.format("%04d", j);
                line = "HTTPS_00_" + tapeHashCode + "_000M_" + count + "\n";
                myWriter.write(line);
                builder.append(line);
            }
        }

        for (AudioInfo audioInfo : sideN) {
            String trackS = String.format("%02d", fileCount + 1);
            String audioId = tapeID + "_" + trackS + "_" + audioInfo.getHash10C();

            // add this entry to the download file
            if (forDownload) {
                String line = audioInfo.getHash10C() + "\t" + audioInfo.getFile().getName() + "\n";
                myWriter2.write(line);
            }

            // add line records to create a N second muted section before next song
            if (fileCount >= 1) {
                currentTimeTotal += muteTime;
                String timeTotalString = String.format("%04d", currentTimeTotal);
                String line = audioId + "_00" + muteTime + "M_" + timeTotalString + "\n";
                myWriter.write(line);
                builder.append(line);

                /*
                 * for(int i = 0; i < muteTime; i++) {
                 * currentTimeTotal += 1;
                 * String timeTotalString = String.format("%04d", currentTimeTotal);
                 * String line = mp3Id + "_000M_" + timeTotalString + "\n";
                 * 
                 * for(int j = 0; j < replicate; j++) { // replicate record N times
                 * myWriter.write(line);
                 * builder.append(line);
                 * }
                 * }
                 */
            }

            for (int i = 0; i < audioInfo.getLength(); i++) {
                String timeString = String.format("%04d", i);
                String timeTotalString = String.format("%04d", currentTimeTotal);
                String line = audioId + "_" + timeString + "_" + timeTotalString + "\n";

                for (int j = 0; j < replicate; j++) { // replicate record N times
                    myWriter.write(line);
                    builder.append(line);
                }

                currentTimeTotal += 1;
            }

            fileCount += 1;
        }

        // close the file writter
        myWriter.close();

        if (myWriter2 != null) {
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
     * @param maxTimeBlock
     */
    public void createDCTArrayList(String tapeID, ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB, int muteTime,
            int maxTimeBlock) {
        if (sideA != null && sideA.size() >= 1) {
            sideADCTList = createDCTArrayListForSide(tapeID + "A", sideA, muteTime, maxTimeBlock);
        }

        if (sideB != null && sideB.size() >= 1) {
            sideBDCTList = createDCTArrayListForSide(tapeID + "B", sideB, muteTime, maxTimeBlock);
        }

        // save to the tape database
        addToTapeDB(tapeID, sideA, sideB, true);

        // save to the DCT Info to a binary file
        saveDCTInfo(sideADCTList, sideBDCTList, tapeID, sideA, sideB);
    }

    /**
     * Create a Dynamic Content Track Array for a particular tape ID
     * 
     * @param tapeID
     * @param muteTime
     * @return true if successful, false otherwise
     */
    public boolean createDCTArrayList(String tapeID, int muteTime) {
        // get the arraylist of audio info objects from the tape database
        ArrayList<String> sideAList = tapeDB.get(tapeID + "A");
        ArrayList<String> sideBList = tapeDB.get(tapeID + "B");
        ArrayList<AudioInfo> sideA = null;
        ArrayList<AudioInfo> sideB = null;

        if (sideAList != null && sideAList.size() >= 1) {
            sideA = new ArrayList<AudioInfo>();
            for (String audioId : sideAList) {
                AudioInfo audioInfo = audioInfoDB.get(audioId);
                sideA.add(audioInfo);
            }

            sideADCTList = createDCTArrayListForSide(tapeID + "A", sideA, muteTime, -1);
        }

        if (sideBList != null && sideBList.size() >= 1) {
            sideB = new ArrayList<AudioInfo>();
            for (String audioId : sideBList) {
                AudioInfo audioInfo = audioInfoDB.get(audioId);
                sideB.add(audioInfo);
            }

            sideBDCTList = createDCTArrayListForSide(tapeID + "B", sideB, muteTime, -1);
        }

        // save to the DCT Info to a binary file if at least sideA or sideB is not null
        if (sideA != null || sideB != null) {
            saveDCTInfo(sideADCTList, sideBDCTList, tapeID, sideA, sideB);
            return true;
        }

        return false;
    }

    /**
     * Save the DCT info to a file
     * 
     * @param tapeID
     * @param sideADCTList
     * @param sideBDCTList
     * @param sideA
     * @param sideB
     */
    public void saveDCTInfo(ArrayList<String> sideADCTList, ArrayList<String> sideBDCTList, String tapeID,
            ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB) {
        DCTInfo dctInfo = new DCTInfo(sideADCTList, sideBDCTList, tapeID, sideA, sideB);

        // save the dctInfo to the default DCT binary file
        try {
            FileOutputStream fileOut = new FileOutputStream(DCT_INFO_FILENAME);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(dctInfo);
            out.close();
            fileOut.close();
            System.out.println("Saved DCT Info to file: " + DCT_INFO_FILENAME + " " + dctInfo.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load the DCT info from a file
     */
    public void loadDCTInfo() {
        try {
            FileInputStream fileIn = new FileInputStream(DCT_INFO_FILENAME);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            DCTInfo dctInfo = (DCTInfo) in.readObject();
            in.close();

            sideADCTList = dctInfo.getSideADCTList();
            sideBDCTList = dctInfo.getSideBDCTList();

            // save to the tape database
            addToTapeDB(dctInfo.getTapeID(), dctInfo.getSideA(), dctInfo.getSideB(), true);

            System.out.println("Loaded DCT Info from file: " + DCT_INFO_FILENAME + " " + dctInfo.toString());
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Failed to load DCT Info from file: " + DCT_INFO_FILENAME);
        }
    }

    /**
     * Clear the DCT array list
     */
    public void clearDCTArrayList() {
        sideADCTList = null;
        sideBDCTList = null;
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
        return createDCTArrayListForSide(tapeID, sideN, muteTime, -1);
    }

    /**
     * Create a array list containing line records for the audio tracks on side
     * A or side B
     * 
     * @param tapeID
     * @param sideN
     * @param muteTime     in seconds
     * @param maxTimeBlock This is used to add mute sections so that no audio is
     *                     played past the playtime of the physical media in seconds
     * @return
     */
    public ArrayList<String> createDCTArrayListForSide(String tapeID, ArrayList<AudioInfo> sideN, int muteTime,
            int maxTimeBlock) {
        System.out.println("Creating DCT Array: " + tapeID + ", Mute Time: " + muteTime);
        System.out.println(sideN);

        ArrayList<String> dctList = new ArrayList<>();
        currentTimeTotal = 0;
        int fileCount = 0;

        // keep track of the number of audio blocks for correct padding
        int blockCount = 1;

        String tapeSide = "A";
        if (tapeID.endsWith("B")) {
            tapeSide = "B";
        }

        // if the maxtime block != -1 and cassetteflowframe doesn't == null then reset
        // the endtimes array
        if (maxTimeBlock != -1 && cassetteFlowFrame != null && tapeSide.equals("A")) {
            cassetteFlowFrame.resetTimeBlockEndTracks();
        }

        for (AudioInfo audioInfo : sideN) {
            String trackS = String.format("%02d", fileCount + 1);

            // get the 10 character hash and see if it contains "_" if so replace with $
            // this is needed becuase youtube video ID can contain "_"
            String cleanHash = audioInfo.getHash10C().replace("_", "$");
            String audioId = tapeID + "_" + trackS + "_" + cleanHash;

            // add line records to create a N second muted section before next song
            if (fileCount >= 1) {
                int maxMuteTime = muteTime;
                String muteString = "_000M_";

                // if maxTimeBlock does not equals -1 then calculate maxMuteTime
                if (maxTimeBlock != -1 && (audioInfo.getLength() + currentTimeTotal > maxTimeBlock * blockCount)) {
                    maxMuteTime = maxTimeBlock * blockCount - currentTimeTotal;
                    muteString = "_00MM_";

                    System.out.println("\nPadding DCT Line Records For: " + maxMuteTime +
                            "(s) After Track: " + fileCount +
                            ", Time Block Count: " + blockCount +
                            ", Current Time: " + currentTimeTotal + "(s)");

                    if (cassetteFlowFrame != null) {
                        cassetteFlowFrame.addTimeBlockEndTrack(tapeSide + fileCount);
                    }

                    // increment the number of time blocks
                    blockCount++;
                }

                for (int i = 0; i < maxMuteTime; i++) {
                    currentTimeTotal += 1;
                    String timeTotalString = String.format("%04d", currentTimeTotal);
                    String line = audioId + muteString + timeTotalString;
                    dctList.add(line);
                }
            }

            for (int i = 0; i < audioInfo.getLength(); i++) {
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
        this.dctOffset = dctOffset * 60; // convert to seconds
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
            } catch (NumberFormatException nfe) {
                totalTime = Integer.parseInt(sa[3]);
                System.out.println("Using backup for DCT TotalTime: " + line);
            }

            // add the dct offset
            // System.out.println("Tape Total Time: " + totalTime + " / Offset " +
            // dctOffset);
            totalTime = totalTime + dctOffset;

            ArrayList<String> dctList;

            // based on total time and side get the line record. Any exception
            // will result in a null being returned
            if (tapeId.endsWith("A")) {
                dctList = sideADCTList;
            } else {
                dctList = sideBDCTList;
            }

            if (dctList != null && totalTime < dctList.size()) {
                return dctList.get(totalTime);
            } else {
                // System.out.println("Invalid Time Code Index: " + line);
                return "TAPE TIME: " + totalTime;
            }
        } catch (NumberFormatException ex) {
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
     * @return The encoded file objects
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
        if (CassetteFlow.isMacOs) {
            try {
                Runtime.getRuntime().exec("pulseaudio");
                Thread.sleep(1000);
                System.out.println("Starting pulseaudio ...");
            } catch (InterruptedException ex) {
            }
        }

        if (sideA != null && !sideA.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A" + ".txt");
            wavFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A-" + BAUDE_RATE + ".wav");
            data = createInputFileForSide(file, tapeID + "A", sideA, muteTime, forDownload);
            runMiniModemAndMergeWav(wavFile, data, muteTime);
            // runMinimodem(wavFile, data);
            wavFiles[0] = wavFile;
        }

        if (sideB != null && !sideB.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B" + ".txt");
            wavFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B-" + BAUDE_RATE + ".wav");
            data = createInputFileForSide(file, tapeID + "B", sideB, muteTime, forDownload);
            runMiniModemAndMergeWav(wavFile, data, muteTime);
            // runMinimodem(wavFile, data);
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
        if (!blankWavFile.exists()) {
            InputStream source = getClass().getClassLoader().getResourceAsStream("sounds/blank_1s.wav");
            Files.copy(source, Paths.get(blankFilename), StandardCopyOption.REPLACE_EXISTING);
        }

        String[] sa = data.split("\n");
        int track = 1;
        String trackData;
        File trackFile = new File(mergedWavFile.getParent() + File.separator + "track_" + track + ".wav");

        for (String line : sa) {
            if (!line.contains("M_")) {
                sb.append(line).append("\n");
            } else {
                trackData = sb.toString();
                runMinimodem(trackFile, trackData);
                wavFiles.add(trackFile);

                // add a blank files
                for (int i = 0; i < muteTime; i++) {
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
        if (wavFiles.size() == 1) {
            wavFiles.add(blankWavFile);
        }

        // merge the wave files now. This takes a lot of memory since the all the waves
        // need
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
        System.out.println("\nEncoding data via JMinimodem...");

        try {
            // 1. Setup Configuration
            JMinimodem.Config config = new JMinimodem.Config();
            config.txMode = true;
            config.sampleRate = 48000.0f; // Standard high quality rate
            config.quiet = true; // Suppress internal logging

            try {
                config.baudRate = Double.parseDouble(BAUDE_RATE);
            } catch (NumberFormatException e) {
                config.baudRate = 1200.0; // Default fallback
                System.err.println("Invalid BAUDE_RATE, defaulting to 1200");
            }

            // 2. Prepare Streams
            // Input: The string data to encode (ensure UTF-8)
            java.io.ByteArrayInputStream textInput = new java.io.ByteArrayInputStream(data.getBytes("UTF-8"));

            // Output: A byte buffer to hold the raw PCM audio data
            java.io.ByteArrayOutputStream pcmOutput = new java.io.ByteArrayOutputStream();

            // 3. Run Encoding (JMinimodem Core)
            // This generates raw PCM samples (no WAV header yet)
            JMinimodem.transmit(config, textInput, pcmOutput);

            // 4. Save to WAV File
            byte[] rawAudio = pcmOutput.toByteArray();

            // Define format: 48kHz, 16-bit, Mono, Signed, Little Endian
            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                    config.sampleRate, 16, 1, true, false);

            // Write the raw bytes + header to the file
            try (javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(
                    new java.io.ByteArrayInputStream(rawAudio), format, rawAudio.length / 2)) {
                javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavFile);
            }

            System.out.println("Done Encoding: " + wavFile.getAbsolutePath() + " (" + rawAudio.length + " bytes)");

        } catch (Exception ex) {
            // Re-throw as IOException to maintain method signature compatibility
            throw new IOException("JMinimodem encoding failed", ex);
        }
    }

    /**
     * Encode directly using JMinimodem, instead of minimodem C program and play the
     * resulting
     * wavefile
     * 
     * @param tapeID
     * @param sideN
     * @param muteTime
     * @param forDownload       Currently not used
     * @param saveDirectoryName
     * @param soundOutput
     * @return Indicate if encode completed or was stopped
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public boolean realTimeEncode(String tapeID, ArrayList<AudioInfo> sideN, int muteTime,
            boolean forDownload, String saveDirectoryName, Mixer.Info soundOutput)
            throws IOException, InterruptedException {

        boolean completed = true;
        stopEncoding = false;
        currentTapeID = tapeID;
        currentTimeTotal = 0;
        currentAudioCount = 1;

        String message;

        wavPlayer = new WavPlayer();

        // PulseAudio restart removed: JMinimodem uses Java Sound directly,
        // so external PulseAudio resets are usually unnecessary.

        for (AudioInfo audioInfo : sideN) {
            long encodeStartTime = System.currentTimeMillis();

            currentAudioID = audioInfo.getHash10C();
            String data = createInputDataForAudio(tapeID, audioInfo, currentAudioCount, 1);

            // indicate the current track being processed
            if (cassetteFlowFrame != null)
                cassetteFlowFrame.setSelectedIndexForSideJList(currentAudioCount - 1);

            message = "JMinimodem Encoding: " + tapeID + " Track [ " + currentAudioCount + " ] ( "
                    + audioInfo.getLengthAsTime() + " )";
            printToGUIConsole(message, false);
            System.out.println("\n" + message);

            String filename = saveDirectoryName + File.separator + "track_" + currentAudioCount + "-" + BAUDE_RATE
                    + ".wav";
            File wavFile = new File(filename);

            // --- JMinimodem Encoding Start ---
            try {
                // 1. Configure JMinimodem
                JMinimodem.Config config = new JMinimodem.Config();
                config.txMode = true;
                config.sampleRate = 48000.0f; // High quality sample rate
                config.quiet = true; // Suppress internal logging
                try {
                    config.baudRate = Double.parseDouble(BAUDE_RATE);
                } catch (NumberFormatException e) {
                    config.baudRate = 1200.0;
                }

                // 2. Prepare Streams
                // Input: Data string to bytes
                ByteArrayInputStream textInput = new ByteArrayInputStream(data.getBytes("UTF-8"));
                // Output: Buffer for Raw PCM
                ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();

                // 3. Generate Audio Data (Core Logic)
                JMinimodem.transmit(config, textInput, pcmOutput);

                // 4. Write to WAV File
                byte[] rawAudio = pcmOutput.toByteArray();
                javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                        config.sampleRate, 16, 1, true, false);

                try (javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(
                        new ByteArrayInputStream(rawAudio), format, rawAudio.length / 2)) {
                    javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavFile);
                }

            } catch (Exception e) {
                message = "Encoding Failed: " + e.getMessage();
                printToGUIConsole(message, true);
                System.err.println(message);
                e.printStackTrace();
                return false;
            }
            // --- JMinimodem Encoding End ---

            long endTime = System.currentTimeMillis();
            long encodeTime = endTime - encodeStartTime;

            message = "JMinimodem Encode Time: " + encodeTime + " milliseconds";
            printToGUIConsole(message, true);
            System.out.println(message);

            message = "\nDone Encoding Track [ " + currentAudioCount + " ] Total Tape Time: " + currentTimeTotal
                    + " seconds";
            printToGUIConsole(message, true);
            System.out.println(message);

            if (currentAudioCount > 1) {
                // sleep for the desired mute time to allow for blank on the tape
                // a blank on the tape allows for track skipping on decks that
                // supported it
                int encodeTimeSeconds = Math.round(encodeTime / 1000);
                int delay = muteTime * 1000 - (int) encodeTime;

                int sleepTime = (encodeTimeSeconds > muteTime) ? encodeTimeSeconds : muteTime;

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
                if (wavPlayer != null) { // check for null here since we could have stop encoding
                    wavPlayer.playBigWav(wavFile, soundOutput);
                }
            } catch (IOException | LineUnavailableException | UnsupportedAudioFileException e) {
                message = "Error Playing Wav: " + filename + "\n" + e.getMessage();
                printToGUIConsole(message, true);
                System.out.println(message);

                e.printStackTrace();

                completed = false;
                break;
            }

            // TO-DO -- Delete the Wav file here
            // wavFile.delete();

            if (stopEncoding) {
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

        if (cassetteFlowFrame != null)
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

        if (cassetteFlowFrame != null)
            cassetteFlowFrame.setEncodingDone();

        return completed;
    }

    /**
     * Create the input data for an mp3 track
     * 
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

        if (wavPlayer != null) {
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

        for (int i = 0; i < shuffledAudio.size(); i++) {
            AudioInfo audioInfo = shuffledAudio.get(i);
            currentTime += audioInfo.getLength();

            int timeWithMute = currentTime + muteTime * i;
            if (timeWithMute <= maxTime) {
                tapeList.add(audioInfo);
                totalTime = timeWithMute;
                String trackCount = String.format("%02d", (i + 1));
                String trackName = "[" + trackCount + "] " + audioInfo;
                System.out.println(trackName);
            } else {
                break;
            }
        }

        System.out
                .println("\n" + tapeList.size() + " Tracks -- Play Time: " + CassetteFlowUtil.getTimeString(totalTime));

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
     * Method to return the length/bit and tag information of the mp3/flac file
     * 
     * @param file The mp3 or flac file
     * @return the file length and bit rate
     */
    public HeaderAndTagInfo getHeaderAndTagInfo(File file) {
        HeaderAndTagInfo headerAndTagInfo = new HeaderAndTagInfo();

        try {
            Tag tag;
            AudioHeader audioHeader;

            if (file.getName().toLowerCase().endsWith("mp3")) {
                MP3File mp3 = (MP3File) AudioFileIO.read(file);
                audioHeader = mp3.getMP3AudioHeader();
                tag = mp3.getTag();
            } else { // flac or wav file?
                AudioFile af = AudioFileIO.read(file);
                audioHeader = af.getAudioHeader();
                tag = af.getTag();
            }

            headerAndTagInfo.length = audioHeader.getTrackLength();
            headerAndTagInfo.bitrate = (int) audioHeader.getBitRateAsNumber() * 1000;
            headerAndTagInfo.title = tag.getFirst(FieldKey.TITLE);
            headerAndTagInfo.artist = tag.getFirst(FieldKey.ARTIST);
            headerAndTagInfo.genre = tag.getFirst(FieldKey.GENRE);
            headerAndTagInfo.album = tag.getFirst(FieldKey.ALBUM);
            headerAndTagInfo.year = tag.getFirst(FieldKey.YEAR);
        } catch (NullPointerException | IOException | CannotReadException | InvalidAudioFrameException
                | ReadOnlyFileException | TagException ex) {
            ex.printStackTrace();
            return null;
        }

        return headerAndTagInfo;
    }

    /**
     * Method to get the mp3 or flac files in a directory
     * 
     * @param directory
     * @param storeParentDirectory
     */
    public final void loadAudioFiles(String directory, boolean storeParentDirectory) {
        try {
            File[] files = getAudioFiles(directory);

            // add the files in the root directory
            for (File file : files) {
                addAudioFileToDatabase(file, storeParentDirectory, true);
            }

            // save the database as a tab delimited text file
            saveAudioInfoDB();

            // see if to set this the audio directory as the default
            if (files.length > 0 && !properties.containsKey("audio.directory")) {
                setDefaultAudioDirectory(directory);
                saveProperties();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to load the mp3 and flac files using information stored in the
     * index. This is used on startup to more quickly load the files in the GUI
     * 
     * @param directory The default directory
     */
    public final void loadAudioFilesFromIndex(String directory) {
        try {
            File[] files = getAudioFiles(directory);

            System.out.println("Loading " + files.length + " audio files using saved index info ...\n");

            // add the files in the root directory
            for (File file : files) {
                String filename = file.getName();
                String sha10hex = CassetteFlowUtil.get10CharacterHash(filename);

                AudioInfo audioInfo = audioInfoDB.get(sha10hex);
                if (audioInfo != null) {
                    audioInfoList.add(audioInfo);
                } else {
                    System.out.println("Index out of date ... Missing entry for " + filename);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Scan directory for supported files i.e. mp3 and flac
     * 
     * @param directory
     * @return
     */
    public File[] getAudioFiles(String directory) {
        File dir = new File(directory);

        FilenameFilter filter = (File f, String name) -> {
            name = name.toLowerCase();
            return name.endsWith(".mp3") || name.endsWith(".flac");
        };

        // Note that this time we are using a File class as an array,
        File[] files = dir.listFiles(filter);

        return files;
    }

    /**
     * Loads all audio files in the specified directory and subdirectories.
     * This is just a convenient way to not to have to manually load a
     * directory containing the correct audio files when using the decoding
     * functionality and speed up the startup time of the gui
     * 
     * @param directory
     */
    public final void buildAudioFileIndex(String directory) {
        try {
            System.out.println("Building Audio File Index Starting @ " + directory);
            startTime = System.currentTimeMillis();

            // reset the audio file index
            audioInfoDB = new HashMap<>();

            Path rootPath = Paths.get(directory);
            List<Path> audioFiles = CassetteFlowUtil.findAllAudioFiles(rootPath);
            Collections.sort(audioFiles); // Ensure deterministic order

            // First pass: Detect duplicate filenames
            HashMap<String, Integer> filenameCounts = new HashMap<>();
            for (Path path : audioFiles) {
                String filename = path.getFileName().toString();
                filenameCounts.put(filename, filenameCounts.getOrDefault(filename, 0) + 1);
            }

            for (Path path : audioFiles) {
                File file = path.toFile();
                String filename = file.getName();

                // Get valid relative path (using forward slashes)
                String relativePath = rootPath.relativize(path).toString().replace("\\", "/");

                // Calculate new hash based on relative path
                String newHash = CassetteFlowUtil.get10CharacterHash(relativePath);

                // Calculate legacy hash (filename based)
                String legacyHash = CassetteFlowUtil.get10CharacterHash(filename);

                // Determine SD Card Filename
                String sdCardFilename = filename;
                if (filenameCounts.get(filename) > 1) {
                    // Collision detected! Rename strategy: filename_HASH.ext
                    String basename = filename;
                    String ext = "";
                    int dotIndex = filename.lastIndexOf(".");
                    if (dotIndex > 0) {
                        basename = filename.substring(0, dotIndex);
                        ext = filename.substring(dotIndex);
                    }
                    sdCardFilename = basename + "_" + newHash + ext;
                }

                // Add to DB using the NEW hash
                addAudioFileToDatabase(file, newHash, relativePath, sdCardFilename, false, false);

                // Also map the LEGACY hash to this AudioInfo object (aliasing) to support old
                // tapes
                if (!newHash.equals(legacyHash)) {
                    // Only map if not already present (Priority: Primary Hash > First Alias)
                    if (!audioInfoDB.containsKey(legacyHash)) {
                        AudioInfo info = audioInfoDB.get(newHash);
                        if (info != null) {
                            audioInfoDB.put(legacyHash, info);
                        }
                    }
                }

                if (cassetteFlowFrame != null) {
                    cassetteFlowFrame.printToConsole(path.toString(), true);
                }
            }

            String message;

            // save the audiodb db as a binary file
            FileOutputStream fos = new FileOutputStream(new File(AUDIO_INDEX_FILENAME));
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(audioInfoDB);

            oos.close();
            fos.close();

            long elapsedTime = System.currentTimeMillis() - startTime;
            message = "\n" + audioFiles.size() + "/" + audioInfoDB.size() + " Audio Files Indexed (" + elapsedTime
                    + " milliseconds)...";
            System.out.println(message);

            // also save it as a text file mainly for use by outside programs
            saveAudioInfoDB();

            if (cassetteFlowFrame != null) {
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

        if (file.canRead()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fis);
                audioInfoDB = (HashMap<String, AudioInfo>) in.readObject();
                in.close();
                fis.close();
                indexLoaded = true;
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Load the stream audio file index if the file exist
     */
    public void loadStreamAudioFileIndex() {
        File file = new File(STREAM_AUDIO_INDEX_FILENAME);

        if (file.canRead() && indexLoaded) {
            try {
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fis);
                streamAudioInfoDB = (HashMap<String, AudioInfo>) in.readObject();

                // merge all the audio info objects in stream audio db into the main audioDB now
                streamAudioInfoDB.forEach((k, v) -> {
                    if (!audioInfoDB.containsKey(k)) {
                        audioInfoDB.put(k, v);
                    }
                });

                in.close();
                fis.close();
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Saves the stream audio info objects to an index so we can load those objects
     * without have to reload then frame the deckcast backend
     */
    void saveStreamAudioDBIndex() {
        try {
            FileOutputStream fos = new FileOutputStream(new File(STREAM_AUDIO_INDEX_FILENAME));
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(streamAudioInfoDB);

            oos.close();
            fos.close();
        } catch (Exception ex) {
            Logger.getLogger(CassetteFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Add the MP3 or flac files to the database
     * 
     * @param file
     * @param storeParentDirectory
     * @param addToList            Weather to add to the jlist for main GUI display
     */
    public void addAudioFileToDatabase(File file, boolean storeParentDirectory, boolean addToList) {
        String filename = file.getName();
        String sha10hex = CassetteFlowUtil.get10CharacterHash(filename);
        // For legacy calls, relativePath is just the filename (or null?)
        // and sdCardFilename is the filename. We can pass filename as relativePath for
        // now or handle it inside.
        addAudioFileToDatabase(file, sha10hex, filename, filename, storeParentDirectory, addToList);
    }

    /**
     * Add the MP3 or flac files to the database with explicit hash and paths
     * 
     * @param file
     * @param sha10hex
     * @param relativePath
     * @param sdCardFilename
     * @param storeParentDirectory
     * @param addToList            Weather to add to the jlist for main GUI display
     */
    public void addAudioFileToDatabase(File file, String sha10hex, String relativePath, String sdCardFilename,
            boolean storeParentDirectory, boolean addToList) {
        HeaderAndTagInfo headerAndTagInfo = getHeaderAndTagInfo(file);
        if (headerAndTagInfo == null) {
            System.out.println("No header information for: " + file.getName());
            return;
        }

        int length = headerAndTagInfo.length;
        int bitrate = headerAndTagInfo.bitrate;

        String lengthAsTime = CassetteFlowUtil.getTimeString(length);

        AudioInfo audioInfo = new AudioInfo(file, sha10hex, length, lengthAsTime, bitrate);
        audioInfo.setTitle(headerAndTagInfo.title);
        audioInfo.setArtist(headerAndTagInfo.artist);
        audioInfo.setAlbum(headerAndTagInfo.album);
        audioInfo.setGenre(headerAndTagInfo.genre);
        audioInfo.setYear(headerAndTagInfo.year);
        audioInfo.setRelativePath(relativePath);
        audioInfo.setSdCardFilename(sdCardFilename);

        if (storeParentDirectory) {
            String parentDirecotryName = CassetteFlowUtil.getParentDirectoryName(file);
            audioInfo.setParentDirectoryName(parentDirecotryName);
        }

        if (addToList) {
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
     * 
     * @param url
     */
    public void setDownloadServer(String url) {
        DOWNLOAD_SERVER = url;
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
        STREAM_AUDIO_INDEX_FILENAME = AUDIO_DIR_NAME + File.separator + "streamAudiodb.bin";
        AUDIO_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "audiodb.txt";
        TAPE_DB_FILENAME = AUDIO_DIR_NAME + File.separator + "tapedb.txt";
        TRACK_LIST_FILENAME = AUDIO_DIR_NAME + File.separator + "tracklist.txt";
        LOG_FILE_NAME = AUDIO_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tape.log";
        DCT_INFO_FILENAME = AUDIO_DIR_NAME + File.separator + "dctinfo.bin";
    }

    /**
     * Set the IP address of the lyraT host
     * 
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
        if (cassetteFlowFrame != null) {
            cassetteFlowFrame.printToConsole(message, append);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar CassetteFlow.jar [options]");
        System.out.println("Options:");
        System.out.println("  -h, --help            Show this help message");
        System.out.println("  -cli                  Run in Command Line Interface mode");
        System.out.println("  -d, --device <index>  Select output device by index");
        System.out.println("  -dir <path> ...       Load audio files from specified directory(s).");
        System.out.println("                        Can accept multiple space-separated paths.");
        System.out.println("  -index                Rebuild audio file index");
        System.out.println("  fsm                   Run in Full Screen Mode (GUI)");
    }

    private static int selectOutputDeviceInteractive() {
        HashMap<String, Mixer.Info> devices = WavPlayer.getOutputDevices();
        ArrayList<String> deviceNames = new ArrayList<>(devices.keySet());

        if (devices.isEmpty()) {
            System.out.println("No audio output devices found.");
            return 0; // Fallback to 0
        }

        System.out.println("\nAvailable Audio Output Devices:");
        for (int i = 0; i < deviceNames.size(); i++) {
            System.out.println("[" + i + "] " + deviceNames.get(i));
        }

        System.out.print("\nSelect device index: ");

        // Use Scanner to get user input from console
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            if (scanner.hasNextInt()) {
                int index = scanner.nextInt();
                if (index >= 0 && index < deviceNames.size()) {
                    return index;
                } else {
                    System.out.println("Invalid index. Defaulting to 0.");
                }
            } else {
                System.out.println("Invalid input. Defaulting to 0.");
            }
        } catch (Exception e) {
            System.out.println("Error reading input. Defaulting to 0.");
        }

        return 0;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CassetteFlow cassetteFlow = new CassetteFlow();

        boolean cliMode = false;
        boolean indexMode = false;
        boolean fsm = false;
        int defaultOutputDeviceIndex = -1; // -1 indicates not set
        List<String> audioDirs = new ArrayList<>();

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "--help":
                    printHelp();
                    return;
                case "-cli":
                case "cli":
                    cliMode = true;
                    break;
                case "-d":
                case "--device":
                    if (i + 1 < args.length) {
                        try {
                            defaultOutputDeviceIndex = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid device index: " + args[i]);
                            return;
                        }
                    } else {
                        System.err.println("Device index missing");
                        return;
                    }
                    break;
                case "-dir":
                    while (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        audioDirs.add(args[++i]);
                    }
                    if (audioDirs.isEmpty()) {
                        System.err.println("Directory path(s) missing for -dir");
                        return;
                    }
                    break;
                case "-index":
                case "index":
                    indexMode = true;
                    break;
                case "fsm":
                    fsm = true;
                    break;
                default:
                    // Backward compatibility
                    try {
                        defaultOutputDeviceIndex = Integer.parseInt(arg);
                    } catch (NumberFormatException e) {
                        if (new File(arg).isDirectory()) {
                            audioDirs.add(arg);
                        }
                    }
                    break;
            }
        }

        if (indexMode) {
            cassetteFlow.buildAudioFileIndex(AUDIO_DIR_NAME);
            if (!cliMode && !fsm)
                return;
        }

        for (String dirName : audioDirs) {
            File dir = new File(dirName);
            if (dir.isDirectory()) {
                System.out.println("Loading Audio Files From: " + dirName);
                cassetteFlow.loadAudioFiles(dirName, true);
                System.out.println("Done with " + dirName);
            } else {
                System.out.println("Invalid Directory: " + dirName);
            }
        }

        if (DEBUG || cliMode) {
            System.out.println("CassetteFlow CLI v2.0.14 (01/01/2026)\n");

            try {
                // load any saved DCT info records
                cassetteFlow.loadDCTInfo();

                // Interactive Device Selection if not specified
                if (defaultOutputDeviceIndex == -1) {
                    defaultOutputDeviceIndex = selectOutputDeviceInteractive();
                }

                // set the default output device
                System.out.println("\nDefault Output Device Index: " + defaultOutputDeviceIndex);
                String defaultOutputDevice = WavPlayer.getOutputDevice(defaultOutputDeviceIndex);
                System.out.println("Default Output Device Name: " + defaultOutputDevice + "\n");

                // start the mp3/flac player
                CassettePlayer cassettePlayer = new CassettePlayer(cassetteFlow, LOG_FILE_NAME);
                cassettePlayer.setMixerName(defaultOutputDevice);
                cassettePlayer.startMinimodem(0);

                // start the cassette flow server
                CassetteFlowServer cassetteFlowServer = new CassetteFlowServer();
                cassetteFlowServer.setCassetteFlow(cassetteFlow);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            // set the look and feel
            FlatDarculaLaf.setup();

            final boolean finalFsm = fsm;

            // ** Start main application UI here
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    cassetteFlowFrame = new CassetteFlowFrame();
                    cassetteFlowFrame.setCassetteFlow(cassetteFlow);

                    if (finalFsm) {
                        cassetteFlowFrame.initFullScreen();
                    } else {
                        cassetteFlowFrame.pack();
                    }

                    cassetteFlowFrame.setVisible(true);

                    long elapsedTime = System.currentTimeMillis() - startTime;
                    System.out.println("\nTotal time to start program: " + elapsedTime + " Milliseconds\n");
                }
            });
        }
    }
}
