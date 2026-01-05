package cassetteflow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * This class reads processes data on cassette/reel to reel tape to get accurate
 * error counts.
 * UPDATED: Uses the pure Java JMinimodem class instead of the external
 * minimodem binary.
 * * @author Nathan
 */
public class TapeDeckTester {
    private static final String VERSION = "Tape Deck Tester Version 1.2.3 (JMinimodem Integrated)";

    // keep track of the total log lines which have been processed so far
    private int logLineCount = 0;

    // keep track of the number of stop and mute records
    private int stopRecords = 0;

    private int totalStops = 0;

    // keep track of the number of read errors after a complete line record
    // has been read in.
    private int dataErrors = 0;

    // keep track of data length errors. Those usually occurs at the start and stop
    // of the tape
    private int dataLengthErrors = 0;

    // keep track of errors converting string to numbers
    private int numberConversionErrors = 0;

    // keep track of errors with invalid characters
    private int invalidCharacterErrors = 0;

    // Threading and Audio Control
    private Thread decoderThread;
    private TargetDataLine microphoneLine;

    // used to indicate if the decoding is running
    private volatile boolean decoding = false;

    // the current line record
    private String currentLineRecord;

    // private current side
    private char currentSide = 'A'; // side A or B
    private int sideAErrors = 0;
    private int sideALineCount = 0;
    private int sideBErrors = 0;
    private int sideBLineCount = 0;

    private static final DecimalFormat df = new DecimalFormat("0.0000");

    // the default baud rate
    private static double baudRate = 1200.0;

    // used to indicate if we are in DCT mode
    private boolean isDctMode = false;

    private CassetteFlow cassetteFlow;

    /**
     * Default constructor
     * 
     * @param isDctMode
     */
    public TapeDeckTester(boolean isDctMode, CassetteFlow cassetteFlow) {
        this.isDctMode = isDctMode;
        this.cassetteFlow = cassetteFlow;

        // print the version
        if (isDctMode) {
            System.out.println(VERSION + " - DCT MODE");
        } else {
            System.out.println(VERSION + " - GENERIC MODE");
        }
    }

    /**
     * Return the current line record
     * 
     * @return
     */
    public String getCurrentLineRecord() {
        return currentLineRecord;
    }

    /**
     * Grab data directly from JMinimodem (Internal Library)
     * * @throws IOException
     */
    public void startMinimodem() throws IOException {
        // Kill any previous session
        stop();

        System.out.println("\nInitializing JMinimodem Audio Capture...");

        // 1. Setup Audio Format (48kHz, 16-bit, Mono)
        float sampleRate = 48000.0f;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);

        try {
            // Open Microphone
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Error: Microphone line not supported.");
                return;
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
            config.baudRate = baudRate;
            config.sampleRate = sampleRate;
            config.quiet = false; // We need this FALSE so it generates "### NOCARRIER"

            // Wrap the Mic Line in a Stream
            AudioInputStream audioStream = new AudioInputStream(microphoneLine);

            // Setup Custom OutputStream to capture decoded text and feed it to
            // newLogFileLine
            LineAccumulator dataOutput = new LineAccumulator();

            // Setup System.err Interceptor to capture "### NOCARRIER" and feed it to
            // newLogFileLine
            PrintStream originalErr = System.err;
            StatusInterceptor statusInterceptor = new StatusInterceptor(originalErr);
            System.setErr(statusInterceptor);

            try {
                // *** BLOCKING CALL - Runs until stop() closes the line ***
                JMinimodem.receive(config, audioStream, dataOutput);
            } catch (Exception ex) {
                // If we stopped manually, an IO exception is expected when the line closes
                if (decoding) {
                    Logger.getLogger(TapeDeckTester.class.getName()).log(Level.SEVERE, null, ex);
                }
            } finally {
                // Restore System.err
                System.setErr(originalErr);
            }
        }, "JMinimodem Decoder Thread");

        decoderThread.start();
    }

    /**
     * Helper Class: Captures decoded bytes, builds strings, and calls
     * newLogFileLine on newlines.
     */
    private class LineAccumulator extends OutputStream {
        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws IOException {
            if (b == '\n' || b == '\r') {
                if (buffer.size() > 0) {
                    String line = buffer.toString("UTF-8");
                    newLogFileLine(line);
                    buffer.reset();
                }
            } else {
                buffer.write(b);
            }
        }
    }

    /**
     * Helper Class: Intercepts System.err to detect JMinimodem status messages
     * (NOCARRIER).
     */
    private class StatusInterceptor extends PrintStream {
        public StatusInterceptor(OutputStream out) {
            super(out);
        }

        @Override
        public void println(String x) {
            super.println(x); // Print to console so user sees it
            if (x != null && x.contains("###")) {
                newLogFileLine(x); // Pass to logic
            }
        }

        @Override
        public void print(String x) {
            super.print(x);
            if (x != null && x.contains("###")) {
                newLogFileLine(x);
            }
        }
    }

    /**
     * Process a line (either decoded data OR a status message)
     * 
     * @param line
     */
    public synchronized void newLogFileLine(String line) {
        if (line != null) {
            line = line.trim();

            // CASE 1: Valid Data Record
            if (line.length() == 29 && !line.startsWith("#")) {
                logLineCount++;
                updateSideLineCount();

                // check to see if all characters are valid
                if (!validCharacters(line))
                    return;

                processRecord(line);
                stopRecords = 0;

                if (isDctMode) {
                    if (logLineCount % 10 == 0) {
                        printStats();
                    }
                } else {
                    System.out.println(
                            line + "\t\tCount: " + logLineCount + "\t# Errors: " + dataErrors + " (" + getPercentError()
                                    + "%)\tDLE: " + dataLengthErrors + " / NCE: " + numberConversionErrors
                                    + " / ICE: " + invalidCharacterErrors);
                }
            }
            // CASE 2: Carrier Lost
            else if (line.contains("### NOCARRIER")) {
                if (stopRecords == 0) {
                    String stopMessage = "";
                    if (isDctMode) {
                        stopMessage = "PLAYBACK STOPPED (errors/line records): " + dataErrors + "/" + logLineCount +
                                " { " + getPercentError() + "%}\n" +
                                "SIDE A ERRORS: " + sideAErrors + "/" + sideALineCount + "\t{ "
                                + getSidePercentError('A') + "% }\n" +
                                "SIDE B ERRORS: " + sideBErrors + "/" + sideBLineCount + "\t{ "
                                + getSidePercentError('B') + "% }\n" +
                                "DATA LENGTH ERRORS: " + dataLengthErrors + "\n" +
                                "TOTAL STOPS: " + totalStops + "\n";
                    } else {
                        stopMessage = "PLAYBACK STOPPED (errors/line records): " + dataErrors + "/" + logLineCount +
                                " { " + getPercentError() + "%}\n" +
                                "DATA LENGTH ERRORS: " + dataLengthErrors + "\n" +
                                "TOTAL STOPS: " + totalStops + "\n";
                    }
                    System.out.println("\n");
                    System.out.println(stopMessage);

                    totalStops++;
                }

                stopRecords++;
            }
            // CASE 3: Data Length Errors (Noise or bad decode)
            else {
                // count errors when line is not long enough but should contain data (has
                // underscore)
                if (!line.contains("###") && line.contains("_")) {
                    dataLengthErrors++;
                    printError("\nInvalid Data Length @: " + line + " (# Data Length Errors: "
                            + dataLengthErrors + ")\n");

                }
            }
        }
    }

    private void printStats() {
        String message = "CURRENT PROGRESS (errors/line records): " + dataErrors + "/" + logLineCount +
                " { " + getPercentError() + "% }\n" +
                "SIDE A ERRORS: " + sideAErrors + "/" + sideALineCount + "\t{ " + getSidePercentError('A') + "% }\n" +
                "SIDE B ERRORS: " + sideBErrors + "/" + sideBLineCount + "\t{ " + getSidePercentError('B') + "% }\n" +
                "DATA LENGTH ERRORS: " + dataLengthErrors + "\n" +
                "TOTAL STOPS: " + totalStops + "\n";
        System.out.println(message);
    }

    /**
     * Get the percent error
     * 
     * @return
     */
    public String getPercentError() {
        double percent = 0.0;

        if (logLineCount != 0) {
            percent = ((double) dataErrors / (double) logLineCount) * 100.0;
        }

        return df.format(percent);
    }

    /**
     * Update the log line count for the side
     */
    public void updateSideLineCount() {
        if (currentSide == 'A') {
            sideALineCount++;
        } else {
            sideBLineCount++;
        }
    }

    /**
     * Update the errors for the side
     */
    public void updateSideErrors() {
        if (!isDctMode)
            return;

        if (currentSide == 'A') {
            sideAErrors++;
        } else {
            sideBErrors++;
        }
    }

    /**
     * Get the percent error for side A or B
     * 
     * @param side
     * @return
     */
    public String getSidePercentError(char side) {
        double percent = 0.0;
        int errors;
        int lineCount;

        if (side == 'A') {
            errors = sideAErrors;
            lineCount = sideALineCount;
        } else {
            errors = sideBErrors;
            lineCount = sideBLineCount;
        }

        if (lineCount != 0)
            percent = ((double) errors / (double) lineCount) * 100.0;

        return df.format(percent);
    }

    /**
     * Make sure we only processing ascii characters
     * * @param input
     * 
     * @return
     */
    private boolean validCharacters(String input) {
        boolean result = true;

        for (int i = 0; i < input.length(); i++) {
            int test = (int) input.charAt(i);

            if (test > 127) {
                result = false;
                invalidCharacterErrors++;
                printError("\nInvalid Character @: " + input + " | " + input.charAt(i) + "\n");
                break;
            }
        }

        return result;
    }

    /**
     * Process a record to check to see if we getting good data
     * 
     * @param line
     * @return
     */
    public String processRecord(String line) {
        String[] sa = line.split("_");

        if (sa.length != 5) {
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
        } catch (NumberFormatException nfe) {
            numberConversionErrors++;
            return printError("\nInvalid Integer @: " + line + "\n");
        }

        // check to see what tape ID is DCT_0
        if (isDctMode) {
            // check to see what tape ID is DCT_0
            if (!tapeId.equals("DCT0A") && !tapeId.equals("DCT0B")) {
                return printError("\nInvalid Tape ID @: " + line + "\n");
            } else {
                if (tapeId.contains("0A")) {
                    currentSide = 'A';
                } else {
                    currentSide = 'B';
                }
            }

            if (!audioId.equals("aaaaaaaaaa")) {
                return printError("\nInvalid Audio File ID @: " + line + "\n");
            }
        } else {
            // GENERIC MODE
            if (!cassetteFlow.tapeDB.containsKey(tapeId)) {
                return printError("\nInvalid Tape ID @: " + line + "\n");
            }

            if (!cassetteFlow.audioInfoDB.containsKey(audioId)) {
                return printError("\nInvalid Audio File ID @: " + line + "\n");
            }
        }

        return line;
    }

    /**
     * Print any error messages out
     * 
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
     * Stop reading from JMinimodem and close the audio line
     */
    public void stop() {
        decoding = false;
        // Closing the line causes the blocked JMinimodem.receive() call to throw an
        // exception and exit.
        if (microphoneLine != null && microphoneLine.isOpen()) {
            microphoneLine.close();
        }
    }

    /**
     * Main method for running the test.
     * 
     * @param args
     */
    public static void main(String[] args) {
        boolean dctMode = false;

        // interate through the args to see if there is a baud rate specified and see
        // what mode we are in
        for (String arg : args) {
            if (arg.equalsIgnoreCase("DCT")) {
                dctMode = true;
            } else {
                try {
                    baudRate = Double.parseDouble(arg);
                } catch (NumberFormatException nfe) {
                }
            }
        }

        // initialize a cassette flow object
        CassetteFlow cassetteFlow = new CassetteFlow();
        TapeDeckTester tapeDeckDataTester = new TapeDeckTester(dctMode, cassetteFlow);

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

            System.exit(0);
        } catch (IOException ex) {
            Logger.getLogger(TapeDeckTester.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}