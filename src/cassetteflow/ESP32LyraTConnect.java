package cassetteflow;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Class used to connect to the ESP32 Lyra board through http
 * 
 * @author Nathan
 */
public class ESP32LyraTConnect {
    private final OkHttpClient httpClient = new OkHttpClient();
    private String host;
    
    public ESP32LyraTConnect(String host) {
        this.host = host;
    }
    
    public String setModeEncode() {
        try {
            String url = host + "?mode=encode";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String setModeDecode() {
        try {
            String url = host + "?mode=decode";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String getMP3DB() {
        try {
            String url = host + "mp3db";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String getTapeDB() {
        try {
            String url = host + "tapedb";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String getInfo() {
        try {
            String url = host + "info";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String getRawData() {
        try {
            String url = host + "raw";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String create(String side, String tape, String mute, String data) {
        try {
            String url = host + "create?side=" + side + "&tape=" + tape + "&mute=" + mute + "&data=" + data;
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String start() {
        try {
            String url = host + "start?side=A";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String play() {
        try {
            String url = host + "play?side=B";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String stop() {
        try {
            String url = host + "stop";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    /**
     * Method to send a get request to the LyraT http server
     * 
     * @param url
     * @return
     * @throws IOException 
     */
    public String sendGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "OkHttp Bot")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Get response body
            return response.body().string();
        }
    }
    
    // main method for testing
    public static void main(String[] args) throws IOException {
        ESP32LyraTConnect lyraT = new ESP32LyraTConnect("http://localhost:8192/");
        System.out.println(lyraT.setModeEncode());
        System.out.println(lyraT.setModeDecode());
        System.out.println(lyraT.getMP3DB());
        System.out.println(lyraT.getTapeDB());
        System.out.println(lyraT.getInfo());
        System.out.println(lyraT.getRawData());
        System.out.println(lyraT.create("B", "120", "4", "tapeId,mp3id_1,mp3id_2,mp3id_3,mp3id_4"));
        System.out.println(lyraT.start());
        System.out.println(lyraT.play());
        System.out.println(lyraT.stop());
    }
}
