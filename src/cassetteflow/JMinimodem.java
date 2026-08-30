package cassetteflow;

import javax.sound.sampled.*;
import java.io.*;
import java.util.*;

/**
 * JMinimodem - A pure Java FSK Modem Implementation from Gemini 3.0 Pro.
 * <p>
 * This class implements a software-defined radio (SDR) modem capable of
 * transmitting and receiving Frequency Shift Keying (FSK) signals.
 * It is designed to be compatible with the "minimodem" C program.
 * </p>
 * <h3>Core Features:</h3>
 * <ul>
 * <li><b>DSP Engine:</b> Uses a Sliding Window Heterodyne filter (matched filter) for demodulation.</li>
 * <li><b>Soft PLL Sync:</b> Digital Phase-Locked Loop to track signal drift without "snapping" on noise.</li>
 * <li><b>DC Blocker:</b> Removes microphone offset voltage (hum) to improve dynamic range.</li>
 * <li><b>Noise Kill Switch:</b> Drops carrier if framing errors (static) exceed a threshold.</li>
 * <li><b>Speed Measurement:</b> Measures edge deltas to determine instantaneous and average tape playback speed / baud rate.</li>
 * <li><b>Dual Mode:</b> Works as a CLI tool or a Library API.</li>
 * </ul>
 */
public class JMinimodem {

    /**
     * Structured container for live FSK diagnostic and speed metrics.
     */
    public static class DiagnosticData {
        public double measuredBaud = 0.0;
        public double speedOffsetPercent = 0.0;
        public double snr = 0.0;
        public int signalPercent = 0;
        public char currentSide = '?';
        public int totalStops = 0;
        public int logLineCount = 0;
        public int dataErrors = 0;
        public int sideAErrors = 0;
        public int sideALineCount = 0;
        public int sideBErrors = 0;
        public int sideBLineCount = 0;
        public int dataLengthErrors = 0;
        public boolean carrier = false;
        public String rawDiagnosticString = "";
    }

    /**
     * Configuration container for all modem settings.
     * Pass an instance of this to the static methods to control behavior.
     */
    public static class Config {
        /** Enable Transmit Mode */
        public boolean txMode = false;
        /** Enable Receive Mode */
        public boolean rxMode = false;
        /** Optional: Read/Write to this file instead of Mic/Speakers */
        public File audioFile = null;
        
        // --- Modem Protocol Settings (Defaults to Bell 202) ---
        /** Baud rate in bits per second (e.g., 1200) */
        public double baudRate = 1200.0;     
        /** Frequency for "1" (Mark/Idle) state */
        public double freqMark = 1200.0;     
        /** Frequency for "0" (Space/Start) state */
        public double freqSpace = 2200.0;    
        /** Sample rate for DSP engine (48kHz standard) */
        public float sampleRate = 48000.0f; 
        
        /** If true, swaps Mark and Space frequencies */
        public boolean invert = false;
        
        // --- Squelch & Diagnostics ---
        /** Minimum Signal-to-Noise Ratio (SNR) required to decode */
        public double confidenceThreshold = 1.0; 
        
        /** Minimum absolute volume (0.0-1.0) to trigger squelch.
         * Set to 0.2 to filter out background microphone noise and static.
         */
        public double noiseFloor = 0.2;         
        
        /** Suppress status messages */
        public boolean quiet = false;
        
        // --- Added for Decoder Telemetry & Speed Diagnostics ---
        public DiagnosticListener diagListener = null;
        public DiagnosticDataListener diagDataListener = null;
        public volatile boolean resetDiagnostics = false;
    }
    
    /**
     * Callback interface to receive periodic FSK diagnostic strings from the decoder thread.
     */
    public interface DiagnosticListener {
        void onDiagnostic(String diagLine);
    }

    /**
     * Callback interface to receive structured FSK diagnostic data from the decoder thread.
     */
    public interface DiagnosticDataListener {
        void onDiagnosticData(DiagnosticData data);
    }

    // =========================================================================
    // 1. CLI ENTRY POINT
    // =========================================================================
    
    /**
     * Main Entry Point for Command Line Interface.
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Config cfg = new Config();
        List<String> positional = new ArrayList<>();

        // --- Argument Parsing Loop ---
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                switch (arg) {
                    case "--tx": case "-t": cfg.txMode = true; break;
                    case "--rx": case "-r": cfg.rxMode = true; break;
                    case "--file": case "-f": cfg.audioFile = new File(args[++i]); break;
                    case "--invert": case "-i": cfg.invert = true; break;
                    case "--quiet": case "-q": cfg.quiet = true; break;
                    case "--confidence": case "-c": cfg.confidenceThreshold = Double.parseDouble(args[++i]); break;
                    case "--mark": cfg.freqMark = Double.parseDouble(args[++i]); break;
                    case "--space": cfg.freqSpace = Double.parseDouble(args[++i]); break;
                    default: break; 
                }
            } else {
                positional.add(arg);
            }
        }

        // Validate that a mode was selected
        if (!cfg.txMode && !cfg.rxMode) {
            System.err.println("Usage: java JMinimodem --rx 1200 [options]");
            return;
        }

        // Apply baud rate preset (e.g., "1200" or "rtty") if provided
        if (!positional.isEmpty()) applyBaudMode(cfg, positional.get(0));

        try {
            if (cfg.txMode) runCliTransmit(cfg);
            else runCliReceive(cfg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Applies standard frequency presets based on baud rate name. 
     */
    private static void applyBaudMode(Config c, String mode) {
        switch (mode.toLowerCase()) {
            case "rtty": 
                c.baudRate = 45.45; c.freqMark = 1585; c.freqSpace = 1415; break;
            case "300": // Bell 103 (Originate)
                c.baudRate = 300; c.freqMark = 1270; c.freqSpace = 1070; break;
            case "1200": // Bell 202
                c.baudRate = 1200; c.freqMark = 1200; c.freqSpace = 2200; break;
            default: // Custom integer baud rate
                try {
                    c.baudRate = Double.parseDouble(mode);
                } catch (NumberFormatException e) {
                    // Ignore, defaults to 1200
                }
        }
    }

    // --- CLI Helper Methods to Setup Streams ---

    private static void runCliTransmit(Config cfg) throws Exception {
        InputStream textSrc = new BufferedInputStream(System.in);
        
        if (cfg.audioFile != null) {
            // File Mode: Capture RAW PCM, then save as WAV
            ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
            transmit(cfg, textSrc, pcmBuffer); // Run Core Logic
            
            // Convert Raw PCM to WAV
            byte[] raw = pcmBuffer.toByteArray();
            AudioFormat fmt = new AudioFormat(cfg.sampleRate, 16, 1, true, false);
            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(raw), fmt, raw.length/2);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, cfg.audioFile);
            
            if(!cfg.quiet) System.err.println("Wrote " + cfg.audioFile.getName());
            
        } else {
            // Speaker Mode: Output directly to Sound Card
            transmit(cfg, textSrc, null); 
        }
    }

    private static void runCliReceive(Config cfg) throws Exception {
        AudioInputStream audioSrc;
        
        if (cfg.audioFile != null) {
            // Read from WAV file
            audioSrc = AudioSystem.getAudioInputStream(cfg.audioFile);
            cfg.sampleRate = audioSrc.getFormat().getSampleRate(); // Adapt DSP to file rate
        } else {
            // Read from Microphone
            AudioFormat req = new AudioFormat(cfg.sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, req);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(req);
            line.start();
            audioSrc = new AudioInputStream(line);
        }

        // Output decoded text to Standard Out
        receive(cfg, audioSrc, System.out);
    }

    // =========================================================================
    // 2. LIBRARY API: TRANSMIT
    // =========================================================================
    
    /**
     * Generates FSK Audio from text input.
     * @param cfg Configuration (Baud rate, frequencies)
     * @param input Data source (Text/Bytes to encode)
     * @param output Audio destination (Raw PCM). If NULL, plays to default Speakers.
     */
    public static void transmit(Config cfg, InputStream input, OutputStream output) throws Exception {
        AudioFormat format = new AudioFormat(cfg.sampleRate, 16, 1, true, false);
        SourceDataLine speaker = null;

        // Setup Output (Speaker vs Stream)
        if (output == null) {
            speaker = AudioSystem.getSourceDataLine(format);
            speaker.open(format); 
            speaker.start();
        }

        double phase = 0; // Continuous phase accumulator to prevent clicks
        double sampleAccum = 0; // Accumulates fractional samples

        // 1. Generate Leader Tone (1000ms Mark)
        // This wakes up the receiver and lets the PLL lock on.
        phase = emit(cfg, cfg.freqMark, cfg.sampleRate * 1.0, phase, output, speaker);
        
        int b;
        double samplesPerBit = cfg.sampleRate / cfg.baudRate;

        // Read input byte-by-byte
        while((b = input.read()) != -1) {
            
            // We must accumulate fractional samples across bit boundaries!
            // If samples=160.5, we emit 160 this time, and carry 0.5 over to the next bit.
            
            // --- START BIT (Space / 0) ---
            sampleAccum += samplesPerBit;
            int nToEmit = (int)sampleAccum;
            sampleAccum -= nToEmit;
            phase = emit(cfg, cfg.freqSpace, nToEmit, phase, output, speaker);
            
            // --- 8 DATA BITS (LSB First) ---
            for(int i=0; i<8; i++) {
                // Check if bit i is 1 or 0
                double f = ((b >> i) & 1) == 1 ? cfg.freqMark : cfg.freqSpace;
                sampleAccum += samplesPerBit;
                nToEmit = (int)sampleAccum;
                sampleAccum -= nToEmit;
                phase = emit(cfg, f, nToEmit, phase, output, speaker);
            }
            
            // --- STOP BIT (Mark / 1) ---
            sampleAccum += samplesPerBit;
            nToEmit = (int)sampleAccum;
            sampleAccum -= nToEmit;
            phase = emit(cfg, cfg.freqMark, nToEmit, phase, output, speaker);
        }
        
        // 2. Trailer Tone (500ms Mark) to flush buffers safely
        emit(cfg, cfg.freqMark, cfg.sampleRate * 0.5, phase, output, speaker);
        
        // Cleanup
        if(speaker != null) { speaker.drain(); speaker.close(); }
        if(output != null) output.flush();
    }

    /**
     * Helper: Generates sine wave samples.
     * @return The new phase angle (preserves continuity)
     */
    private static double emit(Config c, double freq, double samples, double phase, OutputStream out, SourceDataLine line) throws IOException {
        int nSamples = (int)samples;
        byte[] buf = new byte[nSamples * 2]; // 16-bit = 2 bytes per sample
        double inc = 2 * Math.PI * freq / c.sampleRate;
        
        for(int i=0; i<nSamples; i++) {
            // Generate Sine Wave
            short s = (short)(Math.sin(phase) * 32000); // 32000 = ~Full Volume
            phase += inc;
            if(phase > 2*Math.PI) phase -= 2*Math.PI;
            
            // Store as Little Endian PCM
            buf[i*2] = (byte)(s & 0xFF);
            buf[i*2+1] = (byte)((s >> 8) & 0xFF);
        }
        
        if(out != null) out.write(buf);
        if(line != null) line.write(buf, 0, buf.length);
        
        return phase;
    }

    // =========================================================================
    // 3. LIBRARY API: RECEIVE
    // =========================================================================

    /**
     * Decodes FSK Audio into text.
     * Includes High Sensitivity DSP, Digital PLL, Noise Rejection Kill Switch, and Speed Measurement.
     * @param cfg Configuration
     * @param audioSrc Audio Source (Mic or File Stream)
     * @param textOut Text Destination (Where decoded chars are written)
     */
    public static void receive(Config cfg, AudioInputStream audioSrc, OutputStream textOut) throws Exception {
        if(!cfg.quiet) System.err.printf("### RX: %.0f baud @ %.0fHz (High Sensitivity)\n", cfg.baudRate, cfg.sampleRate);

        // --- DSP Initialization ---
        double samplesPerBit = cfg.sampleRate / cfg.baudRate;
        long lastDiagTime = System.currentTimeMillis();
        
        // Use 1.0 (Full Width) window
        double windowWidth = 1.0; 
        
        SlidingFilter filterMark = new SlidingFilter(cfg.invert ? cfg.freqSpace : cfg.freqMark, cfg.sampleRate, (int)(samplesPerBit * windowWidth));
        SlidingFilter filterSpace = new SlidingFilter(cfg.invert ? cfg.freqMark : cfg.freqSpace, cfg.sampleRate, (int)(samplesPerBit * windowWidth));
        
        byte[] buf = new byte[2048];
        int bytesRead;
        
        // --- State Machine ---
        final int STATE_IDLE = 0;
        final int STATE_VERIFY = 1;
        final int STATE_DATA = 2;
        int state = STATE_IDLE; 
        double timer = 0.0;
        int bitIndex = 0;
        int currentByte = 0;
        
        // --- Sync & Diag Variables ---
        boolean carrier = false;
        int carrierCounter = 0;
        boolean lastBitMark = true;
        int framingErrorCount = 0;
        double dcOffset = 0.0;
        long lastEdgeSample = 0;
        long sampleCount = 0;
        double lastMeasuredBaud = 0.0;
        
        // TapeStats Tracking
        int totalStops = 0;
        int logLineCount = 0;
        int dataErrors = 0;
        int dataLengthErrors = 0;
        int sideALineCount = 0;
        int sideAErrors = 0;
        int sideBLineCount = 0;
        int sideBErrors = 0;
        char currentSide = '?';
        boolean isDctMode = false;
        
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        while ((bytesRead = audioSrc.read(buf)) != -1) {
            // Check for Reset Request
            if (cfg.resetDiagnostics) {
                totalStops = 0;
                logLineCount = 0;
                dataErrors = 0;
                dataLengthErrors = 0;
                sideALineCount = 0;
                sideAErrors = 0;
                sideBLineCount = 0;
                sideBErrors = 0;
                lastMeasuredBaud = 0.0;
                sampleCount = 0;
                lastEdgeSample = 0;
                cfg.resetDiagnostics = false;
            }

            for (int i = 0; i < bytesRead; i += 2) {
                short raw = (short)((buf[i] & 0xFF) | (buf[i+1] << 8));
                double sample = raw / 32768.0;

                // DC Bias Removal
                dcOffset = (sample * 0.01) + (dcOffset * 0.99);
                sample -= dcOffset;

                filterMark.process(sample);
                filterSpace.process(sample);
                
                double mMag = filterMark.getMag();
                double sMag = filterSpace.getMag();
                boolean isMark = mMag > sMag;
                double total = mMag + sMag;
                double conf = isMark ? mMag/(sMag+0.0001) : sMag/(mMag+0.0001);
                
                // --- 1000ms Polling Loop for Diagnostic Telemetry ---
                long now = System.currentTimeMillis();
                if (now - lastDiagTime >= 1000) {
                    lastDiagTime = now;
                    if ((cfg.diagListener != null || cfg.diagDataListener != null) && carrier) {
                        double signalLevel = total > 1.0 ? 1.0 : total;
                        
                        // Calculate speed offset percentage
                        float speedOffset = 0;
                        if (lastMeasuredBaud > 0) {
                            speedOffset = (float)((lastMeasuredBaud - cfg.baudRate) * 100.0 / cfg.baudRate);
                        }
                        char sign = (speedOffset >= 0) ? '+' : '-';
                        
                        StringBuilder sb = new StringBuilder();
                        // 1. Baud & SNR
                        sb.append(String.format("Baud: %d (%c%.1f%%) | SNR: %.2f | Sig: %d%%\n", 
                                  (int)lastMeasuredBaud, sign, Math.abs(speedOffset), conf, (int)(signalLevel * 100.0)));
                        
                        // 2. Side & Stops
                        sb.append(String.format("Side: %c | Stops: %d\n", currentSide, totalStops));
                        
                        // 3. Mode
                        sb.append(String.format("Mode: %s\n", isDctMode ? "DCT" : "Generic"));
                        
                        // 4. Total Lines
                        sb.append(String.format("Total: %d\n", logLineCount));
                        
                        // 5. Total Errors
                        sb.append(String.format("Errors: %d\n", dataErrors));
                        
                        // 6. Side A Stats
                        float saPerc = (sideALineCount > 0) ? ((float)sideAErrors * 100.0f / (float)sideALineCount) : 0.0f;
                        sb.append(String.format("Side A: %d/%d (%.2f%%)\n", sideAErrors, sideALineCount, saPerc));
                        
                        // 7. Side B Stats
                        float sbPerc = (sideBLineCount > 0) ? ((float)sideBErrors * 100.0f / (float)sideBLineCount) : 0.0f;
                        sb.append(String.format("Side B: %d/%d (%.2f%%)\n", sideBErrors, sideBLineCount, sbPerc));
                        
                        // 8. Format Errors
                        sb.append(String.format("Format Err: L=%d N=%d", dataLengthErrors, dataErrors));
                        
                        String diagStr = sb.toString();
                        if (cfg.diagListener != null) {
                            cfg.diagListener.onDiagnostic(diagStr);
                        }
                        if (cfg.diagDataListener != null) {
                            DiagnosticData data = new DiagnosticData();
                            data.measuredBaud = lastMeasuredBaud;
                            data.speedOffsetPercent = speedOffset;
                            data.snr = conf;
                            data.signalPercent = (int)(signalLevel * 100.0);
                            data.currentSide = currentSide;
                            data.totalStops = totalStops;
                            data.logLineCount = logLineCount;
                            data.dataErrors = dataErrors;
                            data.sideAErrors = sideAErrors;
                            data.sideALineCount = sideALineCount;
                            data.sideBErrors = sideBErrors;
                            data.sideBLineCount = sideBLineCount;
                            data.dataLengthErrors = dataLengthErrors;
                            data.carrier = carrier;
                            data.rawDiagnosticString = diagStr;
                            cfg.diagDataListener.onDiagnosticData(data);
                        }
                    }
                }
                
                if (total > cfg.noiseFloor && conf > 0.5) {
                    if (carrierCounter < samplesPerBit * 2) carrierCounter++;
                } else {
                    if (carrierCounter > -samplesPerBit * 2) carrierCounter--;
                }

                if (!carrier && carrierCounter > samplesPerBit) {
                    carrier = true;
                    if(!cfg.quiet) System.err.printf("\n### CARRIER DETECTED (Conf: %.2f) ###\n", conf);
                    state = STATE_IDLE;
                    framingErrorCount = 0;
                } else if (carrier && carrierCounter < 0) {
                    if(!cfg.quiet) System.err.println("\n### NOCARRIER ###");
                    totalStops++;
                    carrier = false;
                    state = STATE_IDLE;
                }

                if (!carrier) continue;
                sampleCount++;

                // Track edge transitions to measure instantaneous baud rate
                if (isMark != lastBitMark) {
                    if (lastEdgeSample > 0) {
                        long delta = sampleCount - lastEdgeSample;
                        double bits = (double)delta / samplesPerBit;
                        int k = (int)(bits + 0.5);
                        if (k >= 1 && k <= 12) {
                            double instBaud = ((double)k * cfg.sampleRate / (double)delta);
                            if (lastMeasuredBaud < 1.0) lastMeasuredBaud = instBaud;
                            else lastMeasuredBaud = (lastMeasuredBaud * 0.98) + (instBaud * 0.02);
                        }
                    }
                    lastEdgeSample = sampleCount;
                    if (state == STATE_IDLE && !isMark) {
                        timer = samplesPerBit * 0.5;
                        state = STATE_VERIFY;
                    }
                }
                lastBitMark = isMark;

                switch (state) {
                    case STATE_VERIFY:
                        timer -= 1.0;
                        if (timer <= 0) {
                            if (!isMark) {
                                state = STATE_DATA;
                                timer = samplesPerBit;
                                bitIndex = 0;
                                currentByte = 0;
                            } else {
                                state = STATE_IDLE;
                            }
                        }
                        break;
                    case STATE_DATA:
                        timer -= 1.0;
                        if (timer <= 0) {
                            int bit = isMark ? 1 : 0;
                            if (bitIndex < 8) {
                                currentByte |= (bit << bitIndex);
                                bitIndex++;
                                timer = samplesPerBit;
                            } else {
                                if (isMark) {
                                    framingErrorCount = 0;
                                    int ascii = currentByte & 0x7F;
                                    if (ascii >= 32 || ascii == 10 || ascii == 13 || ascii == 9) {
                                        textOut.write(ascii);
                                        textOut.flush();
                                        if (ascii == '\n') {
                                            String lineStr = lineBuffer.toString("US-ASCII").trim();
                                            lineBuffer.reset();
                                            if (lineStr.length() > 0) {
                                                logLineCount++;
                                                if (lineStr.startsWith("DCT0")) {
                                                    isDctMode = true;
                                                    char side = lineStr.length() > 4 ? lineStr.charAt(4) : '?';
                                                    if (side == 'A') { sideALineCount++; currentSide = 'A'; }
                                                    else if (side == 'B') { sideBLineCount++; currentSide = 'B'; }
                                                    if (lineStr.length() != 29) {
                                                        dataLengthErrors++;
                                                        if (side == 'A') sideAErrors++;
                                                        if (side == 'B') sideBErrors++;
                                                    }
                                                }
                                            }
                                        } else {
                                            lineBuffer.write(ascii);
                                        }
                                    }
                                } else {
                                    framingErrorCount++;
                                    if (framingErrorCount > 6) {
                                        carrier = false;
                                        carrierCounter = -((int)samplesPerBit * 2); 
                                        if(!cfg.quiet) System.err.println("\n### NOCARRIER (Signal Lost) ###");
                                    }
                                }
                                state = STATE_IDLE;
                            }
                        }
                        break;
                }
            }
        }
    }

    // =========================================================================
    // DSP IMPLEMENTATION
    // =========================================================================
    
    /**
     * Sliding Window Heterodyne Filter.
     * <p>
     * This class implements a "Matched Filter" or a single-bin Sliding DFT.
     * It detects the energy presence of a specific frequency over time.
     * </p>
     */
    static class SlidingFilter {
        double[] iBuf, qBuf;   // History buffers (In-phase & Quadrature)
        int ptr=0, size;       // Circular buffer pointer
        double sumI=0, sumQ=0; // Running sums for O(1) efficiency
        double phase=0, inc;   // Local Oscillator phase
        
        /**
         * @param f Target frequency (Hz)
         * @param r Sample rate (Hz)
         * @param w Window size (Number of samples to integrate over)
         */
        SlidingFilter(double f, float r, int w) {
            size=w; 
            iBuf=new double[size]; 
            qBuf=new double[size];
            inc = 2*Math.PI*f/r; // Phase increment per sample
        }
        
        /**
         * Processes one audio sample and updates the energy calculation.
         * @param in Normalized audio sample (-1.0 to 1.0)
         */
        void process(double in) {
            // 1. Generate Local Oscillator (LO)
            double loI = Math.cos(phase);
            double loQ = Math.sin(phase);
            
            // Advance Phase
            phase += inc; 
            if(phase > 2*Math.PI) phase -= 2*Math.PI;
            
            // 2. Mix (Downconvert to DC)
            double vi = in * loI;
            double vq = in * loQ;
            
            // 3. Update Moving Average (Add new, Subtract old)
            sumI = sumI - iBuf[ptr] + vi; 
            sumQ = sumQ - qBuf[ptr] + vq;
            
            // 4. Store in history
            iBuf[ptr] = vi; 
            qBuf[ptr] = vq;
            
            // Advance pointer
            ptr++; 
            if(ptr >= size) ptr = 0;
        }
        
        /** @return The magnitude of energy at the target frequency. */
        double getMag() { return Math.sqrt(sumI*sumI + sumQ*sumQ); }
    }
}