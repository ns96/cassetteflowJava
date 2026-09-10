package cassetteflow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.jflac.io.RandomFileInputStream;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;

/**
 * Utility class to handle exporting Side A and Side B playlists to:
 * 1. Standard M3U8 playlist files (.m3u8)
 * 2. Continuous 44.1 kHz 16-bit Stereo WAV files with inter-track AMS silence gaps (.wav)
 *    Supports both raw (unscaled) and ITU-R BS.1770 LUFS volume-normalized exports.
 */
public class TapeExporter {

    /** Standard CD-quality target format for cassette tape master WAV files */
    public static final AudioFormat TARGET_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            44100.0f,
            16,
            2,
            4,
            44100.0f,
            false // Little Endian
    );

    /** Target loudness for tape master normalization */
    public static final double TARGET_LUFS = -16.0;

    /**
     * Listener interface for real-time progress callbacks to update UI and console.
     */
    public interface ExportProgressListener {
        void onTrackProgress(int completedTracks, int totalTracks, String message);
        void onFinished(boolean success, String summary);
    }

    /**
     * Exports Side A and Side B track lists to standard M3U8 playlist files.
     */
    public static void exportSidesToM3U8(String outputDir, String tapeId,
                                         List<AudioInfo> sideA, List<AudioInfo> sideB,
                                         ExportProgressListener listener) {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        StringBuilder summary = new StringBuilder();
        int generatedCount = 0;

        // Side A
        if (sideA != null && !sideA.isEmpty()) {
            File m3uFileA = new File(dir, "Tape_" + tapeId + "_Side_A.m3u8");
            try {
                writeM3U8File(m3uFileA, "Tape " + tapeId + " - Side A", sideA);
                if (listener != null) {
                    listener.onTrackProgress(1, 2, "Created Playlist: " + m3uFileA.getAbsolutePath());
                }
                summary.append("Side A Playlist: ").append(m3uFileA.getName()).append(" (")
                       .append(sideA.size()).append(" tracks)\n");
                generatedCount++;
            } catch (IOException e) {
                if (listener != null) {
                    listener.onTrackProgress(1, 2, "Error creating Side A playlist: " + e.getMessage());
                }
            }
        } else {
            if (listener != null) {
                listener.onTrackProgress(1, 2, "Side A is empty, skipping Side A playlist.");
            }
        }

        // Side B
        if (sideB != null && !sideB.isEmpty()) {
            File m3uFileB = new File(dir, "Tape_" + tapeId + "_Side_B.m3u8");
            try {
                writeM3U8File(m3uFileB, "Tape " + tapeId + " - Side B", sideB);
                if (listener != null) {
                    listener.onTrackProgress(2, 2, "Created Playlist: " + m3uFileB.getAbsolutePath());
                }
                summary.append("Side B Playlist: ").append(m3uFileB.getName()).append(" (")
                       .append(sideB.size()).append(" tracks)\n");
                generatedCount++;
            } catch (IOException e) {
                if (listener != null) {
                    listener.onTrackProgress(2, 2, "Error creating Side B playlist: " + e.getMessage());
                }
            }
        } else {
            if (listener != null) {
                listener.onTrackProgress(2, 2, "Side B is empty, skipping Side B playlist.");
            }
        }

        if (listener != null) {
            boolean success = generatedCount > 0;
            String msg = success ? "M3U8 Export Complete!\n" + summary.toString()
                                 : "M3U8 Export Failed: No tracks found to export.";
            listener.onFinished(success, msg);
        }
    }

    private static void writeM3U8File(File file, String title, List<AudioInfo> tracks) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write("#EXTM3U");
            writer.newLine();
            writer.write("#PLAYLIST:" + title);
            writer.newLine();

            for (AudioInfo track : tracks) {
                int duration = track.getLength();
                String trackTitle = track.getTitle();
                if (trackTitle == null || trackTitle.trim().isEmpty()) {
                    trackTitle = track.getBasicName();
                }
                String artist = track.getArtist();
                String displayName = (artist != null && !artist.trim().isEmpty())
                        ? artist + " - " + trackTitle
                        : trackTitle;

                writer.write("#EXTINF:" + duration + "," + displayName);
                writer.newLine();

                if (track.getFile() != null) {
                    writer.write(track.getFile().getAbsolutePath());
                } else if (track.getUrl() != null) {
                    writer.write(track.getUrl());
                } else {
                    writer.write("# Stream: " + displayName);
                }
                writer.newLine();
            }
        }
    }

    /**
     * Backward-compatible overload for unscaled WAV export.
     */
    public static void exportSidesToWav(String outputDir, String tapeId,
                                        List<AudioInfo> sideA, List<AudioInfo> sideB,
                                        int muteTimeSeconds,
                                        ExportProgressListener listener) {
        exportSidesToWav(outputDir, tapeId, sideA, sideB, muteTimeSeconds, false, listener);
    }

    /**
     * Exports Side A and Side B to continuous single WAV files with blank spaces (AMS gaps),
     * with optional ITU-R BS.1770 LUFS loudness normalization (-16.0 LUFS with peak guard).
     *
     * @param outputDir target output directory (defaults to TapeFiles folder)
     * @param tapeId tape identifier
     * @param sideA list of Side A AudioInfo items
     * @param sideB list of Side B AudioInfo items
     * @param muteTimeSeconds silence duration in seconds between tracks
     * @param normalizeLUFS if true, scales volume to -16.0 LUFS and names files *_LUFS.wav
     * @param listener progress callback listener
     */
    public static void exportSidesToWav(String outputDir, String tapeId,
                                        List<AudioInfo> sideA, List<AudioInfo> sideB,
                                        int muteTimeSeconds,
                                        boolean normalizeLUFS,
                                        ExportProgressListener listener) {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        int totalTracks = 0;
        if (sideA != null) totalTracks += sideA.size();
        if (sideB != null) totalTracks += sideB.size();

        if (totalTracks == 0) {
            if (listener != null) {
                listener.onFinished(false, "WAV Export Aborted: No audio tracks in Side A or Side B.");
            }
            return;
        }

        int[] completedTracks = new int[]{0};
        StringBuilder summary = new StringBuilder();
        String suffix = normalizeLUFS ? "_LUFS.wav" : "_RAW.wav";

        // Process Side A
        if (sideA != null && !sideA.isEmpty()) {
            File wavA = new File(dir, "Tape_" + tapeId + "_Side_A" + suffix);
            try {
                processSideToWav("Side A", wavA, sideA, muteTimeSeconds, normalizeLUFS, completedTracks, totalTracks, listener);
                summary.append("Side A WAV: ").append(wavA.getName()).append(" (")
                       .append(sideA.size()).append(" tracks, ")
                       .append(wavA.length() / (1024 * 1024)).append(" MB)\n");
            } catch (Exception e) {
                if (listener != null) {
                    listener.onTrackProgress(completedTracks[0], totalTracks,
                            "Error rendering Side A WAV: " + e.getMessage());
                }
                e.printStackTrace();
            }
        }

        // Process Side B
        if (sideB != null && !sideB.isEmpty()) {
            File wavB = new File(dir, "Tape_" + tapeId + "_Side_B" + suffix);
            try {
                processSideToWav("Side B", wavB, sideB, muteTimeSeconds, normalizeLUFS, completedTracks, totalTracks, listener);
                summary.append("Side B WAV: ").append(wavB.getName()).append(" (")
                       .append(sideB.size()).append(" tracks, ")
                       .append(wavB.length() / (1024 * 1024)).append(" MB)\n");
            } catch (Exception e) {
                if (listener != null) {
                    listener.onTrackProgress(completedTracks[0], totalTracks,
                            "Error rendering Side B WAV: " + e.getMessage());
                }
                e.printStackTrace();
            }
        }

        if (listener != null) {
            String modeStr = normalizeLUFS ? "LUFS Normalized (-16.0 LUFS)" : "Standard Unscaled";
            listener.onFinished(true, "WAV Export (" + modeStr + ") Complete!\n" + summary.toString());
        }
    }

    private static void processSideToWav(String sideName, File outputFile,
                                         List<AudioInfo> tracks, int muteTimeSeconds,
                                         boolean normalizeLUFS,
                                         int[] completedTracks, int totalTracks,
                                         ExportProgressListener listener) throws Exception {
        if (outputFile.exists()) {
            outputFile.delete();
        }

        long totalAudioBytes = 0;
        byte[] buffer = new byte[65536];

        // Silence buffer (16-bit stereo = 4 bytes per frame)
        int silenceBytesTotal = muteTimeSeconds * (int) TARGET_FORMAT.getSampleRate() * TARGET_FORMAT.getFrameSize();
        byte[] silenceChunk = new byte[Math.min(8192, Math.max(silenceBytesTotal, 4))];

        // 1. Write placeholder header (44 bytes), data will follow
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 131072)) {

            byte[] headerPlaceholder = new byte[44];
            bos.write(headerPlaceholder);

            for (int i = 0; i < tracks.size(); i++) {
                AudioInfo track = tracks.get(i);
                String trackName = track.getName();

                if (listener != null) {
                    listener.onTrackProgress(completedTracks[0], totalTracks,
                            "[" + sideName + "] (" + (i + 1) + "/" + tracks.size() + ") Processing: " + trackName);
                }

                if (track.getFile() != null && track.getFile().exists()) {
                    if (normalizeLUFS) {
                        // Two-pass normalized processing with scratch file
                        File tempFile = File.createTempFile("cf_lufs_", ".pcm");
                        tempFile.deleteOnExit();

                        AudioNormalizer normalizer = new AudioNormalizer(TARGET_LUFS);
                        try {
                            // Pass 1: Decode PCM to temp file while feeding normalizer
                            try (InputStream pcmStream = openAsTargetPcmStream(track.getFile());
                                 FileOutputStream tempFos = new FileOutputStream(tempFile);
                                 BufferedOutputStream tempBos = new BufferedOutputStream(tempFos, 65536)) {
                                int read;
                                while ((read = pcmStream.read(buffer)) != -1) {
                                    tempBos.write(buffer, 0, read);
                                    normalizer.processPcmChunk(buffer, read);
                                }
                                tempBos.flush();
                            }

                            AudioNormalizer.NormalizationResult normResult = normalizer.calculateResult();

                            if (listener != null) {
                                listener.onTrackProgress(completedTracks[0], totalTracks,
                                        "  -> " + normResult.getSummaryString());
                            }

                            // Pass 2: Read temp file, scale samples, write to output master WAV
                            try (FileInputStream tempFis = new FileInputStream(tempFile);
                                 BufferedInputStream tempBis = new BufferedInputStream(tempFis, 65536)) {
                                int read;
                                while ((read = tempBis.read(buffer)) != -1) {
                                    AudioNormalizer.applyGain(buffer, read, normResult.linearGain);
                                    bos.write(buffer, 0, read);
                                    totalAudioBytes += read;
                                }
                            }
                        } finally {
                            tempFile.delete();
                        }
                    } else {
                        // Unscaled direct stream
                        try (InputStream pcmStream = openAsTargetPcmStream(track.getFile())) {
                            int read;
                            while ((read = pcmStream.read(buffer)) != -1) {
                                bos.write(buffer, 0, read);
                                totalAudioBytes += read;
                            }
                        } catch (Exception ex) {
                            if (listener != null) {
                                listener.onTrackProgress(completedTracks[0], totalTracks,
                                        "  Warning: Could not decode " + trackName + " (" + ex.getMessage() + "), skipping.");
                            }
                        }
                    }
                } else {
                    if (listener != null) {
                        listener.onTrackProgress(completedTracks[0], totalTracks,
                                "  Warning: Missing local file for " + trackName + ", skipping.");
                    }
                }

                // Inject inter-track silence gap (AMS) between tracks (except after final track)
                if (i < tracks.size() - 1 && silenceBytesTotal > 0) {
                    int remainingSilence = silenceBytesTotal;
                    while (remainingSilence > 0) {
                        int toWrite = Math.min(remainingSilence, silenceChunk.length);
                        bos.write(silenceChunk, 0, toWrite);
                        totalAudioBytes += toWrite;
                        remainingSilence -= toWrite;
                    }
                }

                completedTracks[0]++;
                if (listener != null) {
                    listener.onTrackProgress(completedTracks[0], totalTracks,
                            "[" + sideName + "] (" + (i + 1) + "/" + tracks.size() + ") Completed.");
                }
            }
            bos.flush();
        }

        // 2. Seek back and write standard RIFF WAVE header with exact file and data lengths
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            long riffChunkSize = totalAudioBytes + 36;
            byte[] header = createWavHeader(riffChunkSize, totalAudioBytes, TARGET_FORMAT);
            raf.seek(0);
            raf.write(header);
        }
    }

    /**
     * Opens an audio file (FLAC, MP3, WAV, etc.) and returns an InputStream delivering
     * uniform 44.1 kHz, 16-bit, Stereo, Signed, Little-Endian PCM data.
     */
    private static InputStream openAsTargetPcmStream(File file) throws Exception {
        String lowerName = file.getName().toLowerCase();

        AudioInputStream sourcePcmStream;

        if (lowerName.endsWith(".flac")) {
            // Read native FLAC properties
            AudioFileFormat aff = AudioSystem.getAudioFileFormat(file);
            AudioFormat baseFormat = aff.getFormat();

            int sampleRate = (int) baseFormat.getSampleRate();
            int channels = baseFormat.getChannels();
            int sampleSize = baseFormat.getSampleSizeInBits() > 0 ? baseFormat.getSampleSizeInBits() : 16;
            int frameSize = channels * (sampleSize / 8);

            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    sampleSize,
                    channels,
                    frameSize,
                    sampleRate,
                    false
            );

            RandomFileInputStream rfis = new RandomFileInputStream(file);
            sourcePcmStream = new Flac2PcmAudioInputStream(rfis, decodedFormat, -1);
        } else {
            // MP3, WAV, AIFF via AudioSystem & registered SPI providers (mp3spi, tritonus)
            AudioInputStream in = AudioSystem.getAudioInputStream(file);
            AudioFormat baseFormat = in.getFormat();

            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            sourcePcmStream = AudioSystem.getAudioInputStream(decodedFormat, in);
        }

        // Check if format already matches TARGET_FORMAT exactly
        AudioFormat srcFormat = sourcePcmStream.getFormat();
        if (Math.abs(srcFormat.getSampleRate() - TARGET_FORMAT.getSampleRate()) < 1.0f
                && srcFormat.getChannels() == TARGET_FORMAT.getChannels()
                && srcFormat.getSampleSizeInBits() == TARGET_FORMAT.getSampleSizeInBits()
                && srcFormat.getEncoding() == TARGET_FORMAT.getEncoding()) {
            return sourcePcmStream;
        }

        // Check if standard AudioSystem conversion is supported
        if (AudioSystem.isConversionSupported(TARGET_FORMAT, srcFormat)) {
            return AudioSystem.getAudioInputStream(TARGET_FORMAT, sourcePcmStream);
        }

        // Lightweight PCM format standardizer (handles mono->stereo duplication & sample rate resampling)
        return new ResamplingStereoInputStream(sourcePcmStream, srcFormat, TARGET_FORMAT);
    }

    /**
     * Constructs a 44-byte standard RIFF WAVE PCM header.
     */
    private static byte[] createWavHeader(long totalDataLen, long totalAudioLen, AudioFormat format) {
        byte[] header = new byte[44];
        long byteRate = (long) format.getSampleRate() * format.getFrameSize();
        int channels = format.getChannels();
        long sampleRate = (long) format.getSampleRate();
        int bitsPerSample = format.getSampleSizeInBits();

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size = 16 for PCM
        header[20] = 1; header[21] = 0; // AudioFormat = 1 (PCM)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * (bitsPerSample / 8)); // BlockAlign
        header[33] = 0;
        header[34] = (byte) bitsPerSample; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        return header;
    }

    /**
     * Fallback stream to cleanly resample and convert mono/stereo PCM into 44.1kHz 16-bit Stereo.
     */
    private static class ResamplingStereoInputStream extends InputStream {
        private final InputStream source;
        private final AudioFormat srcFormat;
        private final double step; // source frames per target frame
        private double sourceFramePos = 0.0;
        private short prevL = 0, prevR = 0;
        private short currL = 0, currR = 0;
        private boolean eof = false;

        public ResamplingStereoInputStream(InputStream source, AudioFormat srcFormat, AudioFormat targetFormat) {
            this.source = source;
            this.srcFormat = srcFormat;
            this.step = srcFormat.getSampleRate() / (double) targetFormat.getSampleRate();
            readNextSourceFrame();
            prevL = currL;
            prevR = currR;
        }

        private boolean readNextSourceFrame() {
            try {
                if (srcFormat.getChannels() == 1) {
                    int b0 = source.read();
                    int b1 = source.read();
                    if (b0 == -1 || b1 == -1) {
                        eof = true;
                        return false;
                    }
                    short sample = (short) ((b1 << 8) | (b0 & 0xff));
                    currL = sample;
                    currR = sample;
                } else {
                    int l0 = source.read();
                    int l1 = source.read();
                    int r0 = source.read();
                    int r1 = source.read();
                    if (l0 == -1 || l1 == -1 || r0 == -1 || r1 == -1) {
                        eof = true;
                        return false;
                    }
                    currL = (short) ((l1 << 8) | (l0 & 0xff));
                    currR = (short) ((r1 << 8) | (r0 & 0xff));
                }
                return true;
            } catch (IOException e) {
                eof = true;
                return false;
            }
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n == -1 ? -1 : (b[0] & 0xff);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (eof && sourceFramePos >= 1.0) {
                return -1;
            }

            int framesRequested = len / 4;
            if (framesRequested == 0 && len > 0) {
                framesRequested = 1;
            }

            int bytesWritten = 0;
            for (int f = 0; f < framesRequested && off + bytesWritten + 4 <= off + len; f++) {
                while (sourceFramePos >= 1.0) {
                    prevL = currL;
                    prevR = currR;
                    if (!readNextSourceFrame()) {
                        break;
                    }
                    sourceFramePos -= 1.0;
                }

                if (eof && sourceFramePos >= 1.0) {
                    break;
                }

                // Linear interpolation
                double frac = Math.max(0.0, Math.min(1.0, sourceFramePos));
                short outL = (short) Math.round(prevL + frac * (currL - prevL));
                short outR = (short) Math.round(prevR + frac * (currR - prevR));

                // Write 4 bytes (16-bit little-endian stereo)
                b[off + bytesWritten++] = (byte) (outL & 0xff);
                b[off + bytesWritten++] = (byte) ((outL >> 8) & 0xff);
                b[off + bytesWritten++] = (byte) (outR & 0xff);
                b[off + bytesWritten++] = (byte) ((outR >> 8) & 0xff);

                sourceFramePos += step;
            }

            return bytesWritten == 0 ? -1 : bytesWritten;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }
}
