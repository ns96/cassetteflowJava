package cassetteflow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.enums.AuthorizationScope;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.special.PlaybackQueue;
import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.albums.GetAlbumRequest;
import se.michaelthelin.spotify.requests.data.player.GetTheUsersQueueRequest;
import se.michaelthelin.spotify.requests.data.player.SeekToPositionInCurrentlyPlayingTrackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistRequest;

/**
 * A class to connect to the Spotify backend to get tract information and control playback
 * 
 * @author Nathan
 */
public class SpotifyConnector {
    private HttpServer server;
    private CassetteFlowFrame cassetteFlowFrame;
    private CassetteFlow cassetteFlow;
    
    private String code = "";
    private String accessToken = "";
    private String refreshToken = "";

    private static final String clientId = SpotifyAppInfo.clientId;
    private static final String clientSecret = SpotifyAppInfo.clientSecret;
    private static final URI redirectUri = SpotifyHttpManager.makeUri(SpotifyAppInfo.redirectUri);

    private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(redirectUri)
            .build();

    private static final AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi.authorizationCodeUri()
            .scope(AuthorizationScope.STREAMING, 
                    AuthorizationScope.USER_MODIFY_PLAYBACK_STATE,
                    AuthorizationScope.USER_READ_PLAYBACK_STATE,
                    AuthorizationScope.USER_READ_CURRENTLY_PLAYING)
            .build();
    
    private boolean connected = false;
    
    private String streamId = ""; // the spotify id
    private int totalPlaytime; // the total playtime of track in seconds
    private String streamTitle = ""; // the title for the stream
    
    // variable used to see if we playing
    private final int RESET_TIME = -99;
    private int currentTapeTime = RESET_TIME;
    private boolean playing = false;
    
    // the current audio info
    private AudioInfo currentAudioInfo;
    
    // create a dct play for the que list
    private ArrayList<String> sideADCTList;
    private ArrayList<AudioInfo> queList;
    private boolean queListLoaded = false;
    private String queTrackId = "";
    private String oldQueTrackId = "";
    private String queTrack = "";
    private String queListHtml = "";
    
    // keep track of data errors
    private int dataErrors = 0;
    private int logLineCount = 0;
    private int muteRecords = 0;
    
    /**
     * The default constructor
     */
    public SpotifyConnector() {
        try {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 3000), 0);
            server.createContext("/", new SetCodeHandler());
            server.setExecutor(threadPoolExecutor);
            server.start();

            System.out.println("Spotify Connector Server Started ...");
        } catch (IOException ioe) {
            System.out.println("Error Starting Spotify Connector Server ...");
        }
    }
    
    /**
     * The constructor that take the cassette flow objects
     * 
     * @param cassetteFlowFrame
     * @param cassetteFlow
     */
    public SpotifyConnector(CassetteFlowFrame cassetteFlowFrame, CassetteFlow cassetteFlow) {
        this();
        this.cassetteFlowFrame = cassetteFlowFrame;
        this.cassetteFlow = cassetteFlow;
    }
    
    /**
     * Get the URI code for authorization
     * @return 
     */
    public URI getAuthorizationCodeUri() {
        URI uri = authorizationCodeUriRequest.execute();
        System.out.println("URI: " + uri.toString());
        return uri;
    }
        
    /**
     * Stop the http server
     */
    public void stop() {
        server.stop(0);
    }
    
    /**
     * Get all the params from a url
     * 
     * @param query
     * @return
     * @throws UnsupportedEncodingException 
     */
    public Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        
        return queryPairs;
    }
    
    /**
     * Send the response to the client
     * 
     * @param he
     * @param response 
     */
    public synchronized void sendResponse(HttpExchange he, String response) {
        try {
            byte[] data = response.getBytes("UTF-8");
            he.sendResponseHeaders(200, data.length);
            OutputStream os = he.getResponseBody();
            os.write(data);
            os.flush();
            os.close();
        } catch (IOException ex) {
            Logger.getLogger(CassetteFlowServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Stop music playback the server which listens spotify connections 
     */
    void disConnect() {
        stop();
    }
    
    // class to handle setting the code from server
    private class SetCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Access Code: " + params;
            
            // get the access code
            code = params.get("code").toString();
            System.out.println("Access Code Set ...");
            
            try {
                AuthorizationCodeRequest authorizationCodeRequest = spotifyApi.authorizationCode(code)
                        .build();

                AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeRequest.execute();

                // Set access and refresh token for further "spotifyApi" object usage
                accessToken = authorizationCodeCredentials.getAccessToken();
                refreshToken = authorizationCodeCredentials.getRefreshToken();
                spotifyApi.setAccessToken(accessToken);
                spotifyApi.setRefreshToken(refreshToken);

                System.out.println("Access Expires in: " + authorizationCodeCredentials.getExpiresIn());
                
                // start thread to automatically renew the access token
                startRenewThread();
                
                // indicate we are connected
                connected = true;
                
                String message = "Spotify Player Connected ...";
                message = message.toUpperCase();
                
                if(cassetteFlowFrame != null) {
                    cassetteFlowFrame.updateStreamEditorPane(message);
                    cassetteFlowFrame.setStreamPlayerConnected();
                }
            } catch (SpotifyWebApiException | ParseException e) {
                System.out.println("Error ");
            }
            
            sendResponse(he, response);
        }
        
        /**
         * start thread that renews the login information
         */
        private void startRenewThread() {
            System.out.println("Starting Renew Thread ...");
            
            TimerTask renewTask = new TimerTask() {
                private final AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh()
                        .build();
                
                @Override
                public void run() {
                    try {
                        AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeRefreshRequest.execute();

                        // Set access and refresh token for further "spotifyApi" object usage
                        accessToken = authorizationCodeCredentials.getAccessToken();
                        spotifyApi.setAccessToken(accessToken);

                        System.out.println("Renew Expires in: " + authorizationCodeCredentials.getExpiresIn());
                    } catch (IOException | SpotifyWebApiException | ParseException e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }
            };
            
            // run the new task after 45 minutes
            Timer timer = new Timer("Renew Timer");
            timer.scheduleAtFixedRate(renewTask, 2700000, 2700000);
        }
    }
    
    /**
     * Get the items in the playlist or album
     * 
     * @param playlistId 
     */
    public void loadPlaylist(String playlistId) {
        GetPlaylistRequest getPlaylistRequest = spotifyApi.getPlaylist(playlistId)
                .build();
        
        try {
            queList = new ArrayList<>();
            
            Playlist playlist = getPlaylistRequest.execute();
            streamTitle = playlist.getName();
            
            Paging<PlaylistTrack> playlistTrackPaging = playlist.getTracks();
            System.out.println("Total Playlist Tracks: " + playlistTrackPaging.getTotal());
            
            for(PlaylistTrack item: playlistTrackPaging.getItems()) {
                Track track = (Track)item.getTrack();
                String trackId = track.getId();
                String title = track.getName();
                String sha10hex = CassetteFlowUtil.get10CharacterHash(title);
                int length = track.getDurationMs()/1000; // length in seconds
                String lengthAsTime = CassetteFlowUtil.getTimeString(length);
                String url = "[\"" + track.getUri() + "\"]";
                AudioInfo audioInfo = new AudioInfo(null, sha10hex, length, lengthAsTime, 128);
                audioInfo.setTitle(title);
                audioInfo.setStreamId(trackId);
                audioInfo.setUrl(url);
                queList.add(audioInfo);
                
                // store this in the audio info DB
                cassetteFlow.audioInfoDB.put(sha10hex, audioInfo);
                queListLoaded = true;
            }
            
            storeAudioInfoRecords();
            
            // 4/23/2023 DEBUG code
            AudioInfo test = queList.get(2);
            queTrackId = "new";
            oldQueTrackId = "old";
            playTrack(test.getStreamId(), test.getUrl(), 60);
        } catch (IOException | SpotifyWebApiException | ParseException ex) {
            System.out.println("Error loading playlist, might be an album?");
            loadAlbum(playlistId);
        }
    }
    
    /**
     * Get the items in the album
     * 
     * @param albumId 
     */
    public void loadAlbum(String albumId) {
         GetAlbumRequest getAlbumRequest = spotifyApi.getAlbum(albumId)
                 .build();
                
        try {
            queList = new ArrayList<>();
            
            Album album = getAlbumRequest.execute();
            streamTitle = album.getName();
            
            Paging<TrackSimplified> trackPaging = album.getTracks();
            System.out.println("Total Album Tracks: " + trackPaging.getTotal());
            
            for(TrackSimplified track: trackPaging.getItems()) {
                String trackId = track.getId();
                String title = track.getName();
                String sha10hex = CassetteFlowUtil.get10CharacterHash(title);
                int length = track.getDurationMs()/1000; // length in seconds
                String lengthAsTime = CassetteFlowUtil.getTimeString(length);
                String url = "[\"" + track.getUri() + "\"]";
                AudioInfo audioInfo = new AudioInfo(null, sha10hex, length, lengthAsTime, 128);
                audioInfo.setTitle(title);
                audioInfo.setStreamId(trackId);
                audioInfo.setUrl(url);
                queList.add(audioInfo);
                
                // store this in the audio info DB
                cassetteFlow.audioInfoDB.put(sha10hex, audioInfo);
                queListLoaded = true;
            }
            
            storeAudioInfoRecords();
        } catch (IOException | SpotifyWebApiException | ParseException ex) {
            Logger.getLogger(SpotifyConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    /**
     * Load the users playback queue. 4/20/2022 -- This endpoint doesn't work correctly.
     * It does return all the items in the que. See link below
     * https://community.spotify.com/t5/Spotify-for-Developers/Get-User-Queue-Doesn-t-Return-Full-Queue/td-p/5435038
     */
    public void loadPlaybackQue() {
        GetTheUsersQueueRequest usersQueueRequest = spotifyApi.getTheUsersQueue().build();
        try {
            PlaybackQueue playbackQue = usersQueueRequest.execute();
            System.out.println("PlayBack Que: " + playbackQue.getCurrentlyPlaying());
            
            for(IPlaylistItem item: playbackQue.getQueue()) {
                System.out.println(item.getName() + " : " + item.getDurationMs());
            }
            
        } catch (IOException | SpotifyWebApiException | ParseException ex) {
            Logger.getLogger(SpotifyConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Store the dummy audio info records
     */
    private void storeAudioInfoRecords() {
        // populate the dct list and store a dummy record
        sideADCTList = cassetteFlow.createDCTArrayListForSide("STR0A", queList, 4);
        cassetteFlow.addToTapeDB("STR0", queList, null, false);

        String message = "Spotify Playlist/Album Loaded (" + streamTitle + "): " + queList.size() + " Tracks / " + sideADCTList.size() + " seconds ...";
        queListHtml = message; // generate qlist html here
        cassetteFlowFrame.updateStreamEditorPane(queListHtml);
        cassetteFlowFrame.setPlayingCassetteID("STR0A");
    } 
    
    /**
     * Play the tracks through spotify app
     * @param tapeTime 
     */
    public void playStream(int tapeTime) {
        if (currentTapeTime == tapeTime) {
            return;
        }

        if (tapeTime < sideADCTList.size()) {
            String dctLine = sideADCTList.get(tapeTime);
            String[] line = dctLine.split("_");
            String tapeID = line[0];
            String track = line[1];
            String trackId = line[2];
            String playTimeS = line[3];
            String playTimeTotalS = line[4];

            // get the new start time
            int playTime;
            int playTimeTotal;
            try {
                playTime = Integer.parseInt(playTimeS);
                playTimeTotal = Integer.parseInt(playTimeTotalS);
            } catch (NumberFormatException nfe) {
                if (playTimeS.contains("M")) {
                    if (muteRecords == 0) {
                        System.out.println("\nMute section");
                        cassetteFlowFrame.setPlaybackInfo("Mute Section ...", false);
                    } else {
                        System.out.println("Mute section ...");
                        cassetteFlowFrame.setPlaybackInfo("Mute Section ...", true);
                    }

                    muteRecords++;
                    playing = false;
                } else {
                    System.out.println("Start Time Error ... " + dctLine);
                }

                return;
            }

            if (!trackId.equals(queTrackId)) {
                muteRecords = 0;
                currentAudioInfo = cassetteFlow.audioInfoDB.get(trackId);
                String url = currentAudioInfo.getUrl();
                System.out.println("\nPlaying Track: " + track + " -- " + currentAudioInfo);

                queTrack = track;
                queTrackId = trackId;

                playTrack(trackId, url, playTime);

                // update the decode UI with the track thats playing
                String message = "Stream ID: " + currentAudioInfo.getStreamId() + "\n"
                        + currentAudioInfo.getName() + "\n"
                        + "Start Time @ " + playTime + " | Track Number: " + track;

                cassetteFlowFrame.setPlayingAudioInfo(message);
                cassetteFlowFrame.setPlayingAudioTrack(track);

                playing = true;
            }

            cassetteFlowFrame.updateStreamPlaytime(playTime, "[ " + track + " ]");

            // update the UI indicating playtime
            String message = currentAudioInfo.getName() + " [" + track + "]\n"
                    + "Playtime From Tape: " + String.format("%04d", playTime) + " / " + String.format("%04d", currentAudioInfo.getLength()) + "\n"
                    + "Tape Counter: " + playTimeTotal + " (" + CassetteFlowUtil.getTimeString(playTimeTotal) + ")\n"
                    + "Data Errors: " + dataErrors + "/" + logLineCount;

            cassetteFlowFrame.setPlaybackInfo(message, false, "");
        } else {
            playing = false;
        }

        //update the current time
        currentTapeTime = tapeTime;
    }

    /**
     * Plat the particular track
     *
     * @param trackId
     * @param url
     * @param playTime
     */
    public void playTrack(String trackId, String url, int playTime) {
        // send message to change the video and start it playing if we change video
        try {
            if (!queTrackId.equals(oldQueTrackId)) {
                oldQueTrackId = queTrackId;
  
                JsonArray trackObject = JsonParser.parseString(url).getAsJsonArray();
                StartResumeUsersPlaybackRequest startResumeUsersPlaybackRequest = spotifyApi.startResumeUsersPlayback()
                        .uris(trackObject)
                        .position_ms(0)
                        .build();
                startResumeUsersPlaybackRequest.execute();

                System.out.println("Changing to new track ...");
                Thread.sleep(1000);
            }

            if (playTime > 0) { // playing the same track, but we need to go to new location
                int positionMs = playTime*1000;
                SeekToPositionInCurrentlyPlayingTrackRequest seekToPositionInCurrentlyPlayingTrackRequest =
                        spotifyApi.seekToPositionInCurrentlyPlayingTrack(positionMs)
                        .build();
                seekToPositionInCurrentlyPlayingTrackRequest.execute();
                
                System.out.println("Playing song at new time location ...");
            }
        } catch (SpotifyWebApiException | ParseException | InterruptedException | IOException ex) {
            Logger.getLogger(SpotifyConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    } 
    
    /**
     * Stop play back of the stream
     */
    public void stopStream() {
        try {
            if(playing) {
                // TODO Add spotify code to stop playing the stream 

                playing = false;
                cassetteFlowFrame.updateStreamPlaytime(0, "");
                cassetteFlowFrame.setPlaybackInfo("", false);
                
                System.out.println("Stopped Spotify Stream Playback ...");
                
                // reset the que track id
                queTrackId = "";
            }
        } catch (Exception ex) {
            Logger.getLogger(DeckCastConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Method to run micro-server independently
     * 
     * @param args 
     */
    public static void main(String[] args) {
        SpotifyConnector spotifyConnector = new SpotifyConnector();            
        spotifyConnector.getAuthorizationCodeUri();
    }
}
