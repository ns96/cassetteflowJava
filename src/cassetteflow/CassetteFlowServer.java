
package cassetteflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is class implements a simple http server for testing the ESP32LyraT
 * Client
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

    // the offset time when playing dct tracks
    private int offset = 0;

    private boolean readRawData = false;

    /**
     * Main constructor which starts the server
     * 
     * @throws IOException
     */
    public CassetteFlowServer() throws IOException {
        ExecutorService executor = Executors.newCachedThreadPool();

        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8192), 0);

        server.createContext("/", new SetModeHandler());
        server.createContext("/mp3db", new getMp3DBHandler());
        server.createContext("/tapedb", new getTapeDBHandler());
        server.createContext("/info", new getInfoHandler());
        server.createContext("/raw", new getRawHandler());
        server.createContext("/rawdct", new getRawDCTHandler());
        server.createContext("/dct", new getDCTHandler());
        server.createContext("/create", new createHandler());
        server.createContext("/start", new startHandler());
        server.createContext("/play", new playHandler());
        server.createContext("/stop", new stopHandler());

        // define endpoint for interacting with the cassetteflow desktop program
        server.createContext("/dcv", new DecodeViewHandler()); // send the UI
        server.createContext("/dcs", new DecodeStateHandler()); // get the decode state json
        server.createContext("/dcs_lite", new DecodeLiteStateHandler()); // get lightweight decode state json for ESP32
        server.createContext("/dcc", new DecodeCommandHandler()); // send a commend to decorder
        server.createContext("/diag", new DecodeDiagHandler()); // get live FSK speed & tape diagnostic telemetry
        server.createContext("/diagnostics", new DecodeDiagHandler()); // alias

        // Native audio streaming & Web Player endpoints
        server.createContext("/music", new MusicStreamHandler()); // HTTP 206 Partial Content audio streaming
        server.createContext("/player", new WebPlayerHandler()); // Web player HTML application
        server.createContext("/player.html", new WebPlayerHandler()); // Web player HTML alias
        server.createContext("/tapedb.txt", new DatabaseFileHandler("tapedb.txt")); // Static tape database
        server.createContext("/audiodb.txt", new DatabaseFileHandler("audiodb.txt")); // Static audio database
        server.createContext("/tracklist.txt", new DatabaseFileHandler("tracklist.txt")); // Static tracklist database
        server.createContext("/audiodb", new getMp3DBHandler()); // Audio DB alias
        server.createContext("/api/status", new DecodeLiteStateHandler()); // Status alias for web player
        server.createContext("/playing", new PlayingHandler()); // Client Now Playing synchronization

        server.setExecutor(executor);
        server.start();

        System.out.println("Cassette Flow Server Started on port 8192 (Web Player: http://localhost:8192/player)...");
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
            queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }

        return queryPairs;
    }

    public synchronized void sendResponse(HttpExchange he, String response) {
        try {
            addCorsHeaders(he);
            byte[] data = response.getBytes(StandardCharsets.UTF_8);
            he.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
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
            String response = (cassetteFlow != null) ? cassetteFlow.getAudioInfoDBAsString() : "";
            sendResponse(he, response);
        }
    }

    // class to handle getting the tape database
    private class getTapeDBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;
            String response = (cassetteFlow != null) ? cassetteFlow.getTapeDBAsString() : "";
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
                    response = "DECODE " + cassetteFlow.getRawLineRecord();
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
    // class to handle getting continuous raw information stream
    private class getRawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            addCorsHeaders(he);
            he.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            he.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
            he.getResponseHeaders().set("Connection", "keep-alive");
            he.sendResponseHeaders(200, 0); // chunked transfer encoding

            Thread thread = new Thread("Server Raw Reader Thread") {
                @Override
                public void run() {
                    try (OutputStream os = he.getResponseBody()) {
                        String lastSent = "";
                        while (true) {
                            String rawLine = (cassetteFlow != null) ? cassetteFlow.getRawLineRecord() : null;
                            if (rawLine != null && !rawLine.isEmpty() && !rawLine.equals("NO PLAYER ...")) {
                                if (!rawLine.equals(lastSent)) {
                                    lastSent = rawLine;
                                    String response = rawLine + "\r\n";
                                    byte[] data = response.getBytes(StandardCharsets.UTF_8);
                                    os.write(data);
                                    os.flush();
                                }
                            }
                            Thread.sleep(50);
                        }
                    } catch (Exception ex) {
                        // Client disconnected or stream closed
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
    }

    // class to handle getting continuous information with dct translation
    private class getRawDCTHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            addCorsHeaders(he);
            he.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            he.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
            he.getResponseHeaders().set("Connection", "keep-alive");
            he.sendResponseHeaders(200, 0); // chunked transfer encoding

            Thread thread = new Thread("Server DCT Reader Thread") {
                @Override
                public void run() {
                    try (OutputStream os = he.getResponseBody()) {
                        String lastSent = "";
                        while (true) {
                            String rawLine = (cassetteFlow != null) ? cassetteFlow.getRawLineRecord() : null;
                            if (rawLine != null && !rawLine.isEmpty() && !rawLine.equals("NO PLAYER ...")) {
                                if (!rawLine.equals(lastSent)) {
                                    lastSent = rawLine;
                                    String response;
                                    if (rawLine.startsWith("DCT")) {
                                        response = rawLine + "\n --> " + cassetteFlow.getCurrentLineRecord() + "\r\n";
                                    } else {
                                        response = rawLine + "\r\n";
                                    }
                                    byte[] data = response.getBytes(StandardCharsets.UTF_8);
                                    os.write(data);
                                    os.flush();
                                }
                            }
                            Thread.sleep(50);
                        }
                    } catch (Exception ex) {
                        // Client disconnected or stream closed
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
    }

    // class to handle creating a DCT record from a tapedb id
    private class getDCTHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;

            String query = he.getRequestURI().getQuery();

            Map params = splitQuery(query);
            tapeID = params.get("tapeID").toString();
            offset = Integer.parseInt(params.get("offset").toString());

            // create the DCT record
            boolean success = cassetteFlow.createDCTArrayList(tapeID, offset);

            String response = "Creating DCT Record for Tape ID: " + tapeID;

            if (success) {
                response += " -- Success";
            } else {
                response += " -- Failed";
            }

            sendResponse(he, response);
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
         * 
         * @param params
         */
        private void extractInformation(Map params) {
            String side = params.get("side").toString();
            mute = Integer.parseInt(params.get("mute").toString());
            String data[] = params.get("data").toString().split(",");

            tapeID = data[0];

            // get the mp3 ids
            ArrayList<String> mp3Ids = new ArrayList<>();
            for (int i = 1; i < data.length; i++) {
                mp3Ids.add(data[i]);
            }

            if (side.equals("A")) {
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
         * 
         * @param side
         */
        private String startEncoding(String side) {
            final ArrayList<String> sideList = (side.equals("A")) ? sideAList : sideBList;

            if (sideList != null) {
                cassetteFlow.currentTapeID = tapeID;

                // test this in a thread
                Thread thread = new Thread("Server Encoder Thread") {
                    @Override
                    public void run() {
                        for (int i = 0; i < sideList.size(); i++) {
                            int trackNumber = i + 1;
                            String mp3Id = sideList.get(i);
                            cassetteFlow.currentAudioCount = trackNumber;
                            cassetteFlow.currentAudioID = mp3Id;

                            System.out.println("Server Encoding Track # " + trackNumber + " / " + mp3Id);
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ex) {
                            }
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
     * Handles serving mobile page which allows for viewing of currently decoding
     * data
     * 
     */
    private class DecodeViewHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Define the path to your HTML file, as a resource within the JAR.
            String resourcePath = "/decode_viewer.html";

            // Use the class loader to get an InputStream for the resource.
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("Resource not found: " + resourcePath);
                }

                // Read the content from the InputStream into a byte array.
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] response = buffer.toByteArray();

                // Send the response with a 200 OK status.
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();

            } catch (IOException e) {
                // Handle the case where the resource is not found.
                String errorMessage = "404 Not Found: " + resourcePath;
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(404, errorMessage.length());
                OutputStream os = exchange.getResponseBody();
                os.write(errorMessage.getBytes());
                os.close();
                System.err.println(errorMessage);
            }
        }
    }

    private class DecodeStateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Convert the JSONObject to a JSON string
            String jsonResponse = cassetteFlow.getCurrentDecodeState().toString();

            // Set the response headers to indicate JSON content
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            // Send the JSON string as the response body
            byte[] jsonBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, jsonBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(jsonBytes);
            os.close();
        }
    }

    private class DecodeLiteStateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Convert the lightweight JSONObject to a JSON string
            String jsonResponse = cassetteFlow.getLightweightDecodeState().toString();

            // Set the response headers to indicate JSON content and close connection promptly
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Connection", "close");

            // Send the JSON string as the response body
            byte[] jsonBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, jsonBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(jsonBytes);
            os.close();
        }
    }

    /**
     * Class to send commands to the decoding backend
     */
    private class DecodeCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            readRawData = false;

            String query = he.getRequestURI().getQuery();
            Map params = splitQuery(query);
            String command = params.get("c").toString();
            cassetteFlow.runDecodeCommand(command);

            String response = "Decoding command received " + command;
            sendResponse(he, response);
        }
    }

    /**
     * Handler to return the raw/formatted FSK tape diagnostic string
     */
    private class DecodeDiagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String diagText = (cassetteFlow != null) ? cassetteFlow.getDiagnosticStats() : "";
            if (diagText == null || diagText.isEmpty()) {
                diagText = "No diagnostic telemetry available (decoder not running or no carrier).\n";
            }

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Connection", "close");

            byte[] textBytes = diagText.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, textBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(textBytes);
            os.close();
        }
    }

    /**
     * Handler to stream audio files from AUDIO_DIR_NAME with HTTP 206 Partial Content (Range) support.
     */
    private class MusicStreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath(); // e.g. /music/Artist/Album/Song.mp3
            String query = exchange.getRequestURI().getQuery();
            File fileToServe = null;

            // Check if database text file was requested under /music/
            if (path.endsWith("/audiodb.txt") || path.endsWith("/tapedb.txt") || path.endsWith("/tracklist.txt")
                    || path.endsWith("/mp3db") || path.endsWith("/audiodb") || path.endsWith("/tapedb")) {
                String reqName = path.substring(path.lastIndexOf('/') + 1);
                new DatabaseFileHandler(reqName).handle(exchange);
                return;
            }

            // 1. Check if an audio ID / hash was specified in query parameter (e.g. /music?id=hash)
            if (query != null && (query.contains("id=") || query.contains("hash="))) {
                Map<String, String> params = splitQuery(query);
                String hash = params.get("id");
                if (hash == null) hash = params.get("hash");
                if (hash != null && cassetteFlow != null && cassetteFlow.audioInfoDB != null) {
                    AudioInfo info = cassetteFlow.audioInfoDB.get(hash);
                    if (info != null && info.getFile() != null && info.getFile().exists()) {
                        fileToServe = info.getFile();
                    }
                }
            }

            // 2. Otherwise resolve file path relative to CassetteFlow.AUDIO_DIR_NAME
            if (fileToServe == null) {
                String subPath = path.substring(path.indexOf("/music") + 6);
                if (subPath.startsWith("/")) {
                    subPath = subPath.substring(1);
                }

                if (!subPath.isEmpty()) {
                    String decodedSubPath = URLDecoder.decode(subPath, StandardCharsets.UTF_8.name());
                    File baseDir = new File(CassetteFlow.AUDIO_DIR_NAME);
                    File requestedFile = new File(baseDir, decodedSubPath);

                    // Check if file exists directly
                    if (requestedFile.exists() && requestedFile.isFile()) {
                        // Path traversal check
                        if (requestedFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                            fileToServe = requestedFile;
                        }
                    } else if (cassetteFlow != null && cassetteFlow.audioInfoDB != null) {
                        // Try matching by filename in database
                        String targetName = requestedFile.getName();
                        for (AudioInfo info : cassetteFlow.audioInfoDB.values()) {
                            if (info.getFile() != null && info.getFile().exists()
                                    && info.getFile().getName().equalsIgnoreCase(targetName)) {
                                fileToServe = info.getFile();
                                break;
                            }
                        }
                    }
                }
            }

            if (fileToServe != null && fileToServe.exists() && fileToServe.isFile()) {
                String mimeType = getMimeType(fileToServe.getName());
                serveFileWithRangeSupport(exchange, fileToServe, mimeType);
            } else {
                String errorMsg = "404 Audio Not Found: " + path;
                byte[] errorBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, errorBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorBytes);
                }
            }
        }
    }

    /**
     * Handler to serve the embedded player.html single-page web app
     */
    private class WebPlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            byte[] responseData = null;

            // Try loading from classpath resource /player.html
            try (InputStream is = getClass().getResourceAsStream("/player.html")) {
                if (is != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] temp = new byte[8192];
                    int r;
                    while ((r = is.read(temp)) != -1) {
                        buffer.write(temp, 0, r);
                    }
                    responseData = buffer.toByteArray();
                }
            } catch (Exception ignored) {
            }

            // Fallback: try loading from local disk src/player.html
            if (responseData == null) {
                File localPlayer = new File("src" + File.separator + "player.html");
                if (!localPlayer.exists()) localPlayer = new File("player.html");
                if (localPlayer.exists() && localPlayer.isFile()) {
                    try (FileInputStream fis = new FileInputStream(localPlayer);
                         ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                        byte[] temp = new byte[8192];
                        int r;
                        while ((r = fis.read(temp)) != -1) {
                            buffer.write(temp, 0, r);
                        }
                        responseData = buffer.toByteArray();
                    }
                }
            }

            if (responseData != null) {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.sendResponseHeaders(200, responseData.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseData);
                }
            } else {
                String errorMsg = "404 Player HTML Resource Not Found";
                exchange.sendResponseHeaders(404, errorMsg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * Handler to serve static database text files (tapedb.txt, audiodb.txt, tracklist.txt)
     */
    private class DatabaseFileHandler implements HttpHandler {
        private final String defaultFilename;

        public DatabaseFileHandler(String defaultFilename) {
            this.defaultFilename = defaultFilename;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String filename = defaultFilename;
            String path = exchange.getRequestURI().getPath();
            if (path != null && path.lastIndexOf('/') >= 0) {
                String reqName = path.substring(path.lastIndexOf('/') + 1);
                if (!reqName.isEmpty() && reqName.endsWith(".txt")) {
                    filename = reqName;
                }
            }

            File dbFile = new File(CassetteFlow.AUDIO_DIR_NAME, filename);
            if (!dbFile.exists() || !dbFile.isFile()) {
                dbFile = new File(filename);
            }

            if (dbFile.exists() && dbFile.isFile()) {
                serveFileWithRangeSupport(exchange, dbFile, "text/plain; charset=utf-8");
            } else {
                // Fallback: return generated database string from in-memory database
                String responseText = "";
                if (("tapedb.txt".equalsIgnoreCase(filename) || "tapedb".equalsIgnoreCase(filename)) && cassetteFlow != null) {
                    responseText = cassetteFlow.getTapeDBAsString();
                } else if (("audiodb.txt".equalsIgnoreCase(filename) || "audiodb".equalsIgnoreCase(filename) || "mp3db".equalsIgnoreCase(filename)) && cassetteFlow != null) {
                    responseText = cassetteFlow.getAudioInfoDBAsString();
                }

                if (responseText == null) {
                    responseText = "";
                }

                byte[] data = responseText.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            }
        }
    }

    /**
     * Handler to receive now-playing track updates from web/client players
     * Endpoint: GET /playing?track=01.+Song+-+Artist&hash=...
     *       or: POST /playing (with url-encoded params or json body)
     */
    private class PlayingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String track = "";
            String hash = "";
            boolean isPlaying = true;

            // 1. Check query parameters
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && !query.isEmpty()) {
                Map<String, String> params = splitQuery(query);
                if (params.containsKey("track")) {
                    track = params.get("track");
                } else if (params.containsKey("title")) {
                    track = params.get("title");
                    if (params.containsKey("artist")) {
                        track += " - " + params.get("artist");
                    }
                }
                if (params.containsKey("hash")) {
                    hash = params.get("hash");
                }
                if (params.containsKey("playing")) {
                    isPlaying = !"false".equalsIgnoreCase(params.get("playing"));
                } else if (params.containsKey("state") || params.containsKey("status")) {
                    String st = params.containsKey("state") ? params.get("state") : params.get("status");
                    if ("paused".equalsIgnoreCase(st) || "stopped".equalsIgnoreCase(st) || "idle".equalsIgnoreCase(st)) {
                        isPlaying = false;
                    }
                }
            }

            // 2. If POST body and track not in query, inspect request body
            if (track.isEmpty() && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (body.startsWith("{") && body.endsWith("}")) {
                        try {
                            JSONObject json = new JSONObject(body);
                            if (json.has("track")) track = json.getString("track");
                            else if (json.has("title")) {
                                track = json.getString("title");
                                if (json.has("artist")) track += " - " + json.getString("artist");
                            }
                            if (json.has("hash")) hash = json.getString("hash");
                            if (json.has("playing")) isPlaying = json.getBoolean("playing");
                        } catch (Exception ignored) {}
                    } else if (body.contains("=")) {
                        Map<String, String> formParams = splitQuery(body);
                        if (formParams.containsKey("track")) track = formParams.get("track");
                        if (formParams.containsKey("hash")) hash = formParams.get("hash");
                    } else {
                        track = body;
                    }
                }
            }

            if (track != null && !track.trim().isEmpty()) {
                try {
                    track = URLDecoder.decode(track, StandardCharsets.UTF_8).trim();
                } catch (Exception ignored) {}
                if (cassetteFlow != null) {
                    cassetteFlow.setClientNowPlaying(track, hash, isPlaying);
                }
            }

            String jsonResponse = "{\"status\":\"ok\",\"track\":\"" + (track != null ? track.replace("\"", "\\\"") : "") + "\",\"playing\":" + isPlaying + "}\n";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Helper to inject CORS headers across all HTTP responses
     */
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Range, Content-Type, Accept, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges");
    }

    /**
     * Serves a file with HTTP 206 Partial Content (Byte-Range) streaming support for audio seeking.
     */
    private static void serveFileWithRangeSupport(HttpExchange exchange, File file, String contentType) throws IOException {
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.getResponseHeaders().set("Content-Type", contentType);

        long fileLength = file.length();
        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeValue = rangeHeader.substring(6).trim();
            long start = 0;
            long end = fileLength - 1;

            int dashPos = rangeValue.indexOf('-');
            if (dashPos != -1) {
                String startStr = rangeValue.substring(0, dashPos).trim();
                String endStr = rangeValue.substring(dashPos + 1).trim();

                try {
                    if (!startStr.isEmpty()) {
                        start = Long.parseLong(startStr);
                    }
                    if (!endStr.isEmpty()) {
                        end = Long.parseLong(endStr);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            if (start > end || start >= fileLength) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + fileLength);
                exchange.sendResponseHeaders(416, -1); // 416 Range Not Satisfiable
                exchange.close();
                return;
            }

            if (end >= fileLength) {
                end = fileLength - 1;
            }

            long contentLength = end - start + 1;
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
            exchange.sendResponseHeaders(206, contentLength); // 206 Partial Content

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream os = exchange.getResponseBody()) {
                raf.seek(start);
                long bytesRemaining = contentLength;
                byte[] buffer = new byte[32768];
                while (bytesRemaining > 0) {
                    int toRead = (int) Math.min(buffer.length, bytesRemaining);
                    int bytesRead = raf.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;
                    os.write(buffer, 0, bytesRead);
                    bytesRemaining -= bytesRead;
                }
                os.flush();
            }
        } else {
            // Full file request (200 OK)
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
            exchange.sendResponseHeaders(200, fileLength);

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[32768];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        }
    }

    /**
     * Determines the MIME content type based on the file extension
     */
    private static String getMimeType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        return "application/octet-stream";
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

            // trying starting minimodem in try block in case it's not installed
            try {
                cp.startMinimodem(0);
            } catch (IOException iex) {
                System.out.println("No Minimodem program found ...\n");
            }

            CassetteFlowServer cfs = new CassetteFlowServer();
            cfs.setCassetteFlow(cf);

            // indicate where to access the line records
            System.out.println("GoTo: http://localhost:8192/raw to access line records");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
