package cassetteflow;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Native Swing Telemetry & Diagnostics Window (JFrame).
 *
 * Mirrors the real-time decoder and system telemetry from telemetry.html
 * (excluding the real-time FSK terminal stream and Now Playing metadata container).
 *
 * Key Architectural Highlights:
 * 1. Independent Top-Level Window:
 *    Extends {@link javax.swing.JFrame} rather than {@link javax.swing.JDialog} so
 *    it can be freely stacked behind the main {@link CassetteFlowFrame}, minimized
 *    to the Windows desktop taskbar, or snapped side-by-side on small laptop displays.
 * 2. Non-Blocking Background Polling:
 *    Uses a daemon {@link ScheduledExecutorService} running at a 400ms interval to
 *    fetch telemetry snapshots, parse FSK records, and compute metrics off the Swing EDT.
 *    UI updates are safely dispatched via {@link SwingUtilities#invokeLater}.
 * 3. Lock-Free Audio Operations:
 *    Avoids native JavaSound Windows mixer lock contention during active playback by
 *    caching device lists and running manual reloads on a background worker thread.
 * 4. Signal & Tape Diagnostics:
 *    Computes real-time Wow & Flutter (RMS & Peak-to-Peak) over a 40-sample sliding
 *    window, tracks total tape elapsed time from incoming FSK frames, and monitors
 *    carrier lock, speed error, SNR, and parity/format errors.
 */
public class TelemetryFrame extends JFrame {

    // --- Modern Dark Theme Color Palette ---
    private static final Color COLOR_BG = new Color(13, 17, 23);         // Main window background (#0d1117)
    private static final Color COLOR_PANEL = new Color(22, 27, 34);      // Section container background (#161b22)
    private static final Color COLOR_CARD = new Color(33, 38, 45);        // Metric cards and badge background (#21262d)
    private static final Color COLOR_BORDER = new Color(48, 54, 61);      // Subtle border line color (#30363d)
    private static final Color COLOR_TEXT = new Color(201, 209, 217);     // Primary light text (#c9d1d9)
    private static final Color COLOR_TEXT_DIM = new Color(139, 148, 158); // Secondary/muted label text (#8b949e)
    private static final Color COLOR_SUCCESS = new Color(126, 231, 135);  // Good / Nominal status (#7ee787)
    private static final Color COLOR_WARN = new Color(210, 153, 34);      // Caution status (#d29922)
    private static final Color COLOR_DANGER = new Color(255, 123, 114);   // Critical error / Out-of-spec status (#ff7b72)
    private static final Color COLOR_ACCENT = new Color(88, 166, 255);    // Informational blue accent (#58a6ff)
    private static final Color COLOR_GOLD = new Color(255, 209, 102);     // Timecode & side highlight (#ffd166)

    /** Reference to the core CassetteFlow application coordinator. */
    private final CassetteFlow cassetteFlow;

    /**
     * Sliding FIFO buffer storing recent measured baud rates for Wow & Flutter calculation.
     * Capped at {@link #MAX_BAUD_SAMPLES} to mirror the 40-sample window in telemetry.html.
     */
    private final LinkedList<Double> baudSamples = new LinkedList<>();
    private static final int MAX_BAUD_SAMPLES = 40;

    // --- UI Components: Top Header HUD (System & Link Metrics) ---
    private JLabel lblIp;          // Server bind IP and port
    private JLabel lblCpu;         // System CPU utilization percentage
    private JLabel lblMem;         // JVM Heap memory usage percentage and MB
    private JLabel lblCarrier;     // Real-time FSK carrier lock status badge
    private JLabel lblBaud;        // Configured nominal baud rate (e.g. 1200)
    private JLabel lblErr;         // Instantaneous speed error percentage badge
    private JLabel lblSnr;         // Signal-to-noise ratio in dB and signal percent

    // --- UI Components: Center Status Banner ---
    private JLabel lblSideTimecode; // Side (A/B) and total tape elapsed timecode
    private JLabel lblModeBadge;    // Current decoder operational mode (e.g. DECODE AUTO)

    // --- UI Components: 2-Column Signal & Tape Diagnostics Grid ---
    private JLabel lblTotalRecs;    // Cumulative valid decoded records count
    private JLabel lblSpeedErr;     // Measured tape transport speed error percentage
    private JLabel lblDataErrs;     // Cumulative data / checksum errors count
    private JLabel lblMeasBaud;     // Exact measured carrier baud rate
    private JLabel lblSideA;        // Side A record count and error percentage
    private JLabel lblSideB;        // Side B record count and error percentage
    private JLabel lblSnrGrid;      // Audio signal-to-noise ratio in dB
    private JLabel lblCarrierState; // Textual carrier state (Locked / Unlocked)
    private JLabel lblFmtErrs;      // Framing errors: L (Length) and N (Numeric)
    private JLabel lblStops;        // Tape transport stop events count
    private JLabel lblWfRms;        // RMS Wow & Flutter percentage and compliance tag
    private JLabel lblWfPeak;       // Peak-to-Peak Wow & Flutter percentage

    // --- UI Components: Bottom Controls Bar ---
    private JButton btnAudioMon;        // Audio monitor pass-through toggle button
    private JComboBox<String> cbAudioDevice; // Audio output device dropdown
    private JButton btnReloadDevices;   // Manual audio device list refresh button
    private JComboBox<String> cbVolume; // Audio monitor output volume dropdown
    private JButton btnResetStats;      // Decoder statistics reset button
    private JButton btnClose;           // Window close button (docked right)

    // --- Background Polling Executor & State Flags ---
    private ScheduledExecutorService pollExecutor;
    private final AtomicBoolean isPollingActive = new AtomicBoolean(false);
    private boolean updatingControls = false; // Guard flag preventing event feedback during programmatic combo updates
    private boolean audioMonActive = false;   // Local mirror of the audio monitor active state

    // --- Regular Expressions for FSK Terminal Log & Diagnostic Record Parsing ---
    private static final Pattern PATTERN_SIDE_A = Pattern.compile("(?i)\\b(SIDE\\s*A|DCT0A)\\b");
    private static final Pattern PATTERN_SIDE_B = Pattern.compile("(?i)\\b(SIDE\\s*B|DCT0B)\\b");
    private static final Pattern PATTERN_TAPE_COUNTER = Pattern.compile("(?i)(?:Tape Counter|Tape Time):\\s*(\\d+)");
    private static final Pattern PATTERN_TAPE_TIME = Pattern.compile("(?i)TAPE TIME:\\s*(\\d+)");
    private static final Pattern PATTERN_TIMECODE = Pattern.compile("(?i)TIMECODE[:\\s]+(\\d{1,2}):(\\d{2}):(\\d{2})");

    /** Current tape side ("A" or "B") tracked across incoming FSK records. */
    private String clientSide = "A";

    /** Current total tape counter/time in seconds tracked from the last four numbers of FSK records. */
    private int clientTimeSec = 0;

    /**
     * Constructs a new TelemetryFrame without a parent reference.
     *
     * @param cassetteFlow active CassetteFlow coordinator instance
     */
    public TelemetryFrame(CassetteFlow cassetteFlow) {
        this(cassetteFlow, null);
    }

    /**
     * Constructs a new TelemetryFrame positioned relative to the parent frame.
     *
     * @param cassetteFlow active CassetteFlow coordinator instance
     * @param parent       parent frame for centering and icon inheritance (may be null)
     */
    public TelemetryFrame(CassetteFlow cassetteFlow, Frame parent) {
        super("CassetteFlow Telemetry & Diagnostics");
        this.cassetteFlow = cassetteFlow;

        // Inherit window icon from parent if present
        if (parent != null && parent.getIconImage() != null) {
            setIconImage(parent.getIconImage());
        }

        initComponents();

        // Perform one initial audio device enumeration on open
        reloadAudioDevices();

        setSize(860, 520);
        setMinimumSize(new Dimension(820, 460));
        if (parent != null) {
            setLocationRelativeTo(parent);
        } else {
            setLocationByPlatform(true);
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Ensure background polling terminates immediately when window closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopPolling();
            }
        });

        startPolling();
    }

    /**
     * Builds and lays out all UI panels, cards, diagnostic grids, and control buttons.
     */
    private void initComponents() {
        JPanel rootPane = new JPanel();
        rootPane.setLayout(new BorderLayout(8, 8));
        rootPane.setBackground(COLOR_BG);
        rootPane.setBorder(new EmptyBorder(10, 12, 10, 12));

        // =========================================================================
        // 1. Header HUD Panel (Server IP, CPU, Memory, Carrier, Baud, Err, SNR)
        // =========================================================================
        JPanel hudPanel = new JPanel(new BorderLayout(6, 6));
        hudPanel.setBackground(COLOR_PANEL);
        hudPanel.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));

        // Left HUD: System and Connection Metrics
        JPanel hudLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        hudLeft.setOpaque(false);
        lblIp = createPillLabel("IP: Connecting...", COLOR_TEXT);
        lblCpu = createPillLabel("CPU: --%", COLOR_TEXT);
        lblMem = createPillLabel("MEM: --%", COLOR_TEXT);
        lblCarrier = createPillLabel("[NO CARRIER]", COLOR_TEXT_DIM);
        hudLeft.add(lblIp);
        hudLeft.add(lblCpu);
        hudLeft.add(lblMem);
        hudLeft.add(lblCarrier);

        // Right HUD: Link and Speed Metrics
        JPanel hudRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        hudRight.setOpaque(false);
        lblBaud = createPillLabel("Baud: 1200", COLOR_TEXT);
        lblErr = createPillLabel("Err: +0.00%", COLOR_SUCCESS);
        lblSnr = createPillLabel("SNR: -- dB", COLOR_ACCENT);
        hudRight.add(lblBaud);
        hudRight.add(lblErr);
        hudRight.add(lblSnr);

        hudPanel.add(hudLeft, BorderLayout.WEST);
        hudPanel.add(hudRight, BorderLayout.EAST);
        rootPane.add(hudPanel, BorderLayout.NORTH);

        // =========================================================================
        // 2. Center Content: Status Banner + 2-Column Diagnostics Grid
        // =========================================================================
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // A. Summary Banner: Side, Timecode, and Decoder Mode
        JPanel bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBackground(COLOR_CARD);
        bannerPanel.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)));

        lblSideTimecode = new JLabel("SIDE A  |  TIMECODE: 00:00:00 (0s)");
        lblSideTimecode.setFont(new Font("Consolas", Font.BOLD, 13));
        lblSideTimecode.setForeground(COLOR_GOLD);

        lblModeBadge = new JLabel("MODE: DECODE AUTO");
        lblModeBadge.setFont(new Font("Consolas", Font.BOLD, 12));
        lblModeBadge.setForeground(COLOR_ACCENT);

        bannerPanel.add(lblSideTimecode, BorderLayout.WEST);
        bannerPanel.add(lblModeBadge, BorderLayout.EAST);
        centerPanel.add(bannerPanel);
        centerPanel.add(Box.createVerticalStrut(8));

        // B. 2-Column Diagnostics Grid Panel
        JPanel gridCard = new JPanel(new BorderLayout());
        gridCard.setBackground(COLOR_PANEL);
        gridCard.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));

        JLabel lblGridTitle = new JLabel("TAPE TELEMETRY & SIGNAL DIAGNOSTICS");
        lblGridTitle.setFont(new Font("Tahoma", Font.BOLD, 11));
        lblGridTitle.setForeground(COLOR_TEXT_DIM);
        lblGridTitle.setBorder(new EmptyBorder(0, 0, 6, 0));
        gridCard.add(lblGridTitle, BorderLayout.NORTH);

        JPanel gridContent = new JPanel(new GridLayout(6, 2, 24, 6));
        gridContent.setOpaque(false);

        // Row 1: Total Records | Speed Error
        lblTotalRecs = new JLabel("0");
        lblSpeedErr = new JLabel("+0.00%");
        gridContent.add(createStatRow("Total Records:", lblTotalRecs, COLOR_TEXT));
        gridContent.add(createStatRow("Speed Error:", lblSpeedErr, COLOR_SUCCESS));

        // Row 2: Measured Baud | Carrier State
        lblMeasBaud = new JLabel("1200.0 Bd");
        lblCarrierState = new JLabel("Unlocked");
        gridContent.add(createStatRow("Measured Baud:", lblMeasBaud, COLOR_TEXT));
        gridContent.add(createStatRow("Carrier State:", lblCarrierState, COLOR_TEXT_DIM));

        // Row 3: Data Errors | Signal SNR
        lblDataErrs = new JLabel("0");
        lblSnrGrid = new JLabel("-- dB");
        gridContent.add(createStatRow("Data Errors:", lblDataErrs, COLOR_SUCCESS));
        gridContent.add(createStatRow("Signal SNR:", lblSnrGrid, COLOR_ACCENT));

        // Row 4: Side A Records | Side B Records
        lblSideA = new JLabel("0 (0.0%)");
        lblSideB = new JLabel("0 (0.0%)");
        gridContent.add(createStatRow("Side A Records:", lblSideA, COLOR_TEXT));
        gridContent.add(createStatRow("Side B Records:", lblSideB, COLOR_TEXT));

        // Row 5: Format Errors (Length & Numeric) | Total Stops
        lblFmtErrs = new JLabel("L=0 N=0");
        lblStops = new JLabel("0");
        gridContent.add(createStatRow("Format Errors:", lblFmtErrs, COLOR_TEXT));
        gridContent.add(createStatRow("Total Stops:", lblStops, COLOR_TEXT));

        // Row 6: Wow & Flutter (RMS) | W/F Peak (Peak-to-Peak)
        lblWfRms = new JLabel("0.00% [OK]");
        lblWfPeak = new JLabel("0.00%");
        gridContent.add(createStatRow("Wow & Flutter (RMS):", lblWfRms, COLOR_SUCCESS));
        gridContent.add(createStatRow("W/F Peak (P-P):", lblWfPeak, COLOR_TEXT));

        gridCard.add(gridContent, BorderLayout.CENTER);
        centerPanel.add(gridCard);
        rootPane.add(centerPanel, BorderLayout.CENTER);

        // =========================================================================
        // 3. Bottom Controls Panel: Audio Monitor, Device, Vol, Reset, & Close
        // =========================================================================
        JPanel controlsPanel = new JPanel(new BorderLayout(8, 0));
        controlsPanel.setBackground(COLOR_PANEL);
        controlsPanel.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(4, 6, 4, 6)));

        // Left Controls Group (Audio monitor, device dropdown, reload, volume, reset)
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        leftControls.setOpaque(false);

        // Audio Monitor Button
        btnAudioMon = new JButton("AUDIO MON: OFF");
        btnAudioMon.setFont(new Font("Tahoma", Font.BOLD, 11));
        btnAudioMon.setBackground(COLOR_CARD);
        btnAudioMon.setForeground(COLOR_TEXT);
        btnAudioMon.setFocusPainted(false);
        btnAudioMon.setPreferredSize(new Dimension(140, 28));
        btnAudioMon.addActionListener(e -> toggleAudioMonitor());
        leftControls.add(btnAudioMon);

        // Device Selector Dropdown
        JLabel lblDev = new JLabel("Device:");
        lblDev.setFont(new Font("Tahoma", Font.PLAIN, 11));
        lblDev.setForeground(COLOR_TEXT_DIM);
        leftControls.add(lblDev);

        cbAudioDevice = new JComboBox<>(new String[] { "Default Playback Device" });
        cbAudioDevice.setFont(new Font("Tahoma", Font.PLAIN, 11));
        cbAudioDevice.setPreferredSize(new Dimension(200, 26));
        cbAudioDevice.addActionListener(e -> onAudioDeviceSelected());
        leftControls.add(cbAudioDevice);

        // Reload Devices Button (Scans Windows audio hardware on demand)
        btnReloadDevices = new JButton("Reload");
        btnReloadDevices.setToolTipText("Reload audio playback devices");
        btnReloadDevices.setFont(new Font("Tahoma", Font.PLAIN, 11));
        btnReloadDevices.setBackground(COLOR_CARD);
        btnReloadDevices.setForeground(COLOR_TEXT);
        btnReloadDevices.setFocusPainted(false);
        btnReloadDevices.setPreferredSize(new Dimension(68, 26));
        btnReloadDevices.addActionListener(e -> reloadAudioDevices());
        leftControls.add(btnReloadDevices);

        // Volume Selector Dropdown
        JLabel lblVol = new JLabel("Vol:");
        lblVol.setFont(new Font("Tahoma", Font.PLAIN, 11));
        lblVol.setForeground(COLOR_TEXT_DIM);
        leftControls.add(lblVol);

        cbVolume = new JComboBox<>(new String[] {
                "100%", "90%", "80%", "70%", "60%", "50%", "40%", "30%", "20%", "10%", "0% (Mute)"
        });
        cbVolume.setSelectedIndex(5); // Default to 50%
        cbVolume.setFont(new Font("Tahoma", Font.PLAIN, 11));
        cbVolume.setPreferredSize(new Dimension(85, 26));
        cbVolume.addActionListener(e -> onVolumeSelected());
        leftControls.add(cbVolume);

        // Reset Statistics Button
        btnResetStats = new JButton("RESET STATS");
        btnResetStats.setFont(new Font("Tahoma", Font.BOLD, 11));
        btnResetStats.setBackground(new Color(130, 30, 30));
        btnResetStats.setForeground(Color.WHITE);
        btnResetStats.setFocusPainted(false);
        btnResetStats.setPreferredSize(new Dimension(110, 28));
        btnResetStats.addActionListener(e -> resetStats());
        leftControls.add(btnResetStats);

        controlsPanel.add(leftControls, BorderLayout.CENTER);

        // Right Controls Group: Close button glued flush to the right window edge
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        rightControls.setOpaque(false);

        btnClose = new JButton("Close");
        btnClose.setFont(new Font("Tahoma", Font.PLAIN, 11));
        btnClose.setBackground(COLOR_CARD);
        btnClose.setForeground(COLOR_TEXT);
        btnClose.setFocusPainted(false);
        btnClose.setPreferredSize(new Dimension(75, 28));
        btnClose.addActionListener(e -> dispose());
        rightControls.add(btnClose);

        controlsPanel.add(rightControls, BorderLayout.EAST);

        rootPane.add(controlsPanel, BorderLayout.SOUTH);

        setContentPane(rootPane);
    }

    /**
     * Helper to construct a stylized pill/badge label used in the HUD.
     *
     * @param text    label text
     * @param fgColor foreground text color
     * @return configured JLabel
     */
    private JLabel createPillLabel(String text, Color fgColor) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Consolas", Font.BOLD, 11));
        lbl.setForeground(fgColor);
        lbl.setBackground(COLOR_CARD);
        lbl.setOpaque(true);
        lbl.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(3, 8, 3, 8)));
        return lbl;
    }

    /**
     * Helper to construct a diagnostic row with a label on the left and value on the right.
     *
     * @param labelText    metric description
     * @param valueLabel   target JLabel displaying the metric value
     * @param defaultColor initial foreground color for the value
     * @return configured JPanel row
     */
    private JPanel createStatRow(String labelText, JLabel valueLabel, Color defaultColor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(35, 42, 52)),
                new EmptyBorder(2, 0, 2, 0)));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Tahoma", Font.PLAIN, 12));
        lbl.setForeground(COLOR_TEXT_DIM);

        valueLabel.setFont(new Font("Consolas", Font.BOLD, 12));
        valueLabel.setForeground(defaultColor);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        panel.add(lbl, BorderLayout.WEST);
        panel.add(valueLabel, BorderLayout.EAST);
        return panel;
    }

    /**
     * Starts the background telemetry polling thread at 400ms intervals.
     */
    private void startPolling() {
        if (isPollingActive.compareAndSet(false, true)) {
            pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TelemetryFrame-Poller");
                t.setDaemon(true);
                return t;
            });
            pollExecutor.scheduleAtFixedRate(this::pollInBackground, 0, 400, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the background telemetry polling thread and shuts down the executor service.
     */
    private void stopPolling() {
        isPollingActive.set(false);
        if (pollExecutor != null && !pollExecutor.isShutdown()) {
            pollExecutor.shutdownNow();
            pollExecutor = null;
        }
    }

    /**
     * Polls the live telemetry snapshot from CassetteFlow on a background daemon thread
     * and dispatches UI updates safely to the Swing EDT.
     *
     * Offloading this work from the EDT eliminates deadlocks with active audio decoding.
     */
    private void pollInBackground() {
        if (!isPollingActive.get() || cassetteFlow == null)
            return;
        try {
            JSONObject state = cassetteFlow.getTelemetryState();
            if (state == null)
                return;

            // Retrieve current FSK records and terminal streams for timecode parsing
            String currentRec = cassetteFlow.getCurrentLineRecord();
            String rawRec = cassetteFlow.getRawLineRecord();
            String rxText = state.optString("rx_text", "");
            String nowPlaying = state.optString("now_playing", "");

            StringBuilder sb = new StringBuilder();
            if (rxText != null && !rxText.isEmpty()) sb.append(rxText).append("\n");
            if (nowPlaying != null && !nowPlaying.isEmpty()) sb.append(nowPlaying).append("\n");
            if (rawRec != null && !rawRec.isEmpty() && !rawRec.startsWith("NO PLAYER")) sb.append(rawRec).append("\n");
            if (currentRec != null && !currentRec.isEmpty() && !currentRec.startsWith("NO PLAYER")) sb.append(currentRec).append("\n");
            parseTimecodeFromLog(sb.toString());

            String stateSide = state.optString("side", "");
            String side = clientSide;
            if ("B".equalsIgnoreCase(stateSide)) {
                side = "B";
                clientSide = "B";
            } else if ("A".equalsIgnoreCase(stateSide) && !"B".equals(clientSide)) {
                side = "A";
            }

            int timeSec = clientTimeSec;
            if (timeSec <= 0 && state.optInt("time_seconds", 0) > 0) {
                timeSec = state.optInt("time_seconds", 0);
            }

            String timecode = formatTimecode(timeSec);

            final String finalSide = side;
            final int finalTimeSec = timeSec;
            final String finalTimecode = timecode;

            // Dispatch UI modifications to the Swing Event Dispatch Thread (EDT)
            SwingUtilities.invokeLater(() -> applyTelemetryToUI(state, finalSide, finalTimecode, finalTimeSec));
        } catch (Exception ex) {
            // Transient background poll error protection
        }
    }

    /**
     * Updates all UI labels and status badges on the Swing EDT with the polled snapshot.
     *
     * @param state       telemetry snapshot JSON object
     * @param side        current tape side ("A" or "B")
     * @param timecode    formatted timecode string ("HH:MM:SS")
     * @param timeSec     elapsed total tape time in seconds
     */
    private void applyTelemetryToUI(JSONObject state, String side, String timecode, int timeSec) {
        if (!isShowing())
            return;
        try {

            // 1. Header HUD Updates
            lblIp.setText("IP: " + state.optString("param_ip", "localhost:8192"));

            double cpu = state.optDouble("cpu_percent", 0.0);
            lblCpu.setText(String.format("CPU: %.1f%%", cpu));

            double memPct = state.optDouble("mem_percent", 0.0);
            int memMb = state.optInt("mem_used_mb", 0);
            lblMem.setText(String.format("MEM: %.1f%% (%dMB)", memPct, memMb));

            int baud = state.optInt("param_baud", 1200);
            lblBaud.setText("Baud: " + baud);

            boolean carrier = state.optBoolean("carrier", false);
            double measBaud = state.optDouble("param_measured_baud", 1200.0);
            if (carrier) {
                lblCarrier.setText(String.format("[CARRIER LOCKED: %.0f Bd]", measBaud));
                lblCarrier.setForeground(COLOR_SUCCESS);
                lblCarrier.setBorder(
                        new CompoundBorder(new LineBorder(COLOR_SUCCESS, 1, true), new EmptyBorder(3, 8, 3, 8)));
            } else {
                lblCarrier.setText("[NO CARRIER]");
                lblCarrier.setForeground(COLOR_TEXT_DIM);
                lblCarrier.setBorder(
                        new CompoundBorder(new LineBorder(COLOR_BORDER, 1, true), new EmptyBorder(3, 8, 3, 8)));
            }

            double speedErr = state.optDouble("param_speed_error", 0.0);
            lblErr.setText(String.format("Err: %+5.2f%%", speedErr));
            if (Math.abs(speedErr) > 1.5) {
                lblErr.setForeground(COLOR_DANGER);
            } else {
                lblErr.setForeground(COLOR_SUCCESS);
            }

            double snr = state.optDouble("snr", 0.0);
            int sig = state.optInt("sig", 0);
            lblSnr.setText(String.format("SNR: %.1f dB (%d%%)", snr, sig));

            // 2. Banner: Side & Timecode
            lblSideTimecode.setText(String.format("SIDE %s  |  TIMECODE: %s (%ds)", side, timecode, timeSec));

            String mode = state.optString("mode", "DECODE AUTO");
            lblModeBadge.setText("MODE: " + mode.toUpperCase());

            // 3. Diagnostics Grid Updates
            lblTotalRecs.setText(String.format("%,d", state.optInt("total_recs", 0)));

            lblSpeedErr.setText(String.format("%+5.2f%%", speedErr));
            lblSpeedErr.setForeground(Math.abs(speedErr) > 1.5 ? COLOR_DANGER : COLOR_SUCCESS);

            int dataErrs = state.optInt("data_errors", 0);
            lblDataErrs.setText(String.valueOf(dataErrs));
            lblDataErrs.setForeground(dataErrs > 0 ? COLOR_DANGER : COLOR_SUCCESS);

            lblMeasBaud.setText(String.format("%.1f Bd", measBaud));

            int sideACount = state.optInt("side_a_count", 0);
            double sideAErr = state.optDouble("side_a_err", 0.0);
            lblSideA.setText(String.format("%,d (%.1f%% err)", sideACount, sideAErr));

            int sideBCount = state.optInt("side_b_count", 0);
            double sideBErr = state.optDouble("side_b_err", 0.0);
            lblSideB.setText(String.format("%,d (%.1f%% err)", sideBCount, sideBErr));

            lblSnrGrid.setText(String.format("%.1f dB", snr));
            lblCarrierState.setText(carrier ? "Locked" : "Unlocked");
            lblCarrierState.setForeground(carrier ? COLOR_SUCCESS : COLOR_TEXT_DIM);

            int lenErrs = state.optInt("len_errors", 0);
            int numErrs = state.optInt("num_errors", 0);
            lblFmtErrs.setText(String.format("L=%d N=%d", lenErrs, numErrs));

            lblStops.setText(String.valueOf(state.optInt("stops", 0)));

            // 4. Update Wow & Flutter calculation from measured baud samples
            updateWowAndFlutter(measBaud, carrier, baud);

            // 5. Sync Audio Monitor Controls with active state
            updatingControls = true;
            try {
                audioMonActive = state.optBoolean("audio_mon_enabled", false);
                if (audioMonActive) {
                    btnAudioMon.setText("AUDIO MON: ON");
                    btnAudioMon.setBackground(new Color(31, 111, 235)); // Blue
                    btnAudioMon.setForeground(Color.WHITE);
                } else {
                    btnAudioMon.setText("AUDIO MON: OFF");
                    btnAudioMon.setBackground(COLOR_CARD);
                    btnAudioMon.setForeground(COLOR_TEXT);
                }

                // Synchronize active playback device selection if changed externally
                String activeDevice = state.optString("current_audio_device", "");
                if (!activeDevice.isEmpty() && !cbAudioDevice.isPopupVisible()
                        && !activeDevice.equals(cbAudioDevice.getSelectedItem())) {
                    cbAudioDevice.setSelectedItem(activeDevice);
                }

                // Synchronize volume dropdown selection
                int activeVol = state.optInt("audio_mon_volume", 50);
                if (!cbVolume.isPopupVisible()) {
                    String targetPrefix = activeVol + "%";
                    for (int i = 0; i < cbVolume.getItemCount(); i++) {
                        if (cbVolume.getItemAt(i).startsWith(targetPrefix)) {
                            if (cbVolume.getSelectedIndex() != i) {
                                cbVolume.setSelectedIndex(i);
                            }
                            break;
                        }
                    }
                }
            } finally {
                updatingControls = false;
            }

        } catch (Exception ex) {
            // Transient error protection
        }
    }

    /**
     * Calculates RMS and Peak-to-Peak Wow & Flutter from running baud rate samples,
     * mirroring the calculation in telemetry.html.
     *
     * @param measuredBaud  instantaneous measured baud rate
     * @param carrierActive whether FSK carrier lock is acquired
     * @param targetBaud    nominal configured baud rate (e.g. 1200)
     */
    private void updateWowAndFlutter(double measuredBaud, boolean carrierActive, double targetBaud) {
        double nominalBaud = targetBaud > 0 ? targetBaud : 1200.0;

        if (!carrierActive || measuredBaud <= 0.0) {
            if (!carrierActive && !baudSamples.isEmpty()) {
                baudSamples.clear();
            }
            lblWfRms.setText("0.00% [OK]");
            lblWfRms.setForeground(COLOR_SUCCESS);
            lblWfPeak.setText("0.00%");
            return;
        }

        baudSamples.add(measuredBaud);
        if (baudSamples.size() > MAX_BAUD_SAMPLES) {
            baudSamples.removeFirst();
        }

        if (baudSamples.size() >= 3) {
            double sum = 0.0;
            double minBaud = baudSamples.getFirst();
            double maxBaud = baudSamples.getFirst();
            for (double b : baudSamples) {
                sum += b;
                if (b < minBaud)
                    minBaud = b;
                if (b > maxBaud)
                    maxBaud = b;
            }
            double mean = sum / baudSamples.size();
            double sqDiffSum = 0.0;
            for (double b : baudSamples) {
                double diff = b - mean;
                sqDiffSum += diff * diff;
            }
            double stdDev = Math.sqrt(sqDiffSum / baudSamples.size());
            double rmsWf = (stdDev / nominalBaud) * 100.0;
            if (rmsWf < 0.02)
                rmsWf = 0.0; // Filter digital quantization noise floor

            double p2pWf = ((maxBaud - minBaud) / nominalBaud) * 100.0;

            String statusTag = "[OK]";
            Color statusColor = COLOR_SUCCESS;
            if (rmsWf > 0.40) {
                statusTag = "[HIGH]";
                statusColor = COLOR_DANGER;
            } else if (rmsWf > 0.20) {
                statusTag = "[WARN]";
                statusColor = COLOR_WARN;
            }

            lblWfRms.setText(String.format("%.2f%% %s", rmsWf, statusTag));
            lblWfRms.setForeground(statusColor);
            lblWfPeak.setText(String.format("%.2f%%", p2pWf));
        } else {
            lblWfRms.setText("CALC...");
            lblWfRms.setForeground(COLOR_TEXT);
            lblWfPeak.setText("--%");
        }
    }

    /**
     * Toggles the audio pass-through monitor on or off.
     */
    private void toggleAudioMonitor() {
        if (cassetteFlow == null)
            return;
        boolean nextState = !audioMonActive;
        cassetteFlow.runTelemetryCommand("spk_mon", nextState ? 1 : 0);
        btnAudioMon.setText(nextState ? "AUDIO MON: ON" : "AUDIO MON: OFF");
        btnAudioMon.setBackground(nextState ? new Color(31, 111, 235) : COLOR_CARD);
        btnAudioMon.setForeground(nextState ? Color.WHITE : COLOR_TEXT);
    }

    /**
     * Enumerates available audio playback devices on a background worker thread
     * and updates the combo box on the EDT without blocking audio playback.
     */
    public void reloadAudioDevices() {
        if (btnReloadDevices != null) {
            btnReloadDevices.setEnabled(false);
            btnReloadDevices.setText("...");
        }
        new Thread(() -> {
            List<String> devList = CassettePlayer.getAvailablePlaybackDevices();
            SwingUtilities.invokeLater(() -> {
                updatingControls = true;
                try {
                    String selected = (String) cbAudioDevice.getSelectedItem();
                    cbAudioDevice.removeAllItems();
                    for (String d : devList) {
                        cbAudioDevice.addItem(d);
                    }
                    if (selected != null && devList.contains(selected)) {
                        cbAudioDevice.setSelectedItem(selected);
                    }
                } finally {
                    updatingControls = false;
                    if (btnReloadDevices != null) {
                        btnReloadDevices.setEnabled(true);
                        btnReloadDevices.setText("Reload");
                    }
                }
            });
        }, "TelemetryFrame-ReloadDevices").start();
    }

    /**
     * Handles selection changes in the audio output device dropdown.
     */
    private void onAudioDeviceSelected() {
        if (updatingControls || cassetteFlow == null)
            return;
        String dev = (String) cbAudioDevice.getSelectedItem();
        if (dev != null && !dev.isEmpty()) {
            cassetteFlow.runTelemetryCommand("audio_device", dev);
        }
    }

    /**
     * Handles volume changes in the audio monitor volume dropdown.
     */
    private void onVolumeSelected() {
        if (updatingControls || cassetteFlow == null)
            return;
        String volStr = (String) cbVolume.getSelectedItem();
        if (volStr != null) {
            try {
                int vol = Integer.parseInt(volStr.replaceAll("[^0-9]", ""));
                cassetteFlow.runTelemetryCommand("audio_volume", vol);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Formats elapsed seconds into an HH:MM:SS timecode string.
     *
     * @param totalSeconds total elapsed seconds
     * @return formatted timecode
     */
    private static String formatTimecode(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * Parses recent terminal output lines in reverse chronological order to extract
     * tape side (A/B) and the total tape time (last four numbers of FSK record).
     *
     * @param rxText terminal log / raw FSK records string
     */
    private void parseTimecodeFromLog(String rxText) {
        if (rxText == null || rxText.isEmpty()) return;
        String[] lines = rxText.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("###")) continue;

            // Check for Side indicators in line
            if (PATTERN_SIDE_A.matcher(line).find()) {
                clientSide = "A";
            } else if (PATTERN_SIDE_B.matcher(line).find()) {
                clientSide = "B";
            }

            // Case 1: DCT compact format (e.g. "DCT A001270003001" or "DCT B000450001001")
            if (line.toUpperCase().startsWith("DCT ") && line.length() >= 10) {
                char sideChar = Character.toUpperCase(line.charAt(4));
                if (sideChar == 'A' || sideChar == 'B') {
                    clientSide = String.valueOf(sideChar);
                }
                try {
                    clientTimeSec = Integer.parseInt(line.substring(5, 10).trim());
                    return;
                } catch (Exception ignored) {}
            }

            // Case 2: Delimited FSK line (e.g. "DCT0A_01_aaaaaaaaaa_0010_0127" or "TapeA_01_A001_0010_0127")
            if (line.contains("_")) {
                String[] parts = line.split("_");
                if (parts.length >= 5) {
                    String part0 = parts[0].toUpperCase();
                    if (part0.contains("0A") || part0.endsWith("A") || part0.contains("SIDEA") || part0.contains("DCT0A")) {
                        clientSide = "A";
                    } else if (part0.contains("0B") || part0.endsWith("B") || part0.contains("SIDEB") || part0.contains("DCT0B")) {
                        clientSide = "B";
                    }

                    // Total tape time is always the last part (the last four numbers: parts[4])
                    String lastPart = parts[parts.length - 1].replaceAll("[^0-9]", "");
                    if (!lastPart.isEmpty()) {
                        try {
                            clientTimeSec = Integer.parseInt(lastPart);
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Case 3: Raw 29-character FSK record without delimiters
            if (line.length() >= 29 && (line.toUpperCase().startsWith("DCT0A") || line.toUpperCase().startsWith("DCT0B"))) {
                clientSide = line.toUpperCase().startsWith("DCT0B") ? "B" : "A";
                try {
                    String last4 = line.substring(line.length() - 4).replaceAll("[^0-9]", "");
                    if (!last4.isEmpty()) {
                        clientTimeSec = Integer.parseInt(last4);
                        return;
                    }
                } catch (Exception ignored) {}
            }

            // Case 4: "Tape Counter: 127" (total tape counter/time)
            Matcher mCounter = PATTERN_TAPE_COUNTER.matcher(line);
            if (mCounter.find()) {
                try {
                    clientTimeSec = Integer.parseInt(mCounter.group(1));
                    return;
                } catch (Exception ignored) {}
            }

            // Case 5: "TAPE TIME: 127"
            Matcher m4 = PATTERN_TAPE_TIME.matcher(line);
            if (m4.find()) {
                try {
                    clientTimeSec = Integer.parseInt(m4.group(1));
                    return;
                } catch (Exception ignored) {}
            }

            // Case 6: Timecode string (e.g. "TIMECODE: 00:02:07")
            Matcher m5 = PATTERN_TIMECODE.matcher(line);
            if (m5.find()) {
                try {
                    int h = Integer.parseInt(m5.group(1));
                    int m = Integer.parseInt(m5.group(2));
                    int s = Integer.parseInt(m5.group(3));
                    clientTimeSec = (h * 3600) + (m * 60) + s;
                    return;
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Resets decoder telemetry and speed error statistics via a background worker thread.
     */
    private void resetStats() {
        if (cassetteFlow == null)
            return;
        baudSamples.clear();
        new Thread(() -> {
            cassetteFlow.runTelemetryCommand("reset", 0);
            pollInBackground();
        }, "TelemetryFrame-Reset").start();
    }

    /**
     * Disposes the frame after terminating all background polling tasks.
     */
    @Override
    public void dispose() {
        stopPolling();
        super.dispose();
    }
}
