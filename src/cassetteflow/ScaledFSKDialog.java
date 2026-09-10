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
 * Supports:
 * 1. Mixtape Mode (when songs are loaded in Side A / B):
 *    Tracks digital music volume envelope and scales carrier with safe floor (0.05 - 0.50).
 *    Optional combined debug reference audio WAV export.
 * 2. DCT Continuous Stream Mode (auto-activated when Side A and Side B are empty):
 *    Procedural musical envelope simulation (Pop, Rock, Jazz, Ambient, Classical).
 *    Uses tape length directly from the main UI selection (e.g. 60 Min, 90 Min).
 *
 * Real-time JVM heap memory monitor, progress reporting, and execution telemetry log.
 * Zero unscaled WAV files written to disk.
 */
public class ScaledFSKDialog extends JDialog {

    private final CassetteFlow cassetteFlow;
    private final String tapeId;
    private final String effectiveTapeId;
    private final List<AudioInfo> sideAList;
    private final List<AudioInfo> sideBList;
    private final int muteTime;
    private final boolean isDCTMode;
    private final int tapeDurationSeconds;
    private final String tapeLengthLabel;

    // UI Controls
    private JSpinner spinMinScale;
    private JCheckBox chkExportRef;
    private JComboBox<ProceduralEnvelopeGenerator.Genre> cbGenre;
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
        this(parent, cassetteFlow, tapeId, sideA, sideB, muteTime, 1800, "60 Minutes");
    }

    public ScaledFSKDialog(Frame parent, CassetteFlow cassetteFlow, String tapeId,
                           List<AudioInfo> sideA, List<AudioInfo> sideB, int muteTime,
                           int tapeDurationSeconds, String tapeLengthLabel) {
        super(parent, (sideA == null || sideA.isEmpty()) && (sideB == null || sideB.isEmpty())
                ? "Scaled FSK Studio - DCT Procedural Modulation"
                : "Scaled FSK VU-Modulation Studio", true);
        this.cassetteFlow = cassetteFlow;
        this.tapeId = tapeId;
        this.sideAList = sideA != null ? new ArrayList<>(sideA) : new ArrayList<>();
        this.sideBList = sideB != null ? new ArrayList<>(sideB) : new ArrayList<>();
        this.muteTime = muteTime;
        this.isDCTMode = this.sideAList.isEmpty() && this.sideBList.isEmpty();
        this.effectiveTapeId = (tapeId != null && !tapeId.trim().isEmpty())
                ? tapeId.trim()
                : (this.isDCTMode ? "DCT0" : "0001");
        this.tapeDurationSeconds = tapeDurationSeconds > 0 ? tapeDurationSeconds : 1800;
        this.tapeLengthLabel = (tapeLengthLabel != null && !tapeLengthLabel.isEmpty()) ? tapeLengthLabel : "60 Minutes";

        initComponents();
        startMemoryMonitoring();

        setSize(740, 630);
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

        JLabel lblTitle;
        JLabel lblSubtitle;

        if (isDCTMode) {
            lblTitle = new JLabel("Mode: DCT Continuous Stream  |  Tape ID: Tape_" + effectiveTapeId +
                    "  |  Baud Rate: " + cassetteFlow.BAUDE_RATE + " Baud");
            lblTitle.setFont(new Font("Tahoma", Font.BOLD, 14));
            lblSubtitle = new JLabel("Procedural Genre Modulation  |  Tape Length: " + tapeLengthLabel +
                    " (" + (tapeDurationSeconds / 60) + " min/side)  |  Sides: A & B");
            lblSubtitle.setFont(new Font("Tahoma", Font.PLAIN, 12));
        } else {
            lblTitle = new JLabel("Tape ID: Tape_" + effectiveTapeId +
                    "  |  Baud Rate: " + cassetteFlow.BAUDE_RATE + " Baud");
            lblTitle.setFont(new Font("Tahoma", Font.BOLD, 14));
            lblSubtitle = new JLabel("Side A: " + sideAList.size() + " tracks  |  Side B: " +
                    sideBList.size() + " tracks  |  AMS Silence Gap: " + muteTime + "s");
            lblSubtitle.setFont(new Font("Tahoma", Font.PLAIN, 12));
        }
        headerPanel.add(lblTitle);
        headerPanel.add(lblSubtitle);
        contentPane.add(headerPanel);
        contentPane.add(Box.createVerticalStrut(10));

        // 2. Parameters Panel
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        paramPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                isDCTMode ? "DCT Procedural Modulation Parameters" : "Modulation Parameters",
                TitledBorder.LEFT, TitledBorder.TOP));

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

        if (!isDCTMode) {
            paramPanel.add(Box.createHorizontalStrut(15));
            chkExportRef = new JCheckBox("Export Reference Audio WAV (Debug)", false);
            chkExportRef.setFont(new Font("Tahoma", Font.PLAIN, 12));
            chkExportRef.setToolTipText("Saves combined music audio as Tape_<ID><Side>_Reference.wav to verify envelope alignment in Audacity");
            paramPanel.add(chkExportRef);
        } else {
            paramPanel.add(Box.createHorizontalStrut(20));
            paramPanel.add(new JLabel("Simulated Genre:"));
            cbGenre = new JComboBox<>(ProceduralEnvelopeGenerator.Genre.values());
            cbGenre.setFont(new Font("Tahoma", Font.PLAIN, 12));
            cbGenre.setPreferredSize(new Dimension(240, 26));
            paramPanel.add(cbGenre);
        }

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
        overallProgressBar = new JProgressBar(0, isDCTMode ? 2 : Math.max(1, sideAList.size() + sideBList.size()));
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

        if (isDCTMode) {
            logTextArea.setText("> Ready. DCT Continuous Stream Mode (No audio tracks loaded).\n" +
                    "> Tape Length from Main UI: " + tapeLengthLabel + " (" + (tapeDurationSeconds / 60) + " min/side).\n" +
                    "> Procedural genre envelope modulation will simulate realistic music dynamics for VU meters.\n" +
                    "> Click 'Start Encoding' to generate Side A and Side B scaled DCT streams.\n");
        } else {
            logTextArea.setText("> Ready. 100% in-memory modulation enabled.\n" +
                    "> No intermediate unscaled WAV files will be written to disk.\n" +
                    "> Click 'Start Encoding' to begin processing Side A and Side B.\n");
        }

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
        if (chkExportRef != null) chkExportRef.setEnabled(false);
        if (cbGenre != null) cbGenre.setEnabled(false);

        final float minScale = (float) (Math.round(((Number) spinMinScale.getValue()).doubleValue() * 100.0) / 100.0);
        final String minScaleString = "0" + (int) (100 * minScale);
        final boolean exportRef = chkExportRef != null && chkExportRef.isSelected();

        String saveDirectoryName = CassetteFlow.AUDIO_DIR_NAME + File.separator + CassetteFlow.TAPE_FILE_DIR_NAME;
        File saveDir = new File(saveDirectoryName);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        if (isDCTMode) {
            startDCTEncoding(saveDir, saveDirectoryName, minScale, minScaleString);
        } else {
            startMixtapeEncoding(saveDir, saveDirectoryName, minScale, minScaleString, exportRef);
        }
    }

    private void startDCTEncoding(File saveDir, String saveDirectoryName, float minScale, String minScaleString) {
        final ProceduralEnvelopeGenerator.Genre selectedGenre = (ProceduralEnvelopeGenerator.Genre) cbGenre.getSelectedItem();
        final int durationSeconds = this.tapeDurationSeconds;

        overallProgressBar.setMaximum(2);
        overallProgressBar.setValue(0);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            private File outFileA;
            private File outFileB;

            @Override
            protected Void doInBackground() throws Exception {
                long startTime = System.currentTimeMillis();
                appendLog(String.format("Starting procedural DCT FSK encoding (%s, %s)...",
                        selectedGenre.getDisplayName(), tapeLengthLabel));
                appendLog(String.format("Target duration: %d seconds (%02d:%02d) per side.",
                        durationSeconds, durationSeconds / 60, durationSeconds % 60));

                // Process Side A
                lblCurrentTask.setText("Encoding Side A (" + selectedGenre.getFileTag() + ")...");
                outFileA = new File(saveDir, "Tape_" + effectiveTapeId + "A_Scaled" + minScaleString + "_" + selectedGenre.getFileTag() + ".wav");
                FSKModulator.processDCTSideInMemory(
                        cassetteFlow, 'A', effectiveTapeId, durationSeconds, selectedGenre, minScale, outFileA,
                        new FSKModulator.ModulatorProgressListener() {
                            @Override
                            public void onTrackStarted(String side, int currentTrack, int total, String trackName) {}

                            @Override
                            public void onTrackFinished(String side, int currentTrack, int total, String message) {}

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
                overallProgressBar.setValue(1);
                updateMemoryDisplay();

                // Process Side B
                lblCurrentTask.setText("Encoding Side B (" + selectedGenre.getFileTag() + ")...");
                outFileB = new File(saveDir, "Tape_" + effectiveTapeId + "B_Scaled" + minScaleString + "_" + selectedGenre.getFileTag() + ".wav");
                FSKModulator.processDCTSideInMemory(
                        cassetteFlow, 'B', effectiveTapeId, durationSeconds, selectedGenre, minScale, outFileB,
                        new FSKModulator.ModulatorProgressListener() {
                            @Override
                            public void onTrackStarted(String side, int currentTrack, int total, String trackName) {}

                            @Override
                            public void onTrackFinished(String side, int currentTrack, int total, String message) {}

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
                overallProgressBar.setValue(2);
                updateMemoryDisplay();

                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                appendLog(String.format("Both DCT sides completed in %d seconds! In-memory cleanup complete.", elapsedSec));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lblCurrentTask.setText("Status: DCT Modulation Complete!");
                    String msg = "Scaled DCT FSK files generated successfully in:\n" + saveDirectoryName +
                            "\n\n- " + (outFileA != null ? outFileA.getName() : "") +
                            "\n- " + (outFileB != null ? outFileB.getName() : "");
                    JOptionPane.showMessageDialog(ScaledFSKDialog.this,
                            msg, "DCT Modulation Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    appendLog("Error: " + ex.getMessage());
                    lblCurrentTask.setText("Status: Error occurred.");
                    JOptionPane.showMessageDialog(ScaledFSKDialog.this,
                            "Error during DCT modulation: " + ex.getMessage(),
                            "Processing Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                } finally {
                    btnStart.setEnabled(true);
                    spinMinScale.setEnabled(true);
                    if (cbGenre != null) cbGenre.setEnabled(true);
                    updateMemoryDisplay();
                }
            }
        };

        worker.execute();
    }

    private void startMixtapeEncoding(File saveDir, String saveDirectoryName, float minScale, String minScaleString, boolean exportRef) {
        int totalTracks = sideAList.size() + sideBList.size();
        overallProgressBar.setMaximum(Math.max(1, totalTracks));
        overallProgressBar.setValue(0);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            int processedCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                long startTime = System.currentTimeMillis();
                appendLog("Starting batch scaled FSK encoding...");

                // Process Side A
                if (!sideAList.isEmpty()) {
                    File outFileA = new File(saveDir, "Tape_" + effectiveTapeId + "A_Scaled" + minScaleString + ".wav");
                    File refFileA = exportRef ? new File(saveDir, "Tape_" + effectiveTapeId + "A_Reference.wav") : null;
                    FSKModulator.processSideInMemory(
                            cassetteFlow, "A", effectiveTapeId, sideAList, muteTime, minScale, outFileA, refFileA,
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
                    File outFileB = new File(saveDir, "Tape_" + effectiveTapeId + "B_Scaled" + minScaleString + ".wav");
                    File refFileB = exportRef ? new File(saveDir, "Tape_" + effectiveTapeId + "B_Reference.wav") : null;
                    FSKModulator.processSideInMemory(
                            cassetteFlow, "B", effectiveTapeId, sideBList, muteTime, minScale, outFileB, refFileB,
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
                    if (chkExportRef != null) chkExportRef.setEnabled(true);
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
