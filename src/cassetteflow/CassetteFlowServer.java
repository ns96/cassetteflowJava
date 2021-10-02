
package cassetteflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is class implements a simple http server for testing the ESP32LyraT Client
 * Code
 * 
 * @author Nathan
 */
public class CassetteFlowServer {
    private final int ENCODE = 0;
    private final int DECODE = 1;
    private int currentMode = ENCODE;
    
    /**
     * Main constructor which starts the server
     * @throws IOException 
     */
    public CassetteFlowServer() throws IOException {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)Executors.newFixedThreadPool(10);
        
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8192), 0);
        
        server.createContext("/", new SetModeHandler());
        server.createContext("/mp3db", new getMp3DBHandler());
        server.createContext("/tapedb", new getTapeDBHandler());
        server.createContext("/info", new getInfoHandler());
        server.createContext("/raw", new getRawHandler());
        server.createContext("/create", new createHandler());
        server.createContext("/start", new startHandler());
        server.createContext("/play", new playHandler());
        server.createContext("/stop", new stopHandler());
        
        server.setExecutor(threadPoolExecutor);
        server.start();
        
        System.out.println("Cassette Flow Server Started ...");
    }
    
    /**
     * Get all the params from a url
     * 
     * @param url
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
    
    public synchronized void sendResponse(HttpExchange he, String response) {
        try {
            he.sendResponseHeaders(200, response.length());
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes());
            os.flush();
            os.close();
        } catch (IOException ex) {
            Logger.getLogger(CassetteFlowServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    // class to handle setting the mode either decode or encode
    private class SetModeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Setting the mode: " + params;
            
            sendResponse(he, response);
        }
    }
    
    // class to handle getting the mp3 database
    private class getMp3DBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String response = "Getting the mp3 database";
            sendResponse(he, response);
        }
    }
    
    // class to handle getting the mp3 database
    private class getTapeDBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String response = "Getting the tape database";
            sendResponse(he, response);
        }
    }
    
    // class to handle getting information
    private class getInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String response = "Getting current info";
            sendResponse(he, response);
        }
    }
    
    // class to handle getting information
    private class getRawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String response = "Getting raw data ...";
            sendResponse(he, response);
        }
    }
    
    // class to handle creating the input files for encoding
    private class createHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Creating Input File: " + params;
            
            sendResponse(he, response);
        }
    }
        
    // class to start the encoding process
    private class startHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Starting encoding of input file: " + params;
            
            sendResponse(he, response);
        }
    }
    
    // class to handle playing the mp3 indicated in the input file
    private class playHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Playing mp3s from input file: " + params;
            
            sendResponse(he, response);
        }
    }
    
    // class to handle getting information
    private class stopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            String response = "Stopping Encoding or Playing ...";
            sendResponse(he, response);
        }
    }
    
    public static void main(String[] args) {
        try {
            CassetteFlowServer cfs = new CassetteFlowServer();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
}
