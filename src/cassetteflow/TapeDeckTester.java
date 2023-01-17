package cassetteflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class read processes data on cassette/reel to reel tape to get accurate error counts
 * It's makes use of the minimodem program to read data from the tape decks
 * 
 * @author Nathan
 */
public class TapeDeckTester {        
    // keep track of the total log lines which have been process so far
    private int logLineCount = 0;
    
    // keep track off the number of stop and mute records
    private int stopRecords = 0;
    
    // keep track of the number of read errors after a complete line record 
    // has been read in.
    private int dataErrors = 0;
    
    // keep track of data length errors. Those usually occurs at the start and stop of the tape
    private int dataLengthErrors = 0;
    
    // variables used for calling minimodem
    private final String COMMAND_MINIMODEM = "minimodem -r 1200";
    private final String COMMAND_PULSEAUDIO = "pulseaudio";
    private Process process;
    private BufferedReader miniModemReader;
    private BufferedReader miniModemErrReader;
    
    // used to indicate if the minimodem program is running
    private boolean decoding;
    
    // the current line record
    private String currentLineRecord;
    
    // private current side
    private char currentSide = 'A'; // side A or B
    private int sideAErrors = 0;
    private int sideALineCount = 0;
    private int sideBErrors = 0;
    private int sideBLineCount = 0;
    
    private static final DecimalFormat df = new DecimalFormat("0.0000");
   
    /**
     * Default constructor which just adds shutdown hook to terminate the minimodem process
     */
    public TapeDeckTester() {
        System.out.println("Tape Deck Tester Version 1.1.2");
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
     * @throws IOException 
     */
    public void startMinimodem() throws IOException {
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
        
        decoding = true;
        
        // start thread to read from tape
        Thread soutThread = new Thread("Standard Output Reader") {
            @Override
            public void run() {
                miniModemReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                try {
                    while (true) {
                        line = miniModemReader.readLine();

                        if (line != null) { //check for pause again
                            newLogFileLine(line);
                        }
                        
                        if(!decoding) {
                            break;
                        }
                    }
                    
                    miniModemReader.close();
                } catch (IOException ex) {
                    Logger.getLogger(TapeDeckTester.class.getName()).log(Level.SEVERE, null, ex);
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
                        line = miniModemErrReader.readLine();
                        
                        if (line != null) {
                            newLogFileLine(line);
                        }
                        
                        if(!decoding) {
                            break;
                        }
                    }
                    
                    miniModemErrReader.close();
                } catch (IOException ex) {
                    Logger.getLogger(TapeDeckTester.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        serrThread.start();
    }
    
    /**
     * Process a line from running the minimodem program
     * @param line 
     */
    public synchronized void newLogFileLine(String line) {
        if(line != null) {
            line = line.trim();
            
            if(line.length() == 29) {
                logLineCount++;
                updateSideLineCount();
                
                // check to see if all characters are valid
                if(!validCharacters(line)) return;
                
                processRecord(line);
                stopRecords = 0;
                
                if(logLineCount %10 == 0) {
                    String message = "CURRENT PROGRESS (errors/line records): " + dataErrors +  "/" + logLineCount + 
                            " { " + getPercentError() + "% }\n" +
                            "SIDE A ERRORS: " + sideAErrors + "/"  + sideALineCount + "\t{ " + getSidePercentError('A') + "% }\n" +
                            "SIDE B ERRORS: " + sideBErrors + "/"  + sideBLineCount + "\t{ " + getSidePercentError('B') + "% }\n";
                    
                    System.out.println(message);
                }
            } else if(line.contains("### NOCARRIER")) {
                if(stopRecords == 0) {
                    String stopMessage = "PLAYBACK STOPPED (errors/line records): " + dataErrors +  "/" + logLineCount + 
                        " { " + getPercentError() + "%}\n" + 
                        "SIDE A ERRORS: " + sideAErrors + "/"  + sideALineCount + "\t{ " + getSidePercentError('A') + "% }\n" +
                        "SIDE B ERRORS: " + sideBErrors + "/"  + sideBLineCount + "\t{ " + getSidePercentError('B') + "% }\n" +
                        "DATA LENGTH ERRORS: " + dataLengthErrors  + "\n";
                    System.out.println("\n");
                    System.out.println(stopMessage);
                    System.out.println(line + "\n");
                }
                
                stopRecords++;
            } else {
                // count errors when line is not long enough but should contain data
                if(!line.contains("###") && line.contains("_")) {
                    dataLengthErrors++;
                    System.out.println("\nInvalid Data Length @: " + line + " (# Data Length Errors: " + dataLengthErrors +  ")\n");
                }
            }
        }
    }
    
    /**
     * Get the percent error
     * @return 
     */
    public String getPercentError() {
        double percent = 0.0;
        
        if(logLineCount != 0) 
            percent = ((double)dataErrors / (double)logLineCount)*100.0;
        
        return df.format(percent);
    }
    
    /**
     * Update the log line count for the side
     */
    public void updateSideLineCount() {
        if(currentSide == 'A') {
            sideALineCount++;
        } else {
            sideBLineCount++;
        }
    }
    
    /**
     * Update the errors for the side
     */
    public void updateSideErrors() {
        if(currentSide == 'A') {
            sideAErrors++;
        } else {
            sideBErrors++;
        }
    }
    
    /**
     * Get the percent error for side A or B
     * @param side
     * @return 
     */
    public String getSidePercentError(char side) {
        double percent = 0.0;
        int errors;
        int lineCount;
        
        if(side == 'A') {
            errors = sideAErrors;
            lineCount = sideALineCount;
        } else {
            errors = sideBErrors;
            lineCount = sideBLineCount;
        }
        
        if(lineCount != 0) 
            percent = ((double)errors / (double)lineCount)*100.0;
        
        return df.format(percent);
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
                printError("\nInvalid Character @: " + input + " | " + input.charAt(i) + "\n");
                break;
            }
        }
        
        return result;
    }
    
    /**
     * Process a record from the minimodem to check to see if we getting good data
     * @param line 
     * @return
     */
    public String processRecord(String line) {        
        String[] sa = line.split("_");
        
        if(sa.length != 5) {
            return printError("\nInvalid Data Length @: " + line + "\n");
        }
        
        String tapeId = sa[0];
        String trackS = sa[1];
        String audioId = sa[2];
        String playTimeS = sa[3];
        String totalTimeS = sa[4];
        
        try {
            Integer.valueOf(trackS);
            Integer.valueOf(playTimeS);
            Integer.valueOf(totalTimeS);
        } catch(NumberFormatException nfe) {
            return printError("\nInvalid Integer @: " + line + "\n");
        }
        
        // check to see what tape ID is DCT_0
        if(!tapeId.equals("DCT0A") && !tapeId.equals("DCT0B")) {
            return printError("\nInvalid Tape ID @: " + line + "\n");
        } else {
            if(tapeId.contains("0A")) {
                currentSide = 'A';
            } else {
                currentSide = 'B';
            }
        }
        
        if(!audioId.equals("aaaaaaaaaa")) {
            return printError("\nInvalid Audio File ID @: " + line + "\n");
        }
        
        return line;
    }
    
    /**
     * Print any error messages out
     * @param error
     * @return String indicated an error took place
     */
    private String printError(String error) {
        System.out.println(error);
        dataErrors++;
        updateSideErrors();
        
        return "DATA ERROR";
    }
    
    /**
     * Method to get the time in seconds as a string
     * 
     * @param totalSecs
     * @return 
     */
    private String getTimeString(int totalSecs) {
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
     
    // stop reading from minimodem and close it 
    public void stop() {
        decoding = false;

        // stop the minimodem program
        if (process != null) {
            process.destroy();
        }
    }
    
    /**
     * Main method for running the test.
     * @param args 
     */
    public static void main(String[] args) {
        TapeDeckTester tapeDeckDataTester = new TapeDeckTester();
        try {
            tapeDeckDataTester.startMinimodem();
            
            // start the scanner to close the program
            Scanner sc = new Scanner(System.in);
            String input = null;

            do {
                System.out.println("Type X and hit Enter to exit program");
                input = sc.next();
            } while (!input.equalsIgnoreCase("x"));
            
            sc.close();
            tapeDeckDataTester.stop();
        } catch (IOException ex) {
            Logger.getLogger(TapeDeckTester.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
