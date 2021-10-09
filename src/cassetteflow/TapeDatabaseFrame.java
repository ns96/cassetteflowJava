/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cassetteflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.DefaultListModel;

/**
 *
 * @author Nathan
 */
public class TapeDatabaseFrame extends javax.swing.JFrame {
    private HashMap<String, ArrayList<String>> tapeDB;
    
    private HashMap<String, MP3Info> mp3InfoDB;

    /**
     * Creates new form TapeDatabaseFrame
     */
    public TapeDatabaseFrame() {
        initComponents();
    }
    
    /**
     * Set the tape database
     */
    public void setTapeDB(HashMap<String, ArrayList<String>> tapeDB) {
        this.tapeDB = tapeDB;
        
        DefaultListModel model = new DefaultListModel();
        
        Object[] keys = tapeDB.keySet().toArray();
        Arrays.sort(keys);
        
        for(Object key: keys) {
            model.addElement(key);
        }
        
        tapeDBJList.setModel(model);
    }
    
    /**
     * Set the MP3 Info database
     * 
     * @param mp3InfoDB 
     */
    public void setMP3InfoDB(HashMap<String, MP3Info> mp3InfoDB) {
        this.mp3InfoDB = mp3InfoDB;
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        tapeDBJList = new javax.swing.JList<>();
        closeButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        mp3JList = new javax.swing.JList<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Tape Database (Local)");

        tapeDBJList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        tapeDBJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                tapeDBJListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(tapeDBJList);

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        jScrollPane2.setViewportView(mp3JList);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(closeButton))
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 182, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 510, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 315, Short.MAX_VALUE)
                    .addComponent(jScrollPane2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(closeButton))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_closeButtonActionPerformed
    
    /**
     * Detect the the selection
     * @param evt 
     */
    private void tapeDBJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_tapeDBJListValueChanged
        if (!evt.getValueIsAdjusting()) {
            String key = tapeDBJList.getSelectedValue();
            
            DefaultListModel model = new DefaultListModel();
            ArrayList<String> mp3List = tapeDB.get(key);
            
            for (int i = 0; i < mp3List.size(); i++) {
                MP3Info mp3Info = mp3InfoDB.get(mp3List.get(i));
                String trackCount = String.format("%02d", (i + 1));
                String trackName = "[" + trackCount + "] " + mp3Info;
                model.addElement(trackName);
            }
            
            mp3JList.setModel(model);
        }
    }//GEN-LAST:event_tapeDBJListValueChanged

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
            java.util.logging.Logger.getLogger(TapeDatabaseFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(TapeDatabaseFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(TapeDatabaseFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(TapeDatabaseFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new TapeDatabaseFrame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> mp3JList;
    private javax.swing.JList<String> tapeDBJList;
    // End of variables declaration//GEN-END:variables
}