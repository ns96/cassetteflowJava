/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cassetteflow;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.Mixer;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Main User Interface for the cassette flow program
 * 
 * @author Nathan
 */
public class CassetteFlowFrame extends javax.swing.JFrame implements RecordProcessorInterface {
    private CassetteFlow cassetteFlow;
    
    private CassettePlayer cassettePlayer;
    
    private ESP32LyraTConnect lyraTConnect;
    
    private ArrayList<AudioInfo> sideAList = new ArrayList<>();
    
    private ArrayList<AudioInfo> sideBList = new ArrayList<>();
    
    private ArrayList<AudioInfo> sideNList = new ArrayList<>();
    
    private  StreamPlayer player = null;
    
    private Thread playerThread = null;
    
    private boolean playSide = false; // used to indicated if a side is being played
    
    private boolean isFullScreen = false;
    
    // the cassette flow server for testing
    private CassetteFlowServer cassetteFlowServer;
    
    // used to check to see we are using minimodem to do realtime encoding
    private boolean realTimeEncoding = false;
    
    // used when encoding on remote machine
    private int currentTrackIndex = 0;
       
    // keep track of the currenet seconds for long running task
    private int encodeSeconds;
    
    // keep track of the seconds audio has been played so for on the lyraT board
    private int playSeconds;
    
    // holds the resently encoded wav files if any
    private File[] wavFiles;
    
    // stores the audio output channels
    private HashMap<String, Mixer.Info> mixerOutput;
    
    // used to indicate if reading of line records from LyraT board should be done
    private boolean lyraTReadLineRecords = false;
    
    // these store information read from lyraT board
    private int lyraTDataErrors = 0;
    private int lyraTStartTime = 0;
    private String lyraTCurrentTapeId = "";
    private String lyraTCurrentAudioId = "";
    private int lyraTMuteRecords = 0;
    private int lyraTAudioTotalPlayTime = 0;
    private int lyraTCurrentPlayTime = 0;
    private String lyraTAudioFilename = "";
    private boolean lyraTGetDecode;
    
    // used to see if to track stop records in order to estimate the current
    // tape time whn FF or REW especially using a R2R which doesn't have 
    private boolean trackStopRecords = false;
    private int stopRecordsTimer = 0;
    private int stopRecordsCounter = 0;
    private int stopRecordsCounterOld = 0;
    private boolean playing;
    
    // store a list of filtered audioIno objects
    private ArrayList<AudioInfo> filteredAudioList;
    
    // store the current directory location so when we doing filtering
    // we can display the current directory hen we done with filtering
    private String currentAudioDirectory;
    
    /**
     * Creates new form CassetteFlowFrame
     */
    public CassetteFlowFrame() {
        initComponents(); 
        DefaultListModel model = new DefaultListModel();
        audioJList.setModel(model);
        
        model = new DefaultListModel();
        sideAJList.setModel(model);
        
        model = new DefaultListModel();
        sideBJList.setModel(model);
        
        loadAudioOuputDevices();
    }
    
    /**
     * Load the audio output devices
     */
    private void loadAudioOuputDevices() {
        // add the audio outputs to the combo box
        audioOutputComboBox.removeAllItems();
        
        mixerOutput = WavPlayer.getOutputDevices();
        for(String key: mixerOutput.keySet()) {
            audioOutputComboBox.addItem(key);
        }
    }
    
    /**
     * Used to put the program in full screen
     */
    public void initFullScreen() {
       GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        isFullScreen = device.isFullScreenSupported();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setUndecorated(isFullScreen);
        setResizable(!isFullScreen);
        
        if (isFullScreen) {
            // Full-screen mode
            device.setFullScreenWindow(this);
            validate();
        } else {
            // Windowed mode
            this.pack();
            //this.setExtendedState(MAXIMIZED_BOTH);
            this.setVisible(true);
        }
    }
    
    /**
     * Set the main cassette flow object then show default values in UI
     * 
     * @param cassetteFlow 
     */
    public void setCassetteFlow(CassetteFlow cassetteFlow) {
        this.cassetteFlow = cassetteFlow;
        updateUI();
    }
    
    /**
     * Method to update the UI after cassette flow object has loaded the list of
     * flac and mp3 files
     */
    private void updateUI() {
        currentAudioDirectory = CassetteFlow.AUDIO_DIR_NAME;
        
        directoryTextField.setText(currentAudioDirectory);
        logfileTextField.setText(CassetteFlow.LOG_FILE_NAME);
        baudRateTextField.setText(CassetteFlow.BAUDE_RATE);
        lyraTHostTextField.setText(CassetteFlow.LYRA_T_HOST);
        audioDownloadServerTextField.setText(CassetteFlow.DOWNLOAD_SERVER);
        
        addAudioInfoToJList();
    }
    
    /**
     * Merge tape records from LyraT board into the local tape db
     */
    public void mergeCurrentTapeDBToLocal() {
        cassetteFlow.mergeCurrentTapeDBToLocal();
    }
    
    /**
     * Set the current cassette ID so the track list can be displayed
     * 
     * @param cassetteID 
     */
    public void setPlayingCassetteID(final String cassetteID) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                tracksLabel.setText(cassetteID + " Tracks");
                tapeInfoTextArea.setText("");
                
                ArrayList<String> audioIds = cassetteFlow.tapeDB.get(cassetteID);
                
                if(audioIds != null) {
                   for(int i = 0; i < audioIds.size(); i++) {
                       String audioId = audioIds.get(i);
                       
                       AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(audioId);
                       String trackCount = String.format("%02d", (i + 1));
                       tapeInfoTextArea.append("[" + trackCount + "] " + audioInfo.getName() + "\n");
                       
                       // see if there is additional track information if this is for a youtube mix
                       if(cassetteFlow.tracklistDB.containsKey(audioId)) {
                           TrackListInfo trackListInfo = cassetteFlow.tracklistDB.get(audioId);
                           tapeInfoTextArea.append(trackListInfo.toString());
                       }
                   }
                } else {
                   tapeInfoTextArea.setText("Invalid Tape ID ...");
                }
            }
        });
    }
    
    @Override
    public void setPlayingAudioInfo(final String info) {
        SwingUtilities.invokeLater(() -> {
            trackInfoTextArea.setText(info);
        });
    }
    
    @Override
    public void setPlaybackInfo(final String info, boolean append, String newLine) {
        SwingUtilities.invokeLater(() -> {
            if(!append) {
                playbackInfoTextArea.setText(info + newLine);
            } else {
                playbackInfoTextArea.append(info + newLine);
            }
        });
    }
    
    @Override
    public void setPlaybackInfo(final String info, boolean append) {
        setPlaybackInfo(info, append, "\n");
    }
    
    /**
     * Used to estimate the current track during a FF or Rewind operation, especially on
     * a R2R machine. 11/8/2021 Doesn't work correctly
     * 
     * @param stopRecords 
     * @param playTime 
     */
    @Override
    public void setStopRecords(int stopRecords, int playTime) {
        if(r2RComboBox.getSelectedIndex() == 0) {
            return;
        }
        
        if(stopRecords > 0) {
            stopRecordsCounter++;
            
            if (!trackStopRecords) {
                trackStopRecords = true;
                
                // get the time scale which concerts the time FF or REW to tape time
                int timeScale = Integer.parseInt(r2RTextField.getText());

                Timer timer = new Timer(1000, (ActionEvent e) -> {
                    stopRecordsTimer++;
                    
                    if(stopRecordsCounter > stopRecordsCounterOld) {
                        int scaledTime = timeScale * stopRecordsTimer;
                        System.out.println("FF/REW: " + stopRecordsTimer + " / Scale Time: " + scaledTime
                                + " / Old PlayTime: " + playTime + " / New PlayTime: " + (playTime + scaledTime));
                        stopRecordsCounterOld = stopRecordsCounter;
                    } else {
                        System.out.println("No new stop records ...");
                        //stopRecordsTimer--;
                    }
                    
                    // see if to stop the timer
                    if (!trackStopRecords) {
                        Timer callingTimer = (Timer) e.getSource();
                        callingTimer.stop();
                    }
                });
                timer.start();
            }
        } else {
            stopRecordsTimer = 0;
            stopRecordsCounter = 0;
            stopRecordsCounterOld = 0;
            trackStopRecords = false;
            //System.out.println("No audio data ...");
        }
    }
    
    /**
     * Process a line record
     * 
     * @param line 
     */
    @Override
    public void processLineRecord(String line) {
        //printToLyraTConsole("Line Record: " + line, true);
        String[] sa = line.split("_");
        
        if(sa.length != 5) {
            String message = "*Invalid Record: " + line;
            printToConsole(message, true);
            System.out.println(message);
            lyraTDataErrors++;
            return;
        }
        
        String tapeId = sa[0];
        String track = sa[1];
        String audioId = sa[2];
        String playTimeS = sa[3];
        
        // get the total time from the tape data
        int totalTime = 0;
        
        try {
            totalTime = Integer.parseInt(sa[4]);
        } catch(NumberFormatException ex) {
            String message = "*Invalid Time Total: " + line;
            printToConsole(message, true);
            System.out.println(message);
            lyraTDataErrors++;
            return;
        }
        
        // check to see what tape is playing
        if(!tapeId.equals(lyraTCurrentTapeId)) {
            if(!tapeId.equals("HTTPS")) {
                lyraTCurrentTapeId = tapeId;            
                setPlayingCassetteID(tapeId);
            }
        }
        
        if(!lyraTCurrentAudioId.equals(audioId)) {
            if(!playTimeS.equals("000M")) {
                lyraTMuteRecords = 0;
                lyraTCurrentAudioId = audioId;
                
                try {
                    lyraTStartTime = Integer.parseInt(playTimeS);
                } catch (NumberFormatException nfe) {
                    String message = "*Invalid Start Time: " + line;
                    printToConsole(message, true);
                    System.out.println(message);
                    lyraTDataErrors++;
                }
                
                AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(audioId);
                String message;
                
                if(audioInfo != null) {
                    File audioFile = audioInfo.getFile();
                    lyraTAudioFilename = audioFile.getName();
                    lyraTAudioTotalPlayTime = audioInfo.getLength();

                    message = "Audio ID: " + audioId + "\n" + 
                        audioInfo.getName() + "\n" + 
                        "Start Time @ " + lyraTStartTime + " | Track Number: " + track;
                    
                    setPlayingAudioInfo(message);
                } else {
                    message = "*Invalid AudioID: " + line;
                    printToConsole(message, true);
                    System.out.println(message);
                    lyraTDataErrors++;
                }
            } else {
                if (lyraTMuteRecords == 0) {
                    setPlaybackInfo("Mute Section ...", false);
                } else {
                    setPlaybackInfo("Mute Section ...", true);
                }

                lyraTMuteRecords++;
            }
        }
        
        int playTime = 0;
        try {
            playTime = Integer.parseInt(playTimeS);
        } catch(NumberFormatException nfe) {
            String message = "*Invalid Playtime: " + playTimeS;
            System.out.println(message);
            lyraTDataErrors++;
        }
        
        if (lyraTCurrentPlayTime != playTime) {
            lyraTCurrentPlayTime = playTime;

            String message = lyraTAudioFilename + " [" + track + "]\n"
                    + "Playtime From Tape: " + String.format("%04d", lyraTCurrentPlayTime) + " / " + String.format("%04d", lyraTAudioTotalPlayTime) + "\n"
                    + "Tape Counter: " + totalTime + " (" + CassetteFlowUtil.getTimeString(totalTime) + ")\n"
                    + "Data Errors: " + lyraTDataErrors;

            setPlaybackInfo(message, false, "");
        }
    }
    
    /**
     * Add the mp3s/flac files information to the jlist
     */
    private void addAudioInfoToJList() {
        DefaultListModel model = (DefaultListModel) audioJList.getModel();
        
        // sort the list of mp3s/flac before displaying
        Collections.sort(cassetteFlow.audioInfoList, (o1, o2) -> o1.toString().compareTo(o2.toString()));
        
        for (AudioInfo audioInfo: cassetteFlow.audioInfoList) {
            try {
                System.out.println("Audio File: " + audioInfo);
                model.addElement(audioInfo);
            } catch (Exception ex) {
                Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if(lyraTConnect == null) {
            audioCountLabel.setText(cassetteFlow.audioInfoList.size() + " Audio files loaded ...");
        } else {
            audioCountLabel.setText(cassetteFlow.audioInfoList.size() + " LyraT files loaded ...");
        }
    }
    
    /**
     * Check to make sure the input text is the specified length, otherwise return null
     * 
     * @param text
     * @param length
     * @return 
     */
    private String checkInputLength(String text, int length) {
        text = text.trim();
        
        if(text.length() == length) {
            return text;
        } else {
            String message = "Input Text " + text + " must be " + length + " characters ...";
            JOptionPane.showMessageDialog(this, message, "Input Text Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    
    /**
     * Encode using minimodem, but just create the wav file and don't play it
     * 
     * @param forDownload 
     */
    private void directEncode(boolean forDownload) {
        directEncode(forDownload, false);
    }
    
    /**
     * A way to directly encode the data directly to a wav file or in real time
     * which involves playing back the wav file after creation.
     */
    private void directEncode(boolean forDownload, boolean realTime) {
        int side = tapeJTabbedPane.getSelectedIndex();
        
        int muteTime = Integer.parseInt(muteJTextField.getText());
        String saveDirectoryName = CassetteFlow.AUDIO_DIR_NAME + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
        String tapeID = checkInputLength(tapeIDTextField.getText(), 4);
        
        // only continue if we have a valid tape ID
        if(tapeID == null) return;
        
        // check if to create save directory
        File directory = new File(saveDirectoryName);
        if (!directory.exists()){
            directory.mkdir();
        }
        
        // set the wavFiles array to null
        wavFiles = null;
        
        encodeProgressBar.setIndeterminate(true);
        createButton.setEnabled(false);
        realtimeEncodeButton.setEnabled(false);
        //playEncodedWavButton.setEnabled(false);
        createDownloadButton.setEnabled(false);
        
        // get the correct label to update
        final JLabel infoLabel = (side == 0)? trackALabel : trackBLabel;
        
        // start the swing timer to show how long the encode is running for
        encodeSeconds = 0;
        final Timer timer = new Timer(1000, (ActionEvent e) -> {
            encodeSeconds++;
            String timeString = CassetteFlowUtil.getTimeString(encodeSeconds);
            infoLabel.setText("Encode Timer: " + timeString);
            
            if(encodeSeconds % 20 == 0) {
                consoleTextArea.append(".");
            }
        });
        timer.start();
        
        final JFrame frame = this;
        Thread thread = new Thread("Encode Thread") {
            public void run() {
                try {
                    if(realTime) {
                        Mixer.Info soundOutput = mixerOutput.get(audioOutputComboBox.getSelectedItem().toString());
                        
                        boolean completed;
                        if(side == 0) {
                            completed = cassetteFlow.realTimeEncode(tapeID + "A", sideAList, muteTime, forDownload, saveDirectoryName, soundOutput);
                            
                            if(completed) {
                                cassetteFlow.addToTapeDB(tapeID, sideAList, null, true);
                            }
                        } else if(side == 1) {
                            completed = cassetteFlow.realTimeEncode(tapeID + "B", sideBList, muteTime, forDownload, saveDirectoryName, soundOutput);
                            
                            if(completed) {
                                cassetteFlow.addToTapeDB(tapeID, null, sideBList, true);
                            }
                        }
                    } else {
                        // just generate the text and wav files
                        wavFiles = cassetteFlow.directEncode(saveDirectoryName, tapeID, sideAList, sideBList, muteTime, forDownload);
                    }
                } catch (Exception ex) {
                    String message = "Error Encoding With Minimodem";
                    JOptionPane.showMessageDialog(frame, message, "Minimodem Error", JOptionPane.ERROR_MESSAGE);

                    Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
                    
                    wavFiles = null;
                    setEncodingDone();
                }
                
                // stop the timer and clear info label
                timer.stop();
                infoLabel.setText("");
            }
        };
        thread.start();
    } 
    
    /**
     * Load the information for the tape
     * 
     * @param tapeID
     * @param sideAList
     * @param sideBList 
     */
    void loadTapeInformation(String tapeID, ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB, int tapeLength) {
        tapeIDTextField.setText(tapeID);
        
        // set the tape type based base on length
        if(tapeLength < 65) {
            tapeLengthComboBox.setSelectedIndex(0);
        } else if(tapeLength < 95) {
            tapeLengthComboBox.setSelectedIndex(1);
        } else if(tapeLength < 115) {
            tapeLengthComboBox.setSelectedIndex(2);
        } else if(tapeLength < 125) {
            tapeLengthComboBox.setSelectedIndex(3);
        }else {
            tapeLengthComboBox.setSelectedIndex(4);
        }
        
        // now load the mp3s
        if(sideA != null) {
            sideAList = sideA;
            addTracksToTapeJList(sideAList, sideAJList);
            calculateTotalTime(sideAList, sideALabel);
        }
        
        if(sideB != null) {
            sideBList = sideB;
            addTracksToTapeJList(sideBList, sideBJList);
            calculateTotalTime(sideBList, sideBLabel);
        }
    }
    
    /**
     * Used to indicate that the encoding completed
     */
    public void setEncodingDone() {
        encodeProgressBar.setIndeterminate(false);
        createButton.setEnabled(true);
        createDownloadButton.setEnabled(true);
        realtimeEncodeButton.setEnabled(true);
        //playEncodedWavButton.setEnabled(true);
    }
    
    /**
     * Print information to the main console
     * 
     * @param text
     * @param append 
     */
    public void printToConsole(String text, boolean append) {
        SwingUtilities.invokeLater(() -> {
            if(!append) {
                consoleTextArea.setText(text + "\n");
            } else {
                consoleTextArea.append(text + "\n");
            }
        });
    }
    
    /**
     * Set select the current track being process
     * 
     * @param index 
     */
    public void setSelectedIndexForSideJList(int index) {
        int side = tapeJTabbedPane.getSelectedIndex();
        
        if(side == 0 && index < sideAJList.getModel().getSize()) {
            sideAJList.setSelectedIndex(index);
        } else if(side == 1 && index < sideBJList.getModel().getSize()) {
            sideBJList.setSelectedIndex(index);
        } else {
            System.out.println("Wrong jList selected ...");
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        directoryTextField = new javax.swing.JTextField();
        tapeJTabbedPane = new javax.swing.JTabbedPane();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        sideAJList = new javax.swing.JList<>();
        sideALabel = new javax.swing.JLabel();
        trackALabel = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        sideBJList = new javax.swing.JList<>();
        sideBLabel = new javax.swing.JLabel();
        trackBLabel = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        jScrollPane7 = new javax.swing.JScrollPane();
        sideNJList = new javax.swing.JList<>();
        sideNLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        audioJList = new javax.swing.JList<>();
        jLabel1 = new javax.swing.JLabel();
        tapeIDTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        tapeLengthComboBox = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        muteJTextField = new javax.swing.JTextField();
        addAudioToTapeListButton = new javax.swing.JButton();
        removeAudioButton = new javax.swing.JButton();
        removeAllButton = new javax.swing.JButton();
        shuffleButton = new javax.swing.JButton();
        playButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        clearSelectionButton = new javax.swing.JButton();
        playSideButton = new javax.swing.JButton();
        clearAudioListButton = new javax.swing.JButton();
        moveTrackUpButton = new javax.swing.JButton();
        moveTrackDownButton = new javax.swing.JButton();
        directEncodeCheckBox = new javax.swing.JCheckBox();
        encodeProgressBar = new javax.swing.JProgressBar();
        viewTapeDBButton = new javax.swing.JButton();
        defaultButton = new javax.swing.JButton();
        realtimeEncodeButton = new javax.swing.JButton();
        filterAudioListButton = new javax.swing.JButton();
        checkTrackListButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        logfileTextField = new javax.swing.JTextField();
        logfileButton = new javax.swing.JButton();
        startDecodeButton = new javax.swing.JButton();
        jScrollPane4 = new javax.swing.JScrollPane();
        tapeInfoTextArea = new javax.swing.JTextArea();
        jScrollPane5 = new javax.swing.JScrollPane();
        playbackInfoTextArea = new javax.swing.JTextArea();
        jScrollPane6 = new javax.swing.JScrollPane();
        trackInfoTextArea = new javax.swing.JTextArea();
        jLabel4 = new javax.swing.JLabel();
        tracksLabel = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        stopDecodeButton = new javax.swing.JButton();
        directDecodeCheckBox = new javax.swing.JCheckBox();
        mmdelayTextField = new javax.swing.JTextField();
        r2RComboBox = new javax.swing.JComboBox<>();
        r2RTextField = new javax.swing.JTextField();
        r2RLabel = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        lyraTHostTextField = new javax.swing.JTextField();
        lyraTConnectButton = new javax.swing.JButton();
        lyraTDisconnectButton = new javax.swing.JButton();
        jScrollPane9 = new javax.swing.JScrollPane();
        lyraTConsoleTextArea = new javax.swing.JTextArea();
        jLabel5 = new javax.swing.JLabel();
        startServerCheckBox = new javax.swing.JCheckBox();
        lyraTServerTestDBButton = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        lyraTDecodeRadioButton = new javax.swing.JRadioButton();
        lyraTEncodeRadioButton = new javax.swing.JRadioButton();
        lyraTGetInfoButton = new javax.swing.JButton();
        lyraTGetRawButton = new javax.swing.JButton();
        lyraTCreateAButton = new javax.swing.JButton();
        lyraTEncodeAButton = new javax.swing.JButton();
        lyraTPlaySideAButton = new javax.swing.JButton();
        lyraTEncodeBButton = new javax.swing.JButton();
        lyraTPlaySideBButton = new javax.swing.JButton();
        lyraTStopButton = new javax.swing.JButton();
        lyraTPassRadioButton = new javax.swing.JRadioButton();
        clearLyraTConsoleButton = new javax.swing.JButton();
        lyraTStopRawButton = new javax.swing.JButton();
        lyraTCreateBButton = new javax.swing.JButton();
        jPanel9 = new javax.swing.JPanel();
        eqRadioButton1 = new javax.swing.JRadioButton();
        eqRadioButton2 = new javax.swing.JRadioButton();
        eqRadioButton3 = new javax.swing.JRadioButton();
        eqRadioButton4 = new javax.swing.JRadioButton();
        eqRadioButton5 = new javax.swing.JRadioButton();
        eqRadioButton6 = new javax.swing.JRadioButton();
        eqRadioButton7 = new javax.swing.JRadioButton();
        eqRadioButton8 = new javax.swing.JRadioButton();
        jLabel9 = new javax.swing.JLabel();
        speakerRadioButton = new javax.swing.JRadioButton();
        bluetoothRadioButton = new javax.swing.JRadioButton();
        bluetoothComboBox = new javax.swing.JComboBox<>();
        jLabel10 = new javax.swing.JLabel();
        lyraTVolDownButton = new javax.swing.JButton();
        lyraTMuteToggleButton = new javax.swing.JToggleButton();
        lyraTVolUpButton = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        consoleTextArea = new javax.swing.JTextArea();
        clearConsoleButton = new javax.swing.JButton();
        baudRateButton = new javax.swing.JButton();
        baudRateTextField = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        audioOutputComboBox = new javax.swing.JComboBox<>();
        setAudioDownloadServerButton = new javax.swing.JButton();
        audioDownloadServerTextField = new javax.swing.JTextField();
        filterShuffleCheckBox = new javax.swing.JCheckBox();
        shuffleFilterTextField = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        playbackSpeedTextField = new javax.swing.JTextField();
        reloadAudioOutputsButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        addAudioDirectoryButton = new javax.swing.JButton();
        createButton = new javax.swing.JButton();
        createDownloadButton = new javax.swing.JButton();
        audioCountLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("CassetteFlow v 0.9.17 (03/03/2022)");

        jTabbedPane1.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N

        directoryTextField.setText("C:\\\\mp3files");
        directoryTextField.setToolTipText("");
        directoryTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                directoryTextFieldActionPerformed(evt);
            }
        });

        jScrollPane2.setViewportView(sideAJList);

        sideALabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        sideALabel.setText("Side A track Info goes here ...");

        trackALabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        trackALabel.setForeground(java.awt.Color.red);
        trackALabel.setText(" ");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 627, Short.MAX_VALUE)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(sideALabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(trackALabel, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 290, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(trackALabel)
                    .addComponent(sideALabel))
                .addContainerGap())
        );

        tapeJTabbedPane.addTab("Side A", jPanel3);

        jScrollPane3.setViewportView(sideBJList);

        sideBLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        sideBLabel.setText("Side B track Info goes here ...");

        trackBLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        trackBLabel.setForeground(java.awt.Color.red);
        trackBLabel.setText(" ");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 627, Short.MAX_VALUE)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(sideBLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(trackBLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(trackBLabel)
                    .addComponent(sideBLabel))
                .addGap(9, 9, 9))
        );

        tapeJTabbedPane.addTab("Side B", jPanel4);

        sideNJList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Split Large MP3 Files Using ffmpeg (https://www.ffmpeg.org/) -- Work In Progress" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane7.setViewportView(sideNJList);

        sideNLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        sideNLabel.setText("MP3 Split Information Goes Here ...");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane7)
            .addComponent(sideNLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 627, Short.MAX_VALUE)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 290, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(sideNLabel)
                .addContainerGap())
        );

        tapeJTabbedPane.addTab("MP3 Split", jPanel6);

        audioJList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Audio File 1", "Audio File 2" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        audioJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                audioJListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(audioJList);

        jLabel1.setText("4 Digit Tape ID");

        tapeIDTextField.setText("0010");

        jLabel2.setText("Tape Length");

        tapeLengthComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "60 Minutes", "90 Minutes", "110 Minutes", "120 Minutes", "240 Minutes (R2R)" }));
        tapeLengthComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tapeLengthComboBoxActionPerformed(evt);
            }
        });

        jLabel3.setText("Mute (s)");

        muteJTextField.setText("4");

        addAudioToTapeListButton.setText("Add");
        addAudioToTapeListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addAudioToTapeListButtonActionPerformed(evt);
            }
        });

        removeAudioButton.setText("Remove");
        removeAudioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAudioButtonActionPerformed(evt);
            }
        });

        removeAllButton.setText("Remove All");
        removeAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAllButtonActionPerformed(evt);
            }
        });

        shuffleButton.setText("Shuffle");
        shuffleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shuffleButtonActionPerformed(evt);
            }
        });

        playButton.setText("Play");
        playButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playButtonActionPerformed(evt);
            }
        });

        stopButton.setText("Stop");
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });

        clearSelectionButton.setText("Unselect");
        clearSelectionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearSelectionButtonActionPerformed(evt);
            }
        });

        playSideButton.setText("Play Side");
        playSideButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playSideButtonActionPerformed(evt);
            }
        });

        clearAudioListButton.setText("Clear");
        clearAudioListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearAudioListButtonActionPerformed(evt);
            }
        });

        moveTrackUpButton.setText("Move Track Up");
        moveTrackUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveTrackUpButtonActionPerformed(evt);
            }
        });

        moveTrackDownButton.setText("Move Track Down");
        moveTrackDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveTrackDownButtonActionPerformed(evt);
            }
        });

        directEncodeCheckBox.setSelected(true);
        directEncodeCheckBox.setText("Encode to Wav");

        viewTapeDBButton.setText("View Tape DB");
        viewTapeDBButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewTapeDBButtonActionPerformed(evt);
            }
        });

        defaultButton.setText("Default");
        defaultButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultButtonActionPerformed(evt);
            }
        });

        realtimeEncodeButton.setText("Real Time Encode");
        realtimeEncodeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                realtimeEncodeButtonActionPerformed(evt);
            }
        });

        filterAudioListButton.setText("Filter");
        filterAudioListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterAudioListButtonActionPerformed(evt);
            }
        });

        checkTrackListButton.setText("Check");
        checkTrackListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkTrackListButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(playButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(stopButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearSelectionButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearAudioListButton)
                        .addGap(0, 84, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(directoryTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(defaultButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filterAudioListButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tapeJTabbedPane)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(moveTrackUpButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(moveTrackDownButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkTrackListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(realtimeEncodeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(playSideButton)
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tapeIDTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tapeLengthComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(muteJTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(viewTapeDBButton))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(addAudioToTapeListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeAudioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(shuffleButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(directEncodeCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(encodeProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(directoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel1)
                        .addComponent(tapeIDTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel2)
                        .addComponent(tapeLengthComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel3)
                        .addComponent(muteJTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(defaultButton)
                        .addComponent(filterAudioListButton))
                    .addComponent(viewTapeDBButton, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(encodeProgressBar, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(addAudioToTapeListButton)
                                .addComponent(removeAudioButton)
                                .addComponent(removeAllButton)
                                .addComponent(shuffleButton)
                                .addComponent(directEncodeCheckBox)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tapeJTabbedPane))
                    .addComponent(jScrollPane1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(playButton)
                    .addComponent(stopButton)
                    .addComponent(clearSelectionButton)
                    .addComponent(clearAudioListButton)
                    .addComponent(moveTrackUpButton)
                    .addComponent(moveTrackDownButton)
                    .addComponent(playSideButton)
                    .addComponent(realtimeEncodeButton)
                    .addComponent(checkTrackListButton)))
        );

        jTabbedPane1.addTab("ENCODE", jPanel1);

        logfileTextField.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        logfileTextField.setText("logfile");

        logfileButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        logfileButton.setText("Set Logfile");

        startDecodeButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        startDecodeButton.setText("Start");
        startDecodeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startDecodeButtonActionPerformed(evt);
            }
        });

        tapeInfoTextArea.setEditable(false);
        tapeInfoTextArea.setColumns(20);
        tapeInfoTextArea.setFont(new java.awt.Font("Arial", 1, 24)); // NOI18N
        tapeInfoTextArea.setRows(5);
        jScrollPane4.setViewportView(tapeInfoTextArea);

        playbackInfoTextArea.setEditable(false);
        playbackInfoTextArea.setColumns(20);
        playbackInfoTextArea.setFont(new java.awt.Font("Arial", 0, 36)); // NOI18N
        playbackInfoTextArea.setLineWrap(true);
        playbackInfoTextArea.setRows(5);
        playbackInfoTextArea.setWrapStyleWord(true);
        jScrollPane5.setViewportView(playbackInfoTextArea);

        trackInfoTextArea.setEditable(false);
        trackInfoTextArea.setColumns(20);
        trackInfoTextArea.setFont(new java.awt.Font("Arial", 0, 36)); // NOI18N
        trackInfoTextArea.setRows(5);
        jScrollPane6.setViewportView(trackInfoTextArea);

        jLabel4.setFont(new java.awt.Font("Arial", 1, 36)); // NOI18N
        jLabel4.setText("Current Track Information");

        tracksLabel.setFont(new java.awt.Font("Arial", 1, 36)); // NOI18N
        tracksLabel.setText("0000A Tracks");

        jLabel6.setFont(new java.awt.Font("Arial", 1, 36)); // NOI18N
        jLabel6.setText("Playback Information");

        stopDecodeButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        stopDecodeButton.setText("Stop");
        stopDecodeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopDecodeButtonActionPerformed(evt);
            }
        });

        directDecodeCheckBox.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        directDecodeCheckBox.setSelected(true);
        directDecodeCheckBox.setText("Minimodem");

        mmdelayTextField.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        mmdelayTextField.setText("0");
        mmdelayTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mmdelayTextFieldActionPerformed(evt);
            }
        });

        r2RComboBox.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        r2RComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "R2R ||", "R2R >>", "R2R <<" }));

        r2RTextField.setColumns(5);
        r2RTextField.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        r2RTextField.setText("64");

        r2RLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        r2RLabel.setText(" FF/REV Track:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                    .addComponent(logfileTextField)
                    .addComponent(tracksLabel)
                    .addComponent(r2RLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(logfileButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(directDecodeCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mmdelayTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(r2RComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(r2RTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(startDecodeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(stopDecodeButton))
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4)
                            .addComponent(jLabel6))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(logfileTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(logfileButton)
                    .addComponent(startDecodeButton)
                    .addComponent(stopDecodeButton)
                    .addComponent(directDecodeCheckBox)
                    .addComponent(mmdelayTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(r2RComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(r2RTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(tracksLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 141, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jScrollPane4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(r2RLabel))))
        );

        jTabbedPane1.addTab("DECODE", jPanel2);

        lyraTHostTextField.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTHostTextField.setText("http://127.0.0.1:8192/");

        lyraTConnectButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTConnectButton.setText("CONNECT");
        lyraTConnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTConnectButtonActionPerformed(evt);
            }
        });

        lyraTDisconnectButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTDisconnectButton.setText("DISCONNECT");
        lyraTDisconnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTDisconnectButtonActionPerformed(evt);
            }
        });

        lyraTConsoleTextArea.setColumns(20);
        lyraTConsoleTextArea.setRows(5);
        jScrollPane9.setViewportView(lyraTConsoleTextArea);

        jLabel5.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel5.setText("LyraT Host");

        startServerCheckBox.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        startServerCheckBox.setText("Start Test Server");
        startServerCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startServerCheckBoxActionPerformed(evt);
            }
        });

        lyraTServerTestDBButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTServerTestDBButton.setText("Run Test On Server");
        lyraTServerTestDBButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTServerTestDBButtonActionPerformed(evt);
            }
        });

        jLabel7.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel7.setText("LyraT EQ+ Settings:");

        buttonGroup1.add(lyraTDecodeRadioButton);
        lyraTDecodeRadioButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTDecodeRadioButton.setSelected(true);
        lyraTDecodeRadioButton.setText("Decode");
        lyraTDecodeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTDecodeRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(lyraTEncodeRadioButton);
        lyraTEncodeRadioButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTEncodeRadioButton.setText("Encode");
        lyraTEncodeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTEncodeRadioButtonActionPerformed(evt);
            }
        });

        lyraTGetInfoButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTGetInfoButton.setText("Get Information");
        lyraTGetInfoButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTGetInfoButtonActionPerformed(evt);
            }
        });

        lyraTGetRawButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTGetRawButton.setText("Get Line Records");
        lyraTGetRawButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTGetRawButtonActionPerformed(evt);
            }
        });

        lyraTCreateAButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTCreateAButton.setText("Create A");
        lyraTCreateAButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTCreateAButtonActionPerformed(evt);
            }
        });

        lyraTEncodeAButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTEncodeAButton.setText("Encode A");
        lyraTEncodeAButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTEncodeAButtonActionPerformed(evt);
            }
        });

        lyraTPlaySideAButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTPlaySideAButton.setText("Play Side A");
        lyraTPlaySideAButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTPlaySideAButtonActionPerformed(evt);
            }
        });

        lyraTEncodeBButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTEncodeBButton.setText("Encode B");
        lyraTEncodeBButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTEncodeBButtonActionPerformed(evt);
            }
        });

        lyraTPlaySideBButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTPlaySideBButton.setText("Play Side B");
        lyraTPlaySideBButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTPlaySideBButtonActionPerformed(evt);
            }
        });

        lyraTStopButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTStopButton.setText("Stop Encode/Play");
        lyraTStopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTStopButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(lyraTPassRadioButton);
        lyraTPassRadioButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTPassRadioButton.setText("Pass");
        lyraTPassRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTPassRadioButtonActionPerformed(evt);
            }
        });

        clearLyraTConsoleButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        clearLyraTConsoleButton.setText("Clear Console");
        clearLyraTConsoleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearLyraTConsoleButtonActionPerformed(evt);
            }
        });

        lyraTStopRawButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTStopRawButton.setText("Stop Getting Line Records");
        lyraTStopRawButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTStopRawButtonActionPerformed(evt);
            }
        });

        lyraTCreateBButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTCreateBButton.setText("Create B");
        lyraTCreateBButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTCreateBButtonActionPerformed(evt);
            }
        });

        jPanel9.setLayout(new java.awt.GridLayout(4, 4));

        buttonGroup2.add(eqRadioButton1);
        eqRadioButton1.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton1.setSelected(true);
        eqRadioButton1.setText("FLAT/OFF");
        eqRadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton1ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton1);

        buttonGroup2.add(eqRadioButton2);
        eqRadioButton2.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton2.setText("ACOUSTIC");
        eqRadioButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton2ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton2);

        buttonGroup2.add(eqRadioButton3);
        eqRadioButton3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton3.setText("ELECTRONIC");
        eqRadioButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton3ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton3);

        buttonGroup2.add(eqRadioButton4);
        eqRadioButton4.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton4.setText("WORLD (LATIN/REGGAE)");
        eqRadioButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton4ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton4);

        buttonGroup2.add(eqRadioButton5);
        eqRadioButton5.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton5.setText("CLASSICAL");
        eqRadioButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton5ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton5);

        buttonGroup2.add(eqRadioButton6);
        eqRadioButton6.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton6.setText("POP");
        eqRadioButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton6ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton6);

        buttonGroup2.add(eqRadioButton7);
        eqRadioButton7.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton7.setText("ROCK");
        eqRadioButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton7ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton7);

        buttonGroup2.add(eqRadioButton8);
        eqRadioButton8.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        eqRadioButton8.setText("BASS BOOST");
        eqRadioButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eqRadioButton8ActionPerformed(evt);
            }
        });
        jPanel9.add(eqRadioButton8);

        jLabel9.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel9.setText("SOUND OUTPUT");
        jPanel9.add(jLabel9);

        buttonGroup3.add(speakerRadioButton);
        speakerRadioButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        speakerRadioButton.setText("SPEAKER");
        speakerRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                speakerRadioButtonActionPerformed(evt);
            }
        });
        jPanel9.add(speakerRadioButton);

        buttonGroup3.add(bluetoothRadioButton);
        bluetoothRadioButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        bluetoothRadioButton.setText("BLUETOOTH");
        bluetoothRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bluetoothRadioButtonActionPerformed(evt);
            }
        });
        jPanel9.add(bluetoothRadioButton);

        bluetoothComboBox.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        bluetoothComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "BT Speaker 1", "BT Speaker 2", "BT Speaker 3" }));
        jPanel9.add(bluetoothComboBox);

        jLabel10.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel10.setText("VOLUME");
        jPanel9.add(jLabel10);

        lyraTVolDownButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTVolDownButton.setText("VOLUME DOWN");
        lyraTVolDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTVolDownButtonActionPerformed(evt);
            }
        });
        jPanel9.add(lyraTVolDownButton);

        lyraTMuteToggleButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTMuteToggleButton.setText("MUTE");
        lyraTMuteToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTMuteToggleButtonActionPerformed(evt);
            }
        });
        jPanel9.add(lyraTMuteToggleButton);

        lyraTVolUpButton.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        lyraTVolUpButton.setText("VOLUME UP");
        lyraTVolUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lyraTVolUpButtonActionPerformed(evt);
            }
        });
        jPanel9.add(lyraTVolUpButton);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lyraTHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(startServerCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 238, Short.MAX_VALUE)
                .addComponent(lyraTConnectButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lyraTDisconnectButton))
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lyraTStopButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lyraTServerTestDBButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lyraTGetInfoButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lyraTGetRawButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(lyraTDecodeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lyraTEncodeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(lyraTPassRadioButton))
                    .addComponent(lyraTStopRawButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(lyraTCreateAButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lyraTPlaySideAButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 103, Short.MAX_VALUE)
                            .addComponent(lyraTEncodeAButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(lyraTPlaySideBButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lyraTEncodeBButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lyraTCreateBButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(clearLyraTConsoleButton))
                    .addComponent(jScrollPane9)
                    .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lyraTHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lyraTConnectButton)
                    .addComponent(lyraTDisconnectButton)
                    .addComponent(jLabel5)
                    .addComponent(startServerCheckBox))
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGap(9, 9, 9)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lyraTDecodeRadioButton)
                            .addComponent(lyraTEncodeRadioButton)
                            .addComponent(jLabel7)
                            .addComponent(lyraTPassRadioButton)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearLyraTConsoleButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(lyraTServerTestDBButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lyraTGetInfoButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lyraTGetRawButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lyraTCreateAButton)
                            .addComponent(lyraTCreateBButton)))
                    .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lyraTEncodeAButton)
                            .addComponent(lyraTEncodeBButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lyraTPlaySideAButton)
                            .addComponent(lyraTPlaySideBButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 135, Short.MAX_VALUE)
                        .addComponent(lyraTStopRawButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lyraTStopButton))
                    .addComponent(jScrollPane9)))
        );

        jTabbedPane1.addTab("ESP32 LyraT", jPanel5);

        consoleTextArea.setColumns(20);
        consoleTextArea.setFont(new java.awt.Font("Monospaced", 0, 24)); // NOI18N
        consoleTextArea.setRows(5);
        consoleTextArea.setText("Output Console:\n");
        jScrollPane8.setViewportView(consoleTextArea);

        clearConsoleButton.setText("Clear Console");
        clearConsoleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearConsoleButtonActionPerformed(evt);
            }
        });

        baudRateButton.setText("Set Baud Rate");
        baudRateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                baudRateButtonActionPerformed(evt);
            }
        });

        baudRateTextField.setText("1200");

        jLabel8.setText("Audio Output: ");

        audioOutputComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Default" }));

        setAudioDownloadServerButton.setText("Set MP3 Server");
        setAudioDownloadServerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setAudioDownloadServerButtonActionPerformed(evt);
            }
        });

        audioDownloadServerTextField.setText("http://");

        filterShuffleCheckBox.setSelected(true);
        filterShuffleCheckBox.setText("Shuffle Filter (min-max) minutes");
        filterShuffleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterShuffleCheckBoxActionPerformed(evt);
            }
        });

        shuffleFilterTextField.setColumns(4);
        shuffleFilterTextField.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        shuffleFilterTextField.setText("0-10");
        shuffleFilterTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shuffleFilterTextFieldActionPerformed(evt);
            }
        });

        jLabel11.setText("Playback Speed");

        playbackSpeedTextField.setText("1.00");

        reloadAudioOutputsButton.setText("Reload");
        reloadAudioOutputsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadAudioOutputsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane8)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(baudRateButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(baudRateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel8))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(filterShuffleCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(shuffleFilterTextField)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(audioOutputComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(reloadAudioOutputsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 39, Short.MAX_VALUE)
                        .addComponent(setAudioDownloadServerButton))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(playbackSpeedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(audioDownloadServerTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(clearConsoleButton))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 383, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filterShuffleCheckBox)
                    .addComponent(shuffleFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11)
                    .addComponent(playbackSpeedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(clearConsoleButton)
                    .addComponent(baudRateButton)
                    .addComponent(baudRateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8)
                    .addComponent(audioOutputComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(setAudioDownloadServerButton)
                    .addComponent(audioDownloadServerTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(reloadAudioOutputsButton)))
        );

        jTabbedPane1.addTab("Setup / Output Console", jPanel7);

        exitButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        exitButton.setText("Exit");
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });

        addAudioDirectoryButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        addAudioDirectoryButton.setText("Add Directory");
        addAudioDirectoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addAudioDirectoryButtonActionPerformed(evt);
            }
        });

        createButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        createButton.setText("Encode");
        createButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createButtonActionPerformed(evt);
            }
        });

        createDownloadButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        createDownloadButton.setText("Encode HTTP");
        createDownloadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createDownloadButtonActionPerformed(evt);
            }
        });

        audioCountLabel.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        audioCountLabel.setText("0 Audio Files");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
            .addGroup(layout.createSequentialGroup()
                .addComponent(addAudioDirectoryButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(audioCountLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(createDownloadButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(createButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exitButton))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jTabbedPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAudioDirectoryButton)
                    .addComponent(createDownloadButton)
                    .addComponent(createButton)
                    .addComponent(exitButton)
                    .addComponent(audioCountLabel)))
        );

        setBounds(0, 0, 1003, 553);
    }// </editor-fold>//GEN-END:initComponents

    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitButtonActionPerformed
        setVisible(false);
        
        // check to see if the cassette play is not null to stop it
        if(cassettePlayer != null) {
            cassettePlayer.stop();
        }
        
        // save the properties file
        cassetteFlow.saveProperties();
        
        // Exit the application
        System.exit(0);
    }//GEN-LAST:event_exitButtonActionPerformed
    
    /**
     * This set the current mp3/flac directory and loads any files found within
     * 
     * @param evt 
     */
    private void addAudioDirectoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addAudioDirectoryButtonActionPerformed
        File audioDir = new File(directoryTextField.getText());
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(audioDir);
        chooser.setDialogTitle("Select Audio Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            directoryTextField.setText(chooser.getSelectedFile().toString());
            String audioDirectory = directoryTextField.getText();
            
            if(!audioDirectory.isEmpty()) {
                currentAudioDirectory = audioDirectory;
                cassetteFlow.loadAudioFiles(audioDirectory, true);
        
                // clear the current JList
                DefaultListModel model = (DefaultListModel) audioJList.getModel();
                model.clear();
                addAudioInfoToJList();
            }
        } else {
            System.out.println("No Selection ");
        }
    }//GEN-LAST:event_addAudioDirectoryButtonActionPerformed

    private void addAudioToTapeListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addAudioToTapeListButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        
        List selectedAudio = audioJList.getSelectedValuesList();
        DefaultListModel model;
        JLabel sideLabel;
        ArrayList<AudioInfo> audioList;
        
        // see which side of the tape we adding sounds to
        if(side == 2) {
            model = (DefaultListModel) sideNJList.getModel();
            sideLabel = sideNLabel;
            audioList = sideNList;
        } else if (side == 1) {
            model = (DefaultListModel) sideBJList.getModel();
            sideLabel = sideBLabel;
            audioList = sideBList;
        } else {
            model = (DefaultListModel) sideAJList.getModel();
            sideLabel = sideALabel;
            audioList = sideAList;
        }

        int count = model.getSize() + 1;
        
        // add the new mp3s or flac
        for(int i = 0; i < selectedAudio.size(); i++) {
            AudioInfo audioInfo = (AudioInfo)selectedAudio.get(i);
            String trackCount = String.format("%02d", (i + count));
            String trackName = "[" + trackCount + "] " + audioInfo;
            model.addElement(trackName);
            audioList.add(audioInfo);
        }
        
        calculateTotalTime(audioList, sideLabel);
    }//GEN-LAST:event_addAudioToTapeListButtonActionPerformed
    
    /**
     * Used to calculate the total playtime of audio files in side A or B
     * based on selected tab for side A or B
     */
    private void calculateTotalTime() {
        int side = tapeJTabbedPane.getSelectedIndex();
        
        JLabel sideLabel;
        ArrayList<AudioInfo> audioList;
        
        if (side == 1) {
            sideLabel = sideBLabel;
            audioList = sideBList;
        } else {
            // default side A
            sideLabel = sideALabel;
            audioList = sideAList;
        }
        
        calculateTotalTime(audioList, sideLabel);
    }
    
    /**
     * Used to calculate the total playtime of audio file in Side A or Side B
     * 
     * @param audioList
     * @param sideLabel 
     */
    private void calculateTotalTime(ArrayList<AudioInfo> audioList, JLabel sideLabel) {
        int muteTime = Integer.parseInt(muteJTextField.getText());
         
        // calculate the total time
        int totalTime = 0;
        for(AudioInfo audioInfo: audioList) {
            totalTime += audioInfo.getLength();
        }
        
        totalTime += (audioList.size() - 1)*muteTime;
        
        String warning = "";
        if(totalTime > getMaxTapeTime()) {
            warning = " (*** Max Time Exceeded ***)";
        }
        
        sideLabel.setText("Play Time: " + CassetteFlowUtil.getTimeString(totalTime) + " " + warning);
    }
    
    private int getMaxTapeTime() {
        int index = tapeLengthComboBox.getSelectedIndex();
        
        switch (index) {
            case 0:
                return 1800;
            case 1:
                return 2700;
            case 2:
                return 3300;
            case 3:
                return 3600;
            default:
                return 7200;
        }
    }
    
    private void directoryTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_directoryTextFieldActionPerformed
        // just call the filter button audio list action
        filterAudioListButtonActionPerformed(null);
    }//GEN-LAST:event_directoryTextFieldActionPerformed

    private void removeAudioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAudioButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        
        JList jlist;
        JLabel sideLabel;
        ArrayList<AudioInfo> audioList;
        
        // see which side of the tape we adding sounds to
        if(side == 1) {
            jlist = sideBJList;
            sideLabel = sideBLabel;
            audioList = sideBList;
        } else {
            jlist = sideAJList;
            sideLabel = sideALabel;
            audioList = sideAList;
        }
        
        // remove a single entry
        int index = jlist.getSelectedIndex();
        
        if(index != -1) {
            audioList.remove(index);
            addTracksToTapeJList(audioList, jlist);
            calculateTotalTime(audioList, sideLabel);
        }
    }//GEN-LAST:event_removeAudioButtonActionPerformed
    
    /**
     * Try playing the indicated mp3 or flac
     * 
     * @param evt 
     */
    private void playButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playButtonActionPerformed
        List selectedAudio = audioJList.getSelectedValuesList();
        
        if(selectedAudio != null && selectedAudio.size() >= 1) {
            double speedFactor = 1.0; 
            try {
                speedFactor = Double.parseDouble(playbackSpeedTextField.getText());
            } catch(NumberFormatException nfe) {}
            
            final AudioInfo audioInfo = (AudioInfo) selectedAudio.get(0);
            System.out.println("\nPlaying Audio: " + audioInfo  + " / Speed: "  + speedFactor);
            
            // make sure we stop any previous threads
            if(player != null) {
                player.stop();
            } else {
                player = new StreamPlayer();
            }
            
            try {
                String outputMixerName = audioOutputComboBox.getSelectedItem().toString();
                System.out.println("Mixer: " + outputMixerName);
                
                playing = true;
                player.setMixerName(outputMixerName);
                player.setSpeedFactor(speedFactor);
                player.open(audioInfo.getFile());
                player.play();
                
                playButton.setEnabled(false);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error playing audio file");
            }
            
            // start thread to keep track of if we playing sound
            if(playerThread == null) {
                playerThread = new Thread(() -> {
                    try {
                        int stopCount = 0;
                        
                        while(playing) {
                            if(player.isStopped()) {
                                stopCount++;
                                if(stopCount > 10) {
                                    System.out.println("Player Auto Stopped ...");
                                    playing = false;
                                    break;
                                }
                            } else {
                                stopCount = 0;
                            }
                            
                            Thread.sleep(1000);
                        }
                    } catch(InterruptedException ex) {}
                    
                    playButton.setEnabled(true);
                });
                playerThread.start();
            }
        }
        
    }//GEN-LAST:event_playButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        if(player != null) {
            player.stop();
            player.reset();
            
            if(playing) {
                playing = false;
                playButton.setEnabled(true);
            }
        }
        
        // check to see if we not doing a direct encode to stop that
        if(realTimeEncoding) {
            if(lyraTConnect == null) {
                cassetteFlow.stopEncoding();
                realTimeEncoding = false;
            } else {
                realTimeEncoding = false;
                lyraTStopButtonActionPerformed(null);
            }
        }
        
        // see if to stop playing a side of the tape either locally or on the LyraT board
        if (playSide) {
            playSide = false;
        }
    }//GEN-LAST:event_stopButtonActionPerformed

    private void clearSelectionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearSelectionButtonActionPerformed
        audioJList.clearSelection();
    }//GEN-LAST:event_clearSelectionButtonActionPerformed

    private void removeAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAllButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        
        DefaultListModel model;
        JList jlist;
        JLabel sideLabel;
        
        // see which side of the tape we removing sounds to
        if(side == 1) {
            jlist = sideBJList;
            sideLabel = sideBLabel;
            sideBList = new ArrayList<>();
        } else {
            jlist = sideAJList;
            sideLabel = sideALabel;
            sideAList = new ArrayList<>();
        }
        
        // remove a single entry
        model = (DefaultListModel) jlist.getModel();
        model.clear();
        
        sideLabel.setText("Play Time: empty list ..." );
    }//GEN-LAST:event_removeAllButtonActionPerformed

    private void shuffleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shuffleButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        int muteTime = Integer.parseInt(muteJTextField.getText());
        int maxTime = getMaxTapeTime();
        
        // check to see if we should limit the size of mp3s to add to shuffle
        // this will be set to mp3s of 600 seconds (10 minutes) or less if selected
        boolean filterShuffle = filterShuffleCheckBox.isSelected();
        String filterRange = shuffleFilterTextField.getText();
        
        DefaultListModel model;
        JLabel sideLabel;
        ArrayList<AudioInfo> audioList;
        
        // see which side of the tape we adding sounds to
        if(side == 1) {
            model = (DefaultListModel) sideBJList.getModel();
            sideLabel = sideBLabel;
            sideBList = new ArrayList<>();
            audioList = sideBList;
        } else {
            model = (DefaultListModel) sideAJList.getModel();
            sideLabel = sideALabel;
            sideAList = new ArrayList<>();
            audioList = sideAList;
        }
        
        // clear out the jlist model
        model.clear();
        
        // get a shuffle list of mp3s/flac
        ArrayList<AudioInfo> shuffledAudio = cassetteFlow.shuffleAudioList();
        if(filteredAudioList != null) {
            shuffledAudio = cassetteFlow.shuffleAudioList(filteredAudioList);
        }
        
        int currentTime = 0;
        int totalTime = 0;
        int trackCount = 1;
        for(int i = 0; i < shuffledAudio.size(); i++) {
            AudioInfo audioInfo = shuffledAudio.get(i);
            
            // check to see if to exclude this audio files if it's not within the specific range
            if(filterShuffle && !CassetteFlowUtil.withinFilterRange(audioInfo.getLength(), filterRange)) continue;
            
            // check to make sure we not duplicating mp3/flac on the A and B sides
            if(side == 0) {
                if(sideBList.contains(audioInfo)) {
                    System.out.println("\nDuplicate Audio On Side B : " + audioInfo);
                    continue;
                }
            } else {
                if(sideAList.contains(audioInfo)) {
                    System.out.println("\nDuplicate Audio On Side A : " + audioInfo);
                    continue;
                }
            }
            
            currentTime += audioInfo.getLength();
            
            int timeWithMute = currentTime + muteTime*i;
            if(timeWithMute <= maxTime) {
                String trackCountString = String.format("%02d", trackCount);
                String trackName = "[" + trackCountString + "] " + audioInfo;
                model.addElement(trackName);
                audioList.add(audioInfo);
                totalTime = timeWithMute;
                trackCount++;
            } else {
                break;
            }
        }
        
        sideLabel.setText("Play Time: " + CassetteFlowUtil.getTimeString(totalTime));
    }//GEN-LAST:event_shuffleButtonActionPerformed
    
    private void createButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createButtonActionPerformed
        if(lyraTConnect != null) {
            lyraTCreateAndEncodeInputFiles();
        } else if (directEncodeCheckBox.isSelected()) {
            directEncode(false);
        } else {
            try {
                int muteTime = Integer.parseInt(muteJTextField.getText());
                String saveDirectoryName = CassetteFlow.AUDIO_DIR_NAME + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
                String tapeID = checkInputLength(tapeIDTextField.getText(),4);
                
                if(tapeID != null) {
                    cassetteFlow.createInputFiles(saveDirectoryName, tapeID, sideAList, sideBList, muteTime, false);
                }
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
    }//GEN-LAST:event_createButtonActionPerformed

    private void playSideButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playSideButtonActionPerformed
        final int side = tapeJTabbedPane.getSelectedIndex();
        final String tapeID = tapeIDTextField.getText();
        final int muteTime = Integer.parseInt(muteJTextField.getText());
        
        System.out.println("\nPlaying Side " + side);

        // disable some buttons in the UI
        playButton.setEnabled(false);
        playSideButton.setEnabled(false);
        moveTrackUpButton.setEnabled(false);
        moveTrackDownButton.setEnabled(false);
        realtimeEncodeButton.setEnabled(false);
        createDownloadButton.setEnabled(false);
        createButton.setEnabled(false);
        playSide = true;
        
        // if we connected to the lyraT board play the side through there
        if(lyraTConnect != null) {
            lyraTCreateAndPlayInputFiles(side, muteTime);
            return;
        }
        
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
        } else {
            player = new StreamPlayer();
        }
        
        playerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<AudioInfo> audioList;
                String sideString;
                JLabel trackLabel;
                JList jlist;
                
                if(side == 0) {
                    audioList = sideAList;
                    sideString = "A";
                    jlist = sideAJList;
                    trackLabel = trackALabel;
                } else {
                    audioList = sideBList;
                    sideString = "B";
                    jlist = sideBJList;
                    trackLabel = trackBLabel;
                }
                
                // save to the tape database
                cassetteFlow.addToTapeDB(tapeID, sideAList, sideBList, true);
                
                // play the sound file now MP3 or FLAC
                try {
                    int track = 1;
                    
                    for(AudioInfo audioInfo: audioList) {
                        // check to see if playback was stopped
                        if(!playSide) {
                            player.stop();
                            break;
                        }
                        
                        jlist.setSelectedIndex(track -1);
                        //trackLabel.setText("Playing Track: " + String.format("%02d", track));
                        System.out.println("Playing " + audioInfo + " on side: " + sideString);
                        
                        // reset the player to free up resources here
                        player.reset();
                        
                        player.open(audioInfo.getFile());
                        player.play(); 
                        
                        // wait for playback to stop
                        int loopCount = 0;
                        while(player.isPlaying()) {
                            //update display every second
                            if(loopCount%10 == 0) {
                                int playTime = loopCount/10;
                                String message = "Playing Track: " + String.format("%02d", track) + 
                                        " (" + CassetteFlowUtil.getTimeString(playTime) + ")";
                                trackLabel.setText(message);
                            }
                            
                            loopCount++;
                            Thread.sleep(100);
                        }
                        
                        // pause a certain amount of time to create a mute section
                        Thread.sleep(muteTime*1000);
                        track++;
                    }
                                        
                    // re-enable the play side button and other buttons
                    playButton.setEnabled(true);
                    playSideButton.setEnabled(true);
                    moveTrackUpButton.setEnabled(true);
                    moveTrackDownButton.setEnabled(true);
                    realtimeEncodeButton.setEnabled(true);
                    createDownloadButton.setEnabled(true);
                    createButton.setEnabled(true);
                    
                    trackLabel.setText("");
                    playSide = false;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Error playing mp3 file");
                }
            }
        }
        );
        playerThread.start();
    }//GEN-LAST:event_playSideButtonActionPerformed
    
    /**
     * Clear the audio from the list
     * 
     * @param evt 
     */
    private void clearAudioListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearAudioListButtonActionPerformed
        DefaultListModel model = (DefaultListModel) audioJList.getModel();
        model.clear();
        
        // remove records from the list and hash map
        cassetteFlow.audioInfoList.clear();
        cassetteFlow.audioInfoDB.clear();
    }//GEN-LAST:event_clearAudioListButtonActionPerformed

    private void createDownloadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createDownloadButtonActionPerformed
        if(lyraTConnect != null) {
            System.out.println("Creation of Download Input Files Not supported on LyraT ...");
        } else if (directEncodeCheckBox.isSelected()) {
            directEncode(true);
        } else {
            // just generate the input text files then minimodem can be ran seperately
            try {
                int muteTime = Integer.parseInt(muteJTextField.getText());
                String saveDirectoryName = directoryTextField.getText() + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
                String tapeID = checkInputLength(tapeIDTextField.getText(),4);
                
                if(tapeID != null) {
                    cassetteFlow.createInputFiles(saveDirectoryName, tapeID, sideAList, sideBList, muteTime, true);
                }
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
    }//GEN-LAST:event_createDownloadButtonActionPerformed

    private void stopDecodeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopDecodeButtonActionPerformed
        // check to see if we connected to the lyraT board
        if(lyraTConnect != null) {
            lyraTGetDecode = false;
            startDecodeButton.setEnabled(true);
        }
        
        if(cassettePlayer != null) {
            cassettePlayer.stop();
            startDecodeButton.setEnabled(true);
        }
        
        // stop the thread which keeps track of stop records
        trackStopRecords = false;
        
        tapeInfoTextArea.setText("");
        trackInfoTextArea.setText("");
        playbackInfoTextArea.setText("Decoding process stopped ...");
    }//GEN-LAST:event_stopDecodeButtonActionPerformed

    private void startDecodeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startDecodeButtonActionPerformed
        // check to see if we connected to the lyraT board
        if(lyraTConnect != null) {
            // set the lyraT to decode if not done so already
            //lyraTConnect.setModeDecode();
            lyraTGetDecodeRecord();
            startDecodeButton.setEnabled(false);
            return;
        }
        
        String logfile = logfileTextField.getText();
        
        if(cassettePlayer != null) cassettePlayer.stop();
        
        // get the playback speed factor
        double speedFactor = 1.0;
        try {
            speedFactor = Double.parseDouble(playbackSpeedTextField.getText());
        } catch (NumberFormatException nfe) { }
        
        // get the mixer to output the audio two
        String outputMixerName = audioOutputComboBox.getSelectedItem().toString();
        
        cassettePlayer = new CassettePlayer(this, cassetteFlow, logfile);
        cassettePlayer.setMixerName(outputMixerName);
        cassettePlayer.setSpeedFactor(speedFactor);
        
        if(directDecodeCheckBox.isSelected()) {
            try {
                // get the delay used to synchronized the playback time indicated
                // from the data on cassette vs the actual playback time from mp3
                int delay = Integer.parseInt(mmdelayTextField.getText());
                
                cassettePlayer.startMinimodem(delay);
            } catch (Exception ex) {
                String message = "Error Decoding With Minimodem";
                    JOptionPane.showMessageDialog(this, message, "Minimodem Error", JOptionPane.ERROR_MESSAGE);
                Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            cassettePlayer.startLogTailer();
        }

        playbackInfoTextArea.setText("Starting decoding process ...\n");
        startDecodeButton.setEnabled(false);
    }//GEN-LAST:event_startDecodeButtonActionPerformed
    
    private void lyraTGetDecodeRecord() {
        // reset global variable that keep track of track information from lyraT
        lyraTDataErrors = 0;
        lyraTStartTime = 0;
        lyraTCurrentTapeId = "";
        lyraTCurrentAudioId = "";
        lyraTMuteRecords = 0;
        lyraTAudioTotalPlayTime = 0;
        lyraTCurrentPlayTime = 0;
        lyraTAudioFilename = "";
        
        lyraTGetDecode = true;
        
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int stopCount = 0;
                    
                    while(lyraTGetDecode) {
                        String response = lyraTConnect.getInfo();
                        String[] sa = response.split(" ");
                        
                        printToConsole(response, true);
                        
                        if(sa.length > 1 && sa[1].contains("_")) {
                            stopCount = 0;
                            processLineRecord(sa[1]);
                        } else {
                            stopCount++;
                            lyraTCurrentAudioId = "";
                            lyraTCurrentPlayTime = -1;
                            
                            String stopMessage = "Playback Stopped {# errors " + lyraTDataErrors + "} ...";
                            if(stopCount == 1) {
                                setPlaybackInfo(stopMessage, false);
                            } else {
                                setPlaybackInfo(stopMessage, true);
                            }
                        }
                    
                        Thread.sleep(1000);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "LyraT Read Data Error ...", "LyraT Error", JOptionPane.ERROR_MESSAGE);
                    stopDecodeButtonActionPerformed(null);
                    ex.printStackTrace();
                }
            }
        }
        );
        thread.start();
    }
    
    /**
     * Add audio tracks to a tape side
     * 
     * @param audioList
     * @param jlist 
     */
    private void addTracksToTapeJList(ArrayList<AudioInfo> audioList, JList jlist) {
        DefaultListModel model = (DefaultListModel) jlist.getModel();
        model.clear();
        
        int trackCount = 1;

        for(AudioInfo audioInfo: audioList) {     
            String trackName = "[" + String.format("%02d", trackCount) + "] " + audioInfo;
            model.addElement(trackName);
            trackCount++;
        }
    }
    
    /**
     * Direction to move the track. Zero, move track down 1 move track up
     * @param direction 0 for down and 1 for up
     */
    private void moveTrackPosition(int direction) {
        int side = tapeJTabbedPane.getSelectedIndex();
        
        JList jlist;
        ArrayList<AudioInfo> audioList;
        
        // see which side of the tape we adding sounds to
        if(side == 1) {
            jlist = sideBJList;
            audioList = sideBList;
        } else {  //Assume Side A
            jlist = sideAJList;
            audioList = sideAList;
        }
        
        int index = jlist.getSelectedIndex();
        
        if(direction == 1) { // move the track up the list
            if(index >= 1) {
                Collections.swap(audioList, index, index - 1);
                addTracksToTapeJList(audioList, jlist);
                jlist.setSelectedIndex(index - 1);
            }
        } else { // move it down the list
            if(index != -1 && index < (audioList.size() - 1)) {
                Collections.swap(audioList, index, index + 1);
                addTracksToTapeJList(audioList, jlist);
                jlist.setSelectedIndex(index + 1);
            }
        }
    }
    
    /**
     * Move the track up by one if not the first track
     * 
     * @param evt 
     */
    private void moveTrackUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveTrackUpButtonActionPerformed
        moveTrackPosition(1);
    }//GEN-LAST:event_moveTrackUpButtonActionPerformed
    
    /**
     * Move the track down by one position of not the first item
     * 
     * @param evt 
     */
    private void moveTrackDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveTrackDownButtonActionPerformed
        moveTrackPosition(0);
    }//GEN-LAST:event_moveTrackDownButtonActionPerformed

    private void mmdelayTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mmdelayTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_mmdelayTextFieldActionPerformed

    private void startServerCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startServerCheckBoxActionPerformed
        if(startServerCheckBox.isSelected()) {
            try {
                cassetteFlowServer = new CassetteFlowServer();
                cassetteFlowServer.setCassetteFlow(cassetteFlow);
                
                lyraTConsoleTextArea.append("Local Cassette Flow Server Started ...\n\n");
            } catch (IOException ex) {
                Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            if(cassetteFlowServer != null) {
                cassetteFlowServer.stop();
                cassetteFlowServer = null;
            }
        }
    }//GEN-LAST:event_startServerCheckBoxActionPerformed

    private void lyraTDisconnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTDisconnectButtonActionPerformed
        if(lyraTConnect != null) {
            lyraTConnect = null;
            lyraTConsoleTextArea.append("Disconnected From Cassette Flow Server\n\n");
            
            // reaload the default tape databases and update the UI with loaded information
            cassetteFlow.init();
            updateUI();
            
            // re-enabled the disable buttons
            playButton.setEnabled(true);
            realtimeEncodeButton.setEnabled(true);
            createDownloadButton.setEnabled(true);
        }
    }//GEN-LAST:event_lyraTDisconnectButtonActionPerformed

    private void lyraTConnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTConnectButtonActionPerformed
        if(lyraTConnect == null) {
            String host = lyraTHostTextField.getText().trim();
            if(!host.endsWith("/")) {
                host += "/";
            } 
            
            lyraTConnect = new ESP32LyraTConnect(host);
            
            // try getting informtion to see if we connected
            String info = lyraTConnect.getInfo();
            if(info != null) {
                lyraTConsoleTextArea.append("Connected to CassetteFlow Server @ " + host + "\n\n");
                
                // clear the UI and databases
                clearAudioListButtonActionPerformed(null);
                
                // load the database from LyraT
                loadLyraTDatabases();
                
                // save the host
                cassetteFlow.setLyraTHost(host);
                
                // update the UI to indicate we are connect to the remote lyraT host
                addAudioInfoToJList();
                
                lyraTConsoleTextArea.append("Loaded Audio Database ...\n\nLoaded Tape Database ...\n\n");
                
                currentAudioDirectory = "ESP32LyraT @ " + cassetteFlow.LYRA_T_HOST;
                directoryTextField.setText(currentAudioDirectory);
                
                // disable buttons which should not work when connected to LyraT
                playButton.setEnabled(false);
                realtimeEncodeButton.setEnabled(false);
                createDownloadButton.setEnabled(false);
            } else {
                lyraTConsoleTextArea.append("Error Connecting to CassetteFlow Server @ " + host + "\n\n");
                lyraTConnect= null;
            }
        }
    }//GEN-LAST:event_lyraTConnectButtonActionPerformed
    
    /**
     * Load the database from lyraT
     */
    private void loadLyraTDatabases() {
        try {
            if(lyraTConnect == null) return;
            
            String audioDBText = lyraTConnect.getAudioDB();
            cassetteFlow.createAudioInfoDBFromString(audioDBText);
            
            String tapeDBText = lyraTConnect.getTapeDB();
            cassetteFlow.createTapeDBFromString(tapeDBText);
        } catch (Exception ex) {
            Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void viewTapeDBButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewTapeDBButtonActionPerformed
        TapeDatabaseFrame tapeDBFrame = new TapeDatabaseFrame();
        boolean remoteDB = false;
        
        if(lyraTConnect == null) {
            tapeDBFrame.setTitle("Tape Database (" + CassetteFlow.TAPE_DB_FILENAME + ")");
        } else {
            tapeDBFrame.setTitle("Tape Database LyraT@" + cassetteFlow.LYRA_T_HOST);
            remoteDB = true;
        }
        
        tapeDBFrame.setCassetteFlowFrame(this, remoteDB);
        
        tapeDBFrame.setTapeDB(cassetteFlow.tapeDB);
        tapeDBFrame.setAudioInfoDB(cassetteFlow.audioInfoDB);
        
        tapeDBFrame.setVisible(true);
    }//GEN-LAST:event_viewTapeDBButtonActionPerformed

    private void clearConsoleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearConsoleButtonActionPerformed
        consoleTextArea.setText("Output Console >\n");
    }//GEN-LAST:event_clearConsoleButtonActionPerformed
    
    private void lyraTServerTestDBButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTServerTestDBButtonActionPerformed
        String host = lyraTHostTextField.getText();
        
        lyraTConsoleTextArea.setText("Testing LyraT Server @ " + host + "\n\n");
        
        ESP32LyraTConnect testLyraT = new ESP32LyraTConnect(host);
        try {
            lyraTConsoleTextArea.append(testLyraT.runTest());
        } catch(Exception ce) {
            lyraTConsoleTextArea.setText("Connection Failed\n\n" + ce.getMessage());    
        }
    }//GEN-LAST:event_lyraTServerTestDBButtonActionPerformed

    private void lyraTDecodeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTDecodeRadioButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.setModeDecode();
        lyraTConsoleTextArea.append("Set Decode Mode >> " + response + "\n");
    }//GEN-LAST:event_lyraTDecodeRadioButtonActionPerformed

    private void lyraTEncodeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTEncodeRadioButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.setModeEncode();
        lyraTConsoleTextArea.append("Set Encode Mode >> " + response + "\n");
    }//GEN-LAST:event_lyraTEncodeRadioButtonActionPerformed

    private void lyraTPassRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTPassRadioButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.setModePass();
        lyraTConsoleTextArea.append("Set Pass Through Mode >> " + response + "\n");
    }//GEN-LAST:event_lyraTPassRadioButtonActionPerformed

    private void lyraTGetInfoButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTGetInfoButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.getInfo();
        lyraTConsoleTextArea.append(response + "\n");
    }//GEN-LAST:event_lyraTGetInfoButtonActionPerformed

    private void lyraTGetRawButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTGetRawButtonActionPerformed
        if(lyraTConnect == null) return;
        lyraTReadLineRecords = true;
                
        // start the thread to read data from the lyraT
        lyraTGetRawButton.setEnabled(false);
        lyraTConsoleTextArea.append("\nReading of line records started ...\n\n");

        lyraTConnect.getRawData(this);
    }//GEN-LAST:event_lyraTGetRawButtonActionPerformed

    private void lyraTCreateAButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTCreateAButtonActionPerformed
        if(lyraTConnect == null) return;
        lyraTCreateInputFiles(sideAList, null);
    }//GEN-LAST:event_lyraTCreateAButtonActionPerformed
    
    private void lyraTCreateInputFiles(ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB) {
        int muteTime = Integer.parseInt(muteJTextField.getText());
        String tapeID = checkInputLength(tapeIDTextField.getText(), 4);
        String[] tapeLength = tapeLengthComboBox.getSelectedItem().toString().split(" ");
        
        if (tapeID != null) {    
            String response = lyraTConnect.createInputFiles(tapeLength[0], tapeID, sideA, sideB, "" + muteTime);
            
            if(!response.equals("ERROR")) {
                lyraTConsoleTextArea.append("\n" + response + "\n");
                cassetteFlow.addToTapeDB(tapeID, sideA, sideB, false);
            } else {
                String message = "Error Creating LyraT Input Data";
                JOptionPane.showMessageDialog(this, message, "LyraT Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Create and encode the input file on lyraT
     */
    private void lyraTCreateAndEncodeInputFiles() {
        int side = tapeJTabbedPane.getSelectedIndex();
        int muteTime = Integer.parseInt(muteJTextField.getText());
        
        // set the lyraT into encode mode
        lyraTEncodeRadioButton.doClick();
        
        // depending on the side encode the data
        JList sideJList;
        JLabel infoLabel;
        ArrayList<AudioInfo> sideList;
        
        if(side == 0) { // encode A side
            lyraTCreateAButtonActionPerformed(null);
            lyraTEncodeAButtonActionPerformed(null);
            sideJList = sideAJList;
            infoLabel = trackALabel;
            sideList = sideAList;
        } else { // encde B side
            lyraTCreateBButtonActionPerformed(null);
            lyraTEncodeBButtonActionPerformed(null);
            sideJList = sideBJList;
            infoLabel = trackBLabel;
            sideList = sideBList;
        }
        
        // start the thread which will continous get data from the 
        // server to track the encoding process
        // start the swing timer to show how long the encode is running for
        createButton.setEnabled(false);
        
        realTimeEncoding = true;
        encodeSeconds = 0;
        int[] timeForTracks = CassetteFlowUtil.getTimeForTracks(sideList, muteTime);
        
        final Timer timer = new Timer(1000, (ActionEvent e) -> {
            encodeSeconds++;
            String timeString = CassetteFlowUtil.getTimeString(encodeSeconds);
            infoLabel.setText("Encode Timer: " + timeString);
            
            // get information from the lyraT server
            String response = lyraTConnect.getInfo();
            
            if(response.equals("encoded completed")) {
                System.out.println("Encoding Completed @ " + timeString);
                realTimeEncoding = false;
            } else if(!response.contains("stopped")){
                //System.out.println("LyraT Response: " + response);
                String[] data = response.split(" ");
                int time = Integer.parseInt(data[2]);

                if (time % 10 == 0) {
                    int track = CassetteFlowUtil.getTrackFromTime(timeForTracks, time);
                    if (track > currentTrackIndex) {
                        sideJList.setSelectedIndex(currentTrackIndex);
                        currentTrackIndex++;
                    }
                }
            }
            
            // see if to exit
            if(!realTimeEncoding) {
                infoLabel.setText("");
                createButton.setEnabled(true);
                currentTrackIndex = 0;
                
                Timer callingTimer = (Timer)e.getSource();
                callingTimer.stop();
            }
        });
        timer.start();
    }
    
    /**
     * Create and playback the input file on lyraT
     */
    private void lyraTCreateAndPlayInputFiles(int side, int muteTime) {        
        // depending on the side encode the data
        JList sideJList;
        JLabel infoLabel;
        ArrayList<AudioInfo> sideList;
        
        if(side == 0) { // create a input file for A side
            lyraTCreateAButtonActionPerformed(null);
            lyraTPlaySideAButtonActionPerformed(null);
            sideJList = sideAJList;
            infoLabel = trackALabel;
            sideList = sideAList;
        } else { // create input file for B side
            lyraTCreateBButtonActionPerformed(null);
            lyraTPlaySideBButtonActionPerformed(null);
            sideJList = sideBJList;
            infoLabel = trackBLabel;
            sideList = sideBList;
        }
        
        // start the thread which will continous get data from the 
        // server to track the playing process
        // start the swing timer to show how long the encode is running for
        playSeconds = 0;
        int[] timeForTracks = CassetteFlowUtil.getTimeForTracks(sideList, muteTime);
        
        final Timer timer = new Timer(1000, (ActionEvent e) -> {
            playSeconds++;
            String timeString = CassetteFlowUtil.getTimeString(playSeconds);
            infoLabel.setText("Play Timer: " + timeString);
            
            // get information from the lyraT server
            String response = lyraTConnect.getInfo();
            
            if(!response.contains("stopped")){
                //System.out.println("LyraT Response: " + response);
                String[] data = response.split(" ");
                int time = Integer.parseInt(data[2]);

                if (time % 10 == 0) {
                    int track = CassetteFlowUtil.getTrackFromTime(timeForTracks, time);
                    if (track > currentTrackIndex) {
                        sideJList.setSelectedIndex(currentTrackIndex);
                        currentTrackIndex++;
                    }
                }
            } else {
                System.out.println("Playback Completed @ " + timeString);
                playSide = false;
            }
            
            // see if to exit
            if(!playSide) {
                // stop playback on the lyraT board
                lyraTStopButtonActionPerformed(null);
                
                infoLabel.setText("");
                playSideButton.setEnabled(true);
                moveTrackUpButton.setEnabled(true);
                moveTrackDownButton.setEnabled(true);
                
                currentTrackIndex = 0;
                
                Timer callingTimer = (Timer)e.getSource();
                callingTimer.stop();
            }
        });
        timer.start();
    }
    
    public void printToLyraTConsole(String text, boolean append) {
        SwingUtilities.invokeLater(() -> {
            if(!append) {
                lyraTConsoleTextArea.setText(text + "\n");
            } else {
                lyraTConsoleTextArea.append(text + "\n");
            }
        });
    }
    
    /**
     * Set the default directory
     * @param evt 
     */
    private void defaultButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultButtonActionPerformed
        File file = new File(directoryTextField.getText());
        
        if(file.exists()) {
            cassetteFlow.setDefaultAudioDirectory(directoryTextField.getText());
            cassetteFlow.saveProperties();
        }
    }//GEN-LAST:event_defaultButtonActionPerformed

    private void lyraTEncodeAButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTEncodeAButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.start("A");
        lyraTConsoleTextArea.append("Encoding Side A >> " + response + "\n");
    }//GEN-LAST:event_lyraTEncodeAButtonActionPerformed

    private void lyraTEncodeBButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTEncodeBButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.start("B");
        lyraTConsoleTextArea.append("Encoding Side B >> " + response + "\n");
    }//GEN-LAST:event_lyraTEncodeBButtonActionPerformed

    private void lyraTPlaySideAButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTPlaySideAButtonActionPerformed
        if(lyraTConnect == null) return;
        
        buttonGroup1.clearSelection();
        
        String response = lyraTConnect.play("A");
        lyraTConsoleTextArea.append("Playing Side A >> " + response + "\n");
    }//GEN-LAST:event_lyraTPlaySideAButtonActionPerformed

    private void lyraTPlaySideBButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTPlaySideBButtonActionPerformed
        if(lyraTConnect == null) return;
        
        buttonGroup1.clearSelection();
        
        String response = lyraTConnect.play("B");
        lyraTConsoleTextArea.append("Playing Side B >> " + response + "\n");
    }//GEN-LAST:event_lyraTPlaySideBButtonActionPerformed

    private void lyraTStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTStopButtonActionPerformed
        if(lyraTConnect == null) return;
        
        String response = lyraTConnect.stop();
        lyraTConsoleTextArea.append("Stopping encoding/playing >> " + response + "\n");
    }//GEN-LAST:event_lyraTStopButtonActionPerformed
    
    /**
     * Do real time encoding of data
     * 
     * @param evt 
     */
    private void realtimeEncodeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_realtimeEncodeButtonActionPerformed
        realTimeEncoding = true;
        directEncode(false, true);
    }//GEN-LAST:event_realtimeEncodeButtonActionPerformed

    private void baudRateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_baudRateButtonActionPerformed
        try {
            String baud = baudRateTextField.getText().trim();
            Integer.parseInt(baud);
            cassetteFlow.BAUDE_RATE = baud;
        } catch(NumberFormatException nfe) { }
    }//GEN-LAST:event_baudRateButtonActionPerformed
    
    private void setAudioDownloadServerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setAudioDownloadServerButtonActionPerformed
        String mp3DownloadServer = audioDownloadServerTextField.getText();
        cassetteFlow.setDownloadServer(mp3DownloadServer);        
    }//GEN-LAST:event_setAudioDownloadServerButtonActionPerformed
    
    private void clearLyraTConsoleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearLyraTConsoleButtonActionPerformed
        lyraTConsoleTextArea.setText("");
    }//GEN-LAST:event_clearLyraTConsoleButtonActionPerformed

    private void lyraTStopRawButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTStopRawButtonActionPerformed
        if(lyraTConnect == null) return;
        
        lyraTConnect.stopRawDataRead();
        lyraTReadLineRecords = false;
        lyraTGetRawButton.setEnabled(true);
        
        lyraTConsoleTextArea.append("\nReading of line records stopped ...\n");
    }//GEN-LAST:event_lyraTStopRawButtonActionPerformed

    private void lyraTCreateBButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTCreateBButtonActionPerformed
        if(lyraTConnect == null) return;
        lyraTCreateInputFiles(null, sideBList);
    }//GEN-LAST:event_lyraTCreateBButtonActionPerformed

    private void eqRadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton1ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.FLAT);
    }//GEN-LAST:event_eqRadioButton1ActionPerformed

    private void eqRadioButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton2ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.ACOUSTIC);
    }//GEN-LAST:event_eqRadioButton2ActionPerformed

    private void eqRadioButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton3ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.ELECTRONIC);
    }//GEN-LAST:event_eqRadioButton3ActionPerformed

    private void eqRadioButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton4ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.WORLD);
    }//GEN-LAST:event_eqRadioButton4ActionPerformed

    private void eqRadioButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton5ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.CLASSICAL);
    }//GEN-LAST:event_eqRadioButton5ActionPerformed

    private void eqRadioButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton6ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.POP);
    }//GEN-LAST:event_eqRadioButton6ActionPerformed

    private void eqRadioButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton7ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.ROCK);
    }//GEN-LAST:event_eqRadioButton7ActionPerformed

    private void eqRadioButton8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eqRadioButton8ActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.setEQ(EQConstants.BASS_BOOST);
    }//GEN-LAST:event_eqRadioButton8ActionPerformed

    private void speakerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_speakerRadioButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_speakerRadioButtonActionPerformed

    private void audioJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_audioJListValueChanged
        int firstIndex = evt.getFirstIndex();

        if (firstIndex >= 0 && !evt.getValueIsAdjusting()) {
            if(!playSide && player != null && player.isPlaying()) {
                playButtonActionPerformed(null);
            }
            
            List selectedAudio = audioJList.getSelectedValuesList();
            if(selectedAudio.size() != 0) {
                AudioInfo audioInfo = (AudioInfo) selectedAudio.get(0);
                String message = audioInfo.getHash10C() + ": " + audioInfo.getName();
                printToConsole(message, true);
            }
        }
    }//GEN-LAST:event_audioJListValueChanged

    private void tapeLengthComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tapeLengthComboBoxActionPerformed
        calculateTotalTime();
    }//GEN-LAST:event_tapeLengthComboBoxActionPerformed

    private void filterShuffleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterShuffleCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_filterShuffleCheckBoxActionPerformed

    private void shuffleFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shuffleFilterTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_shuffleFilterTextFieldActionPerformed

    private void lyraTMuteToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTMuteToggleButtonActionPerformed
        if(lyraTConnect == null) return;
        
        if(lyraTMuteToggleButton.isSelected()) {
            lyraTConnect.mute();
            System.out.println("Button pressed ...");
        } else {
            lyraTConnect.mute();
            System.out.println("Button unpressed ...");
        }
    }//GEN-LAST:event_lyraTMuteToggleButtonActionPerformed

    private void bluetoothRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bluetoothRadioButtonActionPerformed
        if(lyraTConnect == null) return;
        
    }//GEN-LAST:event_bluetoothRadioButtonActionPerformed

    private void lyraTVolDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTVolDownButtonActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.changeVolume("-10");
    }//GEN-LAST:event_lyraTVolDownButtonActionPerformed

    private void lyraTVolUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lyraTVolUpButtonActionPerformed
        if(lyraTConnect == null) return;
        lyraTConnect.changeVolume("10");
    }//GEN-LAST:event_lyraTVolUpButtonActionPerformed
    
    /**
     * A very basic method to filter the list of mp3
     */
    private void filterAudioListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterAudioListButtonActionPerformed
        String filterText = directoryTextField.getText().toLowerCase().trim();
        DefaultListModel filteredModel = new DefaultListModel();
        
        if(!filterText.isEmpty()) {
            filteredAudioList = new ArrayList<>();
            
            for (AudioInfo audioInfo: cassetteFlow.audioInfoList) {
                try {
                    String ai = audioInfo.toString().toLowerCase();
                    if(ai.contains(filterText)) {
                        System.out.println("Audio File Match: " + audioInfo);
                        filteredModel.addElement(audioInfo);
                        filteredAudioList.add(audioInfo);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        
            audioJList.setModel(filteredModel);
        
            if(lyraTConnect == null) {
                audioCountLabel.setText(filteredModel.size() + " Audio files loaded (F)");
            } else {
                audioCountLabel.setText(filteredModel.size() + " LyraT files loaded (F)");
            }
        } else {
            // just add the empty model and call method to load all the audio files
            filteredAudioList = null;
            directoryTextField.setText(currentAudioDirectory);
            audioJList.setModel(filteredModel);
            addAudioInfoToJList();
        }
    }//GEN-LAST:event_filterAudioListButtonActionPerformed
    
    /**
     * Reload the audio output devices
     * 
     * @param evt 
     */
    private void reloadAudioOutputsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadAudioOutputsButtonActionPerformed
        loadAudioOuputDevices();
    }//GEN-LAST:event_reloadAudioOutputsButtonActionPerformed
    
    /**
     * Check the track list in the A/B sides for duplicates
     * 
     * @param evt 
     */
    private void checkTrackListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkTrackListButtonActionPerformed
        String duplicates = "";
        int duplicateCount = 0;
        int totalTracks = 0;
        
        // use a set for quick way to spot the duplicates since a set does
        // not allow duplicates
        Set<AudioInfo> trackListSet = new HashSet<>();
        
        if(sideAList != null) {
            for (AudioInfo audioInfo : sideAList) {
                if (!trackListSet.add(audioInfo)) {
                    duplicates += audioInfo.toString() + "\n";
                    duplicateCount++;
                }
            }
            totalTracks = sideAList.size();
        }
        
        if(sideBList != null) {
            for (AudioInfo audioInfo : sideBList) {
                if (!trackListSet.add(audioInfo)) {
                    duplicates += audioInfo.toString() + "\n";
                    duplicateCount++;
                }
            }
            
            totalTracks += sideBList.size();
        }
        
        String message = "Track Duplicates Found: " + duplicateCount  + "/" + totalTracks
                 + "\n" + duplicates.trim();
        
        JOptionPane.showMessageDialog(this, message);
    }//GEN-LAST:event_checkTrackListButtonActionPerformed
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addAudioDirectoryButton;
    private javax.swing.JButton addAudioToTapeListButton;
    private javax.swing.JLabel audioCountLabel;
    private javax.swing.JTextField audioDownloadServerTextField;
    private javax.swing.JList<String> audioJList;
    private javax.swing.JComboBox<String> audioOutputComboBox;
    private javax.swing.JButton baudRateButton;
    private javax.swing.JTextField baudRateTextField;
    private javax.swing.JComboBox<String> bluetoothComboBox;
    private javax.swing.JRadioButton bluetoothRadioButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.JButton checkTrackListButton;
    private javax.swing.JButton clearAudioListButton;
    private javax.swing.JButton clearConsoleButton;
    private javax.swing.JButton clearLyraTConsoleButton;
    private javax.swing.JButton clearSelectionButton;
    private javax.swing.JTextArea consoleTextArea;
    private javax.swing.JButton createButton;
    private javax.swing.JButton createDownloadButton;
    private javax.swing.JButton defaultButton;
    private javax.swing.JCheckBox directDecodeCheckBox;
    private javax.swing.JCheckBox directEncodeCheckBox;
    private javax.swing.JTextField directoryTextField;
    private javax.swing.JProgressBar encodeProgressBar;
    private javax.swing.JRadioButton eqRadioButton1;
    private javax.swing.JRadioButton eqRadioButton2;
    private javax.swing.JRadioButton eqRadioButton3;
    private javax.swing.JRadioButton eqRadioButton4;
    private javax.swing.JRadioButton eqRadioButton5;
    private javax.swing.JRadioButton eqRadioButton6;
    private javax.swing.JRadioButton eqRadioButton7;
    private javax.swing.JRadioButton eqRadioButton8;
    private javax.swing.JButton exitButton;
    private javax.swing.JButton filterAudioListButton;
    private javax.swing.JCheckBox filterShuffleCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton logfileButton;
    private javax.swing.JTextField logfileTextField;
    private javax.swing.JButton lyraTConnectButton;
    private javax.swing.JTextArea lyraTConsoleTextArea;
    private javax.swing.JButton lyraTCreateAButton;
    private javax.swing.JButton lyraTCreateBButton;
    private javax.swing.JRadioButton lyraTDecodeRadioButton;
    private javax.swing.JButton lyraTDisconnectButton;
    private javax.swing.JButton lyraTEncodeAButton;
    private javax.swing.JButton lyraTEncodeBButton;
    private javax.swing.JRadioButton lyraTEncodeRadioButton;
    private javax.swing.JButton lyraTGetInfoButton;
    private javax.swing.JButton lyraTGetRawButton;
    private javax.swing.JTextField lyraTHostTextField;
    private javax.swing.JToggleButton lyraTMuteToggleButton;
    private javax.swing.JRadioButton lyraTPassRadioButton;
    private javax.swing.JButton lyraTPlaySideAButton;
    private javax.swing.JButton lyraTPlaySideBButton;
    private javax.swing.JButton lyraTServerTestDBButton;
    private javax.swing.JButton lyraTStopButton;
    private javax.swing.JButton lyraTStopRawButton;
    private javax.swing.JButton lyraTVolDownButton;
    private javax.swing.JButton lyraTVolUpButton;
    private javax.swing.JTextField mmdelayTextField;
    private javax.swing.JButton moveTrackDownButton;
    private javax.swing.JButton moveTrackUpButton;
    private javax.swing.JTextField muteJTextField;
    private javax.swing.JButton playButton;
    private javax.swing.JButton playSideButton;
    private javax.swing.JTextArea playbackInfoTextArea;
    private javax.swing.JTextField playbackSpeedTextField;
    private javax.swing.JComboBox<String> r2RComboBox;
    private javax.swing.JLabel r2RLabel;
    private javax.swing.JTextField r2RTextField;
    private javax.swing.JButton realtimeEncodeButton;
    private javax.swing.JButton reloadAudioOutputsButton;
    private javax.swing.JButton removeAllButton;
    private javax.swing.JButton removeAudioButton;
    private javax.swing.JButton setAudioDownloadServerButton;
    private javax.swing.JButton shuffleButton;
    private javax.swing.JTextField shuffleFilterTextField;
    private javax.swing.JList<String> sideAJList;
    private javax.swing.JLabel sideALabel;
    private javax.swing.JList<String> sideBJList;
    private javax.swing.JLabel sideBLabel;
    private javax.swing.JList<String> sideNJList;
    private javax.swing.JLabel sideNLabel;
    private javax.swing.JRadioButton speakerRadioButton;
    private javax.swing.JButton startDecodeButton;
    private javax.swing.JCheckBox startServerCheckBox;
    private javax.swing.JButton stopButton;
    private javax.swing.JButton stopDecodeButton;
    private javax.swing.JTextField tapeIDTextField;
    private javax.swing.JTextArea tapeInfoTextArea;
    private javax.swing.JTabbedPane tapeJTabbedPane;
    private javax.swing.JComboBox<String> tapeLengthComboBox;
    private javax.swing.JLabel trackALabel;
    private javax.swing.JLabel trackBLabel;
    private javax.swing.JTextArea trackInfoTextArea;
    private javax.swing.JLabel tracksLabel;
    private javax.swing.JButton viewTapeDBButton;
    // End of variables declaration//GEN-END:variables

}
