package cassetteflow;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Pure in-memory FSK Amplitude Modulator.
 * Generates 1200-baud Bell 202 FSK audio in memory using JMinimodem, extracts
 * the volume envelope of the digital music track, and modulates the FSK carrier
 * amplitude with a configurable safe floor (e.g. 0.20 = 20%).
 * 
 * Result: Analog VU meters on vintage tape decks bounce rhythmically to the music,
 * while demodulators maintain unbroken carrier lock.
 * Zero unscaled WAV files are written to disk.
 * Supports optional debug reference audio export (Tape_<ID><Side>_Reference.wav).
 */
public class FSKModulator {

    public static final float FSK_SAMPLE_RATE = 48000.0f;

    public interface ModulatorProgressListener {
        void onTrackStarted(String side, int currentTrack, int totalTracks, String trackName);
        void onTrackFinished(String side, int currentTrack, int totalTracks, String message);
        void onLog(String message);
        void onFinished(boolean success, String summary);
    }

    private static class MusicData {
        float[] envelope;
        byte[] pcmBytes;
    }

    /**
     * Backward-compatible overload without reference output file.
     */
    public static void processSideInMemory(CassetteFlow cassetteFlow,
                                          String side,
                                          String tapeId,
                                          List<AudioInfo> tracks,
                                          int muteTime,
                                          float minScale,
                                          File outputFile,
                                          ModulatorProgressListener listener) throws Exception {
        processSideInMemory(cassetteFlow, side, tapeId, tracks, muteTime, minScale, outputFile, null, listener);
    }

    /**
     * Processes a single tape side 100% in-memory and outputs the final modulated WAV file.
     * Optionally exports a combined reference audio WAV for timing/envelope debugging.
     *
     * @param cassetteFlow main CassetteFlow instance for timecode/data formatting
     * @param side "A" or "B"
     * @param tapeId tape ID (e.g. "0001")
     * @param tracks list of AudioInfo objects on this side
     * @param muteTime inter-track mute time in seconds
     * @param minScale minimum amplitude floor (0.05 - 0.50, default 0.20)
     * @param outputFile destination modulated WAV file (Tape_<ID><Side>_Scaled<floor>.wav)
     * @param refOutputFile optional destination reference WAV file (or null if disabled)
     * @param listener progress callback listener
     * @throws Exception if processing fails
     */
    public static void processSideInMemory(CassetteFlow cassetteFlow,
                                          String side,
                                          String tapeId,
                                          List<AudioInfo> tracks,
                                          int muteTime,
                                          float minScale,
                                          File outputFile,
                                          File refOutputFile,
                                          ModulatorProgressListener listener) throws Exception {
        if (tracks == null || tracks.isEmpty()) {
            if (listener != null) {
                listener.onLog("Side " + side + " has no tracks, skipping.");
            }
            return;
        }

        if (outputFile.exists()) {
            outputFile.delete();
        }
        if (refOutputFile != null && refOutputFile.exists()) {
            refOutputFile.delete();
        }

        int totalTracks = tracks.size();
        long totalAudioBytes = 0;
        long totalRefBytes = 0;

        // Configure JMinimodem for in-memory transmission
        JMinimodem.Config config = new JMinimodem.Config();
        config.txMode = true;
        config.sampleRate = FSK_SAMPLE_RATE;
        config.quiet = true;
        try {
            config.baudRate = Double.parseDouble(cassetteFlow.BAUDE_RATE);
        } catch (Exception e) {
            config.baudRate = 1200.0;
        }

        if (listener != null) {
            listener.onLog(">>> Starting In-Memory Scaled FSK for Side " + side + " (" + totalTracks + " tracks) <<<");
            listener.onLog("Modulation Floor: " + String.format("%.0f%%", minScale * 100) +
                           " | Baud: " + (int) config.baudRate + " | Rate: " + (int) FSK_SAMPLE_RATE + " Hz");
            if (refOutputFile != null) {
                listener.onLog("Debug Reference WAV: ENABLED -> " + refOutputFile.getName());
            }
        }

        FileOutputStream refFos = null;
        BufferedOutputStream refBos = null;
        if (refOutputFile != null) {
            refFos = new FileOutputStream(refOutputFile);
            refBos = new BufferedOutputStream(refFos, 131072);
            refBos.write(new byte[44]); // Placeholder header
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 131072)) {

            // 1. Write placeholder 44-byte WAV header
            byte[] headerPlaceholder = new byte[44];
            bos.write(headerPlaceholder);

            for (int i = 0; i < totalTracks; i++) {
                AudioInfo audioInfo = tracks.get(i);
                int trackNumber = i + 1;
                String trackName = audioInfo.getName();

                if (listener != null) {
                    listener.onTrackStarted(side, trackNumber, totalTracks, trackName);
                    listener.onLog(String.format("[Side %s] (%d/%d) Generating FSK data for: %s",
                            side, trackNumber, totalTracks, trackName));
                }

                // 2. Generate FSK text data in memory
                String fullTapeId = tapeId + side;
                String fskTextData = cassetteFlow.createInputDataForAudio(fullTapeId, audioInfo, trackNumber, 1);

                // 3. Transmit FSK into in-memory PCM byte stream
                ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
                ByteArrayInputStream textInput = new ByteArrayInputStream(fskTextData.getBytes(StandardCharsets.UTF_8));
                JMinimodem.transmit(config, textInput, pcmOutput);

                byte[] rawFskBytes = pcmOutput.toByteArray();
                int fskSampleCount = rawFskBytes.length / 2;
                short[] fskSamples = new short[fskSampleCount];

                // Unpack FSK 16-bit little-endian samples
                for (int s = 0; s < fskSampleCount; s++) {
                    int lo = rawFskBytes[s * 2] & 0xFF;
                    int hi = rawFskBytes[s * 2 + 1];
                    fskSamples[s] = (short) ((hi << 8) | lo);
                }

                // Free temporary FSK byte array
                rawFskBytes = null;
                pcmOutput = null;

                // 4. Extract Music Envelope (and optional PCM for reference) in memory
                MusicData musicData = null;
                float musicPeak = 1.0f;

                if (audioInfo.getFile() != null && audioInfo.getFile().exists()) {
                    try {
                        if (listener != null) {
                            listener.onLog("   Extracting music envelope from " + audioInfo.getFile().getName() + "...");
                        }
                        musicData = extractMusicData(audioInfo.getFile(), FSK_SAMPLE_RATE, fskSampleCount, refOutputFile != null);
                        if (musicData.envelope != null && musicData.envelope.length > 0) {
                            float maxVal = 0.0f;
                            for (float val : musicData.envelope) {
                                if (val > maxVal) maxVal = val;
                            }
                            if (maxVal > 0.001f) {
                                musicPeak = maxVal;
                            }
                        }
                    } catch (Exception ex) {
                        if (listener != null) {
                            listener.onLog("   Warning: Could not decode music envelope (" + ex.getMessage() + "), using fallback.");
                        }
                    }
                }

                // 5. In-Memory Amplitude Modulation
                // Scaled[t] = FSK[t] * max(minScale, musicEnvelope[t] / musicPeak)
                byte[] modulatedBytes = new byte[fskSampleCount * 2];
                for (int s = 0; s < fskSampleCount; s++) {
                    float env = 0.0f;
                    if (musicData != null && musicData.envelope != null && s < musicData.envelope.length) {
                        env = musicData.envelope[s];
                    } else if (musicData == null || musicData.envelope == null) {
                        env = 1.0f; // Default full volume if music couldn't be decoded
                    }

                    float scale = env / musicPeak;
                    if (scale < minScale) {
                        scale = minScale;
                    }
                    if (scale > 1.0f) {
                        scale = 1.0f;
                    }

                    float carrierGain = 32767.0f / 32000.0f;
                    int scaledSample = Math.round(fskSamples[s] * scale * carrierGain);
                    if (scaledSample > 32767) scaledSample = 32767;
                    else if (scaledSample < -32768) scaledSample = -32768;

                    modulatedBytes[s * 2] = (byte) (scaledSample & 0xFF);
                    modulatedBytes[s * 2 + 1] = (byte) ((scaledSample >> 8) & 0xFF);
                }

                // Write modulated track directly to disk
                bos.write(modulatedBytes);
                totalAudioBytes += modulatedBytes.length;

                // Write reference audio track if enabled
                if (refBos != null) {
                    if (musicData != null && musicData.pcmBytes != null) {
                        refBos.write(musicData.pcmBytes);
                        totalRefBytes += musicData.pcmBytes.length;
                    } else {
                        byte[] zeroFallback = new byte[fskSampleCount * 2];
                        refBos.write(zeroFallback);
                        totalRefBytes += zeroFallback.length;
                    }
                }

                // Free track memory
                fskSamples = null;
                musicData = null;
                modulatedBytes = null;

                // 6. Inject inter-track silence gap (AMS) between songs
                if (i < totalTracks - 1 && muteTime > 0) {
                    int silenceBytesTotal = muteTime * (int) FSK_SAMPLE_RATE * 2; // 16-bit mono = 2 bytes/frame
                    byte[] silenceChunk = new byte[Math.min(8192, silenceBytesTotal)];
                    int remaining = silenceBytesTotal;
                    while (remaining > 0) {
                        int toWrite = Math.min(remaining, silenceChunk.length);
                        bos.write(silenceChunk, 0, toWrite);
                        totalAudioBytes += toWrite;
                        if (refBos != null) {
                            refBos.write(silenceChunk, 0, toWrite);
                            totalRefBytes += toWrite;
                        }
                        remaining -= toWrite;
                    }
                }

                if (listener != null) {
                    listener.onTrackFinished(side, trackNumber, totalTracks,
                            String.format("[Side %s] (%d/%d) Modulated & Buffered in RAM.", side, trackNumber, totalTracks));
                }
            }
            bos.flush();
            if (refBos != null) {
                refBos.flush();
            }
        } finally {
            if (refBos != null) {
                try { refBos.close(); } catch (Exception ignored) {}
            }
            if (refFos != null) {
                try { refFos.close(); } catch (Exception ignored) {}
            }
        }

        // 7. Write standard RIFF WAVE header for modulated file
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            long riffChunkSize = totalAudioBytes + 36;
            byte[] header = createMonoWavHeader(riffChunkSize, totalAudioBytes, FSK_SAMPLE_RATE);
            raf.seek(0);
            raf.write(header);
        }

        // 8. Write standard RIFF WAVE header for debug reference file if enabled
        if (refOutputFile != null && refOutputFile.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(refOutputFile, "rw")) {
                long riffChunkSize = totalRefBytes + 36;
                byte[] header = createMonoWavHeader(riffChunkSize, totalRefBytes, FSK_SAMPLE_RATE);
                raf.seek(0);
                raf.write(header);
            }
            if (listener != null) {
                listener.onLog(String.format("Side %s Reference WAV Complete: %s (%d MB)",
                        side, refOutputFile.getName(), refOutputFile.length() / (1024 * 1024)));
            }
        }

        if (listener != null) {
            listener.onLog(String.format("Side %s Scaled FSK Complete: %s (%d MB)",
                    side, outputFile.getName(), outputFile.length() / (1024 * 1024)));
        }
    }

    /**
     * Extracts an amplitude envelope array and optional aligned PCM bytes from a music file.
     */
    private static MusicData extractMusicData(File file, float targetSampleRate,
                                             int targetSampleCount, boolean includePcm) throws Exception {
        MusicData data = new MusicData();
        AudioInputStream ais = AudioSystem.getAudioInputStream(file);
        AudioFormat srcFormat = ais.getFormat();

        // Convert to PCM signed 16-bit if needed
        AudioInputStream pcmAIS = ais;
        if (srcFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || srcFormat.getSampleSizeInBits() != 16) {
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    srcFormat.getSampleRate(),
                    16,
                    srcFormat.getChannels(),
                    srcFormat.getChannels() * 2,
                    srcFormat.getSampleRate(),
                    false
            );
            pcmAIS = AudioSystem.getAudioInputStream(decodedFormat, ais);
            srcFormat = decodedFormat;
        }

        byte[] bytes = pcmAIS.readAllBytes();
        int frameSize = srcFormat.getFrameSize();
        int totalSourceFrames = bytes.length / frameSize;
        int channels = srcFormat.getChannels();
        boolean bigEndian = srcFormat.isBigEndian();

        double resampleRatio = targetSampleRate / (double) srcFormat.getSampleRate();
        float[] envelope = new float[targetSampleCount];
        byte[] pcmOut = includePcm ? new byte[targetSampleCount * 2] : null;

        // Smooth window (approx 20ms) to create a continuous volume envelope for VU needles
        int windowSize = Math.max(1, (int) (targetSampleRate * 0.020f));

        for (int t = 0; t < targetSampleCount; t++) {
            int srcFrame = (int) Math.round(t / resampleRatio);
            if (srcFrame >= totalSourceFrames) {
                break;
            }

            int idx = srcFrame * frameSize;
            long sum = 0;
            short avgSample = 0;
            for (int c = 0; c < channels; c++) {
                int lo = bytes[idx + c * 2] & 0xFF;
                int hi = bytes[idx + c * 2 + 1];
                int sample = bigEndian ? ((hi << 8) | lo) : ((lo) | (hi << 8));
                sum += Math.abs((short) sample);
                if (c == 0) avgSample = (short) sample;
            }
            envelope[t] = (sum / (float) channels) / 32768.0f;

            if (includePcm) {
                pcmOut[t * 2] = (byte) (avgSample & 0xFF);
                pcmOut[t * 2 + 1] = (byte) ((avgSample >> 8) & 0xFF);
            }
        }

        // Apply lightweight moving-average smoothing so VU needles don't buzz harshly on high frequencies
        float[] smoothed = new float[targetSampleCount];
        float runningSum = 0.0f;
        for (int i = 0; i < targetSampleCount; i++) {
            runningSum += envelope[i];
            if (i >= windowSize) {
                runningSum -= envelope[i - windowSize];
                smoothed[i] = runningSum / windowSize;
            } else {
                smoothed[i] = runningSum / (i + 1);
            }
        }

        data.envelope = smoothed;
        data.pcmBytes = pcmOut;
        return data;
    }

    private static byte[] createMonoWavHeader(long totalDataLen, long totalAudioLen, float sampleRate) {
        byte[] header = new byte[44];
        long byteRate = (long) sampleRate * 2; // 16-bit Mono = 2 bytes/frame

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size = 16
        header[20] = 1; header[21] = 0; // PCM = 1
        header[22] = 1; header[23] = 0; // Mono (1 channel)
        header[24] = (byte) (((long) sampleRate) & 0xff);
        header[25] = (byte) ((((long) sampleRate) >> 8) & 0xff);
        header[26] = (byte) ((((long) sampleRate) >> 16) & 0xff);
        header[27] = (byte) ((((long) sampleRate) >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = 2; header[33] = 0; // BlockAlign = 2
        header[34] = 16; header[35] = 0; // BitsPerSample = 16
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        return header;
    }

    /**
     * Generates a continuous modulated DCT (Dynamic Content Track) FSK audio stream in memory
     * using ProceduralEnvelopeGenerator and DurationInputStream, writing directly to the output WAV file.
     *
     * @param cassetteFlow main CassetteFlow instance for baud rate
     * @param side 'A' or 'B'
     * @param tapeId tape ID string
     * @param durationSeconds total duration in seconds per side
     * @param genre procedural genre envelope
     * @param minScale minimum amplitude floor (0.05 - 0.50)
     * @param outputFile output WAV file
     * @param listener progress listener
     * @throws Exception if processing fails
     */
    public static void processDCTSideInMemory(CassetteFlow cassetteFlow,
                                             char side,
                                             String tapeId,
                                             int durationSeconds,
                                             ProceduralEnvelopeGenerator.Genre genre,
                                             float minScale,
                                             File outputFile,
                                             ModulatorProgressListener listener) throws Exception {
        if (outputFile.exists()) {
            outputFile.delete();
        }

        double baudRate = 1200.0;
        try {
            baudRate = Double.parseDouble(cassetteFlow.BAUDE_RATE);
        } catch (Exception ignored) {}

        JMinimodem.Config config = new JMinimodem.Config();
        config.txMode = true;
        config.sampleRate = FSK_SAMPLE_RATE;
        config.quiet = true;
        config.baudRate = baudRate;

        if (listener != null) {
            listener.onLog(String.format(">>> Starting Scaled DCT FSK for Side %c (%d sec / %s) <<<",
                    side, durationSeconds, genre.getDisplayName()));
            listener.onLog("Modulation Floor: " + String.format("%.0f%%", minScale * 100) +
                           " | Baud: " + (int) baudRate + " | Rate: " + (int) FSK_SAMPLE_RATE + " Hz");
        }

        DurationInputStream dis = new DurationInputStream(durationSeconds, baudRate, side);
        long totalAudioBytes = 0;

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 131072)) {

            // 1. Write placeholder 44-byte WAV header
            byte[] headerPlaceholder = new byte[44];
            bos.write(headerPlaceholder);

            // 2. Pre-generate hierarchical musical timeline for the entire side
            ProceduralEnvelopeGenerator.Timeline timeline = ProceduralEnvelopeGenerator.createTimeline(
                    genre, durationSeconds, System.currentTimeMillis() + (side == 'B' ? 99999L : 0L));

            // 3. Modulate on the fly using ModulatingOutputStream
            ModulatingOutputStream modOut = new ModulatingOutputStream(
                    bos, timeline, genre, minScale, FSK_SAMPLE_RATE, side, durationSeconds, listener);

            JMinimodem.transmit(config, dis, modOut);
            modOut.flush();
            bos.flush();

            totalAudioBytes = modOut.getAudioBytesWritten();
        }

        // 3. Finalize RIFF WAV header after stream closure
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            long riffChunkSize = totalAudioBytes + 36;
            byte[] header = createMonoWavHeader(riffChunkSize, totalAudioBytes, FSK_SAMPLE_RATE);
            raf.seek(0);
            raf.write(header);
        }

        if (listener != null) {
            listener.onLog(String.format("[Side %c] DCT Modulation Complete: %s (%.1f MB, %d seconds)",
                    side, outputFile.getName(), totalAudioBytes / (1024.0 * 1024.0), durationSeconds));
        }
    }

    /**
     * Streaming OutputStream filter that intercepts 16-bit PCM little-endian samples from JMinimodem
     * and scales each sample's amplitude on the fly according to the procedural genre envelope.
     */
    private static class ModulatingOutputStream extends OutputStream {
        private final OutputStream out;
        private final ProceduralEnvelopeGenerator.Timeline timeline;
        private final ProceduralEnvelopeGenerator.Genre genre;
        private final float minScale;
        private final float sampleRate;
        private final char side;
        private final int totalSeconds;
        private final ModulatorProgressListener listener;

        private long sampleCount = 0;
        private int leftoverByte = -1;
        private final byte[] outBuffer = new byte[8192];
        private int outBufPos = 0;
        private int lastReportedSec = -1;

        public ModulatingOutputStream(OutputStream out,
                                      ProceduralEnvelopeGenerator.Timeline timeline,
                                      ProceduralEnvelopeGenerator.Genre genre,
                                      float minScale,
                                      float sampleRate,
                                      char side,
                                      int totalSeconds,
                                      ModulatorProgressListener listener) {
            this.out = out;
            this.timeline = timeline;
            this.genre = genre;
            this.minScale = minScale;
            this.sampleRate = sampleRate;
            this.side = side;
            this.totalSeconds = totalSeconds;
            this.listener = listener;
        }

        @Override
        public void write(int b) throws IOException {
            byte[] single = new byte[] { (byte) b };
            write(single, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int i = off;
            int end = off + len;

            if (leftoverByte != -1 && len > 0) {
                int lo = leftoverByte;
                int hi = b[i++] & 0xFF;
                leftoverByte = -1;
                processSample(lo, hi);
            }

            while (i + 1 < end) {
                int lo = b[i++] & 0xFF;
                int hi = b[i++] & 0xFF;
                processSample(lo, hi);
            }

            if (i < end) {
                leftoverByte = b[i++] & 0xFF;
            }
        }

        private void processSample(int lo, int hi) throws IOException {
            short rawSample = (short) ((hi << 8) | lo);
            double timeSec = (double) sampleCount / sampleRate;
            float scale = (timeline != null)
                    ? timeline.getEnvelope(timeSec, minScale)
                    : ProceduralEnvelopeGenerator.getEnvelope(genre, timeSec, minScale);

            float carrierGain = 32767.0f / 32000.0f;
            int scaledSample = Math.round(rawSample * scale * carrierGain);
            if (scaledSample > 32767) scaledSample = 32767;
            else if (scaledSample < -32768) scaledSample = -32768;

            outBuffer[outBufPos++] = (byte) (scaledSample & 0xFF);
            outBuffer[outBufPos++] = (byte) ((scaledSample >> 8) & 0xFF);

            if (outBufPos >= outBuffer.length) {
                out.write(outBuffer, 0, outBufPos);
                outBufPos = 0;
            }

            sampleCount++;

            int currentSec = (int) (sampleCount / sampleRate);
            if (currentSec != lastReportedSec && currentSec > 0 && (currentSec % 15 == 0 || currentSec >= totalSeconds)) {
                lastReportedSec = currentSec;
                if (listener != null) {
                    listener.onLog(String.format("   [Side %c] Modulating DCT audio: %02d:%02d / %02d:%02d (%s)...",
                            side, currentSec / 60, currentSec % 60, totalSeconds / 60, totalSeconds % 60, genre.getDisplayName()));
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if (outBufPos > 0) {
                out.write(outBuffer, 0, outBufPos);
                outBufPos = 0;
            }
            out.flush();
        }

        public long getAudioBytesWritten() {
            return sampleCount * 2;
        }
    }
}
