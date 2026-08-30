package cassetteflow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A custom InputStream that yields DCT Line Data payloads for a specific duration.
 * <p>
 * This stream auto-generates DCT records (e.g. DCT0A_01_aaaaaaaaaa_0015_0015) representing
 * the current second of transmission. 
 * 
 * Once the payload for a second is exhausted, it yields '0xFF' (Mark/Idle tone) 
 * until the exact byte count required by the baud rate constraints for that second is hit.
 * 
 * When the duration runs out, it gracefully returns -1 to stop the JMinimodem loop.
 * </p>
 */
public class DurationInputStream extends InputStream {

    private final double baudRate;
    private final char side;
    
    // Calculated bytes needed for the entire run
    private final long totalBytesRequired;
    private long bytesYielded = 0;
    
    // Internal State
    private int currentSecond = 0;
    private int repeatCount = 0;
    private byte[] currentPayload = null;
    private int payloadIndex = 0;
    private volatile boolean stopped = false;
    private java.util.function.Consumer<String> payloadListener;

    public void setPayloadListener(java.util.function.Consumer<String> listener) {
        this.payloadListener = listener;
    }

    /**
     * @param durationSeconds Total time to broadcast.
     * @param baudRate The serial baud rate (e.g. 1200). 
     * @param side 'A' or 'B' for DCT payload formatting.
     */
    public DurationInputStream(int durationSeconds, double baudRate, char side) {
        this.baudRate = baudRate;
        this.side = side;
        
        // 8-N-1 UART framing = 1 start bit + 8 data bits + 1 stop bit = 10 bits per byte.
        // total bytes = (seconds * baud) / 10
        this.totalBytesRequired = (long) ((durationSeconds * baudRate) / 10.0);
    }

    @Override
    public int read() throws IOException {
        // 1. Check if we have hit the precise duration limit or asked to stop early
        if (stopped || bytesYielded >= totalBytesRequired) {
            return -1; // End of transmission time
        }

        bytesYielded++;
        
        // 2. See if we are currently yielding a payload string
        if (currentPayload != null && payloadIndex < currentPayload.length) {
            return currentPayload[payloadIndex++] & 0xFF;
        }
        
        // 3. We are out of payload bytes but still have time left to hit.
        // Calculate which absolute second of broadcast we are currently in
        // based on how many bytes we've yielded so far.
        int calculatedSecond = (int) (bytesYielded / (baudRate / 10.0));
        
        // 5. Check if it's time to bump the second based on yielded bytes
        if (calculatedSecond > currentSecond) {
            currentSecond = calculatedSecond;
            repeatCount = 0;
        }

        // 6. Every time we need bytes, we just generate the correct record.
        // We will repeat the current record 4 times before bumping currentSecond (if it hasn't bumped yet).
        // Since we are not manually tracking exact fractional phase inside DurationInputStream natively,
        // we'll just keep yielding characters. 
        // Note: At 300 baud, 1 record takes nearly 1 second. At 1200 baud, 4 records take nearly 1 second.
        if (repeatCount < 4) {
            generateNextRecord(currentSecond);
            repeatCount++;
        } else {
            // If we've already done 4 repeats for this mathematical second,
            // we should technically generate the NEXT second's record now rather than waiting!
            currentSecond++;
            generateNextRecord(currentSecond);
            repeatCount = 1;
        }
        
        // Immediately start yielding this fresh string
        bytesYielded--; // Undo the ++ above since we haven't actually yielded a char yet
        return read(); // Recurse to yield the first char of the new string 
    }
    
    /**
     * Constructs the specific 29-character DCT text string expected by VisualizerApp / CassetteFlow
     */
    private void generateNextRecord(int sec) {
        // Format: DCT0A_01_aaaaaaaaaa_%04d_%04d\n
        String record = String.format("DCT0%c_01_aaaaaaaaaa_%04d_%04d\n", side, sec, sec);
        currentPayload = record.getBytes(StandardCharsets.US_ASCII);
        payloadIndex = 0;
        if (payloadListener != null) {
            payloadListener.accept(record);
        }
    }

    /**
     * Flags the stream to return -1 on the next read().
     */
    public void earlyStop() {
        this.stopped = true;
    }

}
