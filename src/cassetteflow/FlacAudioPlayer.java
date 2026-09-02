package cassetteflow;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.jflac.io.RandomFileInputStream;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;

/**
 * High-performance, seekable audio player for FLAC files using Java Sound API,
 * RandomFileInputStream, and jflac-codec. Designed to integrate cleanly with CassettePlayer.
 */
public class FlacAudioPlayer {

    private static final Logger LOGGER = Logger.getLogger(FlacAudioPlayer.class.getName());
    private static final int BUFFER_SIZE = 4096;

    /**
     * Listener interface to track playback progress.
     */
    public interface ProgressListener {
        void onProgress(int elapsedSeconds);
    }

    private SourceDataLine line;
    private Flac2PcmAudioInputStream flacStream;
    private AudioFormat baseFormat;
    private AudioFormat decodedFormat;

    private String mixerName;
    private double speedFactor = 1.0;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private volatile int audioProgress = 0; // seconds elapsed on output line

    private Thread playbackThread;
    private ProgressListener progressListener;

    public FlacAudioPlayer() {
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public void setMixerName(String mixerName) {
        this.mixerName = mixerName;
    }

    public void setSpeedFactor(double speedFactor) {
        this.speedFactor = speedFactor;
    }

    /**
     * Opens a FLAC file using RandomFileInputStream so that fast seeking is fully supported.
     *
     * @param file the FLAC audio file
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public synchronized void open(File file) throws IOException, UnsupportedAudioFileException {
        stop();

        // 1. Read native FLAC audio format properties
        AudioFileFormat aff = AudioSystem.getAudioFileFormat(file);
        baseFormat = aff.getFormat();

        int sampleRate = (int) baseFormat.getSampleRate();
        int channels = baseFormat.getChannels();
        int sampleSize = baseFormat.getSampleSizeInBits() > 0 ? baseFormat.getSampleSizeInBits() : 16;
        int frameSize = channels * (sampleSize / 8);

        decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                sampleSize,
                channels,
                frameSize,
                sampleRate,
                false
        );

        // 2. Open via RandomFileInputStream to enable seek support in Flac2PcmAudioInputStream
        RandomFileInputStream rfis = new RandomFileInputStream(file);
        flacStream = new Flac2PcmAudioInputStream(rfis, decodedFormat, -1);

        audioProgress = 0;
        if (progressListener != null) {
            progressListener.onProgress(0);
        }
    }

    /**
     * Seeks to the specified position in seconds.
     * Uses jflac-codec's fast frame-accurate seeking table.
     *
     * @param seconds target time offset in seconds
     * @throws IOException
     */
    public synchronized void seekTo(int seconds) throws IOException {
        if (seconds <= 0 || flacStream == null || decodedFormat == null) {
            return;
        }

        long targetSample = (long) seconds * (long) decodedFormat.getSampleRate();
        System.out.println("[FlacAudioPlayer] Seeking to " + seconds + "s (target sample: " + targetSample + ")...");
        try {
            flacStream.seek(targetSample);
            System.out.println("[FlacAudioPlayer] Seek complete. Position sample: " + flacStream.getCurrentSample());
        } catch (IllegalArgumentException e) {
            System.err.println("[FlacAudioPlayer] Seek out of bounds: " + e.getMessage());
        }
    }

    /**
     * Starts playback in a dedicated daemon thread.
     *
     * @throws LineUnavailableException
     */
    public synchronized void play() throws LineUnavailableException {
        if (flacStream == null || decodedFormat == null) {
            return;
        }

        stopPlaybackThread();

        // Calculate playback format applying speedFactor to the sample rate
        float playbackSampleRate = (float) (decodedFormat.getSampleRate() * speedFactor);
        AudioFormat lineFormat = new AudioFormat(
                decodedFormat.getEncoding(),
                playbackSampleRate,
                decodedFormat.getSampleSizeInBits(),
                decodedFormat.getChannels(),
                decodedFormat.getFrameSize(),
                playbackSampleRate,
                decodedFormat.isBigEndian()
        );

        line = createSourceDataLine(lineFormat, mixerName);
        line.open(lineFormat);
        line.start();

        playing = true;
        paused = false;

        playbackThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            try {
                while (playing) {
                    if (paused) {
                        Thread.sleep(50);
                        continue;
                    }

                    int bytesRead = flacStream.read(buffer, 0, buffer.length);
                    if (bytesRead == -1) {
                        break;
                    }

                    line.write(buffer, 0, bytesRead);

                    if (line != null) {
                        int currentSec = (int) (line.getMicrosecondPosition() / 1_000_000L);
                        if (currentSec != audioProgress) {
                            audioProgress = currentSec;
                            if (progressListener != null) {
                                progressListener.onProgress(audioProgress);
                            }
                        }
                    }
                }

                if (line != null && playing) {
                    line.drain();
                }
            } catch (Exception e) {
                if (playing) {
                    LOGGER.log(Level.WARNING, "Error during FLAC playback", e);
                }
            } finally {
                cleanupLine();
                playing = false;
            }
        }, "FlacAudioPlayer-Thread");

        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /**
     * Stops audio playback and frees line resources.
     */
    public synchronized void stop() {
        playing = false;
        paused = false;
        stopPlaybackThread();
        cleanupLine();

        if (flacStream != null) {
            try {
                flacStream.close();
            } catch (IOException ignored) {
            }
            flacStream = null;
        }
        audioProgress = 0;
    }

    public synchronized void pause() {
        if (playing) {
            paused = true;
            if (line != null) {
                line.stop();
            }
        }
    }

    public synchronized void resume() {
        if (playing && paused) {
            if (line != null) {
                line.start();
            }
            paused = false;
        }
    }

    public boolean isPlaying() {
        return playing && line != null && line.isActive();
    }

    public int getAudioProgress() {
        return audioProgress;
    }

    private void stopPlaybackThread() {
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            try {
                playbackThread.join(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }
    }

    private synchronized void cleanupLine() {
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    /**
     * Resolves a SourceDataLine matching the specified mixer name, or the default.
     */
    private static SourceDataLine createSourceDataLine(AudioFormat format, String outputMixerName)
            throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        if (outputMixerName != null && !outputMixerName.trim().isEmpty()
                && !outputMixerName.equalsIgnoreCase("Default Playback Device")) {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (mixerInfo.getName().trim().equalsIgnoreCase(outputMixerName.trim())) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        if (mixer.isLineSupported(info)) {
                            return (SourceDataLine) mixer.getLine(info);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return (SourceDataLine) AudioSystem.getLine(info);
    }
}
