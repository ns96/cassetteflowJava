
package cassetteflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
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
    private final int PASS = 2;
    
    private int currentMode = DECODE;
    
    private HttpServer server;
    
    private CassetteFlow cassetteFlow;
    
    // the tape ID for the input files to be created
    private String tapeID;
    
    // array list for storing the mp3 ides associated with create request
    private ArrayList<String> sideAList;
    private ArrayList<String> sideBList;
    
    // the mute time between tracks
    private int mute = 0;
    
    private boolean readRawData = false;
    
    /**
     * Main constructor which starts the server
     * @throws IOException 
     */
    public CassetteFlowServer() throws IOException {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)Executors.newFixedThreadPool(10);
        
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8192), 0);
        
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
     * Set the cassetteflow object
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
    
    // class to handle setting the mode either decode or encode
    private class SetModeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String response = "Set Mode: " + params;
            
            sendResponse(he, response);
        }
    }
    
    // class to handle getting the mp3 database
    private class getMp3DBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            String response = cassetteFlow.getAudioInfoDBAsString();
            sendResponse(he, response);
        }
    }
    
    // class to handle getting the mp3 database
    private class getTapeDBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            String response = cassetteFlow.getTapeDBAsString();
            sendResponse(he, response);
        }
    }
    
    // class to handle getting information
    private class getInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            String response;
            
            switch (currentMode) {
                case DECODE:
                    response = "DECODE " + cassetteFlow.getCurrentLineRecord();
                    break;
                case ENCODE:
                    response = "ENCODE " + cassetteFlow.currentTapeID + " " + cassetteFlow.currentTimeTotal;
                    break;
                default:
                    response = "PASS THROUGH," + cassetteFlow.currentTapeID + "," + cassetteFlow.currentAudioCount +
                            "," + cassetteFlow.currentAudioID;
                    break;
            }
            
            sendResponse(he, response);
        }
    }
    
    // class to handle getting continous information
    private class getRawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = true;
            
            Thread thread = new Thread("Server Raw Reader Thread") {
                @Override
                public void run() {
                    try {
                        he.sendResponseHeaders(200, 0); // specify a length of 0 to use chunking
                        OutputStream os = he.getResponseBody();
                        
                        while (readRawData) {
                            // the \r\n must be added for chunking to work
                            String response = "\r\n" + cassetteFlow.getRawLineRecord();
                            
                            byte[] data = response.getBytes("UTF-8");
                            os.write(data);
                            os.flush();
                            
                            Thread.sleep(200);
                        }
                        
                        os.close();
                    } catch (Exception ex) {
                        System.out.println("Connection closed ...");
                    }
                }
            };
            thread.start();
        }
    }
    
    // class to handle creating the input files for encoding
    private class createHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            extractInformation(params);
            
            String response = "Creating Input File: " + params;
            sendResponse(he, response);
        }
        
        /**
         * Extract the data from the param string
         * @param params 
         */
        private void extractInformation(Map params) {
            String side = params.get("side").toString();
            mute = Integer.parseInt(params.get("mute").toString());
            String data[] = params.get("data").toString().split(",");
            
            tapeID = data[0];
            
            // get the mp3 ids
            ArrayList<String> mp3Ids = new ArrayList<>();
            for(int i = 1; i < data.length; i++) {
                mp3Ids.add(data[i]);
            }
            
            if(side.equals("A")) {
                sideAList = mp3Ids;
            } else {
                sideBList = mp3Ids;
            }
        }
    }
        
    // class to start the encoding process
    private class startHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            
            String query = he.getRequestURI().getQuery();
            
            Map params = splitQuery(query);
            String side = params.get("side").toString();
            
            String message = startEncoding(side);
            
            String response = "Starting encoding of input file: " + params + " || " + message;
            sendResponse(he, response);
        }
        
        /**
         * Start the encoding for the indicate side of the tape
         * @param side 
         */
        private String startEncoding(String side) {
            final ArrayList<String> sideList = (side.equals("A")) ? sideAList : sideBList;
            
            if(sideList != null) {
                cassetteFlow.currentTapeID = tapeID;
                
                // test this in a thread
                Thread thread = new Thread("Server Encoder Thread") {
                    @Override
                    public void run() {
                        for(int i =0; i < sideList.size(); i++) {
                            int trackNumber =  i + 1;
                            String mp3Id = sideList.get(i);
                            cassetteFlow.currentAudioCount = trackNumber;
                            cassetteFlow.currentAudioID = mp3Id;
                            
                            System.out.println("Server Encoding Track # " + trackNumber + " / " + mp3Id);  
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ex) { }
                        }
                        
                        // change the trackNumber to -1 to indicate we are done with encoding
                        cassetteFlow.currentAudioCount = -1;
                    }
                };
                thread.start();
                
                return "OK";
            } else {
                // indiicate to the client that something is wrong
                return "ERROR -- Missing Input Data ...";
            }
        }
    }
    
    // class to handle playing the mp3 indicated in the input file
    private class playHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            
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
            readRawData = false;
            
            String response = "Stopping Encoding or Playing ...";
            sendResponse(he, response);
        }
    }
    
    /**
     * Used by client to test encoding
     */
    public void testEncode() {
        
    }
    
    /**
     * Method to run micro server independently of the GUI application
     * 
     * @param args 
     */
    public static void main(String[] args) {
        try {
            // init the cassette playey and object to start the minimodem program
            CassetteFlow cf = new CassetteFlow();  
            CassettePlayer cp = new CassettePlayer(cf, null);
            cp.setRawLineRecordOnly(true);
            cp.startMinimodem(0);
            
            CassetteFlowServer cfs = new CassetteFlowServer();
            cfs.setCassetteFlow(cf);
            
            // indicate where to access the line records
            System.out.println("GoTo: http://localhost:8192/raw to access line records");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
