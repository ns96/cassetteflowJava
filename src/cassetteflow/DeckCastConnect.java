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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Nathan
 */
public class DeckCastConnect {
    private final CassetteFlowFrame cassetteFlowFrame;
    private Socket socket;
    private boolean connected = false;
    
    private String serverUrl = "";
    private String streamPin;
    private String streamId = ""; // the video id
    private int totalPlaytime; // the total playtime of video in seconds
    private int player; // is it player one or 2
    
    // variable used to see if we playing
    private final int RESET_TIME = -99;
    private int currentTapeTime = RESET_TIME;
    private boolean playing = false;
    
    public DeckCastConnect(CassetteFlowFrame cassetteFlowFrame, String url, String pin) throws URISyntaxException {
        this.cassetteFlowFrame = cassetteFlowFrame;
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
                }
                
                // TO-DO if connected disable the stream CONNECT button 
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
        System.out.println(obj.toString(1));
        
        if(obj.has("videoInfoLiteHTML")) {
            // see if to return based on pin or video id
            if(!streamPin.equals(obj.getString("pin"))) return;
            if(streamId.equals(obj.getString("videoId"))) return;
            
            streamId = obj.getString("videoId");
            player = obj.getInt("player");
            totalPlaytime = obj.getInt("videoTime");

            String infoHtml = obj.getString("videoInfoLiteHTML");
            if (cassetteFlowFrame != null) {
                cassetteFlowFrame.setStreamInformation(streamId, totalPlaytime, player);
                cassetteFlowFrame.updateStreamEditorPane(infoHtml);
            }
            
            // reset the tapeTime variable
            currentTapeTime = RESET_TIME;
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
            if(diff > 5) {
                if(tapeTime < totalPlaytime) {
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
                } else {
                    stopStream();
                }
            }
            
            // update the current time
            currentTapeTime = tapeTime;
            
            // if we are playing then update the counter
            if(playing) {
                cassetteFlowFrame.updateStreamPlaytime(currentTapeTime);
            }
            
            System.out.println("Tape Playtime: " + tapeTime);
        }
    } 
    
    /**
     * Stop play back of the stream
     */
    public void stopStream() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("data", "Player " + player + " State Changed");
            obj.put("player", player);
            obj.put("state", 2);
            obj.put("ctime", currentTapeTime);
            socket.emit("my event", obj);
            
            currentTapeTime = RESET_TIME;
            playing = false;
            cassetteFlowFrame.updateStreamPlaytime(0);
            
            System.out.println("Stop Stream Playback");
        } catch (JSONException ex) {
            Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Disconnect the socket connection
     */
    public void disConnect() {
        if(connected) {
            connected = false;
            socket.disconnect();
        }
    }
    
    // test the functionality
    public static void main(String[] args) {
        try {
            DeckCastConnect dcc = new DeckCastConnect(null, "http://127.0.0.1:5054/", "0001");
            
            // wait for user to press key to exit program
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            in.readLine();
        } catch (URISyntaxException | IOException ex) {
            Logger.getLogger(DeckCastConnect.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
