/*
 * A connector for the deckcast dj backend using socket io
 * https://notabug.org/aleph/socket.io-p2p-client-java
 */
package cassetteflow;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Nathan
 */
public class DeckCastConnect {
    private final CassetteFlowFrame cassetteFlowFrame;
    private final CassetteFlow cassetteFlow;
    
    private Socket socket;
    private boolean connected = false;
    
    private String serverUrl = "";
    private String streamPin;
    private String streamId = ""; // the video id
    private int totalPlaytime; // the total playtime of video in seconds
    private int player = 1; // is it player one or 2
    
    private String streamTitle = ""; // the title for the stream
    
    // variable used to see if we playing
    private final int RESET_TIME = -99;
    private int currentTapeTime = RESET_TIME;
    private boolean playing = false;
    
    // create a dct play for the que list
    private ArrayList<String> sideADCTList;
    private ArrayList<AudioInfo> queList;
    private boolean queListLoaded = false;
    private String queVideoId = "";
    private String queTrack = "";
    
    public DeckCastConnect(CassetteFlowFrame cassetteFlowFrame, CassetteFlow cassetteFlow, String url, String pin) throws URISyntaxException {
        this.cassetteFlowFrame = cassetteFlowFrame;
        this.cassetteFlow = cassetteFlow;
        this.serverUrl = url;
        this.streamPin = pin;
        
        socket = IO.socket(serverUrl);
                
        // print out the connection event
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                connected = socket.connected();
                String message = "DeckcastDJ Stream Player Connected: " + connected;
                message = message.toUpperCase();
                
                if(cassetteFlowFrame != null) {
                   cassetteFlowFrame.updateStreamEditorPane(message);
                   
                   if(connected) {
                       cassetteFlowFrame.setStreamPlayerConnected();
                   }
                }
            }
        });
        
        // Receiving an object from the server
        socket.on("my response", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    processMessage(obj);
                } catch (JSONException ex) {
                    Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        
        // make a connection to the server
        socket.connect();
    }
    
    /**
     * Process the message from the server
     * 
     * @param obj 
     */
    private void processMessage(JSONObject obj) throws JSONException {
        //System.out.println(obj.toString(1));
        //if(!streamPin.equals(obj.getString("pin"))) return;
        
        if(obj.has("videoInfoLiteHTML")) {
            // see if to return based on pin or video id
            if(!streamPin.equals(obj.getString("pin"))) return;
            if(streamId.equals(obj.getString("videoId"))) return;
            
            streamTitle = obj.getString("videoTitle");
            streamId = obj.getString("videoId");
            player = obj.getInt("player");
            totalPlaytime = obj.getInt("videoTime");

            String infoHtml = obj.getString("videoInfoLiteHTML");
            if (cassetteFlowFrame != null) {
                cassetteFlowFrame.setStreamInformation(streamId, totalPlaytime, player);
                cassetteFlowFrame.updateStreamEditorPane(infoHtml);
            }
            
            // reset the tapeTime variable
            if(queList == null) {
                currentTapeTime = RESET_TIME;
            }
        } else if(obj.has("queListData")) {
            sideADCTList = new ArrayList<>();
            queList = new ArrayList<>();
            
            JSONArray queArray = obj.getJSONArray("queListData");
            for(int i = 0; i < queArray.length(); i++) {
                String[] record = queArray.getString(i).split("\t");
                String videoId = record[0];
                String title = record[1];
                String sha10hex = CassetteFlowUtil.get10CharacterHash(title);
                int length = Integer.parseInt(record[2]);
                String lengthAsTime = CassetteFlowUtil.getTimeString(length);
                
                AudioInfo audioInfo = new AudioInfo(null, sha10hex, length, lengthAsTime, 128);
                audioInfo.setTitle(title);
                audioInfo.setStreamId(videoId);
                queList.add(audioInfo);
                
                // store this in the audio info DB
                cassetteFlow.audioInfoDB.put(sha10hex, audioInfo);
                queListLoaded = true;
            }
            
            // populate the dct list and store a dummy record
            sideADCTList = cassetteFlow.createDCTArrayListForSide("STR0A", queList, 4);
            cassetteFlow.addToTapeDB("STR0", queList, null, false);
            
            //String message = "QueList Loaded: " + queList.size() + " Tracks / " + sideADCTList.size() + " seconds ...";
            String queListHtml = obj.getString("queListHTML");
            cassetteFlowFrame.updateStreamEditorPane(queListHtml);
        }
    }
    
    /**
     * Play the stream on the remote server
     * @param tapeTime 
     */
    public void playStream(int tapeTime) {
        if(currentTapeTime != tapeTime) {
            int diff = Math.abs(tapeTime - currentTapeTime);
            
            // see if to send the play command
            if(diff > 5 && sideADCTList == null) {
                if(tapeTime < totalPlaytime) {
                    playSingleTrack(tapeTime);
                } else {
                    stopStream();
                }
            } else {
                playQuedTracks(tapeTime, diff);
            }
            
            // update the current time
            currentTapeTime = tapeTime;
            
            // if we are playing then update the counter
            if(playing && !queListLoaded) {
                cassetteFlowFrame.updateStreamPlaytime(currentTapeTime);
            }
        }
    } 
    
    /**
     * Play a single track i.e. video
     * 
     * @param tapeTime 
     */
    public void playSingleTrack(int tapeTime) {
        try {
            System.out.println("Starting Stream Playback ...");

            JSONObject obj = new JSONObject();
            obj.put("data", "Player " + player + " State Changed");
            obj.put("player", player);
            obj.put("state", 1);
            obj.put("ctime", tapeTime);
            socket.emit("my event", obj);

            playing = true;
        } catch (JSONException ex) {
            Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Play the Qued tracks on the server
     * @param diff
     * @param tapeTime 
     */
    public void playQuedTracks(int tapeTime, int diff) {
        try {
            if(tapeTime < sideADCTList.size()) {
                String dctLine = sideADCTList.get(tapeTime);
                String[] line = dctLine.split("_");
                String tapeID = line[0];
                String track = line[1];
                String trackId = line[2];
                String playTimeS = line[3];
                
                // get the new start time
                int startTime;
                try {
                    startTime = Integer.parseInt(playTimeS);
                } catch(NumberFormatException nfe) {
                    if(playTimeS.contains("M")) {
                        System.out.println("Mute section ...");
                        playing = false;
                    } else {
                        System.out.println("Start Time Error ... " + dctLine);
                    }
                    
                    return;
                }
                
                if(!trackId.equals(queVideoId)) {
                    AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(trackId);
                    String videoId = audioInfo.getStreamId();
                    System.out.println("\nPlaying Track: " + track + " -- " + audioInfo);

                    queTrack = track;
                    queVideoId = trackId;

                    // send message to change the video and start it playing
                    JSONObject obj = new JSONObject();
                    obj.put("data", "Video Changed -- Player " + player);
                    obj.put("uname", "Guest");
                    obj.put("player", player);
                    obj.put("videoId", videoId);
                    socket.emit("my event", obj);
                    System.out.println(obj.toString(2));

                    Thread.sleep(1000);

                    if (startTime > 0) {
                        obj = new JSONObject();
                        obj.put("data", "Player " + player + " State Changed");
                        obj.put("player", player);
                        obj.put("state", 1);
                        obj.put("ctime", startTime);

                        socket.emit("my event", obj);
                        System.out.println(obj.toString(2));
                    }

                    playing = true;
                } else if(diff > 5) {
                    System.out.println("\nChanging Track Start Time " + startTime);
                    
                    JSONObject obj = new JSONObject();
                        obj.put("data", "Player " + player + " State Changed");
                        obj.put("player", player);
                        obj.put("state", 1);
                        obj.put("ctime", startTime);
                        socket.emit("my event", obj);
                }
                
                cassetteFlowFrame.updateStreamPlaytime(startTime);
            } else {
                playing = false;
            }
        } catch (JSONException | InterruptedException ex) {
            Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Stop play back of the stream
     */
    public void stopStream() {
        try {
            if(playing) {
                JSONObject obj = new JSONObject();
                obj.put("data", "Player " + player + " State Changed");
                obj.put("player", player);
                obj.put("state", 2);
                obj.put("ctime", currentTapeTime);
                socket.emit("my event", obj);

                playing = false;
                cassetteFlowFrame.updateStreamPlaytime(0);

                System.out.println("Stop Stream Playback");
                
                // reset the que video id
                if(queList == null) {
                    currentTapeTime = RESET_TIME;
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Disconnect the socket connection
     */
    public void disConnect() {
        if(connected) {
            if(playing) {
                stopStream();
            }
            
            sideADCTList = null;
            queList = null;
            connected = false;
            socket.disconnect();
        }
    }
    
    /**
     * Get the current track title
     * @return 
     */
    public String getTitle() {
        return streamTitle;
    }
    
    /**
     * Return the URL of the server
     * @return 
     */
    public String getServerUrl() {
        return serverUrl;
    }
    
    /**
     * Clear the que array list and dct array list
     */
    public void clearQueList() {
        sideADCTList = null;
        queList = null;
        queVideoId = "";
        queListLoaded = false;
    }
    
    /**
     * Get if quelist are loaded
     * 
     * @return 
     */
    public boolean isQueListLoaded() {
        return queListLoaded;
    }
    
    // test the functionality
    public static void main(String[] args) {
        try {
            DeckCastConnect dcc = new DeckCastConnect(null, null, "http://127.0.0.1:5054/", "0001");
            
            // wait for user to press key to exit program
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            in.readLine();
        } catch (URISyntaxException | IOException ex) {
            Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
