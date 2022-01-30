package cassetteflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Class used to connect to the ESP32 Lyra board through http
 * 
 * @author Nathan
 */
public class ESP32LyraTConnect {
    private final OkHttpClient httpClient;
    private String host;
    
    // used to stop getting raw data
    private boolean readRawData;
    
    public ESP32LyraTConnect(String host) {
        this.host = host;
        
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Create input files 
     * @param tapeID
     * @param sideA
     * @param sideB
     * @param muteTime
     * @param tapeLength
     * @return 
     */
    public String createInputFiles(String tapeLength, String tapeID, ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB, String muteTime) {  
        String response = "";
        
        if (sideA != null && sideA.size() >= 1) {
            String data = tapeID + getData(sideA);
            String r = create("A", tapeLength, muteTime, data);
            
            if(r == null) {
                return "ERROR";
            } else {
                response = r + "\n"; 
            }
        }

        if (sideB != null && sideB.size() >= 1) {
            String data = tapeID + getData(sideB);
            String r = create("B", tapeLength, muteTime, data);
            
            if(r == null) {
                return "ERROR";
            } else {
                response += r; 
            }
        }
        
        return response;
    }
    
    /**
     * Return the mp3 files as comma seperate string
     * @param sideN
     * @return 
     */
    private String getData(ArrayList<AudioInfo> sideN) {
        String data = "";
        
        for(AudioInfo mp3Info: sideN) {
            data += "," + mp3Info.getHash10C();
        }
        
        return data;
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
    
        public String setModePass() {
        try {
            String url = host + "?mode=pass";
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String getAudioDB() {
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
    
    /**
     * Use a URL connection instead of the OKHTTP client since it was just too 
     * hard to figure out how to continuously read from a HTTP client
     * 
     * @param recordProcessor
     */
    public void getRawData(RecordProcessorInterface recordProcessor) {
        Thread thread = new Thread("LyraT Raw Data Thread") {
            @Override
            public void run() {
                try {
                    readRawData = true;

                    URL url = new URL(host + "raw");
                    URLConnection yc = url.openConnection();
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            yc.getInputStream()));

                    String inputLine;
                    while ((inputLine = in.readLine()) != null && readRawData) {
                        if (recordProcessor != null) {
                            recordProcessor.processLineRecord(inputLine);
                        } else {
                            System.out.println(inputLine);
                        }
                    }

                    in.close();
                } catch (IOException ex) {
                    Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        thread.start();
    }
    
    /**
     * Stop read raw data
     */
    public void stopRawDataRead() {
        readRawData = false;
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
    
    public String start(String side) {
        try {
            String url = host + "start?side=" + side;
            return sendGet(url);
        } catch (IOException ex) {
            Logger.getLogger(ESP32LyraTConnect.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public String play(String side) {
        try {
            String url = host + "play?side=" + side;
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
    
    public String setEQ(String band) {
        try {
            String url = host + "eq?band=" + band;
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
        //System.out.println("LyraT GET Request: " + url);
        
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
    
    /**
     * Test the connection and HTTP API of cassetteFlow server
     * 
     * @return Results of the test
     * @throws java.lang.Exception
     */
    public String runTest() throws Exception {
        StringBuilder sb = new StringBuilder();
        String response;
        String message;
        
        response = setModeEncode();
        if(response != null) {
            message = "PASS -- Encode Mode: " + response;
        } else {
            message = "FAIL -- Encode Mode";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = setModeDecode();
        if(response != null) {
            message = "PASS -- Decode Mode: " + response;
        } else {
            message = "FAIL -- Decode Mode";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = setModePass();
        if(response != null) {
            message = "PASS -- Pass Through Mode: " + response;
        } else {
            message = "FAIL -- Pass Through Mode";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = getAudioDB();
        if(response != null) {
            message = "PASS -- Get MP3 Database:\n" + response;
        } else {
            message = "FAIL -- Get MP3 Database";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = getTapeDB();
        if(response != null) {
            message = "PASS -- Get Tape Database:\n" + response;
        } else {
            message = "FAIL -- Get Tape Database";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = getInfo();
        if(response != null) {
            message = "PASS -- Get Info: " + response;
        } else {
            message = "FAIL -- Get Info";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        /*
        response = getRawData();
        if(response != null) {
            message = "PASS -- Get Line Record: " + response;
        } else {
            message = "FAIL -- Get Line Record";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        */
        
        response = create("N", "120", "4", "tapeId,mp3id_1,mp3id_2,mp3id_3,mp3id_4");
        if(response != null) {
            message = "PASS -- Create: " + response;
        } else {
            message = "FAIL -- Creat";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = start("N");
        if(response != null) {
            message = "PASS -- Start Encode: " + response;
        } else {
            message = "FAIL -- Start Encode";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = play("N");
        if(response != null) {
            message = "PASS -- Play Side: " + response;
        } else {
            message = "FAIL -- Play Side";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        response = stop();
        if(response != null) {
            message = "PASS -- Stop Encode/Play: " + response;
        } else {
            message = "FAIL -- Stop Encode/Play";
        }
        sb.append(message).append("\n\n");
        System.out.println(message);
        
        return sb.toString();
    }
    
    // main method for testing
    public static void main(String[] args) throws Exception {
        //ESP32LyraTConnect lyraT = new ESP32LyraTConnect("http://localhost:8192/");
        ESP32LyraTConnect lyraT = new ESP32LyraTConnect("http://192.168.1.205/");
        lyraT.getRawData(null);
        //lyraT.runTest();
    }
}
