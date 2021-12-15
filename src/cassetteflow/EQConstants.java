package cassetteflow;

/**
 * Defines constants for EQ levels based on information taken from
 * https://descriptive.audio/best-equalizer-settings/
 * 
 * @author Nathan
 */
public final class EQConstants {
    private EQConstants() {}
    /*
    static final String FLAT = "0,0,0,0,0,0,0,0,0,0";
    static final String ACOUSTIC = "5,5,4,1,2,2,4,5,4,2"; 
    static final String ELECTRONIC = "5,4,1,0,-2,2,1,1,4,5"; 
    static final String WORLD = "4,3,0,0,-1,-1,-1,0,3,5";
    static final String CLASSICAL = "3,2,0,2,3,1,3,5,3,4";
    static final String POP = "-2,-1,0,2,4,4,2,0,-1,-1";
    static final String ROCK = "5,4,3,1,-1,-1,0,2,3,4";
    static final String BASS_BOOST = "5,4,3,2,1,0,0,0,0,0";
    */
    
    
    /**
     * Above values multiply by 1.5 and rounded up
     * 0 -> 0
     * 1 -> 2
     * 2 -> 3
     * 3 -> 5
     * 4 -> 6
     * 5 -> 8
     * 6 -> 9
     */
    static final String FLAT = "0,0,0,0,0,0,0,0,0,0";
    static final String ACOUSTIC = "8,8,6,2,3,3,6,8,6,3"; 
    static final String ELECTRONIC = "8,6,2,0,-3,3,2,2,6,8"; 
    static final String WORLD = "6,5,0,0,-2,-2,-2,0,5,8";
    static final String CLASSICAL = "5,3,0,3,5,2,5,8,5,6";
    static final String POP = "-3,-2,0,3,6,6,3,0,-2,-2";
    static final String ROCK = "8,6,5,2,-2,-2,0,3,5,6";
    static final String BASS_BOOST = "8,5,5,3,2,0,0,0,0,0";     
}
