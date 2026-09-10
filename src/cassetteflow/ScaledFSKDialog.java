package cassetteflow;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Interactive dialog for Scaled FSK modulation.
 * Provides controls for the minimum amplitude floor, an optional debug reference
 * audio export checkbox, real-time JVM heap memory tracking, progress reporting,
 * and an execution log.
 */
public class ScaledFSKDialog extends JDialog {

    private final CassetteFlow cassetteFlow;
    private final String tapeId;
    private final List<AudioInfo> sideAList;
    private final List<AudioInfo> sideBList;
    private final int muteTime;

    // UI Controls
    private JSpinner spinMinScale;
    private JCheckBox chkExportRef;
    private JProgressBar memoryProgressBar;
    private JLabel lblMemoryStatus;
    private JProgressBar overallProgressBar;
    private JLabel lblCurrentTask;
    private JTextArea logTextArea;
    private JButton btnStart;
    private JButton btnClose;

    // Timer for memory polling
    private javax.swing.Timer memoryTimer;

    public ScaledFSKDialog(Frame parent, CassetteFlow cassetteFlow, String tapeId,
                           List<AudioInfo> sideA, List<AudioInfo> sideB, int muteTime) {
        super(parent, "Scaled FSK VU-Modulation Studio", true);
        this.cassetteFlow = cassetteFlow;
        this.tapeId = tapeId;
        this.sideAList = sideA != null ? new ArrayList<>(sideA) : new ArrayList<>();
        this.sideBList = sideB != null ? new ArrayList<>(sideB) : new ArrayList<>();
        this.muteTime = muteTime;

        initComponents();
        startMemoryMonitoring();

        setSize(740, 640);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        contentPane.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Header Info Panel
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                new EmptyBorder(8, 10, 8, 10)
        ));
        JLabel lblTitle = new JLabel("Tape ID: Tape_" + tapeId +
                "  |  Baud Rate: " + cassetteFlow.BAUDE_RATE + " Baud");
        lblTitle.setFont(new Font("Tahoma", Font.BOLD, 14));
        JLabel lblTracks = new JLabel("Side A: " + sideAList.size() + " tracks  |  Side B: " +
                sideBList.size() + " tracks  |  AMS Silence Gap: " + muteTime + "s");
        lblTracks.setFont(new Font("Tahoma", Font.PLAIN, 12));
        headerPanel.add(lblTitle);
        headerPanel.add(lblTracks);
        contentPane.add(headerPanel);
        contentPane.add(Box.createVerticalStrut(10));

        // 2. Parameters Panel (Spinner + Debug Reference Checkbox)
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        paramPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Modulation Parameters", TitledBorder.LEFT, TitledBorder.TOP));

        paramPanel.add(new JLabel("Min Amplitude (Floor):"));

        // Use custom rounding in SpinnerNumberModel to prevent IEEE-754 double precision drift from blocking 0.05
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(0.20, 0.045, 0.505, 0.01) {
            @Override
            public Object getNextValue() {
                Number val = (Number) super.getNextValue();
                if (val == null) return null;
                double rounded = Math.round(val.doubleValue() * 100.0) / 100.0;
                return (rounded > 0.50) ? null : rounded;
            }

            @Override
            public Object getPreviousValue() {
                Number val = (Number) super.getPreviousValue();
                if (val == null) return null;
                double rounded = Math.round(val.doubleValue() * 100.0) / 100.0;
                return (rounded < 0.05) ? null : rounded;
            }
        };
        spinMinScale = new JSpinner(spinnerModel);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinMinScale, "0.00");
        spinMinScale.setEditor(editor);
        spinMinScale.setPreferredSize(new Dimension(75, 26));
        paramPanel.add(spinMinScale);

        paramPanel.add(Box.createHorizontalStrut(15));

        chkExportRef = new JCheckBox("Export Reference Audio WAV (Debug)", false);
        chkExportRef.setFont(new Font("Tahoma", Font.PLAIN, 12));
        chkExportRef.setToolTipText("Saves combined music audio as Tape_<ID><Side>_Reference.wav to verify envelope alignment in Audacity");
        paramPanel.add(chkExportRef);

        contentPane.add(paramPanel);
        contentPane.add(Box.createVerticalStrut(8));

        // 3. JVM Memory & Resource Monitor Panel
        JPanel memoryPanel = new JPanel(new BorderLayout(6, 4));
        memoryPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "JVM Memory & Resource Monitor", TitledBorder.LEFT, TitledBorder.TOP));
        lblMemoryStatus = new JLabel("Heap Used: Calculating...");
        lblMemoryStatus.setFont(new Font("Monospaced", Font.PLAIN, 11));
        memoryProgressBar = new JProgressBar(0, 100);
        memoryProgressBar.setStringPainted(true);
        memoryProgressBar.setForeground(new Color(60, 140, 200));
        memoryProgressBar.setPreferredSize(new Dimension(100, 20));

        memoryPanel.add(lblMemoryStatus, BorderLayout.NORTH);
        memoryPanel.add(memoryProgressBar, BorderLayout.CENTER);
        contentPane.add(memoryPanel);
        contentPane.add(Box.createVerticalStrut(8));

        // 4. Processing Progress Panel
        JPanel progressPanel = new JPanel(new BorderLayout(6, 4));
        progressPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Processing Progress", TitledBorder.LEFT, TitledBorder.TOP));
        lblCurrentTask = new JLabel("Status: Ready to encode.");
        lblCurrentTask.setFont(new Font("Tahoma", Font.PLAIN, 12));
        overallProgressBar = new JProgressBar(0, Math.max(1, sideAList.size() + sideBList.size()));
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setValue(0);
        overallProgressBar.setPreferredSize(new Dimension(100, 22));

        progressPanel.add(lblCurrentTask, BorderLayout.NORTH);
        progressPanel.add(overallProgressBar, BorderLayout.CENTER);
        contentPane.add(progressPanel);
        contentPane.add(Box.createVerticalStrut(8));

        // 5. Process & Telemetry Log
        JPanel logPanel = new JPanel(new BorderLayout(4, 4));
        logPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Process & Telemetry Log", TitledBorder.LEFT, TitledBorder.TOP));
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logTextArea.setBackground(new Color(248, 248, 248));
        logTextArea.setText("> Ready. 100% in-memory modulation enabled.\n" +
                "> No intermediate unscaled WAV files will be written to disk.\n" +
                "> Click 'Start Encoding' to begin processing Side A and Side B.\n");

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setPreferredSize(new Dimension(680, 180));
        logPanel.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(logPanel);

        add(contentPane, BorderLayout.CENTER);

        // 6. Bottom Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        btnStart = new JButton("Start Encoding");
        btnStart.setFont(new Font("Tahoma", Font.BOLD, 13));
        btnStart.setPreferredSize(new Dimension(140, 32));
        btnStart.addActionListener(e -> startEncoding());

        btnClose = new JButton("Close");
        btnClose.setFont(new Font("Tahoma", Font.PLAIN, 13));
        btnClose.setPreferredSize(new Dimension(90, 32));
        btnClose.addActionListener(e -> dispose());

        buttonPanel.add(btnStart);
        buttonPanel.add(btnClose);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void startMemoryMonitoring() {
        updateMemoryDisplay();
        memoryTimer = new javax.swing.Timer(500, e -> updateMemoryDisplay());
        memoryTimer.start();
    }

    private void updateMemoryDisplay() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        long max = rt.maxMemory();

        int usedMb = (int) (used / (1024 * 1024));
        int totalMb = (int) (total / (1024 * 1024));
        int maxMb = (int) (max / (1024 * 1024));
        int pct = (int) Math.round((used / (double) total) * 100.0);

        lblMemoryStatus.setText(String.format("Heap Used: %d MB / %d MB (%d%%)  |  Max Available: %d MB",
                usedMb, totalMb, pct, maxMb));
        memoryProgressBar.setValue(pct);
        memoryProgressBar.setString(pct + "% (" + usedMb + " MB)");
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append("> " + message + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    private void startEncoding() {
        btnStart.setEnabled(false);
        spinMinScale.setEnabled(false);
        chkExportRef.setEnabled(false);
        final float minScale = (float) (Math.round(((Number) spinMinScale.getValue()).doubleValue() * 100.0) / 100.0);
        final boolean exportRef = chkExportRef.isSelected();

        String saveDirectoryName = CassetteFlow.AUDIO_DIR_NAME + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
        File saveDir = new File(saveDirectoryName);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        int totalTracks = sideAList.size() + sideBList.size();
        overallProgressBar.setMaximum(Math.max(1, totalTracks));
        overallProgressBar.setValue(0);

        String minScaleString = "0" + (int) (100 * minScale);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            int processedCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                long startTime = System.currentTimeMillis();
                appendLog("Starting batch scaled FSK encoding...");

                // Process Side A
                if (!sideAList.isEmpty()) {
                    File outFileA = new File(saveDir, "Tape_" + tapeId + "A_Scaled" + minScaleString + ".wav");
                    File refFileA = exportRef ? new File(saveDir, "Tape_" + tapeId + "A_Reference.wav") : null;
                    FSKModulator.processSideInMemory(
                            cassetteFlow, "A", tapeId, sideAList, muteTime, minScale, outFileA, refFileA,
                            new FSKModulator.ModulatorProgressListener() {
                                @Override
                                public void onTrackStarted(String side, int currentTrack, int total, String trackName) {
                                    SwingUtilities.invokeLater(() ->
                                            lblCurrentTask.setText(String.format("Side %s [%d/%d] - %s", side, currentTrack, total, trackName)));
                                }

                                @Override
                                public void onTrackFinished(String side, int currentTrack, int total, String message) {
                                    processedCount++;
                                    SwingUtilities.invokeLater(() -> {
                                        overallProgressBar.setValue(processedCount);
                                        updateMemoryDisplay();
                                    });
                                }

                                @Override
                                public void onLog(String message) {
                                    appendLog(message);
                                }

                                @Override
                                public void onFinished(boolean success, String summary) {
                                    appendLog(summary);
                                }
                            }
                    );
                }

                // Process Side B
                if (!sideBList.isEmpty()) {
                    File outFileB = new File(saveDir, "Tape_" + tapeId + "B_Scaled" + minScaleString + ".wav");
                    File refFileB = exportRef ? new File(saveDir, "Tape_" + tapeId + "B_Reference.wav") : null;
                    FSKModulator.processSideInMemory(
                            cassetteFlow, "B", tapeId, sideBList, muteTime, minScale, outFileB, refFileB,
                            new FSKModulator.ModulatorProgressListener() {
                                @Override
                                public void onTrackStarted(String side, int currentTrack, int total, String trackName) {
                                    SwingUtilities.invokeLater(() ->
                                            lblCurrentTask.setText(String.format("Side %s [%d/%d] - %s", side, currentTrack, total, trackName)));
                                }

                                @Override
                                public void onTrackFinished(String side, int currentTrack, int total, String message) {
                                    processedCount++;
                                    SwingUtilities.invokeLater(() -> {
                                        overallProgressBar.setValue(processedCount);
                                        updateMemoryDisplay();
                                    });
                                }

                                @Override
                                public void onLog(String message) {
                                    appendLog(message);
                                }

                                @Override
                                public void onFinished(boolean success, String summary) {
                                    appendLog(summary);
                                }
                            }
                    );
                }

                long duration = (System.currentTimeMillis() - startTime) / 1000;
                appendLog(String.format("All sides completed in %d seconds! In-memory cleanup complete.", duration));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lblCurrentTask.setText("Status: Modulation Complete!");
                    String msg = "Scaled FSK files generated successfully in:\n" + saveDirectoryName;
                    if (exportRef) {
                        msg += "\n(Debug Reference Audio WAVs also exported)";
                    }
                    JOptionPane.showMessageDialog(ScaledFSKDialog.this,
                            msg, "Modulation Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    appendLog("Error: " + ex.getMessage());
                    lblCurrentTask.setText("Status: Error occurred.");
                    JOptionPane.showMessageDialog(ScaledFSKDialog.this,
                            "Error during modulation: " + ex.getMessage(),
                            "Processing Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                } finally {
                    btnStart.setEnabled(true);
                    spinMinScale.setEnabled(true);
                    chkExportRef.setEnabled(true);
                    updateMemoryDisplay();
                }
            }
        };

        worker.execute();
    }

    @Override
    public void dispose() {
        if (memoryTimer != null && memoryTimer.isRunning()) {
            memoryTimer.stop();
        }
        super.dispose();
    }
}
