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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import org.apache.commons.codec.digest.DigestUtils;

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
    public HashMap<String, MP3Info> mp3sMap = new HashMap<>();
    
    // stores the cassette ID to the mp3ids
    public HashMap<String, ArrayList<String>> cassetteDB = new HashMap<>();
    
    private static String CASSETTE_DB_FILENAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "cassetteDB.txt"; 
    
    
    // store tje MP3Info object is a list for convinience
    public ArrayList<MP3Info> mp3sList = new ArrayList<>();
    
    // private 
    
    // the location of mp3 files
    public String downloadServerRoot = "http://192.168.1.14/~pi/mp3/";
    
    // debug flag
    private static final boolean DEBUG = false;
    
    private static CassetteFlowFrame cassetteFlowFrame;
    
    /**
     * Default constructor that just loads the mp3 files and cassette map database
     */
    public CassetteFlow() {
        loadMP3Files(MP3_DIR_NAME);
        
        File file = new File(CASSETTE_DB_FILENAME);
        loadCassetteDB(file);
    }
    
    /**
     * The the MP3 map as a tab delimited file
     */
    private void saveMP3InfoDB() {
        try {
            FileWriter writer = new FileWriter(MP3_DB_FILENAME);
            
            for(String key: mp3sMap.keySet()) {
                MP3Info mp3Info = mp3sMap.get(key);
                String line = key + "\t" + mp3Info.getLength() + "\t" + mp3Info.getFile() + "\n";
                writer.write(line);
            }
            
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Saves the cassette data which stores info on tape IDs and their associated mp3 ids
     * 
     * @param file
     * @throws IOException 
     */
    public void saveCassetteDB(File file) throws IOException {
        try {
            /**ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(cassetteDB);
            oos.close();
            */
            FileWriter writer = new FileWriter(file);
            
            for(String key: cassetteDB.keySet()) {
                String line = key + "\t" + String.join("\t", cassetteDB.get(key)) + "\n";
                writer.write(line);
            }
            
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Saves the cassette data which stores info on tape IDs and their associated mp3 ids
     * 
     * @param file 
     */
    public void loadCassetteDB(File file) {
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
                    
                    cassetteDB.put(key, mp3Ids);
                }
                reader.close();
                
                
                System.out.println("\nCassette database file loaded ... ");
                for(String key: cassetteDB.keySet()) {
                    ArrayList<String> mp3Ids = cassetteDB.get(key);
                    System.out.println(key + " >> " + mp3Ids);
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void addToCassetteDB(String tapeID, ArrayList<MP3Info> sideAList, ArrayList<MP3Info> sideBList) {
        // save information for side A
        ArrayList<String> mp3Ids = new ArrayList<>();
        
        if(sideAList != null) {
            // store track for side A
            for(MP3Info mp3Info: sideAList) {
                mp3Ids.add(mp3Info.getHash10C());
            }
        
            cassetteDB.put(tapeID + "A", mp3Ids);
        }
        
        if(sideBList != null) {
            mp3Ids = new ArrayList<>();
            
            // store track for side B
            for(MP3Info mp3Info: sideBList) {
                mp3Ids.add(mp3Info.getHash10C());
            }
        
            cassetteDB.put(tapeID + "B", mp3Ids);
        }
        
        // save the database file now
        try {
            File file = new File(CASSETTE_DB_FILENAME);
            saveCassetteDB(file);
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
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
            addToCassetteDB(tapeID, sideA, sideB);
            
            return true;
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }
    
    public String createInputFileForSide(File inputFile, String tapeID, ArrayList<MP3Info> sideN, int muteTime, boolean forDownload) throws IOException {
        System.out.println("Creating Cassette Tape Input: " + inputFile + ", " + tapeID + ", " +  muteTime);
        System.out.println(sideN);
        
        FileWriter myWriter = new FileWriter(inputFile);
        StringBuilder builder = new StringBuilder();
        
        // used for creating an input file which will download mp3s from the server
        String tapeHashCode;
        FileWriter myWriter2 = null;
        
        int replicate = 4;
        int timeTotal = 0;
        int mp3Count = 0;
        
        // if this is for a download file, specify that in the first 10 seconds of data
        // when this text is decoded the mp3s will be downloaded
        if(forDownload) {
            tapeHashCode = get10CharacterHash(tapeID + sideN.toString());
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
     */
    public void directEncode(String saveDirectoryName, String tapeID, ArrayList<MP3Info> sideA, 
            ArrayList<MP3Info> sideB, int muteTime, boolean forDownload) throws IOException {
        
        File file;
        File waveFile;
        String data;
        
        if(sideA != null && !sideA.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A" + ".txt");
            waveFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "A-1200" + ".wav");
            data = createInputFileForSide(file, tapeID, sideA, muteTime, forDownload);
            runMinimodem(waveFile, data);
        }
        
        if(sideB != null && !sideB.isEmpty()) {
            file = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B" + ".txt");
            waveFile = new File(saveDirectoryName + File.separator + "Tape_" + tapeID + "B-1200" + ".wav");
            data = createInputFileForSide(file, tapeID, sideB, muteTime, forDownload);
            runMinimodem(waveFile, data);
        }
        
        // save to the tape data base
        addToCassetteDB(tapeID, sideA, sideB);
        
        cassetteFlowFrame.setEncodingDone();
    }
    
    private void runMinimodem(File waveFile, String data) throws IOException {
        // call minimodem to do encoding
        String command = "minimodem --tx 1200 -f " + waveFile.toString().replace("\\", "/");
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
        
        System.out.println("\nDone Encoding Command: " + command);
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
        
        System.out.println("\n" + tapeList.size() + " Tracks -- Play Time: " + getTimeString(totalTime));
        
        return tapeList;
    }
    
    /**
     * Copy and shuffle the mp3sList Array
     * @return shuffle list containing the mp3 objects
     */
    public ArrayList<MP3Info> shuffleMP3List() {
         ArrayList<MP3Info> mp3sListCopy = (ArrayList<MP3Info>) mp3sList.clone();
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
    public void loadMP3Files(String directory) {
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
            
            // save the database as a text delimited text file
            saveMP3InfoDB();
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
        String sha10hex = get10CharacterHash(filename);
        int length = getMP3Length(file);
        String lengthAsTime = getTimeString(length);

        MP3Info mp3Info = new MP3Info(file, sha10hex, length, lengthAsTime);
        mp3sList.add(mp3Info);
        mp3sMap.put(sha10hex, mp3Info);

        System.out.println(sha10hex + " -- " + file.getName() + " : " + length);
    }
    
    public String get10CharacterHash(String string) {
        String sha256hex = DigestUtils.sha256Hex(string);
        return sha256hex.substring(0, 10);
    }
    
    public String getTimeString(int totalSecs) {
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Method to set the download server where the mp3 for download are stored
     * 
     * This is predominantly for testing purposes
     * @param url 
     */
    public void setDownloadServerRoot(String url) {
        this.downloadServerRoot = url;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // check to make sure the mp3 directory exists. Hardcode for now, but will fix later 
        File mp3Dir = new File(MP3_DIR_NAME);
        if(!mp3Dir.exists()) {
            // must be running on the rpi4 reterminal
            MP3_DIR_NAME = "/home/pi/Music";
            MP3_DB_FILENAME = MP3_DIR_NAME + File.separator + "mp3InfoDB.txt";
            CASSETTE_DB_FILENAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "cassetteDB.txt";
            LOG_FILE_NAME = MP3_DIR_NAME + File.separator + TAPE_FILE_DIR_NAME + File.separator + "tape.log"; 
        }
        
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
