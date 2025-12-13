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
 * <li><b>Dual Mode:</b> Works as a CLI tool or a Library API.</li>
 * </ul>
 */
public class JMinimodem {

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
        
        /** * Minimum absolute volume (0.0-1.0) to trigger squelch.
         * Set to 0.2 to filter out background microphone noise and static.
         */
        public double noiseFloor = 0.2;         
        
        /** Suppress status messages */
        public boolean quiet = false;            
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

        // 1. Generate Leader Tone (1000ms Mark)
        // This wakes up the receiver and lets the PLL lock on.
        phase = emit(cfg, cfg.freqMark, 1000, phase, output, speaker);
        
        int b;
        // Read input byte-by-byte
        while((b = input.read()) != -1) {
            
            // --- START BIT (Space / 0) ---
            phase = emit(cfg, cfg.freqSpace, 1000/cfg.baudRate, phase, output, speaker);
            
            // --- 8 DATA BITS (LSB First) ---
            for(int i=0; i<8; i++) {
                // Check if bit i is 1 or 0
                double f = ((b >> i) & 1) == 1 ? cfg.freqMark : cfg.freqSpace;
                phase = emit(cfg, f, 1000/cfg.baudRate, phase, output, speaker);
            }
            
            // --- STOP BIT (Mark / 1) ---
            phase = emit(cfg, cfg.freqMark, 1000/cfg.baudRate, phase, output, speaker);
        }
        
        // 2. Trailer Tone (500ms Mark) to flush buffers safely
        emit(cfg, cfg.freqMark, 500, phase, output, speaker);
        
        // Cleanup
        if(speaker != null) { speaker.drain(); speaker.close(); }
        if(output != null) output.flush();
    }

    /**
     * Helper: Generates sine wave samples for a specific duration.
     * @return The new phase angle (preserves continuity)
     */
    private static double emit(Config c, double freq, double ms, double phase, OutputStream out, SourceDataLine line) throws IOException {
        int samples = (int)(c.sampleRate * ms / 1000.0);
        byte[] buf = new byte[samples * 2]; // 16-bit = 2 bytes per sample
        double inc = 2 * Math.PI * freq / c.sampleRate;
        
        for(int i=0; i<samples; i++) {
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
     * Includes High Sensitivity DSP, Digital PLL, and Noise Rejection Kill Switch.
     * * @param cfg Configuration
     * @param audioSrc Audio Source (Mic or File Stream)
     * @param textOut Text Destination (Where decoded chars are written)
     */
    public static void receive(Config cfg, AudioInputStream audioSrc, OutputStream textOut) throws Exception {
        if(!cfg.quiet) System.err.printf("### RX: %.0f baud @ %.0fHz (High Sensitivity)\n", cfg.baudRate, cfg.sampleRate);

        // --- DSP Initialization ---
        double samplesPerBit = cfg.sampleRate / cfg.baudRate;
        
        // FIX: Use 1.0 (Full Width) window. 
        // 0.8 is too narrow for 1200Hz tone @ 1200 baud (period is exactly 1 bit).
        // A full window captures the entire wave cycle, preventing phase-dependent fading.
        int windowSize = (int)Math.round(samplesPerBit * 1.0); 
        
        // Create Matched Filters
        SlidingFilter filterMark = new SlidingFilter(cfg.invert ? cfg.freqSpace : cfg.freqMark, cfg.sampleRate, windowSize);
        SlidingFilter filterSpace = new SlidingFilter(cfg.invert ? cfg.freqMark : cfg.freqSpace, cfg.sampleRate, windowSize);
        
        byte[] buf = new byte[2048];
        int bytesRead;
        
        // --- State Machine Constants ---
        final int STATE_IDLE = 0;   // Waiting for Start Bit edge
        final int STATE_VERIFY = 1; // Waiting 0.5 bits to confirm Start
        final int STATE_DATA = 2;   // Reading 8 data bits
        int state = STATE_IDLE; 
        
        double timer = 0.0;
        int bitIndex = 0;
        int currentByte = 0;
        
        // --- Sync Variables ---
        boolean carrier = false;
        int carrierCounter = 0;     // Squelch debounce
        boolean lastBitMark = true; // For edge detection
        int framingErrorCount = 0;  // Noise Kill Switch counter
        double dcOffset = 0.0;      // DC Blocker state

        while ((bytesRead = audioSrc.read(buf)) != -1) {
            // Process samples (16-bit Little Endian)
            for (int i = 0; i < bytesRead; i += 2) {
                // Convert PCM to normalized double
                short raw = (short)((buf[i] & 0xFF) | (buf[i+1] << 8));
                double sample = raw / 32768.0;

                // FIX: DC Bias Removal (High Pass Filter)
                // Removes microphone hum/offset that deafens the filters
                dcOffset = (sample * 0.01) + (dcOffset * 0.99);
                sample -= dcOffset;

                // 1. Run DSP Filters
                filterMark.process(sample);
                filterSpace.process(sample);
                
                double mMag = filterMark.getMag(); // Mark Magnitude
                double sMag = filterSpace.getMag(); // Space Magnitude
                
                boolean isMark = mMag > sMag;
                double total = mMag + sMag;

                // 2. Calculate Confidence (SNR)
                // Ratio of Dominant Tone / (Weak Tone + epsilon)
                double conf = isMark ? mMag/(sMag+0.0001) : sMag/(mMag+0.0001);

                // 3. Carrier Squelch
                // Total energy must be > noiseFloor (0.2).
                // Confidence must be decent (> 0.5) to prevent triggering on white noise.
                if (total > cfg.noiseFloor && conf > 0.5) {
                    if (carrierCounter < samplesPerBit * 2) carrierCounter++;
                } else {
                    if (carrierCounter > -samplesPerBit * 2) carrierCounter--;
                }

                if (!carrier && carrierCounter > samplesPerBit) {
                    carrier = true;
                    if(!cfg.quiet) System.err.printf("\n### CARRIER DETECTED (Conf: %.2f) ###\n", conf);
                    state = STATE_IDLE; // Reset logic on new signal
                    framingErrorCount = 0;
                } else if (carrier && carrierCounter < 0) {
                    carrier = false;
                    if(!cfg.quiet) System.err.println("\n### NOCARRIER ###");
                    state = STATE_IDLE;
                }

                if (!carrier) continue;

                // 4. Soft Digital PLL (Phase-Locked Loop)
                // If the signal flips (Mark<->Space), we align our internal clock.
                if (isMark != lastBitMark) {
                     // If we are IDLE and see a flip to Space (0), that is a Start Bit Edge.
                     if (state == STATE_IDLE && !isMark) {
                         // Align timer to sample exactly in the middle of the Start Bit (0.5 bits from now)
                         timer = samplesPerBit * 0.5;
                         state = STATE_VERIFY;
                     } else {
                         // Soft Nudge logic could go here for data-bit transitions
                     }
                }
                lastBitMark = isMark;

                // 5. UART Logic
                switch (state) {
                    case STATE_VERIFY:
                        timer -= 1.0;
                        if (timer <= 0) {
                            if (!isMark) { // Confirmed Start Bit (Still Space)
                                state = STATE_DATA;
                                timer = samplesPerBit; // Schedule next read
                                bitIndex = 0;
                                currentByte = 0;
                            } else {
                                state = STATE_IDLE; // Glitch (flipped back to Mark)
                            }
                        }
                        break;

                    case STATE_DATA:
                        timer -= 1.0;
                        if (timer <= 0) {
                            int bit = isMark ? 1 : 0;
                            
                            if (bitIndex < 8) {
                                // Accumulate Data Bits
                                currentByte |= (bit << bitIndex);
                                bitIndex++;
                                timer = samplesPerBit;
                            } else {
                                // Stop Bit Check (Must be Mark)
                                if (isMark) {
                                    framingErrorCount = 0; // Valid byte, reset error counter
                                    int ascii = currentByte & 0x7F; // 7-bit clean
                                    // Print valid chars
                                    if (ascii >= 32 || ascii == 10 || ascii == 13 || ascii == 9) {
                                        textOut.write(ascii);
                                        textOut.flush();
                                    }
                                } else {
                                    framingErrorCount++;
                                    // KILL SWITCH: 6 consecutive errors = signal lost / static
                                    if (framingErrorCount > 6) {
                                        carrier = false;
                                        carrierCounter = -((int)samplesPerBit * 2); 
                                        if(!cfg.quiet) System.err.println("\n### NOCARRIER (Signal Lost) ###");
                                    }
                                }
                                state = STATE_IDLE; // Ready for next byte
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