package cassetteflow;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TestBaud {
    public static void main(String[] args) throws Exception {
        System.out.println("Validating JMinimodem with Strategy 2 (alpha = 0.005)...\n");
        testRun(44100.0f, 1200.0, 0.0);
        testRun(48000.0f, 1200.0, 0.0);
        testRun(96000.0f, 1200.0, 0.0);
        testRun(44100.0f, 300.0, 0.0);
        testRun(48000.0f, 300.0, 0.0);
        testRun(48000.0f, 1200.0, 0.1);
    }

    public static void testRun(float sampleRate, double targetBaud, double noiseLevel) throws Exception {
        JMinimodem.Config cfg = new JMinimodem.Config();
        cfg.baudRate = targetBaud;
        if (targetBaud == 300) {
            cfg.freqMark = 1270.0;
            cfg.freqSpace = 1070.0;
        } else {
            cfg.freqMark = 1200.0;
            cfg.freqSpace = 2200.0;
        }
        cfg.sampleRate = sampleRate;
        cfg.quiet = true;

        ByteArrayOutputStream audioPcm = new ByteArrayOutputStream();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sb.append(String.format("DCT0A%03d 1234567890123456789\n", i));
        }
        String originalText = sb.toString();
        byte[] textBytes = originalText.getBytes(StandardCharsets.US_ASCII);
        JMinimodem.transmit(cfg, new ByteArrayInputStream(textBytes), audioPcm);
        
        byte[] pcmData = audioPcm.toByteArray();
        if (noiseLevel > 0) {
            Random r = new Random(42);
            for (int i = 0; i < pcmData.length; i += 2) {
                short s = (short)((pcmData[i] & 0xFF) | (pcmData[i+1] << 8));
                double val = s / 32768.0 + r.nextGaussian() * noiseLevel;
                if (val > 1.0) val = 1.0;
                if (val < -1.0) val = -1.0;
                short outS = (short)(val * 32767);
                pcmData[i] = (byte)(outS & 0xFF);
                pcmData[i+1] = (byte)((outS >> 8) & 0xFF);
            }
        }

        final List<Double> baudList = new ArrayList<>();
        cfg.diagDataListener = new JMinimodem.DiagnosticDataListener() {
            @Override
            public void onDiagnosticData(JMinimodem.DiagnosticData data) {
                if (data.measuredBaud > 0) {
                    baudList.add(data.measuredBaud);
                }
            }
        };

        ByteArrayInputStream audioIn = new ByteArrayInputStream(pcmData);
        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(cfg.sampleRate, 16, 1, true, false);
        javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(audioIn, format, pcmData.length / 2);
        
        ByteArrayOutputStream decodedText = new ByteArrayOutputStream();
        JMinimodem.receive(cfg, ais, decodedText);
        
        double lastBaud = baudList.isEmpty() ? 0.0 : baudList.get(baudList.size() - 1);
        double errPct = (lastBaud - targetBaud) * 100.0 / targetBaud;
        String decoded = decodedText.toString(StandardCharsets.US_ASCII);
        boolean exact = decoded.equals(originalText);

        System.out.printf("Rate: %5.0f Hz | Target: %4.0f | Measured: %7.2f Baud (Error: %+5.2f%%) | Match: %s\n",
                sampleRate, targetBaud, lastBaud, errPct, exact ? "EXACT (60/60 lines)" : "DIFF");
    }
}
