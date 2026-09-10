package cassetteflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hierarchical Procedural Musical Envelope Generator.
 * Synthesizes realistic, dynamic volume envelopes for analog VU meters based on
 * genuine musical arrangements rather than repetitive periodic LFOs.
 * 
 * Key Features:
 * 1. Multi-Track Album Structure: Divides tape side into distinct 3-4 minute "songs"
 *    separated by authentic 2.5-3.5 second silence gaps (needles drop to the floor).
 * 2. Dynamic Section Phrasing: Each song features structured Intro, Verse, Chorus,
 *    Bridge/Breakdown, and Outro sections with varying dynamic energy levels.
 * 3. Rhythmic Syncopation & Drum Fills: Kicks, snares, 16th-note fills, cymbal crashes,
 *    and phrase boundary variations prevent repetitive loop patterns.
 * 4. Multi-Frequency Organic Instrumental Body: Simulates chord progressions, melody lines,
 *    and vocal inflections using non-harmonic organic waveforms.
 * 5. Analog VU Meter Ballistics: Physical one-pole inertia model (30ms attack, 200ms decay)
 *    ensures needle motion mirrors authentic analog tape decks.
 */
public class ProceduralEnvelopeGenerator {

    public enum Genre {
        POP_DANCE("Pop / Dance (124 BPM)", 124.0, "DCT-Pop"),
        ROCK_ALTERNATIVE("Rock / Alternative (118 BPM)", 118.0, "DCT-Rock"),
        RAP_HIPHOP("Rap / Hip-Hop (90 BPM)", 90.0, "DCT-Rap"),
        REGGAE("Reggae / Dub (76 BPM)", 76.0, "DCT-Reggae"),
        JAZZ_SWING("Jazz / Swing (108 BPM)", 108.0, "DCT-Jazz"),
        AMBIENT_CHILL("Ambient / Lo-Fi Chill (72 BPM)", 72.0, "DCT-Ambient"),
        CLASSICAL("Classical / Symphony (Dynamic)", 84.0, "DCT-Classical");

        private final String displayName;
        private final double bpm;
        private final String fileTag;

        Genre(String displayName, double bpm, String fileTag) {
            this.displayName = displayName;
            this.bpm = bpm;
            this.fileTag = fileTag;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getBpm() {
            return bpm;
        }

        public String getFileTag() {
            return fileTag;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Pre-computed, continuous musical envelope timeline for an entire tape side.
     * Evaluated at 100 Hz (10ms resolution) with linear interpolation, providing
     * sub-millisecond query performance and zero CPU overhead during FSK streaming.
     */
    public static class Timeline {
        private final float[] points;
        private final int sampleRateHz;
        private final int durationSeconds;

        public Timeline(float[] points, int sampleRateHz, int durationSeconds) {
            this.points = points;
            this.sampleRateHz = sampleRateHz;
            this.durationSeconds = durationSeconds;
        }

        /**
         * Looks up and interpolates the procedural volume envelope at a given playback time.
         * Guaranteed to be clamped between minScale and 1.0.
         */
        public float getEnvelope(double timeSec, float minScale) {
            if (points == null || points.length == 0) return minScale;
            if (timeSec < 0.0) timeSec = 0.0;
            double samplePos = timeSec * sampleRateHz;
            int idx = (int) samplePos;
            if (idx >= points.length - 1) {
                float val = points[points.length - 1];
                return Math.max(minScale, Math.min(1.0f, val));
            }
            float frac = (float) (samplePos - idx);
            float interpolated = points[idx] * (1.0f - frac) + points[idx + 1] * frac;
            return Math.max(minScale, Math.min(1.0f, interpolated));
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }
    }

    private static class Section {
        double startTime;
        double duration;
        double baseEnergy;
        boolean hasDrums;
        boolean isChorus;
        boolean isBridge;

        Section(double startTime, double duration, double baseEnergy, boolean hasDrums, boolean isChorus, boolean isBridge) {
            this.startTime = startTime;
            this.duration = duration;
            this.baseEnergy = baseEnergy;
            this.hasDrums = hasDrums;
            this.isChorus = isChorus;
            this.isBridge = isBridge;
        }
    }

    private static class SongPlan {
        double startTime;
        double duration;
        double bpm;
        int seedOffset;
        List<Section> sections = new ArrayList<>();
    }

    /**
     * Generates a realistic multi-song hierarchical timeline for a given genre, duration, and seed.
     */
    public static Timeline createTimeline(Genre genre, int durationSeconds, long seed) {
        int sampleRateHz = 100; // 10ms resolution
        int numPoints = durationSeconds * sampleRateHz + sampleRateHz;
        float[] raw = new float[numPoints];

        Random rng = new Random(seed ^ (long) genre.ordinal() * 31337L);

        // 1. Arrange Songs and Inter-Song Gaps
        List<SongPlan> songs = new ArrayList<>();
        double currentT = 0.0;
        int songIndex = 0;

        while (currentT < durationSeconds) {
            double songDuration = 180.0 + rng.nextDouble() * 60.0; // 180s - 240s (~3 to 4 min)
            if (currentT + songDuration > durationSeconds) {
                songDuration = durationSeconds - currentT;
            }
            if (songDuration < 25.0) break;

            SongPlan song = new SongPlan();
            song.startTime = currentT;
            song.duration = songDuration;
            song.seedOffset = songIndex * 137 + rng.nextInt(500);

            // Vary BPM slightly around genre baseline
            double bpmDrift = (rng.nextDouble() * 10.0 - 5.0);
            song.bpm = Math.max(60.0, genre.getBpm() + bpmDrift);

            double beatPeriod = 60.0 / song.bpm;
            double barPeriod = beatPeriod * 4.0;

            // Generate structured sections: Intro, Verse 1, Chorus 1, Verse 2, Chorus 2, Bridge, Chorus 3, Outro
            double secT = 0.0;
            // Intro (4-8 bars)
            double introDur = (rng.nextBoolean() ? 4 : 8) * barPeriod;
            song.sections.add(new Section(secT, introDur, 0.40, false, false, false));
            secT += introDur;

            // Verse 1 (12-16 bars)
            double v1Dur = 16 * barPeriod;
            song.sections.add(new Section(secT, v1Dur, 0.58, true, false, false));
            secT += v1Dur;

            // Chorus 1 (12-16 bars)
            double c1Dur = 16 * barPeriod;
            song.sections.add(new Section(secT, c1Dur, 0.88, true, true, false));
            secT += c1Dur;

            // Verse 2 (12-16 bars)
            double v2Dur = 16 * barPeriod;
            song.sections.add(new Section(secT, v2Dur, 0.62, true, false, false));
            secT += v2Dur;

            // Chorus 2 (16 bars)
            double c2Dur = 16 * barPeriod;
            song.sections.add(new Section(secT, c2Dur, 0.90, true, true, false));
            secT += c2Dur;

            // Bridge / Breakdown (8 bars)
            double bridgeDur = 8 * barPeriod;
            song.sections.add(new Section(secT, bridgeDur, 0.35, false, false, true));
            secT += bridgeDur;

            // Chorus 3 / Final Chorus (16 bars)
            double c3Dur = 16 * barPeriod;
            song.sections.add(new Section(secT, c3Dur, 0.96, true, true, false));
            secT += c3Dur;

            // Outro (4-8 bars)
            double outroDur = (songDuration - secT > 0) ? (songDuration - secT) : (8 * barPeriod);
            song.sections.add(new Section(secT, outroDur, 0.48, true, false, false));

            songs.add(song);

            // Inter-song silence gap (2.5s - 3.5s)
            double gap = 2.5 + rng.nextDouble() * 1.0;
            currentT = song.startTime + songDuration + gap;
            songIndex++;
        }

        // 2. Synthesize Envelope Point by Point
        for (int i = 0; i < numPoints; i++) {
            double t = (double) i / sampleRateHz;

            // Find which song we are in
            SongPlan currentSong = null;
            for (SongPlan s : songs) {
                if (t >= s.startTime && t < s.startTime + s.duration) {
                    currentSong = s;
                    break;
                }
            }

            if (currentSong == null) {
                // In inter-song gap: silence floor! (Needles rest at minimum floor)
                raw[i] = 0.0f;
                continue;
            }

            double songTime = t - currentSong.startTime;
            double beatPeriod = 60.0 / currentSong.bpm;
            double barPeriod = beatPeriod * 4.0;

            // Find current section
            Section sec = currentSong.sections.get(currentSong.sections.size() - 1);
            for (Section s : currentSong.sections) {
                if (songTime >= s.startTime && songTime < s.startTime + s.duration) {
                    sec = s;
                    break;
                }
            }

            double secTime = songTime - sec.startTime;
            int barIndex = (int) (secTime / barPeriod);
            double barTime = secTime % barPeriod;
            double beatInBar = barTime / beatPeriod;
            int beatIndex = (int) beatInBar; // 0, 1, 2, 3
            double beatFract = beatInBar - Math.floor(beatInBar); // 0.0 to 1.0

            // A. Drums & Percussion Accents
            double drumVal = 0.0;
            if (sec.hasDrums) {
                switch (genre) {
                    case POP_DANCE:
                        // 4-on-the-floor kick pulse
                        double kick = Math.exp(-beatFract * 14.0);
                        // Sharp snare on beats 2 & 4 (beatIndex 1 and 3)
                        double snare = (beatIndex == 1 || beatIndex == 3) ? (0.85 * Math.exp(-beatFract * 10.0)) : 0.0;
                        // Syncopated ghost kick in even bars on beat 2.5
                        double ghostKick = 0.0;
                        if (barIndex % 2 == 1 && beatIndex == 2 && beatFract > 0.5) {
                            double frac2 = (beatFract - 0.5) * 2.0;
                            ghostKick = 0.65 * Math.exp(-frac2 * 14.0);
                        }
                        // Drum fills on 8th bar of phrase (barIndex % 8 == 7)
                        double fill = 0.0;
                        if (barIndex % 8 == 7 && (beatIndex == 2 || beatIndex == 3)) {
                            double sixteenth = (beatFract * 4.0) % 1.0;
                            fill = 0.75 * Math.exp(-sixteenth * 12.0);
                        }
                        // Cymbal crash on beat 1 of Chorus
                        double crash = (sec.isChorus && barIndex == 0) ? (0.60 * Math.exp(-barTime * 1.5)) : 0.0;

                        drumVal = Math.max(kick, Math.max(snare, Math.max(ghostKick, Math.max(fill, crash))));
                        break;

                    case ROCK_ALTERNATIVE:
                        // Kick on 1 and 3 (beatIndex 0 and 2)
                        double rockKick = (beatIndex == 0 || beatIndex == 2) ? Math.exp(-beatFract * 12.0) : 0.0;
                        // Heavy snare punch on 2 and 4 (beatIndex 1 and 3)
                        double rockSnare = (beatIndex == 1 || beatIndex == 3) ? (1.0 * Math.exp(-beatFract * 8.5)) : 0.0;
                        // Tom fills on bar 4, 8, 12, 16
                        double rockFill = 0.0;
                        if (barIndex % 4 == 3 && beatIndex == 3) {
                            double sixteenth = (beatFract * 4.0) % 1.0;
                            rockFill = 0.85 * Math.exp(-sixteenth * 11.0);
                        }
                        double rockCrash = (sec.isChorus && barIndex == 0) ? (0.70 * Math.exp(-barTime * 1.8)) : 0.0;
                        drumVal = Math.max(rockKick, Math.max(rockSnare, Math.max(rockFill, rockCrash)));
                        break;

                    case RAP_HIPHOP:
                        // Heavy 808 Sub-Bass Kick on Beat 1 (beatIndex 0) and syncopated beat 2.5 or 3
                        double rapKick = 0.0;
                        if (beatIndex == 0) {
                            rapKick = Math.exp(-beatFract * 7.5); // Extended 808 boom decay
                        } else if (barIndex % 2 == 1 && beatIndex == 2 && beatFract > 0.5) {
                            double frac2 = (beatFract - 0.5) * 2.0;
                            rapKick = 0.85 * Math.exp(-frac2 * 8.0); // Syncopated 808
                        } else if (beatIndex == 2) {
                            rapKick = 0.70 * Math.exp(-beatFract * 8.0);
                        }

                        // Snappy Snare / Clap on Beats 2 & 4 (beatIndex 1 and 3)
                        double rapSnare = (beatIndex == 1 || beatIndex == 3) ? (1.0 * Math.exp(-beatFract * 9.5)) : 0.0;

                        // Trap / Boom-Bap Hi-Hat 16th-note chatter and 32nd rolls
                        double hihatRoll = 0.0;
                        if (barIndex % 4 == 3 && (beatIndex == 1 || beatIndex == 3)) {
                            double fastPhase = (beatFract * 8.0) % 1.0;
                            hihatRoll = 0.45 * Math.exp(-fastPhase * 12.0);
                        } else {
                            double tickPhase = (beatFract * 4.0) % 1.0;
                            hihatRoll = 0.25 * Math.exp(-tickPhase * 15.0);
                        }

                        // Beat drop: silence last beat of bar 8 before drop
                        if (barIndex % 8 == 7 && beatIndex == 3) {
                            drumVal = 0.0;
                        } else {
                            drumVal = Math.max(rapKick, Math.max(rapSnare, hihatRoll));
                        }
                        break;

                    case REGGAE:
                        // Iconic "One Drop": Beat 1 (beatIndex 0) has subtle hi-hat tap (no heavy kick)
                        double reggaeHat = (beatIndex == 0) ? (0.20 * Math.exp(-beatFract * 12.0)) : 0.0;

                        // Heavy Kick + Rimshot hit together on Beat 3 (beatIndex 2)
                        double oneDrop = (beatIndex == 2) ? (1.0 * Math.exp(-beatFract * 9.0)) : 0.0;

                        // Off-Beat "Skank" guitar/keyboard chop on the "and" of every beat
                        double skankPulse = 0.0;
                        if (beatFract > 0.35 && beatFract < 0.85) {
                            double skankPhase = Math.abs(beatFract - 0.50) * 2.0;
                            skankPulse = 0.65 * Math.exp(-skankPhase * 10.0);
                        }

                        // Dub delay / echo fill on the last beat of a 4-bar phrase
                        double dubFill = 0.0;
                        if (barIndex % 4 == 3 && beatIndex == 3) {
                            double triplet = (beatFract * 3.0) % 1.0;
                            dubFill = 0.75 * Math.exp(-triplet * 9.0);
                        }

                        drumVal = Math.max(oneDrop, Math.max(skankPulse, Math.max(dubFill, reggaeHat)));
                        break;

                    case JAZZ_SWING:
                        // 67/33 swung triplet shuffle
                        double swingPulse;
                        if (beatFract < 0.5) {
                            swingPulse = 0.40 * Math.exp(-beatFract * 8.0);
                        } else {
                            double offPhase = Math.abs(beatFract - 0.67);
                            swingPulse = 0.75 * Math.exp(-offPhase * 9.0);
                        }
                        // Walking bass pulse on every beat
                        double bassPulse = 0.50 * Math.exp(-beatFract * 5.0);
                        drumVal = Math.max(swingPulse, bassPulse);
                        break;

                    case AMBIENT_CHILL:
                        // Gentle sidechain pump on beats 0 and 2
                        if (beatIndex == 0 || beatIndex == 2) {
                            drumVal = 0.40 * Math.exp(-beatFract * 4.0);
                        } else {
                            drumVal = 0.10 * Math.exp(-beatFract * 3.0);
                        }
                        break;

                    case CLASSICAL:
                        // Dynamic orchestral articulation / timpani accent
                        if (barIndex % 4 == 0 && beatIndex == 0) {
                            drumVal = 0.70 * Math.exp(-beatFract * 5.0);
                        } else {
                            drumVal = 0.20 * Math.sin(barTime * 2.0);
                        }
                        break;
                }
            }

            // B. Multi-Frequency Instrumental Body (Basslines, Vocals, Chords)
            double seedOff = currentSong.seedOffset;
            double wave1 = Math.sin(songTime * 1.85 + seedOff);
            double wave2 = Math.cos(songTime * 3.7 + seedOff * 1.3);
            double wave3 = Math.sin(songTime * 0.45 + seedOff * 0.7);
            double body = 0.55 + 0.25 * wave1 + 0.12 * wave2 + 0.08 * wave3;

            // Bridge crescendo riser
            double energy = sec.baseEnergy;
            if (sec.isBridge) {
                double bridgeProgress = secTime / sec.duration;
                if (bridgeProgress > 0.6) {
                    // Snare roll build-up into chorus explosion
                    double riserPhase = (bridgeProgress - 0.6) / 0.4;
                    energy = 0.35 + 0.55 * (riserPhase * riserPhase);
                }
            }

            // Combine Energy: Section Baseline + Instrumental Body + Percussion Hits
            double instant = energy * (body * 0.45 + drumVal * 0.55);

            // Smooth fade-out in last 4 seconds of the song
            if (songTime > currentSong.duration - 4.0) {
                double fade = (currentSong.duration - songTime) / 4.0;
                instant *= Math.max(0.0, Math.min(1.0, fade));
            }

            raw[i] = (float) Math.max(0.0, Math.min(1.0, instant));
        }

        // 3. Analog VU Meter Ballistics Filter (Fast 30ms rise, Smooth 200ms fall)
        float[] ballistics = new float[numPoints];
        float currentLevel = 0.0f;
        float alphaAttack = 0.38f; // ~30ms at 100 Hz
        float alphaDecay = 0.075f; // ~200ms at 100 Hz

        for (int i = 0; i < numPoints; i++) {
            float target = raw[i];
            if (target > currentLevel) {
                currentLevel += alphaAttack * (target - currentLevel);
            } else {
                currentLevel += alphaDecay * (target - currentLevel);
            }
            ballistics[i] = currentLevel;
        }

        // 4. Peak Normalization: Ensure loudest chorus hits reach the full 1.0 (0 dBFS) ceiling
        float maxPeak = 0.0f;
        for (int i = 0; i < numPoints; i++) {
            if (ballistics[i] > maxPeak) {
                maxPeak = ballistics[i];
            }
        }
        if (maxPeak > 0.01f) {
            float normFactor = 1.0f / maxPeak;
            for (int i = 0; i < numPoints; i++) {
                ballistics[i] = Math.min(1.0f, ballistics[i] * normFactor);
            }
        }

        return new Timeline(ballistics, sampleRateHz, durationSeconds);
    }

    // Cached timeline for static getEnvelope calls
    private static Timeline cachedTimeline = null;
    private static Genre cachedGenre = null;

    /**
     * Backward-compatible helper method for instantaneous lookup.
     */
    public static synchronized float getEnvelope(Genre genre, double timeSec, float minScale) {
        if (cachedTimeline == null || cachedGenre != genre || timeSec > cachedTimeline.getDurationSeconds()) {
            cachedTimeline = createTimeline(genre, Math.max(3600, (int) timeSec + 600), 12345L);
            cachedGenre = genre;
        }
        return cachedTimeline.getEnvelope(timeSec, minScale);
    }
}
