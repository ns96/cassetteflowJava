 package cassetteflow;

import com.formdev.flatlaf.FlatDarculaLaf;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.SwingUtilities;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;

/**
 * A simple program for creating input files for the cassette flow encoding
 * program/method. It make use of the following libraries for dealing with 
 * MP3 playback and meta data extraction
 * 
 * Playing of MP3s: https://github.com/radinshayanfar/MyJlayer
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
    public static String MP3_DIR_NAME = "c:\\mp3files";
    
    // store the mp3 id hashmap to a tab delimitted file
    public static String MP3_DB_FILENAME = MP3_DIR_NAME + File.separator + "mp3InfoDB.txt";
    
    // default directory where the text files to be encoded
    public static final String TAPE_FILE_DIR_NAME = "TapeFiles";
    
    public static String LOG_FILE_NAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tape.log"; 
    
    // stores the mp3info object keyed by the 10 character hash
    public HashMap<String, MP3Info> mp3InfoDB = new HashMap<>();
    
    // also store the MP3Info object is a list for convinience
    public ArrayList<MP3Info> mp3InfoList = new ArrayList<>();
    
    // stores the cassette ID to the mp3ids
    public HashMap<String, ArrayList<String>> tapeDB = new HashMap<>();
    
    public static String TAPE_DB_FILENAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tapeDB.txt";   
    
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
    
    private String propertiesFilename = "cassetteFlow.properties";
    
    // used to stop realtime encoding
    private boolean stopEncoding = false;
    
    // the number of times to replicate a data line in the input files
    private int replicate = 4;
    
    // used when doing realtime encoding to keep track total track time
    private int timeTotal = 0;
    
    // The wav file player
    private WavPlayer wavPlayer;
    
    // debug flag
    private static final boolean DEBUG = false;
    
    /**
     * Default constructor that just loads the mp3 files and cassette map database
     */
    public CassetteFlow() {
        loadProperties();
        
        loadMP3Files(MP3_DIR_NAME);
        
        File file = new File(TAPE_DB_FILENAME);
        loadTapeDB(file);
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
            
            setDefaultMP3Directory(properties.getProperty("mp3.directory"));
        } catch (IOException e) {
            String mp3Directory = System.getProperty("user.home");
            setDefaultMP3Directory(mp3Directory);
        }
    }
    
    /**
     * Save the default properties file
     */
    public void saveProperties() {
        try (FileWriter output = new FileWriter(propertiesFilename)) {
            properties.put("mp3.directory", MP3_DIR_NAME);
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
     * Save the MP3 map as a tab delimited file. Not currently used other than
     * to provide examples of how this should look
     */
    private void saveMP3InfoDB() {
        try {
            FileWriter writer = new FileWriter(MP3_DB_FILENAME);
            
            for(String key: mp3InfoDB.keySet()) {
                MP3Info mp3Info = mp3InfoDB.get(key);
                String line = key + "\t" + mp3Info.getLength() + "\t" + mp3Info.getFile().getName() + "\n";
                writer.write(line);
            }
            
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Get the mp3 info database has a string. Used for testing the cassette flow server
     * @return 
     */
    public String getMP3InfoDBAsString() {
        StringBuilder sb = new StringBuilder();
        
        for (String key : mp3InfoDB.keySet()) {
            MP3Info mp3Info = mp3InfoDB.get(key);
            String line = key + "\t" + mp3Info.getLength() + "\t" + mp3Info.getFile().getName() + "\n";
            sb.append(line);
        }
        
        return sb.toString();
    }
    
    /**
     * Method to create the mp3 db as a String
     * 
     * @param data 
     */
    public void createMP3InfoDBFromString(String data) throws Exception {
        HashMap<String, MP3Info> remoteDB = new HashMap<>();
        ArrayList<MP3Info> remoteList = new ArrayList<>();

        for (String line : data.split("\n")) {
            String[] sa = line.split("\t");

            String id = sa[0];
            int playtime = Integer.parseInt(sa[1]);
            String playtimeString = CassetteFlowUtil.getTimeString(playtime);
            File file = new File(sa[2]);

            MP3Info mp3Info = new MP3Info(file, id, playtime, playtimeString);
            remoteDB.put(id, mp3Info);
            remoteList.add(mp3Info);
        }

        // update the objects in the cassett flow frame
        mp3InfoDB = remoteDB;
        mp3InfoList = remoteList;
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
    private void loadTapeDB(File file) {
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
                    
                    ArrayList<String> mp3Ids = new ArrayList<>();
                    for(int i = 1; i < sa.length; i++) {
                        mp3Ids.add(sa[i]);
                    }
                    
                    tapeDB.put(key, mp3Ids);
                }
                reader.close();
                
                
                System.out.println("\nCassette database file loaded ... ");
                
                for(String key: tapeDB.keySet()) {
                    ArrayList<String> mp3Ids = tapeDB.get(key);
                    System.out.println(key + " >> " + mp3Ids);
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Method to create the tape db as a String
     * 
     * @param data 
     */
    public void createTapeDBFromString(String data) throws Exception {
        HashMap<String, ArrayList<String>> remoteDB = new HashMap<>();
        
        for (String line : data.split("\n")) {
            String[] sa = line.split("\t");
            String key = sa[0];
            
            ArrayList<String> mp3Ids = new ArrayList<>();
            for (int i = 1; i < sa.length; i++) {
                mp3Ids.add(sa[i]);
            }

            remoteDB.put(key, mp3Ids);
        }
        
        tapeDB = remoteDB;
    }
    
    /**
     * Add an entry to the tape database
     * 
     * @param tapeID
     * @param sideAList
     * @param sideBList
     * @param save 
     */
    public void addToTapeDB(String tapeID, ArrayList<MP3Info> sideAList, ArrayList<MP3Info> sideBList, boolean save) {
        // save information for side A
        ArrayList<String> mp3Ids = new ArrayList<>();
        
        if(sideAList != null) {
            // store track for side A
            for(MP3Info mp3Info: sideAList) {
                mp3Ids.add(mp3Info.getHash10C());
            }
        
            tapeDB.put(tapeID + "A", mp3Ids);
        }
        
        if(sideBList != null) {
            mp3Ids = new ArrayList<>();
            
            // store track for side B
            for(MP3Info mp3Info: sideBList) {
                mp3Ids.add(mp3Info.getHash10C());
            }
        
            tapeDB.put(tapeID + "B", mp3Ids);
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
     * @param forDownload specifies if this is for mp3 which are to be downloaded
     * 
     * @return indicates if the input file(s) were successfully created
     */
    public boolean createInputFiles(String directoryName, String tapeID, ArrayList<MP3Info> sideA, ArrayList<MP3Info> sideB, int muteTime, boolean forDownload) {
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
     * Create the input files for the mp3 tracks
     * 
     * @param inputFile
     * @param tapeID
     * @param sideN
     * @param muteTime
     * @param forDownload
     * @return
     * @throws IOException 
     */
    public String createInputFileForSide(File inputFile, String tapeID, ArrayList<MP3Info> sideN, int muteTime, boolean forDownload) throws IOException {
        System.out.println("Creating Cassette Tape Input: " + inputFile + ", " + tapeID + ", " +  muteTime);
        System.out.println(sideN);
        
        FileWriter myWriter = new FileWriter(inputFile);
        StringBuilder builder = new StringBuilder();
        
        // used for creating an input file which will download mp3s from the server
        String tapeHashCode;
        FileWriter myWriter2 = null;

        timeTotal = 0;
        int mp3Count = 0;
        
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
        
        for(MP3Info mp3Info: sideN) {
            String trackS = String.format("%02d", mp3Count+1);
            String mp3Id = tapeID + "_" + trackS + "_" + mp3Info.getHash10C();
            
            // add this entry to the download file
            if(forDownload) {
                String line = mp3Info.getHash10C() + "\t" + mp3Info.getFile().getName() + "\n";
                myWriter2.write(line);
            }
            
            // add line records to create a N second muted section before next song
            if(mp3Count >= 1) {
                for(int i = 0; i < muteTime; i++) {
                    timeTotal += 1;
                    String timeTotalString = String.format("%04d", timeTotal);
                    String line = mp3Id + "_000M_" + timeTotalString + "\n";
                    
                    for(int j = 0; j < replicate; j++) { // replicate record N times
                        myWriter.write(line);
                        builder.append(line);
                    }
                }
            }
        
            for(int i = 0; i < mp3Info.getLength(); i++) {
                String timeString = String.format("%04d", i);
                String timeTotalString = String.format("%04d", timeTotal);
                String line = mp3Id + "_" + timeString + "_" + timeTotalString + "\n";
            
                for(int j = 0; j < replicate; j++) { // replicate record N times
                    myWriter.write(line);
                    builder.append(line);
                }
                
                timeTotal += 1;
            }
    
            mp3Count += 1;
        }
    
        // close the file writter
        myWriter.close();
        
        if(myWriter2 != null) {
            myWriter2.close();
        }
        
        return builder.toString();
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
    public File[] directEncode(String saveDirectoryName, String tapeID, ArrayList<MP3Info> sideA, 
            ArrayList<MP3Info> sideB, int muteTime, boolean forDownload) throws IOException {
        
        // list to stored array list
        File[] wavFiles = new File[2];
        wavFiles[0] = null;
        wavFiles[1] = null;
        
        File file;
        File wavFile;
        String data;
        
        if(sideA != null && !sideA.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A" + ".txt");
            wavFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A-" + BAUDE_RATE + ".wav");
            data = createInputFileForSide(file, tapeID + "A", sideA, muteTime, forDownload);
            runMinimodem(wavFile, data);
            wavFiles[0] = wavFile;
        }
        
        if(sideB != null && !sideB.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B" + ".txt");
            wavFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B-" + BAUDE_RATE + ".wav");
            data = createInputFileForSide(file, tapeID + "B", sideB, muteTime, forDownload);
            runMinimodem(wavFile, data);
            wavFiles[1] = wavFile;
        }
        
        // save to the tape data base
        addToTapeDB(tapeID, sideA, sideB, true);
        
        cassetteFlowFrame.setEncodingDone();
        
        return wavFiles;
    }
    
    /**
     * Run minimodem to encode data
     * 
     * @param waveFile
     * @param data
     * @throws IOException 
     */
    private void runMinimodem(File waveFile, String data) throws IOException {
        // call minimodem to do encoding
        String command = "minimodem --tx " + BAUDE_RATE + " -f " + waveFile.toString().replace("\\", "/");
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
     */
    public boolean realTimeEncode(String tapeID, ArrayList<MP3Info> sideN, int muteTime, 
            boolean forDownload, String saveDirectoryName, Mixer.Info soundOutput) throws IOException, InterruptedException {
        
        boolean completed = true;
        stopEncoding = false;
        timeTotal = 0;
        
        int mp3Count = 1;
        
        String message;
        
        wavPlayer = new WavPlayer();
        
        for(MP3Info mp3Info: sideN) {
            long startTime = System.currentTimeMillis();
            
            String data = createInputDataForMP3(tapeID, mp3Info, mp3Count);
            
            message = "Minimodem Encoding: " + tapeID + " Track [ " + mp3Count + " ] ( " + mp3Info.getLengthAsTime() + " )";
            cassetteFlowFrame.printToConsole(message, false);
            System.out.println("\n" + message);
            
            String filename = saveDirectoryName + File.separator + "track_" + mp3Count + "-" + BAUDE_RATE + ".wav";
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
            cassetteFlowFrame.printToConsole(message, true);
            System.out.println(message);
            
            message = "\nDone Encoding Track [ " + mp3Count + " ] Total Tape Time: " + timeTotal + " seconds";
            cassetteFlowFrame.printToConsole(message, true);
            System.out.println(message); 
            
            // playback the wav file and wait for it to be done
            message = "\nPlaying Wav File: " + filename;
            cassetteFlowFrame.printToConsole(message, true);
            System.out.println(message);
            
            try {
                File wavFile = new File(filename);
                wavPlayer.playBigWav(wavFile, soundOutput);
            } catch(IOException | LineUnavailableException | UnsupportedAudioFileException e) {
                message = "Error Playing Wav: " + filename + "\n" + e.getMessage();
                cassetteFlowFrame.printToConsole(message, true);
                System.out.println(message);
                e.printStackTrace();
                completed = false;
                break;
            }
            
            // TO-DO -- Delete the Wav file here
            
            if(stopEncoding) {
                message = "\nReal Time Encoding Stopped ...";
                cassetteFlowFrame.printToConsole(message, true);
                System.out.println(message);
                completed = false;
                break;
            }
            
            // increment mp3Count
            mp3Count++;
            
            // sleep for the desired mute time to allow for blank on the tape
            // a blank on the tape allows for track skipping on decks that
            // supported it
            message = "\nMute For " + muteTime + " seconds ...";
            cassetteFlowFrame.printToConsole(message, true);
            System.out.println(message);
            
            int delay = muteTime*1000 - (int)encodeTime;
            
            if(delay > 0) {
                timeTotal += muteTime;
                Thread.sleep(delay);
            } else {
                timeTotal += encodeTime;
            }
        }
        
        // indicate that the encoding is done
        message = "\nEncoding of  " + (mp3Count - 1) + " Tracks Done ...";
        cassetteFlowFrame.printToConsole(message, true);
        System.out.println(message);
        
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
        cassetteFlowFrame.printToConsole(message, false);
        System.out.println("\n" + message);
        
        wavPlayer = new WavPlayer();
        try {
            wavPlayer.playBigWav(wavFile, soundOutput);
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException e) {
            message = "Error Playing Wav: " + wavFile.getName() + "\n" + e.getMessage();
            cassetteFlowFrame.printToConsole(message, true);
            System.out.println(message);
            
            e.printStackTrace();
            completed = false;
        }

        if (stopEncoding) {
            message = "\nPlaying of Encoded Wav Stopped ...";
            cassetteFlowFrame.printToConsole(message, true);
            System.out.println(message);
            completed = false;
        }

        // indicate that the encoding is done
        message = "\nPlaying of Encoded of Wav Done ...";
        cassetteFlowFrame.printToConsole(message, true);
        System.out.println(message);
        
        cassetteFlowFrame.setEncodingDone();
        
        return completed;
    }
    
    /**
     * Create the input data for an mp3 track
     * @param tapeID
     * @param mp3Info
     * @param mp3Count
     * @return
     */
    public String createInputDataForMP3(String tapeID, MP3Info mp3Info, int mp3Count) {
        StringBuilder builder = new StringBuilder();

        String trackString = String.format("%02d", mp3Count);
        String mp3Id = tapeID + "_" + trackString + "_" + mp3Info.getHash10C();
        
        // add a 1 second mute record to allow loading of mp3 correctly?
        for (int i = 0; i < 1; i++) {
            timeTotal += 1;
            String timeTotalString = String.format("%04d", timeTotal);
            String line = mp3Id + "_000M_" + timeTotalString + "\n";

            for (int j = 0; j < replicate; j++) { // replicate record N times
                builder.append(line);
            }
        }
        
        // add line records for each second of sound
        for (int i = 0; i < mp3Info.getLength(); i++) {
            String timeString = String.format("%04d", i);
            String timeTotalString = String.format("%04d", timeTotal);
            String line = mp3Id + "_" + timeString + "_" + timeTotalString + "\n";

            for (int j = 0; j < replicate; j++) { // replicate record N times
                builder.append(line);
            }

            timeTotal += 1;
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
    
    public ArrayList<MP3Info> getRandomMP3List(int maxTime, int muteTime) {
        // get a shuffle list of mp3s
        ArrayList<MP3Info> shuffledMp3s = shuffleMP3List();
        ArrayList<MP3Info> tapeList = new ArrayList<>();
        
        System.out.println("\nGenerating Ramdom List Of MP3s: (" + maxTime + "s / " + muteTime + "s)");
        
        int currentTime = 0;
        int totalTime = 0;
        
        for(int i = 0; i < shuffledMp3s.size(); i++) {
            MP3Info mp3Info = shuffledMp3s.get(i);
            currentTime += mp3Info.getLength();
            
            int timeWithMute = currentTime + muteTime*i;
            if(timeWithMute <= maxTime) {
                tapeList.add(mp3Info);
                totalTime = timeWithMute;
                String trackCount = String.format("%02d", (i + 1));
                String trackName = "[" + trackCount + "] " + mp3Info;
                System.out.println(trackName);
            } else {
                break;
            }
        }
        
        System.out.println("\n" + tapeList.size() + " Tracks -- Play Time: " + CassetteFlowUtil.getTimeString(totalTime));
        
        return tapeList;
    }
    
    /**
     * Copy and shuffle the mp3sList Array
     * @return shuffle list containing the mp3 objects
     */
    public ArrayList<MP3Info> shuffleMP3List() {
         ArrayList<MP3Info> mp3sListCopy = (ArrayList<MP3Info>) mp3InfoList.clone();
         Collections.shuffle(mp3sListCopy);
         return mp3sListCopy;
    }
    
    /**
     * Method to return the length in seconds of the mp3
     * 
     * @param file
     * @return 
     */
    public int getMP3Length(File file) {
        try {
            FileInputStream fileIS = new FileInputStream(file);
            Bitstream bitstream = new Bitstream(fileIS);
            Header h = bitstream.readFrame();
            
            long tn = fileIS.getChannel().size();

            return (int)h.total_ms((int) tn)/1000;
        } catch(Exception ex) {
            ex.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Method to get the mp3 files is a directory
     * 
     * @param directory
     * @return 
     */
    public final void loadMP3Files(String directory) {
        // try-catch block to handle exceptions
        try {
            File dir = new File(directory);

            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File f, String name) {
                    // We want to find only .mp3 files                   
                    return name.toLowerCase().endsWith(".mp3");
                }
            };

            // Note that this time we are using a File class as an array,
            File[] files = dir.listFiles(filter);

            // Get the names of the files by using the .getName() method
            for (File file : files) {
                addMP3FileToDatabase(file);
            }
            
            // save the database as a tab delimited text file
            saveMP3InfoDB();
            
            // see if to set this the mp3 directory as the default
            if(files.length > 0 && !properties.containsKey("mp3.directory")) {
                setDefaultMP3Directory(directory);
                saveProperties();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
    
    /**
     * Add the MP3 to the database
     * 
     * @param file 
     */
    public void addMP3FileToDatabase(File file) {
        String filename = file.getName();
        String sha10hex = CassetteFlowUtil.get10CharacterHash(filename);
        int length = getMP3Length(file);
        String lengthAsTime = CassetteFlowUtil.getTimeString(length);

        MP3Info mp3Info = new MP3Info(file, sha10hex, length, lengthAsTime);
        mp3InfoList.add(mp3Info);
        mp3InfoDB.put(sha10hex, mp3Info);

        System.out.println(sha10hex + " -- " + file.getName() + " : " + length);
    }
        
    /**
     * Method to set the download server where the mp3 for download are stored
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
     * @param mp3Directory
     */
    public void setDefaultMP3Directory(String mp3Directory) {
        MP3_DIR_NAME = mp3Directory;
        MP3_DB_FILENAME = MP3_DIR_NAME + File.separator + "mp3InfoDB.txt";
        TAPE_DB_FILENAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tapeDB.txt";
        LOG_FILE_NAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tape.log";
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
        
        if(DEBUG || (args.length > 0 && cla.equals("cli"))) {
            CassettePlayer cassettePlayer = new CassettePlayer(cassetteFlow, LOG_FILE_NAME);
            cassettePlayer.startLogTailer();
            
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
