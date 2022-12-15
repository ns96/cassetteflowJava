package cassetteflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;

/**
 *
 * @author Nathan
 */
public class TrackFinderFrame extends javax.swing.JFrame {
    private CassetteFlow cassetteFlow;
    private CassetteFlowFrame cassetteFlowFrame;

    private TreeMap<String, ArrayList<AudioInfo>> artistMap;
    private TreeMap<String, ArrayList<AudioInfo>> genreMap;
    private TreeMap<String, ArrayList<AudioInfo>> albumMap;
    private TreeMap<String, ArrayList<AudioInfo>> yearMap;
    private TreeMap<String, ArrayList<AudioInfo>> folderMap;
    
    // also store the found AudioInfo object in the list
    private ArrayList<AudioInfo> foundAudioInfoList = new ArrayList<>();
    
    // indicate if we pressed found folders
    private boolean viewAll = false;
    
    // specify the record limit to display and send to main display
    private final int RECORD_LIMIT =  5000;
    
    /**
     * Creates new form TrackFinderFrame
     */
    public TrackFinderFrame() {
        initComponents();
        
        DefaultListModel model = new DefaultListModel();
        foundJList.setModel(model);
    }
    
    /**
     * Set the Cassette Flow frame object
     * 
     * @param cassetteFlowFrame 
     */
    void setCassetteFlowFrame(CassetteFlowFrame cassetteFlowFrame) {
        this.cassetteFlowFrame = cassetteFlowFrame;
        this.cassetteFlow = cassetteFlowFrame.getCassetteFlow();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        closeButton = new javax.swing.JButton();
        addToMainJListButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        foundLabel = new javax.swing.JLabel();
        searchByComboBox = new javax.swing.JComboBox<>();
        searchButton = new javax.swing.JButton();
        searchTextField = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        foundJList = new javax.swing.JList<>();
        searchProgressBar = new javax.swing.JProgressBar();
        formatComboBox = new javax.swing.JComboBox<>();
        viewAllButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        trackInfoTextArea = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Track Finder");

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        addToMainJListButton.setText("Add To Track List");
        addToMainJListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addToMainJListButtonActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel1.setText("Found: ");

        foundLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        foundLabel.setForeground(java.awt.Color.red);
        foundLabel.setText("0");

        searchByComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Search By Title", "Search By Artist", "Search By Genre", "Search By Album", "Search By Year ", "Search By Folder" }));

        searchButton.setText("Search");
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        searchTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchTextFieldActionPerformed(evt);
            }
        });

        foundJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                foundJListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(foundJList);

        formatComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "ALL", "MP3", "FLAC" }));

        viewAllButton.setText("View All");
        viewAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewAllButtonActionPerformed(evt);
            }
        });

        trackInfoTextArea.setEditable(false);
        trackInfoTextArea.setColumns(20);
        trackInfoTextArea.setRows(5);
        jScrollPane2.setViewportView(trackInfoTextArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(searchByComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(formatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchButton))
                    .addComponent(jScrollPane1)
                    .addComponent(jScrollPane2)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addToMainJListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(foundLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(viewAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(closeButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(searchByComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(searchTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(searchButton)
                        .addComponent(formatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 280, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(addToMainJListButton)
                            .addComponent(jLabel1)
                            .addComponent(foundLabel))
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(viewAllButton)
                            .addComponent(closeButton)))
                    .addComponent(searchProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        setVisible(false);
    }//GEN-LAST:event_closeButtonActionPerformed
    
    /**
     * Search for the audio info objects matching the search term. This needs
     * to be done in a thread
     * @param evt 
     */
    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        viewAll = false;
        trackInfoTextArea.setText("");
        
        final int searchBy = searchByComboBox.getSelectedIndex();
        final String searchTerm = searchTextField.getText().toLowerCase().trim();
        final String format = formatComboBox.getSelectedItem().toString().toLowerCase();
        
        // update the gui components
        searchButton.setEnabled(false);
        searchProgressBar.setIndeterminate(true);
        
        foundAudioInfoList = new ArrayList<>();
        
        Thread thread = new Thread("Searcher Thread") {
            @Override
            public void run() {
                for (AudioInfo audioInfo : cassetteFlow.audioInfoDB.values()) {
                    String title = audioInfo.getName();
                    
                    String searchIn;
                    if(searchBy == 0) {
                        searchIn = title;
                    } else if(searchBy == 1) {
                        searchIn = audioInfo.getArtist();
                    } else if(searchBy == 2) {
                        searchIn = audioInfo.getGenre();
                    } else if(searchBy == 3) {
                        searchIn = audioInfo.getAlbum();
                    } else if(searchBy == 4) {
                        searchIn = audioInfo.getYear();
                    } else {
                        searchIn = CassetteFlowUtil.getParentDirectoryName(audioInfo.getFile());
                    }
                    
                    // make sure we have something to search in
                    if(searchIn == null) { continue; }
                    
                    searchIn = searchIn.toLowerCase();
                                        
                    if (!format.equals("all")) {
                        if (searchIn.contains(searchTerm) && title.contains(format)) {
                            foundAudioInfoList.add(audioInfo);
                        }
                    } else {
                        if (searchIn.contains(searchTerm)) {
                            foundAudioInfoList.add(audioInfo);
                        }
                    }
                }
                                
                // update the UI now
                SwingUtilities.invokeLater(() -> {
                    // add the results to the UI
                    addAudioInfoToJList(searchBy);
                    searchButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }//GEN-LAST:event_searchButtonActionPerformed
    
    /**
     * Add the found tracks to the main GUI track list
     * 
     * @param evt 
     */
    private void addToMainJListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addToMainJListButtonActionPerformed
        if(viewAll) {
            TreeMap<String, ArrayList<AudioInfo>> recordMap = getRecordMap();
            if(recordMap == null) 
                return;
            
            foundAudioInfoList = new ArrayList<>();
            
            List<String> values = foundJList.getSelectedValuesList();
            
            for(String value: values) {
                String key = getRecordMapKey(value);
                foundAudioInfoList.addAll(recordMap.get(key));
            }
        } 
        
        if(foundAudioInfoList.size() <= RECORD_LIMIT) {
            cassetteFlow.audioInfoList.addAll(foundAudioInfoList);
        } else {
            cassetteFlow.audioInfoList.addAll(foundAudioInfoList.subList(0, RECORD_LIMIT));
        }
    
        // call this method to update the UI with the added values
        cassetteFlowFrame.addAudioInfoToJList();
    }//GEN-LAST:event_addToMainJListButtonActionPerformed
    
    /**
     * Based on the selected value in the jlist, return a key for the record map
     * @param value
     * @return 
     */
    private String getRecordMapKey(String value) {
        // this should be done better using indexof and substring
        if (value.contains(" || ")) {
            String[] sa1 = value.split(" || ");
            value = sa1[1];
        }

        return value.split(" \\(")[0];
    }
    
    /**
     * Based on what we searching by get the correct record map
     * @return 
     */
    private TreeMap<String, ArrayList<AudioInfo>> getRecordMap() {
        int index = searchByComboBox.getSelectedIndex();
        TreeMap<String, ArrayList<AudioInfo>> recordMap = null;

        if (index == 1) {
            recordMap = artistMap;
        } else if (index == 2) {
            recordMap = genreMap;
        } else if (index == 3) {
            recordMap = albumMap;
        } else if (index == 4) {
            recordMap = yearMap;
        } else if (index == 5) {
            recordMap = folderMap;
        } else {
            System.out.println("No map records ...");
        }
        
        return recordMap;
    }
    
    /**
     * Find all the parent folders for the audio files
     * 
     * @param evt 
     */
    private void viewAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewAllButtonActionPerformed
        viewAll = true;
        int index = searchByComboBox.getSelectedIndex();
        
        if(index == 1) {
            viewAllArtist();
        } else if(index == 2) {
            viewAllGenres();
        } else if(index == 3) {
            viewAllAlbums();
        } else if(index == 4) {
            viewAllYears();
        } else if(index == 5) {
            viewAllFolders();
        } else {
            System.out.println("Nothing to Group By ...");
        } 
        
    }//GEN-LAST:event_viewAllButtonActionPerformed
    
    /**
     * Detect enter pressed in text box
     * 
     * @param evt 
     */
    private void searchTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchTextFieldActionPerformed
        searchButtonActionPerformed(evt);
    }//GEN-LAST:event_searchTextFieldActionPerformed

    /**
     * Keep track of selections so we can display track information
     * 
     * @param evt 
     */
    private void foundJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_foundJListValueChanged
        if(evt.getValueIsAdjusting()) return;

        if(!viewAll) {
            int index = foundJList.getSelectedIndex();
            if(index != -1) {
                AudioInfo audioInfo = foundAudioInfoList.get(index);
                trackInfoTextArea.setText(audioInfo.getFullInfo());
            }
        } else {
            String value = foundJList.getSelectedValue();
            trackInfoTextArea.setText(value + "\n");
        }
    }//GEN-LAST:event_foundJListValueChanged
    
    /**
     * Find and display all artist
     */
    private void viewAllArtist() {
        // if we already found all the folders just display folders and resturn
        if(artistMap != null) {
            addViewAllRecordsToJList(artistMap, "artists");
            return;
        }
        
        artistMap = new TreeMap<>();
        
        // update the gui components
        viewAllButton.setEnabled(false);
        searchProgressBar.setIndeterminate(true);
        
        Thread thread = new Thread("Artist Thread") {
            @Override
            public void run() {
                ArrayList<AudioInfo> audioInfoList; 
                
                for (AudioInfo audioInfo : cassetteFlow.audioInfoDB.values()) {
                    String artist = audioInfo.getArtist();
                    if(artist == null) continue;
                    
                    if(artistMap.containsKey(artist)) {
                        audioInfoList = artistMap.get(artist);
                        audioInfoList.add(audioInfo);
                    } else {
                        audioInfoList = new ArrayList<>();
                        audioInfoList.add(audioInfo);
                        artistMap.put(artist, audioInfoList);
                    }
                }
                                
                // update the UI now
                SwingUtilities.invokeLater(() -> {
                    // add the results to the UI
                    addViewAllRecordsToJList(artistMap, "artists");
                    
                    viewAllButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }
    
    /**
     * Find and display all genres
     */
    private void viewAllGenres() {
        // if we already found all the genres just display folders and return
        if(genreMap != null) {
            addViewAllRecordsToJList(genreMap, "genres");
            return;
        }
        
        genreMap = new TreeMap<>();
        
        // update the gui components
        viewAllButton.setEnabled(false);
        searchProgressBar.setIndeterminate(true);
        
        Thread thread = new Thread("Genre Thread") {
            @Override
            public void run() {
                ArrayList<AudioInfo> audioInfoList; 
                
                for (AudioInfo audioInfo : cassetteFlow.audioInfoDB.values()) {
                    String genre = audioInfo.getGenre();
                    if(genre == null) continue;
                    
                    if(genreMap.containsKey(genre)) {
                        audioInfoList = genreMap.get(genre);
                        audioInfoList.add(audioInfo);
                    } else {
                        audioInfoList = new ArrayList<>();
                        audioInfoList.add(audioInfo);
                        genreMap.put(genre, audioInfoList);
                    }
                }
                                
                // update the UI now
                SwingUtilities.invokeLater(() -> {
                    // add the results to the UI
                    addViewAllRecordsToJList(genreMap, "genres");
                    
                    viewAllButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }
    
    /**
     * Find and display all albums
     */
    private void viewAllAlbums() {
        // if we already found all the artist just display albums and return
        if(albumMap != null) {
            addViewAllRecordsToJList(albumMap, "albums");
            return;
        }
        
        albumMap = new TreeMap<>();
        
        // update the gui components
        viewAllButton.setEnabled(false);
        searchProgressBar.setIndeterminate(true);
        
        Thread thread = new Thread("Album Thread") {
            @Override
            public void run() {
                ArrayList<AudioInfo> audioInfoList; 
                
                for (AudioInfo audioInfo : cassetteFlow.audioInfoDB.values()) {
                    String album = audioInfo.getAlbum();
                    if(album == null) continue;
                    
                    if(albumMap.containsKey(album)) {
                        audioInfoList = albumMap.get(album);
                        audioInfoList.add(audioInfo);
                    } else {
                        audioInfoList = new ArrayList<>();
                        audioInfoList.add(audioInfo);
                        albumMap.put(album, audioInfoList);
                    }
                }
                                
                // update the UI now
                SwingUtilities.invokeLater(() -> {
                    // add the results to the UI
                    addViewAllRecordsToJList(albumMap, "album");
                    
                    viewAllButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }
    
    /**
     * Find and display all years
     */
    private void viewAllYears() {
        // if we already found all the artist just display albums and return
        if(albumMap != null) {
            addViewAllRecordsToJList(yearMap, "years");
            return;
        }
        
        yearMap = new TreeMap<>();
        
        // update the gui components
        viewAllButton.setEnabled(false);
        searchProgressBar.setIndeterminate(true);
        
        Thread thread = new Thread("Year Thread") {
            @Override
            public void run() {
                ArrayList<AudioInfo> audioInfoList; 
                
                for (AudioInfo audioInfo : cassetteFlow.audioInfoDB.values()) {
                    String year = audioInfo.getYear();
                    if(year == null) continue;
                    
                    if(yearMap.containsKey(year)) {
                        audioInfoList = yearMap.get(year);
                        audioInfoList.add(audioInfo);
                    } else {
                        audioInfoList = new ArrayList<>();
                        audioInfoList.add(audioInfo);
                        yearMap.put(year, audioInfoList);
                    }
                }
                                
                // update the UI now
                SwingUtilities.invokeLater(() -> {
                    // add the results to the UI
                    addViewAllRecordsToJList(yearMap, "years");
                    
                    viewAllButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }
    
    /**
     * Find and display all folders
     */
    private void viewAllFolders() {
        // if we already found all the folders just display folders and resturn
        if(folderMap != null) {
            addViewAllRecordsToJList(folderMap, "folders");
            return;
        }
        
        folderMap = new TreeMap<>();
        
        // update the gui components
        viewAllButton.setEnabled(false);
        searchProgressBar.setIndeterminate(true);
        
        Thread thread = new Thread("Folder Thread") {
            @Override
            public void run() {
                ArrayList<AudioInfo> audioInfoList; 
                
                for (AudioInfo audioInfo : cassetteFlow.audioInfoDB.values()) {
                    String folder = CassetteFlowUtil.getParentDirectoryName(audioInfo.getFile());
                    
                    if(folderMap.containsKey(folder)) {
                        audioInfoList = folderMap.get(folder);
                        audioInfoList.add(audioInfo);
                    } else {
                        audioInfoList = new ArrayList<>();
                        audioInfoList.add(audioInfo);
                        folderMap.put(folder, audioInfoList);
                    }
                }
                                
                // update the UI now
                SwingUtilities.invokeLater(() -> {
                    // add the results to the UI
                    addViewAllRecordsToJList(folderMap, "folders");
                    
                    viewAllButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }
    
    /**
     * Add the found audio info objects to the list now
     */
    private void addAudioInfoToJList(int searchBy) {
        DefaultListModel model = (DefaultListModel) foundJList.getModel();
        model.clear();

        // sort the list of mp3s/flac before displaying
        Collections.sort(foundAudioInfoList, (o1, o2) -> o1.toString().compareTo(o2.toString()));
        
        if(foundAudioInfoList.size() <= RECORD_LIMIT) { 
            for (AudioInfo audioInfo : foundAudioInfoList) {
                try {
                    String searchIn = "";
                    
                    if(searchBy == 1) {
                        searchIn = audioInfo.getArtist() + " || ";
                    } else if(searchBy == 2) {
                        searchIn = audioInfo.getGenre()  + " || ";
                    } else if(searchBy == 3) {
                        searchIn = audioInfo.getAlbum() + " || ";
                    } else if(searchBy == 4) {
                        searchIn = audioInfo.getYear() + " || ";
                    } else if(searchBy == 5) {
                        String folder = CassetteFlowUtil.getParentDirectoryName(audioInfo.getFile());
                        searchIn = folder + " || ";
                    }
                    
                    model.addElement(searchIn + audioInfo);
                } catch (Exception ex) {
                    Logger.getLogger(TrackFinderFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            foundLabel.setText(foundAudioInfoList.size() + " tracks");
        } else {
            System.out.println("Display limit exceeded. Only showing " + RECORD_LIMIT + " entries ...");
            
            for(int i = 0; i < RECORD_LIMIT; i++) {
                try {
                    AudioInfo audioInfo = foundAudioInfoList.get(i);
                    
                    String searchIn = "";
                    
                    if(searchBy == 1) {
                        searchIn = audioInfo.getArtist() + " || ";
                    } else if(searchBy == 2) {
                        searchIn = audioInfo.getGenre()  + " || ";
                    } else if(searchBy == 3) {
                        searchIn = audioInfo.getAlbum() + " || ";
                    } else if(searchBy == 4) {
                        searchIn = audioInfo.getYear() + " || ";
                    } else if(searchBy == 5) {
                        String folder = CassetteFlowUtil.getParentDirectoryName(audioInfo.getFile());
                        searchIn = folder + " || ";
                    }
                    
                    model.addElement(searchIn + audioInfo);
                } catch (Exception ex) {
                    Logger.getLogger(TrackFinderFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            foundLabel.setText("*" + foundAudioInfoList.size() + " tracks*");
        }
    }
    
    /**
     * Add the found audio records artist, genre, or folders to the jlist now
     */
    private void addViewAllRecordsToJList(TreeMap<String, ArrayList<AudioInfo>> recordMap, String type) {
        DefaultListModel model = (DefaultListModel) foundJList.getModel();
        model.clear();

        // sort the list of mp3s/flac before displaying
        TreeSet<String> recordSet = new TreeSet(recordMap.keySet());

        for (String record : recordSet) {
            try {
                int tracks = recordMap.get(record).size();
                
                if(tracks <= 1) continue; // 12/8/2022 tempt bug fix to not display folders with only 1 track 
                
                if(record.isBlank()) {
                    if(type.equals("years")) {
                        record = "_UNKNOWN";
                    } else {
                        record = "_DUMMY";
                    }
                }
                
                model.addElement(record + " (" + tracks + " tracks)");
            } catch (Exception ex) {
                Logger.getLogger(TrackFinderFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        foundLabel.setText(recordSet.size() + " " + type);
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(TrackFinderFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(TrackFinderFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(TrackFinderFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(TrackFinderFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new TrackFinderFrame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addToMainJListButton;
    private javax.swing.JButton closeButton;
    private javax.swing.JComboBox<String> formatComboBox;
    private javax.swing.JList<String> foundJList;
    private javax.swing.JLabel foundLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton searchButton;
    private javax.swing.JComboBox<String> searchByComboBox;
    private javax.swing.JProgressBar searchProgressBar;
    private javax.swing.JTextField searchTextField;
    private javax.swing.JTextArea trackInfoTextArea;
    private javax.swing.JButton viewAllButton;
    // End of variables declaration//GEN-END:variables

}
