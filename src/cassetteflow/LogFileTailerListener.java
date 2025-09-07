package cassetteflow;

/**
 *
 */
public interface LogFileTailerListener {
    /**
     * A new line has been added to the tailed log file
     *
     * @param line The new line that has been added to the tailed log file
     */
    public void newLineRecord(String line);
}
