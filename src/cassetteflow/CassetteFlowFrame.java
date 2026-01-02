/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cassetteflow;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.Mixer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileFilter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
       
    // keep trackNum of the currenet seconds for long running task
    private int encodeSeconds;
    
    // keep trackNum of the seconds audio has been played so for on the lyraT board
    private int playSeconds;
    
    // holds the resently encoded wav files if any
    private File[] wavFiles;
    
    // stores the audio output channels
    private HashMap<String, Mixer.Info> mixerOutput;
    
    // used to indicate if reading of line records from LyraT board should be done
    private boolean lyraTReadLineRecords = false;
        
    // keep trackNum if we playing any audio
    private boolean playing;
    
    // keep trackNum if we playing youtube trackNum
    private boolean playingYouTube = false;
    
    // keep trackNum if we playing spotify trackNum
    private boolean playingSpotify = false;
    
    // store a list of filtered audioIno objects
    private ArrayList<AudioInfo> filteredAudioList;
    
    // store the current directory location so when we doing filtering
    // we can display the current directory hen we done with filtering
    private String currentAudioDirectory;
    
    // The frame to view the tape database
    TapeDatabaseFrame tapeDBFrame;
    
    // The frame to search the index files
    TrackFinderFrame trackFinderFrame;
    
    // store the current tape id being decoded so we can view the tracks
    // in the tapeDBFrame
    private String currentCassetteId;
    
    // store the current tracks being played when decoding
    private int currentPlayingTrack = 0;
    
    // initiat the objects to allow control of streaming
    // music sites. Store the id for the stream video/track being played
    // deckCastConnectorDisplay is used only to show current playing trackNum through web ui
    private DeckCastConnector deckCastConnector;
    private DeckCastConnector deckCastConnectorDisplay;
    private SpotifyConnector spotifyConnector;  
    private String streamId;
    private int streamTotalTime = 0;
    private int streamPlayer = 0;
    
    // variables to automatically change the DCT offset value if the correct mute
    private int maxTimeBlock = -1; // the maximumum time block
    private ArrayList<String> timeBlockEndTracks = new ArrayList<>();
    
    // The JSON object used to when creating a jcard template
    private JSONObject jcardJSON;
    
    // dialog used to tell user that the physical media needs to be reset
    private JDialog resetMediaDialog = null;
    
    // specify if to hide the lyraT tab
    private static final boolean HIDE_LYRAT_TAB = true;
    
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
        
        // load the jcard json template
        try {
            String jsonText = CassetteFlowUtil.getResourceFileAsString("template.jcard.json");
            jcardJSON = new JSONObject(jsonText);
        } catch(IOException | JSONException ex) { }
        
        // Hide the LyrT tab pane if needed
        if(HIDE_LYRAT_TAB) {
            mainTabbedPane.remove(3);
        }
    }
    
    /**
     * Return the main CasssetFlow object
     * @return 
     */
    public CassetteFlow getCassetteFlow() {
        return cassetteFlow;
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
        
        // start the cassetteflow server here
        try {
            cassetteFlowServer = new CassetteFlowServer();
            cassetteFlowServer.setCassetteFlow(cassetteFlow);

            consoleTextArea.append("Local Cassette Flow Server Started ...\n\n");
        } catch (IOException ex) {
            consoleTextArea.append("Local Cassette Flow Server Failed To Start ...\n\n");
            Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // update the UI now
        updateUI();
    }
    
    /**
     * Method to update the UI after cassette flow object has loaded the list of
     * flac and mp3 files
     */
    private void updateUI() {
        currentAudioDirectory = CassetteFlow.AUDIO_DIR_NAME;
        
        directoryTextField.setText(currentAudioDirectory);
        baudRateTextField.setText(CassetteFlow.BAUDE_RATE);
        lyraTHostTextField.setText(CassetteFlow.LYRA_T_HOST);
        audioDownloadServerTextField.setText(CassetteFlow.DOWNLOAD_SERVER);
        jcardSiteTextField.setText(CassetteFlow.JCARD_SITE);
        
        // load the list of decorders either minimodem local or a 
        // cassetteflow server over http
        decoderSourceComboBox.removeAllItems();
        decoderSourceComboBox.addItem("JMinimodem");
        decoderSourceComboBox.addItem(CassetteFlow.LYRA_T_HOST);
        
        addAudioInfoToJList();
    }
    
    /**
     * Merge tape records from LyraT board into the local tape db
     */
    public void mergeCurrentTapeDBToLocal() {
        cassetteFlow.mergeCurrentTapeDBToLocal();
    }
    
    /**
     * Set the current cassette ID so the trackNum list can be displayed
     * 
     * @param cassetteID 
     */
    @Override
    public void setPlayingCassetteID(final String cassetteID) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                currentCassetteId = cassetteID;
                tracksLabel.setText(cassetteID + " Tracks");
                tapeInfoTextArea.setText("");
                
                // set the side based on cassette ID
                String tapeSide = "A";
                if (cassetteID.endsWith("B")) {
                    tapeSide = "B";
                }
                
                ArrayList<String> audioIds = cassetteFlow.tapeDB.get(cassetteID);
                
                // json array of tracks to send to mobile clients
                JSONArray tracksArray = new JSONArray();
                
                int trackTotal = 0;
                
                if(audioIds != null) {
                   for(int i = 0; i < audioIds.size(); i++) {
                       String audioId = audioIds.get(i);
                       
                       AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(audioId);
                       String trackCount = String.format("%02d", (i + 1));
                       
                       String endTrack = tapeSide + (i+1);
                       if(timeBlockEndTracks.contains(endTrack)) {
                           trackCount = "*" + trackCount;
                       }
                       String trackTitle = "[" + trackCount + "] " + audioInfo.getName();
                       tapeInfoTextArea.append(trackTitle + "\n");
                       
                       // add a jsonobject to the tracks array
                       JSONObject trackObject = CassetteFlowUtil.getTrackInfoAsJSON((i + 1), trackTitle, audioInfo);
                       if(trackObject != null) {
                           tracksArray.put(trackObject);
                       }
                                             
                       // see if there is additional trackNum information if this 
                       // is for a long youtube mix for example
                       if(cassetteFlow.tracklistDB.containsKey(audioId)) {
                           TrackListInfo trackListInfo = cassetteFlow.tracklistDB.get(audioId);
                           tapeInfoTextArea.append(trackListInfo.toString());
                           trackTotal += trackListInfo.getTrackCount();
                       } else {
                           trackTotal++;
                       }
                   }
                   
                   // send information to create the current decode state oject
                   cassetteFlow.setCurrentDecodeState(tracksArray, cassetteID, 0, false);
                } else {
                   tapeInfoTextArea.setText("Invalid Tape ID ...");
                }
                
                tracksInfoLabel.setText(trackTotal + " Track(s)");
            }
        });
    }
    
    @Override
    public void setPlayingAudioInfo(final String info) {
        SwingUtilities.invokeLater(() -> {
            trackInfoTextArea.setText(info);
        });
    }
    
    /**
     * Set the current trackNum that's playing
     * @param track 
     */
    public void setPlayingAudioTrack(String track) {
        try {
            currentPlayingTrack = Integer.parseInt(track.trim());
            
            // check to see if to update the playing trackNum
            if(tapeDBFrame != null && tapeDBFrame.isVisible()) {
                tapeDBFrame.setSelectedTrack(currentPlayingTrack);
            }
        } catch (NumberFormatException nfe) {
            currentPlayingTrack = 0;
        }
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
        
        // update the decode state information in the cassetteflow object
        boolean trackPlaying = true;
        if(info.toLowerCase().contains("mute") || info.toLowerCase().contains("stop")) {
            trackPlaying = false;
        }
        cassetteFlow.setCurrentDecodeState(info, (currentPlayingTrack - 1), trackPlaying);
    }
    
    /**
     * Set the playback information displayed to user
     * @param info
     * @param append
     */
    @Override
    public void setPlaybackInfo(final String info, boolean append) {
        setPlaybackInfo(info, append, "\n");
    }
    
    /**
     * Method to execute basic decode command so that the web client can control
     * the decode process
     * 
     * @param command 
     */
    public void runDecodeCommand(String command) {
        if(command.equals("start") && startDecodeButton.isEnabled()) {
            startDecodeButtonActionPerformed(null);
        } else if(command.equals("stop") && !startDecodeButton.isEnabled()) {
            stopDecodeButtonActionPerformed(null);
        } else if(command.equals("offset") && resetMediaDialog != null) {
            int currentOffset = Integer.parseInt(dctOffsetComboBox.getSelectedItem().toString());
            int offset = (maxTimeBlock/60) + currentOffset;
            
            resetMediaDialog.dispose();
            resetMediaDialog = null;
            
            dctOffsetComboBox.setSelectedItem(String.valueOf(offset));
        } else if(command.equals("reset")) {
            if(resetMediaDialog != null) {
                resetMediaDialog.dispose();
                resetMediaDialog = null;
            }
            
            dctOffsetComboBox.setSelectedItem(String.valueOf(0));
        } else {
            System.out.println("Invalid Command or Can't Execute:" + command);
        } 
    }
    
    /**
     * Method to increment the DCT offset based on the max block time variable
     * and time block count
     */
    @Override
    public void incrementDCTDecodeOffset() {
        if(padDCTCheckBox.isSelected()) {
            try {
                int currentOffset = Integer.parseInt(dctOffsetComboBox.getSelectedItem().toString());
                int offset = (maxTimeBlock/60) + currentOffset;
                
                // show popup asking to reset the physical media to the begining
                showConfirmDCTOffsetDialog(offset);

                //dctOffsetComboBox.setSelectedItem(String.valueOf(offset));
            } catch (NumberFormatException nfe) {
                System.out.println("Unable to increment the DCT Offset ...");
            }
        }
    }
    
    /**
     * Reset the end block array list
     */
    public void resetTimeBlockEndTracks() {
        timeBlockEndTracks = new ArrayList<>();
    }
    
    /**
     * Method to add tracks which are at the end of timeBlocks to make it easier
     * when using DTC tracks to know when the physical media should be reset
     * 
     * @param sideAndTrackNum
     */
    public void addTimeBlockEndTrack(String sideAndTrackNum) {
        if (timeBlockEndTracks == null) {
            timeBlockEndTracks = new ArrayList<>();
        }

        timeBlockEndTracks.add(sideAndTrackNum);
    }
    
    /**
     * Non UI blocking info dialog
     * @param owner
     * @param title
     * @param message 
     */
    private void showConfirmDCTOffsetDialog(int offset) {
        String message = "<html>Please reset the physical media to the begining of Side A/B <br/>" + 
                        "and hit OK to set the DCT offset to " + offset + " minutes ...</html>";
        
        resetMediaDialog = new JDialog(this, "Reset Media", false);
        resetMediaDialog.setLayout(new BorderLayout());

        JLabel messageLabel = new JLabel(message, SwingConstants.CENTER);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        resetMediaDialog.add(messageLabel, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.addActionListener((ActionEvent e) -> {
            resetMediaDialog.dispose();
            dctOffsetComboBox.setSelectedItem(String.valueOf(offset));
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        resetMediaDialog.add(buttonPanel, BorderLayout.SOUTH);

        resetMediaDialog.pack();
        resetMediaDialog.setLocationRelativeTo(this); // Center the dialog relative to its owner
        resetMediaDialog.setVisible(true);
    }
    
    /**
     * Process a line record from the lyraT board or cassetteflow server
     * 
     * @param line 
     */
    @Override
    public void processLineRecord(String line) {
        // if we just connected to the lyraT device for record reading just pass it
        // to the cassette player
        if(cassettePlayer != null ) {
            cassettePlayer.newLineRecord(line);
        }
        
        printToConsole(line, true);
        //printToLyraTConsole("Line Record: " + line, true);
    }
    
    /**
     * Add the mp3s/flac files information to the jlist
     */
    public void addAudioInfoToJList() {
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
     * @param tapeLength
     * @param createDCT
     */
    void loadTapeInformation(String tapeID, ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB, int tapeLength, boolean createDCT) {
        tapeIDTextField.setText(tapeID);
        
        // set the tape type based base on length if we don't have the pad dct checkbox selected
        if(!padDCTCheckBox.isSelected()) {
            if (tapeLength < 65) {
                tapeLengthComboBox.setSelectedIndex(0);
            } else if (tapeLength < 95) {
                tapeLengthComboBox.setSelectedIndex(1);
            } else if (tapeLength < 115) {
                tapeLengthComboBox.setSelectedIndex(2);
            } else if (tapeLength < 125) {
                tapeLengthComboBox.setSelectedIndex(3);
            } else {
                tapeLengthComboBox.setSelectedIndex(4);
            }
        }
        
        // now load the audio information
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
        
        // if createDCT then call the function that create the DCT records
        createDCTButtonActionPerformed(null);
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
     * Set select the current trackNum being process
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
        mainTabbedPane = new javax.swing.JTabbedPane();
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
        trackListInfoTextArea = new javax.swing.JTextArea();
        exportTemplateButton = new javax.swing.JButton();
        refreshTrackListButton = new javax.swing.JButton();
        jLabel14 = new javax.swing.JLabel();
        jcardSiteTextField = new javax.swing.JTextField();
        jcardSiteButton = new javax.swing.JButton();
        loadTemplateButton = new javax.swing.JButton();
        jcardTitleTextField = new javax.swing.JTextField();
        jcardGroupTextField = new javax.swing.JTextField();
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
        createDCTButton = new javax.swing.JButton();
        storeToTapeDBButton = new javax.swing.JButton();
        findTracksButton = new javax.swing.JButton();
        padDCTCheckBox = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
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
        tracksInfoLabel = new javax.swing.JLabel();
        viewCurrentTapeButton = new javax.swing.JButton();
        jLabel12 = new javax.swing.JLabel();
        dctOffsetComboBox = new javax.swing.JComboBox<>();
        decoderSourceComboBox = new javax.swing.JComboBox<>();
        decorderSourceLabel = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        jScrollPane10 = new javax.swing.JScrollPane();
        streamEditorPane = new javax.swing.JEditorPane();
        jLabel13 = new javax.swing.JLabel();
        streamComboBox = new javax.swing.JComboBox<>();
        streamConnectButton = new javax.swing.JButton();
        streamPlaytimeLabel = new javax.swing.JLabel();
        streamDisconnectButton = new javax.swing.JButton();
        streamPinTextField = new javax.swing.JTextField();
        streamPlayClearButton = new javax.swing.JButton();
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
        lyraTRawDataReadCheckBox = new javax.swing.JCheckBox();
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
        buildAudioIndexButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        addAudioDirectoryButton = new javax.swing.JButton();
        createButton = new javax.swing.JButton();
        createDownloadButton = new javax.swing.JButton();
        audioCountLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("CassetteFlow v 2.0.14 (01/01/2026)");

        mainTabbedPane.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        mainTabbedPane.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                mainTabbedPaneKeyPressed(evt);
            }
        });

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
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 730, Short.MAX_VALUE)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(sideALabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(trackALabel, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 315, Short.MAX_VALUE)
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
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 730, Short.MAX_VALUE)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(sideBLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(trackBLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 321, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(trackBLabel)
                    .addComponent(sideBLabel))
                .addGap(9, 9, 9))
        );

        tapeJTabbedPane.addTab("Side B", jPanel4);

        trackListInfoTextArea.setColumns(20);
        trackListInfoTextArea.setRows(5);
        jScrollPane7.setViewportView(trackListInfoTextArea);

        exportTemplateButton.setText("Save Template");
        exportTemplateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportTemplateButtonActionPerformed(evt);
            }
        });

        refreshTrackListButton.setText("Load/Refresh");
        refreshTrackListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshTrackListButtonActionPerformed(evt);
            }
        });

        jLabel14.setText("J-Card Template Site =>");

        jcardSiteTextField.setText("https://ed7n.github.io/jcard-template/");
        jcardSiteTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jcardSiteTextFieldActionPerformed(evt);
            }
        });

        jcardSiteButton.setText("View Site");
        jcardSiteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jcardSiteButtonActionPerformed(evt);
            }
        });

        loadTemplateButton.setText("Open Template");
        loadTemplateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadTemplateButtonActionPerformed(evt);
            }
        });

        jcardTitleTextField.setText("Album Title");

        jcardGroupTextField.setText("Album Artist");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane7)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(jLabel14)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jcardSiteTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 333, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(refreshTrackListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadTemplateButton, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jcardTitleTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 182, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jcardGroupTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 176, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jcardSiteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exportTemplateButton))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 298, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(jcardSiteTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jcardSiteButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exportTemplateButton)
                    .addComponent(refreshTrackListButton)
                    .addComponent(loadTemplateButton)
                    .addComponent(jcardTitleTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jcardGroupTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        tapeJTabbedPane.addTab("Copy/Export Track List", jPanel6);

        audioJList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Audio File 1", "Audio File 2" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        audioJList.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                audioJListKeyPressed(evt);
            }
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

        tapeLengthComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "60 Minutes", "90 Minutes", "110 Minutes", "120 Minutes", "150 Minutes (R2R)", "160 Minutes (R2R)", "180 Minutes (R2R)", "240 Minutes (R2R)", "360 Minutes (R2R)", "44 Minutes (Vinyl)" }));
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

        createDCTButton.setText("Create DCT");
        createDCTButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createDCTButtonActionPerformed(evt);
            }
        });

        storeToTapeDBButton.setText("Store");
        storeToTapeDBButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                storeToTapeDBButtonActionPerformed(evt);
            }
        });

        findTracksButton.setText("Find");
        findTracksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                findTracksButtonActionPerformed(evt);
            }
        });

        padDCTCheckBox.setText("Pad");
        padDCTCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                padDCTCheckBoxActionPerformed(evt);
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
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(directoryTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 234, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(defaultButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filterAudioListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(findTracksButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tapeJTabbedPane)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(moveTrackUpButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(moveTrackDownButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkTrackListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(createDCTButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(padDCTCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                        .addComponent(storeToTapeDBButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
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
                        .addComponent(filterAudioListButton)
                        .addComponent(findTracksButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(viewTapeDBButton)
                        .addComponent(storeToTapeDBButton)))
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
                        .addComponent(tapeJTabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 382, Short.MAX_VALUE))
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
                    .addComponent(checkTrackListButton)
                    .addComponent(createDCTButton)
                    .addComponent(padDCTCheckBox)))
        );

        mainTabbedPane.addTab("ENCODE", jPanel1);

        startDecodeButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        startDecodeButton.setText("START");
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
        stopDecodeButton.setText("STOP");
        stopDecodeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopDecodeButtonActionPerformed(evt);
            }
        });

        tracksInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        tracksInfoLabel.setText("## Track(s)");

        viewCurrentTapeButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        viewCurrentTapeButton.setText("View");
        viewCurrentTapeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewCurrentTapeButtonActionPerformed(evt);
            }
        });

        jLabel12.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        jLabel12.setText("DCT Offset ");

        dctOffsetComboBox.setEditable(true);
        dctOffsetComboBox.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        dctOffsetComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0", "22", "44", "60", "120", "180", "240", "300" }));
        dctOffsetComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dctOffsetComboBoxActionPerformed(evt);
            }
        });

        decoderSourceComboBox.setEditable(true);
        decoderSourceComboBox.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        decoderSourceComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        decoderSourceComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decoderSourceComboBoxActionPerformed(evt);
            }
        });

        decorderSourceLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        decorderSourceLabel.setText("Local");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                    .addComponent(tracksLabel)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(tracksInfoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(viewCurrentTapeButton))
                    .addComponent(decoderSourceComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(decorderSourceLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 339, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel12)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dctOffsetComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 158, Short.MAX_VALUE)
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
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(startDecodeButton)
                        .addComponent(stopDecodeButton)
                        .addComponent(jLabel12)
                        .addComponent(dctOffsetComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(decoderSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(decorderSourceLabel)))
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
                        .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 173, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(jScrollPane4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(tracksInfoLabel)
                            .addComponent(viewCurrentTapeButton)))))
        );

        mainTabbedPane.addTab("DECODE", jPanel2);

        streamEditorPane.setEditable(false);
        streamEditorPane.setContentType("text/html"); // NOI18N
        jScrollPane10.setViewportView(streamEditorPane);

        jLabel13.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jLabel13.setText("Stream");

        streamComboBox.setEditable(true);
        streamComboBox.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        streamComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "http://pi86.sytes.net:5054/", "http://127.0.0.1:5054/", "http://127.0.0.1:5154/", "http://127.0.0.1:5254/", "https://www.spotify.com/" }));

        streamConnectButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        streamConnectButton.setText("CONNECT");
        streamConnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                streamConnectButtonActionPerformed(evt);
            }
        });

        streamPlaytimeLabel.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        streamPlaytimeLabel.setText("Play Time: 00:00:00/00:00:00 [000]");

        streamDisconnectButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        streamDisconnectButton.setText("DISCONNECT");
        streamDisconnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                streamDisconnectButtonActionPerformed(evt);
            }
        });

        streamPinTextField.setText("0001");
        streamPinTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                streamPinTextFieldActionPerformed(evt);
            }
        });

        streamPlayClearButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        streamPlayClearButton.setText("Clear");
        streamPlayClearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                streamPlayClearButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane10)
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(jLabel13)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(streamComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 240, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(streamConnectButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(streamDisconnectButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(streamPinTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 224, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(streamPlaytimeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 289, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(streamPlayClearButton))))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(streamComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(streamConnectButton)
                    .addComponent(streamPlaytimeLabel)
                    .addComponent(streamDisconnectButton)
                    .addComponent(streamPinTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(streamPlayClearButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 434, Short.MAX_VALUE))
        );

        mainTabbedPane.addTab("STREAM PLAY", jPanel8);

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

        lyraTRawDataReadCheckBox.setSelected(true);
        lyraTRawDataReadCheckBox.setText("Raw Data Read Only");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lyraTHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lyraTRawDataReadCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(startServerCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                            .addComponent(lyraTPlaySideAButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                    .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, 1003, Short.MAX_VALUE)))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lyraTHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lyraTConnectButton)
                    .addComponent(lyraTDisconnectButton)
                    .addComponent(jLabel5)
                    .addComponent(startServerCheckBox)
                    .addComponent(lyraTRawDataReadCheckBox))
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 174, Short.MAX_VALUE)
                        .addComponent(lyraTStopRawButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lyraTStopButton))
                    .addComponent(jScrollPane9)))
        );

        mainTabbedPane.addTab("ESP32 LyraT", jPanel5);

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

        buildAudioIndexButton.setText("Build and Save Audio File Index");
        buildAudioIndexButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buildAudioIndexButtonActionPerformed(evt);
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
                        .addComponent(reloadAudioOutputsButton))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(playbackSpeedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 140, Short.MAX_VALUE)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(setAudioDownloadServerButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(audioDownloadServerTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearConsoleButton))
                    .addComponent(buildAudioIndexButton)))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 411, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(filterShuffleCheckBox)
                        .addComponent(shuffleFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel11)
                        .addComponent(playbackSpeedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(buildAudioIndexButton, javax.swing.GroupLayout.Alignment.TRAILING))
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

        mainTabbedPane.addTab("SETUP / CONSOLE", jPanel7);

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
            .addGroup(layout.createSequentialGroup()
                .addComponent(addAudioDirectoryButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(audioCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(createDownloadButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(createButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exitButton))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(mainTabbedPane))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(mainTabbedPane)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAudioDirectoryButton)
                    .addComponent(createDownloadButton)
                    .addComponent(createButton)
                    .addComponent(exitButton)
                    .addComponent(audioCountLabel)))
        );

        getAccessibleContext().setAccessibleName("");
        getAccessibleContext().setAccessibleDescription("");

        setBounds(0, 0, 1230, 553);
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
        
        // if nothing was selected then 
        if(selectedAudio.isEmpty()) {
            selectedAudio = cassetteFlow.audioInfoList;
        }
        
        DefaultListModel model;
        JLabel sideLabel;
        ArrayList<AudioInfo> audioList;
        
        // see which side of the tape we adding sounds to
        if (side == 0) {
            model = (DefaultListModel) sideAJList.getModel();
            sideLabel = sideALabel;
            audioList = sideAList;
        } else {
            model = (DefaultListModel) sideBJList.getModel();
            sideLabel = sideBLabel;
            audioList = sideBList;
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
    
    /**
     * Get the length of the tape in seconds based on the selected value in
     * combobox
     * @return 
     */
    private int getMaxTapeTime() {
        String[] sa = tapeLengthComboBox.getSelectedItem().toString().split(" ");
        int timeInSeconds = (Integer.parseInt(sa[0])/2) * 60;
        return timeInSeconds;
    }
    
    /**
     * either filter the trackNum list or load Spotify album or tracks
     * 
     * @param evt 
     */
    private void directoryTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_directoryTextFieldActionPerformed
        String value = directoryTextField.getText();
        
        // see if to load spotify playlist or album tracks
        if(spotifyConnector != null) {
            if(value.contains("open.spotify.com")) {
                audioCountLabel.setText(" Loading Spotify tracks ...");
            
                String[] sa = spotifyConnector.getSpotifyMediaId(value);
                if(sa[0].equals("playlist")) {
                    loadSpotifyTracks(spotifyConnector.loadPlaylist(sa[1], false));
                } else if(sa[0].equals("album")) {
                    loadSpotifyTracks(spotifyConnector.loadAlbum(sa[1], false));
                } else if(sa[0].equals("track")) {
                    loadSpotifyTracks(spotifyConnector.loadTrack(sa[1], false));
                }
            } else if(value.contains("spotify:")) {
                loadStreamingTracks("Spotify", spotifyConnector.getQueList());
            }
        } else if(deckCastConnector != null) {
            if(value.contains("youtube.com/playlist")) {
                // see with to add https://www. if it's missing otherwise vieos don't load
                if(!value.contains("https://www.")) {
                    value = value.replace("https://", "https://www.");
                }
                
                encodeProgressBar.setIndeterminate(true);
                audioCountLabel.setText(" Loading YouTube playlist tracks ...");
                deckCastConnector.loadPlaylist(value);
            } else if(value.contains("youtube:")) {
                loadStreamingTracks("YouTube", deckCastConnector.getQueList());
            } else {
                System.out.println("Unable to load Youtube videos for: " + value);
            }
        } else {
            // just call the filter button audio list action
            filterAudioListButtonActionPerformed(null);
        }
    }//GEN-LAST:event_directoryTextFieldActionPerformed
    
    /**
     * Load the Spotify tracks into the main UI
     * 
     * @param spotifyTrackList
     */
    private void loadSpotifyTracks(ArrayList<AudioInfo> spotifyTrackList) {
        if(spotifyTrackList == null) {
            String message = "Error Loading Spotify Tracks ...";
            JOptionPane.showMessageDialog(this, message, "Spotify Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if(filteredAudioList == null) 
            filteredAudioList = new ArrayList<>();
        
        DefaultListModel filteredModel = new DefaultListModel();
        
        filteredAudioList.addAll(spotifyTrackList); 
        for (AudioInfo audioInfo : filteredAudioList) {
            filteredModel.addElement(audioInfo);
        }

        audioJList.setModel(filteredModel);
        audioCountLabel.setText(filteredModel.size() + " Spotify tracks loaded");
    }
    
    /**
     * Load the YouTube tracks into the main UI
     * 
     * @param streamer
     * @param streamingTrackList
     */
    public void loadStreamingTracks(String streamer, ArrayList<AudioInfo> streamingTrackList) {
        if(filteredAudioList == null) 
            filteredAudioList = new ArrayList<>();
        
        DefaultListModel filteredModel = new DefaultListModel();
        
        filteredAudioList.addAll(streamingTrackList); 
        for (AudioInfo audioInfo : filteredAudioList) {
            filteredModel.addElement(audioInfo);
        }

        audioJList.setModel(filteredModel);
        audioCountLabel.setText(filteredModel.size() + " " + streamer + " tracks loaded");
        encodeProgressBar.setIndeterminate(false);
    }
    
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
        // get list of selected audio files
        List selectedAudio = audioJList.getSelectedValuesList();
        int trackNum = audioJList.getSelectedIndex() + 1;
        
        if(selectedAudio != null && selectedAudio.size() >= 1) {
            double speedFactor = 1.0; 
            try {
                speedFactor = Double.parseDouble(playbackSpeedTextField.getText());
            } catch(NumberFormatException nfe) {}
            
            final AudioInfo audioInfo = (AudioInfo) selectedAudio.get(0);
            
            // check to see if we playing a spotify trackNum. If so return
            if(spotifyConnector != null) {
                String url = audioInfo.getUrl();
                if(url != null && url.contains("spotify")) {
                    playSpotifyTrack(url, audioInfo.toString(), audioInfo.getLength());
                    
                    // send information to deck cast about playing trackNum
                    if (deckCastConnectorDisplay != null) {
                        deckCastConnectorDisplay.displayPlayingAudioInfo(audioInfo, 0, "spotify", trackNum);
                    }
                    
                    return;
                }
            } 
            
            // check to see if we playing a youtube trackNum. If so return
            if(deckCastConnector != null) {
                String videoId = audioInfo.getStreamId();
                if(videoId != null) {
                    playYouTubeTrack(videoId, audioInfo.toString(), audioInfo.getLength(), trackNum);
                    return;
                }
            }
            
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
                
                // send information to deckcast about playing trackNum
                if(deckCastConnectorDisplay != null) {
                    deckCastConnectorDisplay.displayPlayingAudioInfo(audioInfo, 0, "mp3/FLAC", trackNum);
                }
            } catch (StreamPlayerException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error playing audio file ...");
            }
            
            // start thread to keep trackNum of if we playing sound
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
    
    /**
     * play a Spotify trackNum
     * 
     * @param trackURI 
     * @param title
     * @param length
     */
    private void playSpotifyTrack(String trackURI, String title, int length) {
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
            player = null;
        }

        playButton.setEnabled(false);
        
        System.out.println("\nPlaying Spotify Audio: " + title);
        playingSpotify = spotifyConnector.playTrack(trackURI, 0);
        
        if(!playingSpotify) {
            JOptionPane.showMessageDialog(null, "Error Playing Spotify Track\n"
            + "No Active Player Found");
            playButton.setEnabled(true);
            return;
        }
        
        // start thread to keep trackNum of if we playing sound
        if (playerThread == null) {
            playerThread = new Thread(() -> {
                try {
                    int playTime = 0;

                    while (playingSpotify) {
                        Thread.sleep(1000);
                        playTime++;
                        
                        if(playTime >= length) {
                            System.out.println("Spotify Track Finished ...");
                            break;
                        }
                    }
                } catch (InterruptedException ex) { }
                
                playingSpotify = false;
                playButton.setEnabled(true);
            });
            playerThread.start();
        }
    }
    
    /**
     * Method to play a side of Spotify tracks
     */
    private void playSpotifyTracks(int side, String tapeID, int muteTime) {
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
            player = null;
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
                
                // play the spotify trackNum now
                try {
                    int trackNum = 1;
                    int currentPlayTime = 0;
                    
                    for(AudioInfo audioInfo: audioList) {
                        // check to see if playback was stopped
                        if(!playSide) {
                            break;
                        }
                        
                        jlist.setSelectedIndex(trackNum -1);
                        //trackLabel.setText("Playing Track: " + String.format("%02d", trackNum));
                        System.out.println("Playing " + audioInfo + " on side: " + sideString);
                        
                        // play the spotify trackNum
                        String url = audioInfo.getUrl();
                        int length = audioInfo.getLength();
                        
                        if(url != null && url.contains("spotify")) {
                            playingSpotify = spotifyConnector.playTrack(url, 0);
                            if(!playingSpotify) {
                                JOptionPane.showMessageDialog(null, "Error Playing Spotify Track\n"
                                        + "No Active Player Found");
                                break;
                            }
                            
                            // send information to deckcast about playing trackNum
                            if (deckCastConnectorDisplay != null) {
                                deckCastConnectorDisplay.displayPlayingAudioInfo(audioInfo, 0, "spotify", trackNum);
                            }
                        } else {
                            continue;
                        }
                        
                        // wait for playback to stop
                        int playTime = 0;
                        while(playingSpotify) {
                            //update display every second
                            String message = "Playing Track: " + String.format("%02d", trackNum) + 
                                        " (" + CassetteFlowUtil.getTimeString(playTime) + " | " +
                                        CassetteFlowUtil.getTimeString(currentPlayTime) + ")";
                            
                            trackLabel.setText(message);

                            if (playTime >= length) {
                                break;
                            }
                            
                            playTime++;
                            currentPlayTime++;
                            Thread.sleep(1000);
                        }
                        
                        // pause a certain amount of time to create a mute section
                        Thread.sleep(muteTime*1000);
                        currentPlayTime += muteTime;
                        trackNum++;
                    }
                    
                    // stop the playback if needed
                    spotifyConnector.stopStream();
                                        
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
                    playingSpotify = false;
                    
                    if (deckCastConnectorDisplay != null) {
                        deckCastConnectorDisplay.clearAudioInfoDisplay();
                    }
                } catch (InterruptedException e) {
                    JOptionPane.showMessageDialog(null, "Error playing Spotify stream");
                }
            }
        }
        );
        playerThread.start();
    }
    
    /**
     * play a YouTube trackNum
     * 
     * @param videoId 
     * @param title
     * @param length
     */
    private void playYouTubeTrack(String videoId, String title, int length, int trackNum) {
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
            player = null;
        }

        playButton.setEnabled(false);
        
        System.out.println("\nPlaying YouTube Audio: " + title);
        playingYouTube = deckCastConnector.playSingleTrack(videoId, trackNum);
        
        if(!playingYouTube) {
            JOptionPane.showMessageDialog(null, "Error Playing YouTube Track\n"
            + "No Active Player Found");
            playButton.setEnabled(true);
            return;
        }
        
        // start thread to keep trackNum of if we playing sound
        if (playerThread == null) {
            playerThread = new Thread(() -> {
                try {
                    int playTime = 1; // assume it takes a second to load player

                    while (playingYouTube) {
                        Thread.sleep(1000);
                        playTime++;
                        
                        if(playTime >= length) {
                            System.out.println("YouTube Track Finished ...");
                            break;
                        }
                    }
                } catch (InterruptedException ex) { }
                
                playingYouTube = false;
                playButton.setEnabled(true);
            });
            playerThread.start();
        }
    }
    
    /**
     * Method to play a side of YouTube tracks
     */
    private void playYouTubeTracks(int side, String tapeID, int muteTime) {
        // make sure we stop any previous threads
        if (player != null) {
            player.stop();
            player = null;
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
                
                // play the youtube trackNum now
                try {
                    int trackNum = 1;
                    int currentPlayTime = 1;
                    
                    for(AudioInfo audioInfo: audioList) {
                        // check to see if playback was stopped
                        if(!playSide) {
                            break;
                        }
                        
                        jlist.setSelectedIndex(trackNum -1);
                        //trackLabel.setText("Playing Track: " + String.format("%02d", trackNum));
                        System.out.println("Playing " + audioInfo + " on side: " + sideString);
                        
                        // play the spotify trackNum
                        String videoId = audioInfo.getStreamId();
                        int length = audioInfo.getLength();
                        
                        if(videoId != null) {
                            playingYouTube = deckCastConnector.playSingleTrack(videoId, trackNum);
                            if(!playingYouTube) {
                                JOptionPane.showMessageDialog(null, "Error Playing YouTube Track\n"
                                        + "No Active Player Found");
                                break;
                            }
                        } else {
                            continue;
                        }
                        
                        // wait for playback to stop, assume player takes 1 second to load
                        int playTime = 1;
                        while(playingYouTube) {
                            //update display every second
                            String message = "Playing Track: " + String.format("%02d", trackNum) + 
                                        " (" + CassetteFlowUtil.getTimeString(playTime) + " | " +
                                        CassetteFlowUtil.getTimeString(currentPlayTime) + ")";
                            
                            trackLabel.setText(message);

                            if (playTime >= length) {
                                break;
                            }

                            playTime++;
                            currentPlayTime++;
                            Thread.sleep(1000);
                        }
                        
                        // pause a certain amount of time to create a mute section
                        Thread.sleep(muteTime*1000);
                        currentPlayTime += muteTime;
                        trackNum++;
                    }
                    
                    // stop the playback if needed
                    deckCastConnector.stopStream();
                                        
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
                    playingYouTube = false;
                } catch (InterruptedException e) {
                    JOptionPane.showMessageDialog(null, "Error playing YouTube stream");
                }
            }
        }
        );
        playerThread.start();
    }
    
    /**
     * Stop audio playback
     * @param evt 
     */
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
        
        // finally check to see if we playing youtube
        if(playingYouTube) {
            deckCastConnector.stopStream();
            playingYouTube = false;
            playButton.setEnabled(true);
        }
        
        // finally check to see if we playing spotify
        if(playingSpotify) {
            spotifyConnector.stopStream();
            playingSpotify = false;
            playButton.setEnabled(true);
        }
        
        if(deckCastConnectorDisplay != null) {
            deckCastConnectorDisplay.clearAudioInfoDisplay();
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
        
        // see if we might be playing youtube tracks instead
        if(deckCastConnector != null) {
            playYouTubeTracks(side, tapeID, muteTime);
            return;
        }
        
        // see if we might be playing spotify tracks instead
        if(spotifyConnector != null) {
            playSpotifyTracks(side, tapeID, muteTime);
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
                    int trackNum = 1;
                    int currentPlayTime = 0;
                            
                    for(AudioInfo audioInfo: audioList) {
                        // check to see if playback was stopped
                        if(!playSide) {
                            player.stop();
                            break;
                        }
                        
                        jlist.setSelectedIndex(trackNum -1);
                        //trackLabel.setText("Playing Track: " + String.format("%02d", trackNum));
                        System.out.println("Playing " + audioInfo + " on side: " + sideString);
                        
                        // check to make sure we are not trying to play a streaming audio track
                        if (audioInfo.getStreamId() != null || audioInfo.getUrl() != null) {
                            String streamType = "YouTube";
                            if(audioInfo.getUrl().contains("spotify")) {
                                streamType = "Spotify";
                            }
                            
                            String message = "Playback Error for " + streamType + " Track: " + audioInfo + 
                                    "\nConnect to Streaming Backend ....";
                            
                            // display an error message dialog for user
                            JOptionPane.showMessageDialog(null, message,
                                    "Error Playing Side", JOptionPane.ERROR_MESSAGE);
                           
                            System.out.println(message);
                            break;
                        }
                        
                        // reset the player to free up resources here
                        player.reset();
                        
                        player.open(audioInfo.getFile());
                        player.play(); 
                        
                        // send information to deckcast backend about playing trackNum
                        if (deckCastConnectorDisplay != null) {
                            deckCastConnectorDisplay.displayPlayingAudioInfo(audioInfo, 0, "mp3/FLAC", trackNum);
                        }

                        // wait for playback to stop
                        int loopCount = 0;
                        while(player.isPlaying()) {
                            //update display every second
                            if(loopCount%10 == 0) {
                                int playTime = loopCount/10;
                                currentPlayTime++;
                                        
                                String message = "Playing Track: " + String.format("%02d", trackNum) + 
                                        " (" + CassetteFlowUtil.getTimeString(playTime) + " | " +
                                        CassetteFlowUtil.getTimeString(currentPlayTime) + ")";
                                trackLabel.setText(message);
                            }
                            
                            loopCount++;
                            Thread.sleep(100);
                        }
                        
                        // pause a certain amount of time to create a mute section
                        Thread.sleep(muteTime*1000);
                        currentPlayTime += muteTime;
                        trackNum++;
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
                } catch (StreamPlayerException | InterruptedException e) {
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
        
        // clear any filtered audio
        filteredAudioList = null;
        
        // remove records from the list and hashmap database
        cassetteFlow.audioInfoList.clear();
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
            lyraTStopRawButtonActionPerformed(null);
            startDecodeButton.setEnabled(true);
        }
        
        if(cassettePlayer != null) {
            cassettePlayer.stop();
            startDecodeButton.setEnabled(true);
        }
        
        if(deckCastConnector != null) {
            deckCastConnector.stopStream();
            deckCastConnector.resetOldQueVideoId();
        }
        
        if(deckCastConnectorDisplay != null) {
            deckCastConnectorDisplay.clearAudioInfoDisplay();
        }
        
        if(spotifyConnector != null) {
            spotifyConnector.stopStream();
        }
        
        tracksInfoLabel.setText("## Track(s)");
        tapeInfoTextArea.setText("");
        trackInfoTextArea.setText("");
        playbackInfoTextArea.setText("Decoding process stopped ...");
        
        // send the stop encode information to any web clients getting data 
        cassetteFlow.setCurrentDecodeState("Decode Stopped", 0, false);
    }//GEN-LAST:event_stopDecodeButtonActionPerformed

    private void startDecodeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startDecodeButtonActionPerformed
        if(cassettePlayer != null) cassettePlayer.stop();
        
        // get the playback speed factor
        double speedFactor = 1.0;
        try {
            speedFactor = Double.parseDouble(playbackSpeedTextField.getText());
        } catch (NumberFormatException nfe) { }
        
        // get the mixer to output the audio two
        String outputMixerName = audioOutputComboBox.getSelectedItem().toString();
        
        cassettePlayer = new CassettePlayer(this, cassetteFlow, null);
        cassettePlayer.setMixerName(outputMixerName);
        cassettePlayer.setSpeedFactor(speedFactor);
        
        if(deckCastConnector != null) {
            cassettePlayer.setDeckCastConnector(deckCastConnector);
            setPlayingCassetteID("STR0A");
        }
        
        if(spotifyConnector != null) {
            cassettePlayer.setSpotifyConnector(spotifyConnector);
            setPlayingCassetteID("STR0A");
        }
        
        // set the cassett flow connector for displaying information
        if(deckCastConnectorDisplay != null) {
            cassettePlayer.setDeckCastConnectorDisplay(deckCastConnectorDisplay);
        }
        
        // check to see if we not getting the line record from the lyraT device 
        if(lyraTConnect != null && lyraTRawDataReadCheckBox.isSelected()) {
            // start pulling raw records from lyraT if its not already doing so
            if(!lyraTReadLineRecords) {
                lyraTGetRawButtonActionPerformed(null);
            }
        } else {
            try {
                cassettePlayer.startMinimodem(0);
            } catch (Exception ex) {
                String message = "Error Decoding With Minimodem";
                    JOptionPane.showMessageDialog(this, message, "Minimodem Error", JOptionPane.ERROR_MESSAGE);
                Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if(deckCastConnector == null && spotifyConnector == null) {
            playbackInfoTextArea.setText("Starting decoding process ...\n");
        } else {
            if(deckCastConnector != null) {
                trackInfoTextArea.setText("Stream @ " + deckCastConnector.getServerUrl());
            } else {
                trackInfoTextArea.setText("Stream @ Spotify");
            }
            
            playbackInfoTextArea.setText("Starting decoding process for Stream ...\n");
        }
        
        startDecodeButton.setEnabled(false);
    }//GEN-LAST:event_startDecodeButtonActionPerformed
        
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
        
        if(direction == 1) { // move the trackNum up the list
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
     * Move the trackNum up by one if not the first trackNum
     * 
     * @param evt 
     */
    private void moveTrackUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveTrackUpButtonActionPerformed
        moveTrackPosition(1);
    }//GEN-LAST:event_moveTrackUpButtonActionPerformed
    
    /**
     * Move the trackNum down by one position of not the first item
     * 
     * @param evt 
     */
    private void moveTrackDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveTrackDownButtonActionPerformed
        moveTrackPosition(0);
    }//GEN-LAST:event_moveTrackDownButtonActionPerformed

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
                lyraTConsoleTextArea.append("Local Cassette Flow Server Stopped ...\n\n");
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
            
            // try getting information to see if we connected
            String info = lyraTConnect.getInfo();
            if(info != null) {
                lyraTConsoleTextArea.append("Connected to CassetteFlow Server @ " + host + "\n\n");
                
                // save the host
                cassetteFlow.setLyraTHost(host);
                
                if(!lyraTRawDataReadCheckBox.isSelected()) {
                    // clear the UI and databases
                    clearAudioListButtonActionPerformed(null);
                
                    // load the database from LyraT
                    loadLyraTDatabases();
                
                    // update the UI to indicate we are connect to the remote lyraT host
                    addAudioInfoToJList();
                
                    lyraTConsoleTextArea.append("Loaded Audio Database ...\n\nLoaded Tape Database ...\n\n");
                
                    currentAudioDirectory = "ESP32LyraT @ " + cassetteFlow.LYRA_T_HOST;
                    directoryTextField.setText(currentAudioDirectory);
                
                    // disable buttons which should not work when connected to LyraT
                    playButton.setEnabled(false);
                    realtimeEncodeButton.setEnabled(false);
                    createDownloadButton.setEnabled(false);
                }
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
        tapeDBFrame = new TapeDatabaseFrame();
        boolean remoteDB = false;
        
        if(lyraTConnect == null) {
            tapeDBFrame.setTitle("Tape Database (" + CassetteFlow.TAPE_DB_FILENAME + ")");
        } else {
            tapeDBFrame.setTitle("Tape Database LyraT@" + cassetteFlow.LYRA_T_HOST);
            remoteDB = true;
        }
        
        tapeDBFrame.setCassetteFlowFrame(this, remoteDB);
        tapeDBFrame.setVisible(true);
    }//GEN-LAST:event_viewTapeDBButtonActionPerformed
    
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
        // server to trackNum the encoding process
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
        // server to trackNum the playing process
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
            
            // scan this directory and build search index
            buildAudioIndexButtonActionPerformed(null);
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
            if(!playSide && (player != null && player.isPlaying() || playingYouTube || playingSpotify)) {
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
     * Check the trackNum list in the A/B sides for duplicates
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
    
    /**
     * Method to add a Dynamic Content Track which allows for dynamic playback
     * audio of any digital content from a generically encode timestamp tape i.e DTC tape
     * 
     * @param evt 
     */
    private void createDCTButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createDCTButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        int tapeLength = getMaxTapeTime(); // tape length per side
        
        DefaultListModel model;
        JLabel sideLabel;
        ArrayList<AudioInfo> audioList;
        
        // see which side of the tape we adding sounds to
        if (side == 1) {
            model = (DefaultListModel) sideBJList.getModel();
            sideLabel = sideBLabel;
            audioList = sideBList;
        } else {
            model = (DefaultListModel) sideAJList.getModel();
            sideLabel = sideALabel;
            audioList = sideAList;
        }

        int count = model.getSize() + 1;
        
        // see if to create a dummy audio info object for creating a DCT text file
        // to be recorded
        if(count == 1) {
            tapeLength += 120; // add 2 minutes to total tape length
            
            // set the tape id to DCT1
            tapeIDTextField.setText("DCT0");
            
            // add a dummy audio info 
            String timeString = CassetteFlowUtil.getTimeString(tapeLength);
            File file = new File("DCT -- Dynamic Content Track");
            AudioInfo audioInfo = new AudioInfo(file, "aaaaaaaaaa", tapeLength, timeString, 0);
            String trackCount = String.format("%02d", count);
            String trackName = "[" + trackCount + "] " + audioInfo;
            model.addElement(trackName);
            audioList.add(audioInfo);

            calculateTotalTime(audioList, sideLabel);
        } else {
            // we need to create and set the DCT arrays for side A and side B
            // in the cassetteflow object
            String tapeID = tapeIDTextField.getText();
            int muteTime = Integer.parseInt(muteJTextField.getText());
            
            // see what to set the max block time to in seconds. If not -1 then
            // tracks are spaced so that we do not partially play tracks before
            // the DCT tracks on the physical media runs out
            maxTimeBlock = -1;
            if(padDCTCheckBox.isSelected()) {
                maxTimeBlock = getMaxTapeTime();
                timeBlockEndTracks = new ArrayList<>();
            }
            
            if(deckCastConnector != null) {
                deckCastConnector.updateDCTList(sideAList, sideBList, muteTime, maxTimeBlock);
            } else if(spotifyConnector != null) {
                spotifyConnector.updateDCTList(sideAList, sideBList, muteTime, maxTimeBlock);
            } else {
                cassetteFlow.createDCTArrayList(tapeID, sideAList, sideBList, muteTime, maxTimeBlock);
            }
            
            String newLabelText;
            
            String labelText = sideALabel.getText();
            if(!labelText.contains("Set As Dynamic Content")) {
                newLabelText = labelText + " || Set As Dynamic Content ...";
            } else {
                newLabelText = labelText + ".";
            }
            sideALabel.setText(newLabelText);
            
            labelText = sideBLabel.getText();
            if(!labelText.contains("Set As Dynamic Content")) {
                newLabelText = labelText + " || Set As Dynamic Content ...";
            } else {
                newLabelText = labelText + ".";
            }
            sideBLabel.setText(newLabelText);
        }
    }//GEN-LAST:event_createDCTButtonActionPerformed

    private void viewCurrentTapeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewCurrentTapeButtonActionPerformed
        viewTapeDBButtonActionPerformed(evt);
        tapeDBFrame.setSelectedTapeId(currentCassetteId);
        tapeDBFrame.setSelectedTrack(currentPlayingTrack);
    }//GEN-LAST:event_viewCurrentTapeButtonActionPerformed
    
    private void dctOffsetComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dctOffsetComboBoxActionPerformed
        try {
            String value = dctOffsetComboBox.getSelectedItem().toString();
            int offset = Integer.parseInt(value);
            cassetteFlow.setDCTOffset(offset);
        } catch(NumberFormatException nfe) { }
    }//GEN-LAST:event_dctOffsetComboBoxActionPerformed
    
    /**
     * Connect to a streaming media service
     * @param evt 
     */
    private void streamConnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_streamConnectButtonActionPerformed
        try {
            String streamUrl = streamComboBox.getSelectedItem().toString();
            String streamPin = streamPinTextField.getText();
            
            // get the max time block if we need it
            maxTimeBlock = -1;
            if(padDCTCheckBox.isSelected()) {
                maxTimeBlock = getMaxTapeTime();
            }
            
            if(streamUrl.contains("spotify")) {
                spotifyConnector = new SpotifyConnector(this, cassetteFlow);
                spotifyConnector.setMaxTimeBlock(maxTimeBlock);
                URI uri = spotifyConnector.getAuthorizationCodeUri();
                
                // launch the browser to complete login
                Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
                if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                }
            } else if(streamPin.equals("-1")) {
                deckCastConnectorDisplay = new DeckCastConnector(null, null, streamUrl, "CF");
            } else {
                deckCastConnector = new DeckCastConnector(this, cassetteFlow, streamUrl, streamPin);
                deckCastConnector.setMaxTimeBlock(maxTimeBlock);
            }
        } catch (IOException | URISyntaxException ex) {
            Logger.getLogger(DeckCastConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_streamConnectButtonActionPerformed

    private void streamDisconnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_streamDisconnectButtonActionPerformed
        if(deckCastConnector != null) {
            deckCastConnector.disConnect();
            deckCastConnector = null;
        }
        
        if(spotifyConnector != null) {
            spotifyConnector.disConnect();
            spotifyConnector = null;
        }
        
        // clear the display
        updateStreamEditorPane("Stream Player Disconnected ...");
        streamConnectButton.setEnabled(true);
    }//GEN-LAST:event_streamDisconnectButtonActionPerformed

    /**
     * Add to the side playlist of a + button is pressed
     * 
     * @param evt 
     */
    private void audioJListKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_audioJListKeyPressed
        //System.out.println("Key pressed code=" + evt.getKeyCode() + ", char=" + evt.getKeyChar());
        if(evt.getKeyChar() == '+') {
            addAudioToTapeListButtonActionPerformed(null);
        }
    }//GEN-LAST:event_audioJListKeyPressed
    
    /**
     * Detect key presses to implement support for the buttons on the Reterminal
     * @param evt 
     */
    private void mainTabbedPaneKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_mainTabbedPaneKeyPressed
        System.out.println("Key pressed code=" + evt.getKeyCode() + ", char=" + evt.getKeyChar());
        
        char key  = evt.getKeyChar();
        
        if(key == 'a') {
            startDecodeButtonActionPerformed(null);
        } else if(key == 'f') {
            stopDecodeButtonActionPerformed(null);
        }
    }//GEN-LAST:event_mainTabbedPaneKeyPressed
    
    /**
     * Load the trackNum list information for side A and B here to make easier
 to create J-cards using an online template such as
 https://ed7n.github.io/jcard-template/
     * 
     * @param evt 
     */
    private void refreshTrackListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshTrackListButtonActionPerformed
        StringBuilder sb = new StringBuilder();
        
        if(sideAList != null) {
            sb.append("SIDE A\n");
            for (AudioInfo audioInfo : sideAList) {
                sb.append(audioInfo.getBasicName()).append("\n");
            }
            sb.append("\n\n");
        }
        
        if(sideBList != null) {
            sb.append("SIDE B\n");
            for (AudioInfo audioInfo : sideBList) {
                sb.append(audioInfo.getBasicName()).append("\n");
            }
        }
        
        String info = sb.toString().trim();        
        trackListInfoTextArea.setText(info);
    }//GEN-LAST:event_refreshTrackListButtonActionPerformed
    
    /**
     * Open the website for the jcard template site
     * 
     * @param evt 
     */
    private void jcardSiteTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jcardSiteTextFieldActionPerformed
        try {
            String url = jcardSiteTextField.getText();
            
            URI uri = new URI(url);
            Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(uri);
            }
        } catch (URISyntaxException | IOException ex) {
            Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_jcardSiteTextFieldActionPerformed

    private void jcardSiteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jcardSiteButtonActionPerformed
        jcardSiteTextFieldActionPerformed(null);
    }//GEN-LAST:event_jcardSiteButtonActionPerformed
    
    /**
     * Open the jcard template file so it can be populated with the 
     * tracklist and date information
     * 
     * @param evt 
     */
    private void loadTemplateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadTemplateButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return "J Card Template (*.jcard.json)";
            }

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                } else {
                    return f.getName().toLowerCase().endsWith(".jcard.json");
                }
            }
        });
        
        int result = fileChooser.showOpenDialog(this);
 
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                System.out.println("Loading Template File: " + selectedFile.getAbsolutePath());
                
                String jsonText = CassetteFlowUtil.getContentAsString(selectedFile);
                JSONObject js = new JSONObject(jsonText);
                
                if(js.has("sideAContents") && js.has("sideBContents")) {
                    jcardJSON = js;
                    JOptionPane.showMessageDialog(this, "JCard Template Loaded");
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid JCard Template");
                }
            } catch(IOException | JSONException ex) {
                JOptionPane.showMessageDialog(this, "Invalid JCard Template");
            }
        }
    }//GEN-LAST:event_loadTemplateButtonActionPerformed
    
    /**
     * Export the json file need for creation of jcard
     * 
     * @param evt 
     */
    private void exportTemplateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportTemplateButtonActionPerformed
        try {
            jcardJSON.put("titleUpper", jcardTitleTextField.getText());
            jcardJSON.put("titleLower", jcardGroupTextField.getText());
            
            LocalDate currentDate = LocalDate.now();
            int currentDay = currentDate.getDayOfMonth();
            String currentMonth = currentDate.getMonth().toString();
            int currentYear = currentDate.getYear();
            String date = currentMonth + " " + currentDay + " " + currentYear;
            jcardJSON.put("noteLower", date);
            
            String[] sa = trackListInfoTextArea.getText().split("\n\n");
            String sideA = sa[0].replace("SIDE A", "").trim();
            String sideB = sa[1].replace("SIDE B", "").trim();
            jcardJSON.put("sideAContents", sideA);
            jcardJSON.put("sideBContents", sideB);
            
            // save the json file now for import into the online site
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Specify a file to save");

            int userSelection = fileChooser.showSaveDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave));
                writer.write(jcardJSON.toString(2));
                writer.close();
            }
        } catch (JSONException | IOException ex) {
            Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_exportTemplateButtonActionPerformed
    
    /**
     * Store the current sideA and sideB to the database
     * @param evt 
     */
    private void storeToTapeDBButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_storeToTapeDBButtonActionPerformed
        String tapeID = tapeIDTextField.getText();
        cassetteFlow.addToTapeDB(tapeID, sideAList, sideBList, true);
    }//GEN-LAST:event_storeToTapeDBButtonActionPerformed
    
    /**
     * 
     * @param evt 
     */
    private void findTracksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_findTracksButtonActionPerformed
        if(trackFinderFrame == null) {
            trackFinderFrame = new TrackFinderFrame();
            trackFinderFrame.setTitle("Track Finder (" + cassetteFlow.audioInfoDB.size() + " files)");
            trackFinderFrame.setCassetteFlowFrame(this);
            trackFinderFrame.setVisible(true);
        } else {
            trackFinderFrame.setVisible(true);
        }
    }//GEN-LAST:event_findTracksButtonActionPerformed

    private void streamPlayClearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_streamPlayClearButtonActionPerformed
        if(deckCastConnector != null) {
            deckCastConnector.clearQueList();
        } else if(spotifyConnector != null) {
            spotifyConnector.clearQueList();
        } else {
            updateStreamEditorPane("");
        }
    }//GEN-LAST:event_streamPlayClearButtonActionPerformed

    private void streamPinTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_streamPinTextFieldActionPerformed
        // if we connected to spotify load a Spotify playlist or album for DCT playback 
        if(spotifyConnector != null) {
            String[] info = spotifyConnector.getSpotifyMediaId(streamPinTextField.getText());
            spotifyConnector.loadPlaylist(info[1], true);
        } else {
            try {
                // create a deckcast connector for displaying the current audio trackNum playing through mp3 or spotify
                String streamUrl = streamComboBox.getSelectedItem().toString();
                deckCastConnectorDisplay = new DeckCastConnector(null, null, streamUrl, "CF");
                
                // print message to the stream editor pane
                updateStreamEditorPane("DeckcastDJ Stream Player For Display @ " + streamUrl + "playing\n");
            } catch (URISyntaxException ex) {
                Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_streamPinTextFieldActionPerformed
    
    /**
     * Function to set the the decoder data source. Either local minimodem or a 
     * lyraT board or cassetteflow server over http
     * 
     * @param evt 
     */
    private void decoderSourceComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decoderSourceComboBoxActionPerformed
        Object source = decoderSourceComboBox.getSelectedItem();
        if(source == null) return;
        
        if (source.equals("JMinimodem")) {
            decorderSourceLabel.setText("Using Local Decoder ...");
            lyraTConnect = null;
        } else {
            if (lyraTConnect == null) {
                String host = source.toString().trim();
                if (!host.endsWith("/")) {
                    host += "/";
                }

                lyraTConnect = new ESP32LyraTConnect(host);

                // try getting information to see if we connected to a lyraT boarder
                // or cassetteflow server
                String info = lyraTConnect.getInfo();
                
                if (info != null) {
                    decorderSourceLabel.setText("Connected to Server ...");

                    // save the host
                    cassetteFlow.setLyraTHost(host);
                } else {
                    decorderSourceLabel.setText("Connection Error ...");
                    lyraTConnect = null;
                }
            }
        }       
    }//GEN-LAST:event_decoderSourceComboBoxActionPerformed

    private void padDCTCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_padDCTCheckBoxActionPerformed
        if(padDCTCheckBox.isSelected()) {
            maxTimeBlock = getMaxTapeTime();
            
            if(deckCastConnector != null) {
                deckCastConnector.setMaxTimeBlock(maxTimeBlock);
            }
            
            if(spotifyConnector != null) {
                spotifyConnector.setMaxTimeBlock(maxTimeBlock);
            }
        } else {
            timeBlockEndTracks = new ArrayList<>();
        }
    }//GEN-LAST:event_padDCTCheckBoxActionPerformed

    /**
     * Find all audio files in the root/subdirectories then build index
     * @param evt 
     */
    private void buildAudioIndexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buildAudioIndexButtonActionPerformed
        printToConsole("Finding All Audio Files ...\n", false);

        Thread thread = new Thread("Audio Indexer Thread") {
            @Override
            public void run() {
                cassetteFlow.buildAudioFileIndex(currentAudioDirectory);
            }
        };
        thread.start();
    }//GEN-LAST:event_buildAudioIndexButtonActionPerformed

    private void reloadAudioOutputsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadAudioOutputsButtonActionPerformed

    }//GEN-LAST:event_reloadAudioOutputsButtonActionPerformed

    private void shuffleFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shuffleFilterTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_shuffleFilterTextFieldActionPerformed

    private void filterShuffleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterShuffleCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_filterShuffleCheckBoxActionPerformed

    private void setAudioDownloadServerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setAudioDownloadServerButtonActionPerformed
        String mp3DownloadServer = audioDownloadServerTextField.getText();
        cassetteFlow.setDownloadServer(mp3DownloadServer);
    }//GEN-LAST:event_setAudioDownloadServerButtonActionPerformed

    private void baudRateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_baudRateButtonActionPerformed
        try {
            String baud = baudRateTextField.getText().trim();
            Integer.parseInt(baud);
            cassetteFlow.BAUDE_RATE = baud;
        } catch(NumberFormatException nfe) { }
    }//GEN-LAST:event_baudRateButtonActionPerformed

    private void clearConsoleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearConsoleButtonActionPerformed
        consoleTextArea.setText("Output Console >\n");
    }//GEN-LAST:event_clearConsoleButtonActionPerformed

    /**
     * 
     * @param text 
     */
    public void updateStreamEditorPane(String text) {
        SwingUtilities.invokeLater(() -> {
            String html = "<html>" + text + "</html>";
            streamEditorPane.setText(html);
            streamEditorPane.setCaretPosition(0); // Scroll to the top
        });
    }
    
    /**
     * Set the total playtime for the stream player
     * 
     * @param streamId The unique id of the stream audio
     * @param streamTotalTime The total playtime stream
     * @param streamPlayer The stream player number
     */
    public void setStreamInformation(String streamId, int streamTotalTime, int streamPlayer) {
        this.streamId = streamId;
        this.streamTotalTime = streamTotalTime;
        this.streamPlayer = streamPlayer;
        
        // update the timer label
        updateStreamPlaytime(0, "");
    }
    
    /**
     * Update the stream play time
     * @param streamPlaytime The stream playtime
     * @param track The trackNum number from a que list
     */
    public void updateStreamPlaytime(int streamPlaytime, String track) {
        // update the timer label
        String totalTimeString = CassetteFlowUtil.getTimeString(streamTotalTime);
        String playTimeString = CassetteFlowUtil.getTimeString(streamPlaytime);        
        streamPlaytimeLabel.setText("Play Time: " + playTimeString + "/" + totalTimeString + " " + track);
    }
    
    public void setStreamPlayerConnected(boolean connected) {
        if(connected) {
            // clear the dct array
            cassetteFlow.clearDCTArrayList();
            streamConnectButton.setEnabled(false);
        }
    }
   
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
    private javax.swing.JButton buildAudioIndexButton;
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
    private javax.swing.JButton createDCTButton;
    private javax.swing.JButton createDownloadButton;
    private javax.swing.JComboBox<String> dctOffsetComboBox;
    private javax.swing.JComboBox<String> decoderSourceComboBox;
    private javax.swing.JLabel decorderSourceLabel;
    private javax.swing.JButton defaultButton;
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
    private javax.swing.JButton exportTemplateButton;
    private javax.swing.JButton filterAudioListButton;
    private javax.swing.JCheckBox filterShuffleCheckBox;
    private javax.swing.JButton findTracksButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
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
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JTextField jcardGroupTextField;
    private javax.swing.JButton jcardSiteButton;
    private javax.swing.JTextField jcardSiteTextField;
    private javax.swing.JTextField jcardTitleTextField;
    private javax.swing.JButton loadTemplateButton;
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
    private javax.swing.JCheckBox lyraTRawDataReadCheckBox;
    private javax.swing.JButton lyraTServerTestDBButton;
    private javax.swing.JButton lyraTStopButton;
    private javax.swing.JButton lyraTStopRawButton;
    private javax.swing.JButton lyraTVolDownButton;
    private javax.swing.JButton lyraTVolUpButton;
    private javax.swing.JTabbedPane mainTabbedPane;
    private javax.swing.JButton moveTrackDownButton;
    private javax.swing.JButton moveTrackUpButton;
    private javax.swing.JTextField muteJTextField;
    private javax.swing.JCheckBox padDCTCheckBox;
    private javax.swing.JButton playButton;
    private javax.swing.JButton playSideButton;
    private javax.swing.JTextArea playbackInfoTextArea;
    private javax.swing.JTextField playbackSpeedTextField;
    private javax.swing.JButton realtimeEncodeButton;
    private javax.swing.JButton refreshTrackListButton;
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
    private javax.swing.JRadioButton speakerRadioButton;
    private javax.swing.JButton startDecodeButton;
    private javax.swing.JCheckBox startServerCheckBox;
    private javax.swing.JButton stopButton;
    private javax.swing.JButton stopDecodeButton;
    private javax.swing.JButton storeToTapeDBButton;
    private javax.swing.JComboBox<String> streamComboBox;
    private javax.swing.JButton streamConnectButton;
    private javax.swing.JButton streamDisconnectButton;
    private javax.swing.JEditorPane streamEditorPane;
    private javax.swing.JTextField streamPinTextField;
    private javax.swing.JButton streamPlayClearButton;
    private javax.swing.JLabel streamPlaytimeLabel;
    private javax.swing.JTextField tapeIDTextField;
    private javax.swing.JTextArea tapeInfoTextArea;
    private javax.swing.JTabbedPane tapeJTabbedPane;
    private javax.swing.JComboBox<String> tapeLengthComboBox;
    private javax.swing.JLabel trackALabel;
    private javax.swing.JLabel trackBLabel;
    private javax.swing.JTextArea trackInfoTextArea;
    private javax.swing.JTextArea trackListInfoTextArea;
    private javax.swing.JLabel tracksInfoLabel;
    private javax.swing.JLabel tracksLabel;
    private javax.swing.JButton viewCurrentTapeButton;
    private javax.swing.JButton viewTapeDBButton;
    // End of variables declaration//GEN-END:variables

}
