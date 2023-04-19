package cassetteflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

/**
 * A class to connect to the Spotify backend to get tract information and control playback
 * 
 * @author Nathan
 */
public class SpotifyConnector {
    private HttpServer server;
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
            .build();
    
    /**
     * The default constructor
     * 
     * @throws IOException 
     */
    public SpotifyConnector() throws IOException {
        
        
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)Executors.newFixedThreadPool(10);
        
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 3000), 0);
        server.createContext("/", new SetCodeHandler());
        server.setExecutor(threadPoolExecutor);
        server.start();
        
        System.out.println("Spotify Connector Server Started ...");
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
     * Set the CassetteFlow object
     * 
     * @param cassetteFlow 
     */
    public void setCassetteFlow(CassetteFlow cassetteFlow) {
        this.cassetteFlow = cassetteFlow;
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
    
    // class to handle setting the code from server
    private class SetCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Access Code: " + params;
            
            // get the access code
            code = params.get("code").toString();
            System.out.println("Access Code Set");
            
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
     * Method to run micro-server independently
     * 
     * @param args 
     */
    public static void main(String[] args) {
        try {
            //CassetteFlow cf = new CassetteFlow();
            SpotifyConnector spotifyConnector = new SpotifyConnector();
            //spotifyConnector.setCassetteFlow(cf);
            
            spotifyConnector.getAuthorizationCodeUri();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
