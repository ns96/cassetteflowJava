package cassetteflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.org.apache.xerces.internal.xs.ItemPSVI;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.core5.http.ParseException;
import org.json.JSONException;
import org.json.JSONObject;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.enums.AuthorizationScope;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.special.PlaybackQueue;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.albums.GetAlbumsTracksRequest;
import se.michaelthelin.spotify.requests.data.player.GetTheUsersQueueRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest;

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
        GetPlaylistsItemsRequest getPlaylistsItemsRequest = spotifyApi.getPlaylistsItems(playlistId)
            .limit(50)
            .build();
        
        try {
            Paging<PlaylistTrack> playlistTrackPaging = getPlaylistsItemsRequest.execute();
            System.out.println("Total Playlist Tracks: " + playlistTrackPaging.getTotal());
            
            for(PlaylistTrack item: playlistTrackPaging.getItems()) {
                Track track = (Track)item.getTrack();
                System.out.println(track.getName() + " : " + track.getDurationMs());
            }
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
        GetAlbumsTracksRequest getAlbumsTracksRequest = spotifyApi.getAlbumsTracks(albumId)
            .limit(50)
            .build();
        
        try {
            Paging<TrackSimplified> trackPaging = getAlbumsTracksRequest.execute();
            System.out.println("Total Album Tracks: " + trackPaging.getTotal());
            
            for(TrackSimplified track: trackPaging.getItems()) {
                System.out.println(track.getName() + " : " + track.getDurationMs());
            }
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
     * Method to run micro-server independently
     * 
     * @param args 
     */
    public static void main(String[] args) {
        SpotifyConnector spotifyConnector = new SpotifyConnector();            
        spotifyConnector.getAuthorizationCodeUri();
    }
}
