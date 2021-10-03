/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cassetteflow;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javazoom.jl.player.Player;

/**
 * Main User Interface for the cassette flow program
 * @author Nathan
 */
public class CassetteFlowFrame extends javax.swing.JFrame {
    private CassetteFlow cassetteFlow;
    
    private CassettePlayer cassettePlayer;
    
    private ArrayList<MP3Info> sideAList = new ArrayList<>();
    
    private ArrayList<MP3Info> sideBList = new ArrayList<>();
    
    private ArrayList<MP3Info> sideNList = new ArrayList<>();
    
    private Player player = null;
    
    private Thread playerThread = null;
    
    private boolean playSide = false; // used to indicated if a side is being played
    
    private boolean isFullScreen = false;
    /**
     * Creates new form CassetteFlowFrame
     */
    public CassetteFlowFrame() {
        initComponents(); 
        DefaultListModel model = new DefaultListModel();
        mp3JList.setModel(model);
        
        model = new DefaultListModel();
        sideAJList.setModel(model);
        
        model = new DefaultListModel();
        sideBJList.setModel(model);
    }
    
    public void initFullScreen() {
       GraphicsEnvironment env = GraphicsEnvironment
                .getLocalGraphicsEnvironment();
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
    
    public void setCassetteFlow(CassetteFlow cassetteFlow) {
        this.cassetteFlow = cassetteFlow;
        directoryTextField.setText(CassetteFlow.MP3_DIR_NAME);
        logfileTextField.setText(CassetteFlow.LOG_FILE_NAME);
        addMP3FilesToList();
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
                
                ArrayList<String> mp3Ids = cassetteFlow.cassetteDB.get(cassetteID);
                
                if(mp3Ids != null) {
                   for(int i = 0; i < mp3Ids.size(); i++) {
                       MP3Info mp3Info = cassetteFlow.mp3sMap.get(mp3Ids.get(i));
                       String trackCount = String.format("%02d", (i + 1));
                       tapeInfoTextArea.append("[" + trackCount + "] " + mp3Info + "\n");
                   }
                } else {
                   tapeInfoTextArea.setText("Invalid Tape ID ...");
                }
            }
        });
    }
    
    public void setPlayingMP3Info(final String info) {
        SwingUtilities.invokeLater(() -> {
            trackInfoTextArea.setText(info);
        });
    }
    
    public void setPlaybackInfo(final String info, boolean append) {
        SwingUtilities.invokeLater(() -> {
            if(!append) {
                playbackInfoTextArea.setText(info);
            } else {
                playbackInfoTextArea.append(info + "\n");
            }
        });
    }
    
    /**
     * Add the mp3s file information to the list
     */
    private void addMP3FilesToList() {
        DefaultListModel model = (DefaultListModel) mp3JList.getModel();

        for (MP3Info mp3Info: cassetteFlow.mp3sList) {
            try {
                System.out.println("MP3 File: " + mp3Info);
                model.addElement(mp3Info);
            } catch (Exception ex) {
                Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        mp3CountLabel.setText(cassetteFlow.mp3sList.size() + " MP3s files loaded ...");
    }
    
    /**
     * A way to directly encode the data without 
     */
    private void directEncode(boolean forDownload) {                                                   
        int muteTime = Integer.parseInt(muteJTextField.getText());
        String saveDirectoryName = directoryTextField.getText() + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
        String tapeID = tapeIDTextField.getText();
        
        encodeProgressBar.setIndeterminate(true);
        createButton.setEnabled(false);
        createDownloadButton.setEnabled(false);
        
        final JFrame frame = this;
        Thread thread = new Thread("Encode Thread") {
            public void run() {
                try {
                    cassetteFlow.directEncode(saveDirectoryName, tapeID, sideAList, sideBList, muteTime, forDownload);
                } catch (IOException ex) {
                    String message = "Error Encoding With Minimodem";
                    JOptionPane.showMessageDialog(frame, message, "Minimodem Error", JOptionPane.ERROR_MESSAGE);

                    Logger.getLogger(CassetteFlowFrame.class.getName()).log(Level.SEVERE, null, ex);
                    setEncodingDone();
                }
            }
        };
        thread.start();
    } 
    
    public void setEncodingDone() {
        encodeProgressBar.setIndeterminate(false);
        createButton.setEnabled(true);
        createDownloadButton.setEnabled(true);
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
                consoleTextArea.setText(text);
            } else {
                consoleTextArea.append(text + "\n");
            }
        });
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

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
        mp3JList = new javax.swing.JList<>();
        jLabel1 = new javax.swing.JLabel();
        tapeIDTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        tapeLengthComboBox = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        muteJTextField = new javax.swing.JTextField();
        addMP3Button = new javax.swing.JButton();
        removeMP3Button = new javax.swing.JButton();
        removeAllButton = new javax.swing.JButton();
        shuffleButton = new javax.swing.JButton();
        playButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        clearSelectionButton = new javax.swing.JButton();
        playSideButton = new javax.swing.JButton();
        clearMP3ListButton = new javax.swing.JButton();
        moveTrackUpButton = new javax.swing.JButton();
        moveTrackDownButton = new javax.swing.JButton();
        directEncodeCheckBox = new javax.swing.JCheckBox();
        encodeProgressBar = new javax.swing.JProgressBar();
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
        jPanel5 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        consoleTextArea = new javax.swing.JTextArea();
        exitButton = new javax.swing.JButton();
        setMP3DirectoryButton = new javax.swing.JButton();
        createButton = new javax.swing.JButton();
        createDownloadButton = new javax.swing.JButton();
        mp3CountLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("CassetteFlow v 0.5.1 (10/03/2021)");

        jTabbedPane1.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N

        directoryTextField.setText("C:\\\\mp3files");
        directoryTextField.setToolTipText("");
        directoryTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                directoryTextFieldActionPerformed(evt);
            }
        });

        jScrollPane2.setViewportView(sideAJList);

        sideALabel.setText("Side A track Info goes here ...");

        trackALabel.setText(" ");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 593, Short.MAX_VALUE)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(sideALabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(trackALabel, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(trackALabel)
                    .addComponent(sideALabel))
                .addContainerGap())
        );

        tapeJTabbedPane.addTab("Side A", jPanel3);

        jScrollPane3.setViewportView(sideBJList);

        sideBLabel.setText("Side B track Info goes here ...");

        trackBLabel.setText(" ");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 593, Short.MAX_VALUE)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(sideBLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(trackBLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(trackBLabel)
                    .addComponent(sideBLabel))
                .addGap(9, 9, 9))
        );

        tapeJTabbedPane.addTab("Side B", jPanel4);

        sideNJList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane7.setViewportView(sideNJList);

        sideNLabel.setText("MP3 Split Information Goes Here ...");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane7)
            .addComponent(sideNLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 593, Short.MAX_VALUE)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 244, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(sideNLabel)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        tapeJTabbedPane.addTab("MP3 Split", jPanel6);

        mp3JList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "MP3 File 1", "MP3 File 2" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(mp3JList);

        jLabel1.setText("4 Digit Cassette ID");

        tapeIDTextField.setText("0010");

        jLabel2.setText("Tape Length");

        tapeLengthComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "60 Minutes", "90 Minutes", "110 Minutes", "120 Minutes" }));
        tapeLengthComboBox.setSelectedIndex(1);

        jLabel3.setText("Mute (s)");

        muteJTextField.setText("4");

        addMP3Button.setText("Add");
        addMP3Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addMP3ButtonActionPerformed(evt);
            }
        });

        removeMP3Button.setText("Remove");
        removeMP3Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeMP3ButtonActionPerformed(evt);
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

        clearMP3ListButton.setText("Clear");
        clearMP3ListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearMP3ListButtonActionPerformed(evt);
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
        directEncodeCheckBox.setText("Direct Encode");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(directoryTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                    .addComponent(jScrollPane1)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(playButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(stopButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearSelectionButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearMP3ListButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tapeJTabbedPane)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(moveTrackUpButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(moveTrackDownButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(playSideButton))
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
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(addMP3Button)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(removeMP3Button)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(removeAllButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(shuffleButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(directEncodeCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(encodeProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addContainerGap())))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(directoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(tapeIDTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(tapeLengthComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(muteJTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(encodeProgressBar, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(addMP3Button)
                                .addComponent(removeMP3Button)
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
                    .addComponent(clearMP3ListButton)
                    .addComponent(moveTrackUpButton)
                    .addComponent(moveTrackDownButton)
                    .addComponent(playSideButton)))
        );

        jTabbedPane1.addTab("ENCODE", jPanel1);

        logfileTextField.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        logfileTextField.setText("C:\\\\mp3files\\\\TapeFiles\\\\tape.log");

        logfileButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        logfileButton.setText("Set Logfile");
        logfileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logfileButtonActionPerformed(evt);
            }
        });

        startDecodeButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        startDecodeButton.setText("Start Decoding");
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
        playbackInfoTextArea.setRows(5);
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
        stopDecodeButton.setText("Stop Decoding");
        stopDecodeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopDecodeButtonActionPerformed(evt);
            }
        });

        directDecodeCheckBox.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        directDecodeCheckBox.setSelected(true);
        directDecodeCheckBox.setText("Minimodem");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                        .addComponent(logfileTextField, javax.swing.GroupLayout.Alignment.LEADING))
                    .addComponent(tracksLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(logfileButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(directDecodeCheckBox)
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
                    .addComponent(directDecodeCheckBox))
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
                        .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE))
                    .addComponent(jScrollPane4)))
        );

        jTabbedPane1.addTab("DECODE", jPanel2);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 936, Short.MAX_VALUE)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 418, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("ESP32 LyraT", jPanel5);

        consoleTextArea.setColumns(20);
        consoleTextArea.setFont(new java.awt.Font("Monospaced", 0, 24)); // NOI18N
        consoleTextArea.setRows(5);
        consoleTextArea.setText("Output Console:");
        jScrollPane8.setViewportView(consoleTextArea);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 936, Short.MAX_VALUE)
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 418, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Output Console", jPanel7);

        exitButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        exitButton.setText("Exit");
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });

        setMP3DirectoryButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        setMP3DirectoryButton.setText("Set MP3 Directory");
        setMP3DirectoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setMP3DirectoryButtonActionPerformed(evt);
            }
        });

        createButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        createButton.setText("Create Input Files");
        createButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createButtonActionPerformed(evt);
            }
        });

        createDownloadButton.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        createDownloadButton.setText("Create HTTP Input Files");
        createDownloadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createDownloadButtonActionPerformed(evt);
            }
        });

        mp3CountLabel.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        mp3CountLabel.setText("0 MP3s");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
            .addGroup(layout.createSequentialGroup()
                .addComponent(setMP3DirectoryButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(mp3CountLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(18, 18, 18)
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(setMP3DirectoryButton)
                    .addComponent(createDownloadButton)
                    .addComponent(createButton)
                    .addComponent(exitButton)
                    .addComponent(mp3CountLabel)))
        );

        setBounds(0, 0, 943, 535);
    }// </editor-fold>//GEN-END:initComponents

    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitButtonActionPerformed
        setVisible(false);
        System.exit(0);
    }//GEN-LAST:event_exitButtonActionPerformed
    
    /**
     * This set the current mp3 directory and loads any mp3 files found within
     * 
     * @param evt 
     */
    private void setMP3DirectoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setMP3DirectoryButtonActionPerformed
        File mp3Dir = new File(directoryTextField.getText());
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(mp3Dir);
        chooser.setDialogTitle("Select MP3 Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            directoryTextField.setText(chooser.getSelectedFile().toString());
            directoryTextFieldActionPerformed(null);
        } else {
            System.out.println("No Selection ");
        }
    }//GEN-LAST:event_setMP3DirectoryButtonActionPerformed

    private void addMP3ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addMP3ButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        
        List selectedMp3s = mp3JList.getSelectedValuesList();
        DefaultListModel model;
        JLabel sideLabel;
        ArrayList<MP3Info> mp3List;
        
        // see which side of the tape we adding sounds to
        if(side == 2) {
            model = (DefaultListModel) sideNJList.getModel();
            sideLabel = sideNLabel;
            mp3List = sideNList;
        } else if (side == 1) {
            model = (DefaultListModel) sideBJList.getModel();
            sideLabel = sideBLabel;
            mp3List = sideBList;
        } else {
            model = (DefaultListModel) sideAJList.getModel();
            sideLabel = sideALabel;
            mp3List = sideAList;
        }

        int count = model.getSize() + 1;
        
        // add the new mp3s
        for(int i = 0; i < selectedMp3s.size(); i++) {
            MP3Info mp3Info = (MP3Info)selectedMp3s.get(i);
            String trackCount = String.format("%02d", (i + count));
            String trackName = "[" + trackCount + "] " + mp3Info;
            model.addElement(trackName);
            mp3List.add(mp3Info);
        }
        
        calculateTotalTime(mp3List, sideLabel);
    }//GEN-LAST:event_addMP3ButtonActionPerformed
    
    /**
     * Used to calculate the total playtime of mp3 in Side A or Side B
     * @param mp3List
     * @param sideLabel 
     */
    private void calculateTotalTime(ArrayList<MP3Info> mp3List, JLabel sideLabel) {
        int muteTime = Integer.parseInt(muteJTextField.getText());
         
        // calculate the total time
        int totalTime = 0;
        for(MP3Info mp3Info: mp3List) {
            totalTime += mp3Info.getLength();
        }
        
        totalTime += (mp3List.size() - 1)*muteTime;
        
        String warning = "";
        if(totalTime > getMaxTapeTime()) {
            warning = " (*** Max Time Exceeded ***)";
        }
        
        sideLabel.setText("Play Time: " + cassetteFlow.getTimeString(totalTime) + " " + warning);
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
            default:
                return 3600;
        }
    }
    
    private void directoryTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_directoryTextFieldActionPerformed
        String mp3Directory = directoryTextField.getText();
        if(!mp3Directory.startsWith("http://")) {
            cassetteFlow.loadMP3Files(mp3Directory);
        
            // clear the current JList
            DefaultListModel model = (DefaultListModel) mp3JList.getModel();
            model.clear();
            addMP3FilesToList();
        } else {
            cassetteFlow.setDownloadServerRoot(mp3Directory);
        }
    }//GEN-LAST:event_directoryTextFieldActionPerformed

    private void removeMP3ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeMP3ButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        
        DefaultListModel model;
        JList jlist;
        JLabel sideLabel;
        ArrayList<MP3Info> mp3List;
        
        // see which side of the tape we adding sounds to
        if(side == 1) {
            jlist = sideBJList;
            sideLabel = sideBLabel;
            mp3List = sideBList;
        } else {
            jlist = sideAJList;
            sideLabel = sideALabel;
            mp3List = sideAList;
        }
        
        // remove a single entry
        model = (DefaultListModel) jlist.getModel();
        int index = jlist.getSelectedIndex();
        
        if(index != -1) {
            mp3List.remove(index);
            model.remove(index);
            calculateTotalTime(mp3List, sideLabel);
        }
    }//GEN-LAST:event_removeMP3ButtonActionPerformed

    private void playButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playButtonActionPerformed
        List selectedMp3s = mp3JList.getSelectedValuesList();
        
        if(selectedMp3s != null && selectedMp3s.size() >= 1) {
            final MP3Info mp3Info = (MP3Info) selectedMp3s.get(0);
            System.out.println("\nPlaying MP3: " + mp3Info);
            
            // make sure we stop any previous threads
            if(player != null) {
                player.close();
                player = null;
            }
            
            playerThread = new Thread( new Runnable() {
                @Override
                public void run() {
                    try {
                        FileInputStream mp3Stream = new FileInputStream(mp3Info.getFile());
                        player = new Player(mp3Stream);
                        player.play();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null, "Error playing mp3 file");
                    }
                }
            }
            );
            playerThread.start();
        }
    }//GEN-LAST:event_playButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        if(player != null) {
            player.close();
            player = null;
            
            if(playSide) {
                playSide = false;
                playSideButton.setEnabled(true);
            }
        }
    }//GEN-LAST:event_stopButtonActionPerformed

    private void clearSelectionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearSelectionButtonActionPerformed
        mp3JList.clearSelection();
    }//GEN-LAST:event_clearSelectionButtonActionPerformed

    private void removeAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAllButtonActionPerformed
        int side = tapeJTabbedPane.getSelectedIndex();
        
        DefaultListModel model;
        JList jlist;
        JLabel sideLabel;
        ArrayList<MP3Info> mp3List;
        
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
        
        DefaultListModel model;
        JLabel sideLabel;
        ArrayList<MP3Info> mp3List;
        
        // see which side of the tape we adding sounds to
        if(side == 1) {
            model = (DefaultListModel) sideBJList.getModel();
            sideLabel = sideBLabel;
            sideBList = new ArrayList<>();
            mp3List = sideBList;
        } else {
            model = (DefaultListModel) sideAJList.getModel();
            sideLabel = sideALabel;
            sideAList = new ArrayList<>();
            mp3List = sideAList;
        }
        
        // clear out the jlist model
        model.clear();
        
        // get a shuffle list of mp3s
        ArrayList<MP3Info> shuffledMp3s = cassetteFlow.shuffleMP3List();
        
        int currentTime = 0;
        int totalTime = 0;
        int trackCount = 1;
        for(int i = 0; i < shuffledMp3s.size(); i++) {
            MP3Info mp3Info = shuffledMp3s.get(i);
            
            // check to make sure we not duplicating mp3 on the A and B side as mp3
            if(side == 0) {
                if(sideBList.contains(mp3Info)) {
                    System.out.println("\nDuplicate MP3 On Side B : " + mp3Info);
                    continue;
                }
            } else {
                if(sideAList.contains(mp3Info)) {
                    System.out.println("\nDuplicate MP3 On Side A : " + mp3Info);
                    continue;
                }
            }
            
            currentTime += mp3Info.getLength();
            
            int timeWithMute = currentTime + muteTime*i;
            if(timeWithMute <= maxTime) {
                String trackCountString = String.format("%02d", trackCount);
                String trackName = "[" + trackCountString + "] " + mp3Info;
                model.addElement(trackName);
                mp3List.add(mp3Info);
                totalTime = timeWithMute;
                trackCount++;
            } else {
                break;
            }
        }
        
        sideLabel.setText("Play Time: " + cassetteFlow.getTimeString(totalTime));
    }//GEN-LAST:event_shuffleButtonActionPerformed

    private void createButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createButtonActionPerformed
        if (directEncodeCheckBox.isSelected()) {
            directEncode(false);
        } else {
            try {
                int muteTime = Integer.parseInt(muteJTextField.getText());
                String saveDirectoryName = directoryTextField.getText() + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
                String tapeID = tapeIDTextField.getText();

                cassetteFlow.createInputFiles(saveDirectoryName, tapeID, sideAList, sideBList, muteTime, false);
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
    }//GEN-LAST:event_createButtonActionPerformed

    private void playSideButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playSideButtonActionPerformed
        final int side = tapeJTabbedPane.getSelectedIndex(); 
        final int muteTime = Integer.parseInt(muteJTextField.getText());
        
        System.out.println("\nPlaying Side " + side);

        // make sure we stop any previous threads
        if (player != null) {
            player.close();
            player = null;
        }
        
        playButton.setEnabled(false);
        playSideButton.setEnabled(false);
        playSide = true;
        
        playerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<MP3Info> mp3List;
                String sideString;
                JLabel trackLabel;
                int[] selectedIndices;
                
                if(side == 0) {
                    mp3List = sideAList;
                    sideString = "A";
                    trackLabel = trackALabel;
                    selectedIndices = sideAJList.getSelectedIndices();
                } else {
                    mp3List = sideBList;
                    sideString = "B";
                    trackLabel = trackBLabel;
                    selectedIndices = sideAJList.getSelectedIndices();
                }
                
                try {
                    // check to see only to play the selected mp3 or all of it of none selected
                    
                    int track = 1;
                    int index = 0;
                    
                    for(MP3Info mp3Info: mp3List) {
                        /**if(selectedIndices.length != 0 && index < selectedIndices.length) {
                            int selectedTrack = selectedIndices[index] + 1;
                            if(selectedTrack == track) { 
                                index++;
                            } else {
                                continue;
                            }
                        }*/
                        
                        trackLabel.setText("Playing Track: " + String.format("%02d", track));
                        System.out.println("Playing " + mp3Info + " on side: " + sideString);
                        
                        FileInputStream mp3Stream = new FileInputStream(mp3Info.getFile());
                        player = new Player(mp3Stream);
                        player.play(); // This is a blocking call
                        
                        // check to see if playback was stopped
                        if(!playSide) {
                            break;
                        }
                        
                        // pause a certain about of time to create a mute portion
                        Thread.sleep(muteTime*1000);
                        track++;
                    }
                    
                    // reable the play side button
                    playButton.setEnabled(true);
                    playSideButton.setEnabled(true);
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
     * Clear the mp3 from the list
     * @param evt 
     */
    private void clearMP3ListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearMP3ListButtonActionPerformed
        DefaultListModel model = (DefaultListModel) mp3JList.getModel();
        model.clear();
        
        // remove records from the list and hash map
        cassetteFlow.mp3sList.clear();
        cassetteFlow.mp3sMap.clear();
    }//GEN-LAST:event_clearMP3ListButtonActionPerformed

    private void createDownloadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createDownloadButtonActionPerformed
        if (directEncodeCheckBox.isSelected()) {
            directEncode(true);
        } else {
            try {
                int muteTime = Integer.parseInt(muteJTextField.getText());
                String saveDirectoryName = directoryTextField.getText() + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
                String tapeID = tapeIDTextField.getText();

                cassetteFlow.createInputFiles(saveDirectoryName, tapeID, sideAList, sideBList, muteTime, true);
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
    }//GEN-LAST:event_createDownloadButtonActionPerformed

    private void stopDecodeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopDecodeButtonActionPerformed
        if(cassettePlayer != null) {
            cassettePlayer.stop();
            startDecodeButton.setEnabled(true);
        }

        tapeInfoTextArea.setText("");
        trackInfoTextArea.setText("");
        playbackInfoTextArea.setText("Decoding process stopped ...");
    }//GEN-LAST:event_stopDecodeButtonActionPerformed

    private void startDecodeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startDecodeButtonActionPerformed
        String logfile = logfileTextField.getText();
        cassettePlayer = new CassettePlayer(this, cassetteFlow, logfile);
        
        if(directDecodeCheckBox.isSelected()) {
            try {
                cassettePlayer.startMinimodem();
            } catch (IOException ex) {
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

    private void logfileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logfileButtonActionPerformed
        File logDir = new File(logfileTextField.getText());

        // default to user directory
        JFileChooser fileChooser = new JFileChooser(logDir.getParent());

        int result = fileChooser.showOpenDialog(CassetteFlowFrame.this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if(file.exists()) {
                logfileTextField.setText(file.toString());
            }
        }
    }//GEN-LAST:event_logfileButtonActionPerformed
    
    private void addItemsToJList(ArrayList<MP3Info> mp3List, JList jlist) {
        DefaultListModel model = (DefaultListModel) jlist.getModel();
        model.clear();
        
        int trackCount = 1;
        for(MP3Info mp3Info: mp3List) {
            String trackName = "[" + trackCount + "] " + mp3Info;
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
        ArrayList<MP3Info> mp3List;
        
        // see which side of the tape we adding sounds to
        if(side == 1) {
            jlist = sideBJList;
            mp3List = sideBList;
        } else {  //Assume Side A
            jlist = sideAJList;
            mp3List = sideAList;
        }
        
        int index = jlist.getSelectedIndex();
        
        if(direction == 1) { // move the track up the list
            if(index >= 1) {
                Collections.swap(mp3List, index, index - 1);
                addItemsToJList(mp3List, jlist);
                jlist.setSelectedIndex(index - 1);
            }
        } else { // move it down the list
            if(index != -1 && index < (mp3List.size() - 1)) {
                Collections.swap(mp3List, index, index + 1);
                addItemsToJList(mp3List, jlist);
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
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addMP3Button;
    private javax.swing.JButton clearMP3ListButton;
    private javax.swing.JButton clearSelectionButton;
    private javax.swing.JTextArea consoleTextArea;
    private javax.swing.JButton createButton;
    private javax.swing.JButton createDownloadButton;
    private javax.swing.JCheckBox directDecodeCheckBox;
    private javax.swing.JCheckBox directEncodeCheckBox;
    private javax.swing.JTextField directoryTextField;
    private javax.swing.JProgressBar encodeProgressBar;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton logfileButton;
    private javax.swing.JTextField logfileTextField;
    private javax.swing.JButton moveTrackDownButton;
    private javax.swing.JButton moveTrackUpButton;
    private javax.swing.JLabel mp3CountLabel;
    private javax.swing.JList<String> mp3JList;
    private javax.swing.JTextField muteJTextField;
    private javax.swing.JButton playButton;
    private javax.swing.JButton playSideButton;
    private javax.swing.JTextArea playbackInfoTextArea;
    private javax.swing.JButton removeAllButton;
    private javax.swing.JButton removeMP3Button;
    private javax.swing.JButton setMP3DirectoryButton;
    private javax.swing.JButton shuffleButton;
    private javax.swing.JList<String> sideAJList;
    private javax.swing.JLabel sideALabel;
    private javax.swing.JList<String> sideBJList;
    private javax.swing.JLabel sideBLabel;
    private javax.swing.JList<String> sideNJList;
    private javax.swing.JLabel sideNLabel;
    private javax.swing.JButton startDecodeButton;
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
    // End of variables declaration//GEN-END:variables
}