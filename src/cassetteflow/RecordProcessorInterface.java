package cassetteflow;

/**
 * A simple interface to process records
 * 
 * @author Nathan
 */
public interface RecordProcessorInterface {
    void setPlayingCassetteID(String cassetteID);
    
    void setPlayingMP3Info(String info);
    
    void setPlaybackInfo(String info, boolean append, String newLine);
    
    void setPlaybackInfo(final String info, boolean append);
    
    void setStopRecords(int stopRecords, int playTime);
    
    void processLineRecord(String lineRecord);
}
