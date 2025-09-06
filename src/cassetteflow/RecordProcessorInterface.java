package cassetteflow;

/**
 * A simple interface to process records
 * 
 * @author Nathan
 */
public interface RecordProcessorInterface {
    void setPlayingCassetteID(String cassetteID);
    
    void setPlayingAudioInfo(String info);
    
    void setPlaybackInfo(String info, boolean append, String newLine);
    
    void setPlaybackInfo(final String info, boolean append);
    
    void incrementDCTDecodeOffset();
    
    void processLineRecord(String lineRecord);
}
