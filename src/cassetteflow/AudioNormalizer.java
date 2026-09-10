package cassetteflow;

/**
 * High-performance ITU-R BS.1770-4 loudness measurement and gain normalizer.
 * Provides K-weighting filtering, integrated LUFS calculation, and peak-guarded
 * volume scaling tailored for analog cassette tape recording.
 */
public class AudioNormalizer {

    /** Default target loudness for cassette tape masters (-16.0 LUFS aligns with 0 dB VU) */
    public static final double DEFAULT_TARGET_LUFS = -16.0;

    /** Ceiling peak to guarantee no digital clipping (-0.5 dBFS in 16-bit signed PCM) */
    public static final short PEAK_CEILING_SAMPLE = 30920;

    // Stage 1: High-shelf filter (models head acoustic shadow) at 44.1 kHz
    private static final double B0_S1 = 1.53512485958697;
    private static final double B1_S1 = -2.69169618940638;
    private static final double B2_S1 = 1.19839281085285;
    private static final double A1_S1 = -1.69065929318241;
    private static final double A2_S1 = 0.73238050604561;

    // Stage 2: High-pass filter (RLB weighting, removes DC/sub-rumble) at 44.1 kHz
    private static final double B0_S2 = 1.0;
    private static final double B1_S2 = -2.0;
    private static final double B2_S2 = 1.0;
    private static final double A1_S2 = -1.99004745483398;
    private static final double A2_S2 = 0.99007225036621;

    /** Filter state for a single channel */
    private static class BiquadState {
        double x1 = 0, x2 = 0;
        double y1 = 0, y2 = 0;
    }

    private final BiquadState s1L = new BiquadState();
    private final BiquadState s1R = new BiquadState();
    private final BiquadState s2L = new BiquadState();
    private final BiquadState s2R = new BiquadState();

    private double sumSquareL = 0.0;
    private double sumSquareR = 0.0;
    private long totalFrames = 0;
    private int maxAbsPeak = 0;

    private final double targetLufs;

    public AudioNormalizer() {
        this(DEFAULT_TARGET_LUFS);
    }

    public AudioNormalizer(double targetLufs) {
        this.targetLufs = targetLufs;
    }

    /**
     * Feeds 16-bit signed stereo little-endian PCM bytes to update loudness and peak state.
     *
     * @param buffer byte buffer containing 16-bit stereo PCM
     * @param length number of valid bytes in buffer
     */
    public void processPcmChunk(byte[] buffer, int length) {
        int frames = length / 4;
        for (int i = 0; i < frames; i++) {
            int idx = i * 4;

            // Extract 16-bit little-endian samples
            short sampleL = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xff));
            short sampleR = (short) ((buffer[idx + 3] << 8) | (buffer[idx + 2] & 0xff));

            // Track peak
            int absL = Math.abs(sampleL);
            int absR = Math.abs(sampleR);
            if (absL > maxAbsPeak) maxAbsPeak = absL;
            if (absR > maxAbsPeak) maxAbsPeak = absR;

            // Normalize to [-1.0, 1.0] for K-weighting filters
            double inL = sampleL / 32768.0;
            double inR = sampleR / 32768.0;

            // Stage 1: High shelf
            double s1OutL = filter(inL, s1L, B0_S1, B1_S1, B2_S1, A1_S1, A2_S1);
            double s1OutR = filter(inR, s1R, B0_S1, B1_S1, B2_S1, A1_S1, A2_S1);

            // Stage 2: High pass
            double s2OutL = filter(s1OutL, s2L, B0_S2, B1_S2, B2_S2, A1_S2, A2_S2);
            double s2OutR = filter(s1OutR, s2R, B0_S2, B1_S2, B2_S2, A1_S2, A2_S2);

            sumSquareL += s2OutL * s2OutL;
            sumSquareR += s2OutR * s2OutR;
            totalFrames++;
        }
    }

    private static double filter(double in, BiquadState s,
                                 double b0, double b1, double b2,
                                 double a1, double a2) {
        double out = b0 * in + b1 * s.x1 + b2 * s.x2 - a1 * s.y1 - a2 * s.y2;
        s.x2 = s.x1;
        s.x1 = in;
        s.y2 = s.y1;
        s.y1 = out;
        return out;
    }

    /**
     * Result of the loudness analysis containing measured LUFS, applied gain factor,
     * and peak guard status.
     */
    public static class NormalizationResult {
        public final double measuredLufs;
        public final double idealGainDb;
        public final double appliedGainDb;
        public final double linearGain;
        public final boolean peakGuardApplied;
        public final int maxOriginalPeak;

        public NormalizationResult(double measuredLufs, double idealGainDb,
                                   double appliedGainDb, double linearGain,
                                   boolean peakGuardApplied, int maxOriginalPeak) {
            this.measuredLufs = measuredLufs;
            this.idealGainDb = idealGainDb;
            this.appliedGainDb = appliedGainDb;
            this.linearGain = linearGain;
            this.peakGuardApplied = peakGuardApplied;
            this.maxOriginalPeak = maxOriginalPeak;
        }

        public String getSummaryString() {
            String peakStatus = peakGuardApplied ? "Peak Guard Clamped" : "Safe";
            return String.format("%.1f LUFS -> Gain: %+.1f dB (%s)",
                    measuredLufs, appliedGainDb, peakStatus);
        }
    }

    /**
     * Finalizes analysis and returns the calculated normalization parameters.
     */
    public NormalizationResult calculateResult() {
        if (totalFrames == 0) {
            return new NormalizationResult(-70.0, 0.0, 0.0, 1.0, false, 0);
        }

        double zL = sumSquareL / totalFrames;
        double zR = sumSquareR / totalFrames;
        double sumPower = zL + zR;

        double measuredLufs;
        if (sumPower <= 1e-12) {
            measuredLufs = -70.0;
        } else {
            measuredLufs = -0.691 + 10.0 * Math.log10(sumPower);
        }

        double idealGainDb = targetLufs - measuredLufs;
        double linearGain = Math.pow(10.0, idealGainDb / 20.0);

        boolean peakGuard = false;
        if (maxAbsPeak > 0 && (linearGain * maxAbsPeak > PEAK_CEILING_SAMPLE)) {
            linearGain = (double) PEAK_CEILING_SAMPLE / maxAbsPeak;
            peakGuard = true;
        }

        double appliedGainDb = 20.0 * Math.log10(linearGain);

        return new NormalizationResult(measuredLufs, idealGainDb, appliedGainDb, linearGain, peakGuard, maxAbsPeak);
    }

    /**
     * Applies linear gain scaling to a 16-bit signed stereo little-endian PCM buffer.
     *
     * @param buffer byte buffer containing 16-bit stereo PCM
     * @param length number of valid bytes in buffer
     * @param linearGain gain multiplier
     */
    public static void applyGain(byte[] buffer, int length, double linearGain) {
        int frames = length / 4;
        for (int i = 0; i < frames; i++) {
            int idx = i * 4;

            // Left
            short sampleL = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xff));
            int scaledL = (int) Math.round(sampleL * linearGain);
            if (scaledL > 32767) scaledL = 32767;
            else if (scaledL < -32768) scaledL = -32768;
            buffer[idx] = (byte) (scaledL & 0xff);
            buffer[idx + 1] = (byte) ((scaledL >> 8) & 0xff);

            // Right
            short sampleR = (short) ((buffer[idx + 3] << 8) | (buffer[idx + 2] & 0xff));
            int scaledR = (int) Math.round(sampleR * linearGain);
            if (scaledR > 32767) scaledR = 32767;
            else if (scaledR < -32768) scaledR = -32768;
            buffer[idx + 2] = (byte) (scaledR & 0xff);
            buffer[idx + 3] = (byte) ((scaledR >> 8) & 0xff);
        }
    }
}
