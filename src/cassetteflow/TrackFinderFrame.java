/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
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
    private TreeMap<String, ArrayList<AudioInfo>> folderMap;
    
    // also store the found AudioInfo object in the list
    private ArrayList<AudioInfo> foundAudioInfoList = new ArrayList<>();
    
    // indicate if we pressed found folders
    private boolean viewAll = false;
    
    // specify the record limit to display and send to main display
    private final int RECORD_LIMIT =  1500;
    
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

        searchByComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Search By Title", "Search By Artist", "Search By Genre", "Search By Album", "Search By Folder" }));

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

        jScrollPane1.setViewportView(foundJList);

        formatComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "ALL", "MP3", "FLAC" }));

        viewAllButton.setText("View All");
        viewAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewAllButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(addToMainJListButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(foundLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(searchProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(viewAllButton))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(searchByComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(searchTextField)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(formatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(searchButton)
                            .addComponent(closeButton)))
                    .addComponent(jScrollPane1))
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
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 278, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeButton)
                    .addComponent(addToMainJListButton)
                    .addComponent(jLabel1)
                    .addComponent(foundLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(searchProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addComponent(viewAllButton)))
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
                    addAudioInfoToJList();
                    
                    searchButton.setEnabled(true);
                    searchProgressBar.setIndeterminate(false);
                });
            }
        };
        thread.start();
    }//GEN-LAST:event_searchButtonActionPerformed
    
    /**
     * Add the found tracks to the tracklist in the main GUI track list
     * 
     * @param evt 
     */
    private void addToMainJListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addToMainJListButtonActionPerformed
        if(viewAll) {
            int index = searchByComboBox.getSelectedIndex();
            TreeMap<String, ArrayList<AudioInfo>> recordMap = null;
            
            if (index == 1) {
                recordMap = artistMap;
            } else if (index == 2) {
                recordMap = genreMap;
            } else if (index == 3) {
                recordMap = albumMap;
            } else if (index == 4) {
                recordMap = folderMap;
            } else {
                System.out.println("No map records ...");
            } 
            
            if(recordMap == null) {return;}
            
            foundAudioInfoList = new ArrayList<>();
            
            List<String> keys = foundJList.getSelectedValuesList();
            
            for(String key: keys) {
                String[] sa = key.split(" \\(");
                foundAudioInfoList.addAll(recordMap.get(sa[0]));
            }
        } 
        
        if(foundAudioInfoList.size() <= RECORD_LIMIT) {
            cassetteFlow.audioInfoList.addAll(foundAudioInfoList);
        } else {
            cassetteFlow.audioInfoList.addAll(foundAudioInfoList.subList(0, RECORD_LIMIT));
        }
    
        cassetteFlowFrame.addAudioInfoToJList();
    }//GEN-LAST:event_addToMainJListButtonActionPerformed
    
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
     * Find and display all genres to display
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
     * Find and display all albums to display
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
    private void addAudioInfoToJList() {
        DefaultListModel model = (DefaultListModel) foundJList.getModel();
        model.clear();

        // sort the list of mp3s/flac before displaying
        Collections.sort(foundAudioInfoList, (o1, o2) -> o1.toString().compareTo(o2.toString()));
        
        if(foundAudioInfoList.size() <= RECORD_LIMIT) { 
            for (AudioInfo audioInfo : foundAudioInfoList) {
                try {
                    model.addElement(audioInfo);
                } catch (Exception ex) {
                    Logger.getLogger(TrackFinderFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            foundLabel.setText(foundAudioInfoList.size() + " tracks");
        } else {
            System.out.println("Display limit excedded. Only showing 1000 found entries ...");
            
            for(int i = 0; i < RECORD_LIMIT; i++) {
                try {
                    AudioInfo audioInfo = foundAudioInfoList.get(i);
                    model.addElement(audioInfo);
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
                
                if(record.isBlank()) {
                    record = "_DUMMY";
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
    private javax.swing.JButton searchButton;
    private javax.swing.JComboBox<String> searchByComboBox;
    private javax.swing.JProgressBar searchProgressBar;
    private javax.swing.JTextField searchTextField;
    private javax.swing.JButton viewAllButton;
    // End of variables declaration//GEN-END:variables

}
