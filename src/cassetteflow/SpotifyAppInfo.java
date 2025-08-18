package cassetteflow;

/**
 * Store information specify to Spotify app to allow easy hiding. 
 * This should note be committed with client secrete in there;
 * @author Nathan
 */
public final class SpotifyAppInfo {
    static final String CLIENT_ID = "dc143ecbc3aa42a5aa6505be98169dfd";
    static final String CLIENT_SECRET = "17615fac4ba04ac9b9b0a1188034a6fe";
    static final String REDIRECT_URI = "http://localhost:3000";
    
    private SpotifyAppInfo() {}  
}
