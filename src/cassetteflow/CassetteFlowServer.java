
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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.json.JSONArray;
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
        // The bundled J-Card app takes a card as a base64url query parameter, which can
        // be a few KB, so give the JDK's server more room than its small default before
        // any request header limit is read.
        if (System.getProperty("sun.net.httpserver.maxReqHeaderSize") == null) {
            System.setProperty("sun.net.httpserver.maxReqHeaderSize", "262144");
        }

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

        // Telemetry Web App & Control Endpoints
        server.createContext("/telemetry", new TelemetryViewHandler());
        server.createContext("/telemetry.html", new TelemetryViewHandler());
        server.createContext("/api/telemetry", new TelemetryApiHandler());
        server.createContext("/api/devices", new AudioDevicesHandler());
        server.createContext("/api/cmd", new CommandHandler());

        // Library & Tape Builder Web App (audio playback only, no FSK encoding)
        server.createContext("/library", new LibraryViewHandler()); // send the UI
        server.createContext("/library.html", new LibraryViewHandler()); // alias
        server.createContext("/encode", new LibraryViewHandler()); // alias for the desktop ENCODE style app
        server.createContext("/api/library", new LibraryJsonHandler()); // full audio library metadata as gzip json
        server.createContext("/api/tapedb", new StoreTapeHandler()); // store a tape record into tapedb.txt
        server.createContext("/jcard/template", new JCardTemplateHandler()); // default j-card template

        // Vendored J-Card Template web app (upstream ed7n/jcard-template, BSD 2-Clause)
        server.createContext("/jcard", new JCardViewHandler()); // page + res/** assets
        server.createContext("/api/jcard", new JCardDataHandler()); // card json for a tape

        server.setExecutor(executor);
        server.start();

        System.out.println("Cassette Flow Server Started on port 8192 (Web Player: http://localhost:8192/player | Library: http://localhost:8192/library | Telemetry: http://localhost:8192/telemetry)...");
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
                        long lastLineTime = System.currentTimeMillis();
                        long lastNoCarrierTime = 0;
                        while (true) {
                            long now = System.currentTimeMillis();
                            String rawLine = (cassetteFlow != null) ? cassetteFlow.getRawLineRecord() : null;
                            if (rawLine != null && !rawLine.isEmpty() && !rawLine.equals("NO PLAYER ...")) {
                                if (!rawLine.equals(lastSent)) {
                                    lastSent = rawLine;
                                    lastLineTime = now;
                                    String response = rawLine + "\r\n";
                                    byte[] data = response.getBytes(StandardCharsets.UTF_8);
                                    os.write(data);
                                    os.flush();
                                }
                            }

                            // Inactivity watchdog: send NOCARRIER when no new line decoded for >= 1500ms
                            // (FSK tape lines are transmitted at 1000ms intervals, so 1500ms detects missing records without false triggers)
                            if (now - lastLineTime >= 1500) {
                                if (now - lastNoCarrierTime >= 2000) {
                                    lastNoCarrierTime = now;
                                    String noCarrier = "### NOCARRIER ###\r\n";
                                    byte[] data = noCarrier.getBytes(StandardCharsets.UTF_8);
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
                        long lastLineTime = System.currentTimeMillis();
                        long lastNoCarrierTime = 0;
                        while (true) {
                            long now = System.currentTimeMillis();
                            String rawLine = (cassetteFlow != null) ? cassetteFlow.getRawLineRecord() : null;
                            if (rawLine != null && !rawLine.isEmpty() && !rawLine.equals("NO PLAYER ...")) {
                                if (!rawLine.equals(lastSent)) {
                                    lastSent = rawLine;
                                    lastLineTime = now;
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

                            // Inactivity watchdog: send NOCARRIER when no new line decoded for >= 1500ms
                            // (FSK tape lines are transmitted at 1000ms intervals, so 1500ms detects missing records without false triggers)
                            if (now - lastLineTime >= 1500) {
                                if (now - lastNoCarrierTime >= 2000) {
                                    lastNoCarrierTime = now;
                                    String noCarrier = "### NOCARRIER ###\r\n";
                                    byte[] data = noCarrier.getBytes(StandardCharsets.UTF_8);
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

            sendPage(exchange, "/player.html", "404 Player HTML Resource Not Found",
                    "src" + File.separator + "player.html", "player.html");
        }
    }

    /**
     * Handler to serve the embedded library.html single page web app. This is the
     * web version of the desktop ENCODE tab but for audio playback only, so no
     * FSK encoding is done.
     */
    private class LibraryViewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            sendPage(exchange, "/library.html", "404 Library HTML Resource Not Found",
                    "src" + File.separator + "library.html", "library.html");
        }
    }

    /**
     * Serves the vendored J-Card Template web app and its assets.
     *
     * /jcard, /jcard/, /jcard/index.html -> the page with the CassetteFlow bridge
     *                                       injected before &lt;/body&gt;
     * /jcard/res/**                      -> the vendored static assets
     *
     * The vendored files are never edited: the bridge script tag (and, when a tape
     * id was supplied, the card data) is inserted while the page is being served.
     */
    private class JCardViewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath();

            // ---- assets under /jcard/res/...
            if (path.startsWith("/jcard/res/")) {
                String relativePath = path.substring("/jcard/".length());
                if (relativePath.contains("..")) {
                    sendJsonError(exchange, 400, "Invalid asset path");
                    return;
                }

                byte[] data = loadPageBytes("/jcard/" + relativePath, "src" + File.separator + "jcard"
                        + File.separator + relativePath.replace("/", File.separator));
                if (data == null) {
                    sendJsonError(exchange, 404, "Asset not found: " + relativePath);
                    return;
                }

                exchange.getResponseHeaders().set("Content-Type", getMimeType(relativePath));
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
                return;
            }

            // ---- the bridge script (ours, lives next to the vendored page)
            if (path.endsWith("/cf-bridge.js")) {
                byte[] bridge = loadPageBytes("/jcard/cf-bridge.js", "src" + File.separator + "jcard"
                        + File.separator + "cf-bridge.js");
                if (bridge == null) {
                    sendJsonError(exchange, 404, "cf-bridge.js not found");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", getMimeType("cf-bridge.js"));
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.sendResponseHeaders(200, bridge.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bridge);
                }
                return;
            }

            // ---- the page itself
            byte[] page = loadPageBytes("/jcard/index.html",
                    "src" + File.separator + "jcard" + File.separator + "index.html");
            if (page == null) {
                sendJsonError(exchange, 404, "The bundled J-Card Template was not found");
                return;
            }

            String html = new String(page, StandardCharsets.UTF_8);
            html = injectJCardBridge(html, exchange.getRequestURI().getRawQuery());
            byte[] body = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Handler returning the J-Card data for a tape as JSON, e.g.
     * /api/jcard?tape=0042 or /api/jcard?tape=0042&amp;combine=1
     */
    private class JCardDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if (cassetteFlow == null) {
                sendJsonError(exchange, 503, "CassetteFlow is not attached to the server");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = (query != null && query.contains("="))
                    ? splitQuery(query) : new LinkedHashMap<String, String>();

            String tapeId = params.containsKey("tape") ? params.get("tape").trim() : "";
            if (tapeId.isEmpty()) {
                sendJsonError(exchange, 400, "A tape id is required, e.g. /api/jcard?tape=0042");
                return;
            }

            boolean combine = "1".equals(params.get("combine"))
                    || "true".equalsIgnoreCase(params.get("combine"));

            JSONObject card = cassetteFlow.buildJCardData(tapeId.toUpperCase(), combine,
                    params.get("title"), params.get("artist"), null);

            sendJson(exchange, 200, card);
        }
    }

    /**
     * Insert the CassetteFlow bridge into the vendored page. When a tape id was
     * requested the card data is embedded as well so the page renders it straight
     * away; otherwise the bridge falls back to its own ?data= parameter.
     *
     * @param html the vendored page
     * @param rawQuery the request query string, may be null
     * @return the page with the bridge injected
     */
    private String injectJCardBridge(String html, String rawQuery) {
        StringBuilder scripts = new StringBuilder();

        // The vendored page references its assets relatively (res/styles/..., res/scripts/...),
        // so the base URL has to be pinned: it is served from /jcard and would otherwise
        // resolve them against the server root.
        String base = "<base href=\"/jcard/\">\n";
        int headAt = html.toLowerCase().indexOf("<head>");
        if (headAt >= 0) {
            int afterHead = headAt + "<head>".length();
            html = html.substring(0, afterHead) + base + html.substring(afterHead);
        } else {
            html = base + html;
        }

        if (rawQuery != null && rawQuery.contains("tape=")) {
            try {
                Map<String, String> params = splitQuery(rawQuery);
                String tapeId = params.containsKey("tape") ? params.get("tape").trim().toUpperCase() : "";

                if (!tapeId.isEmpty() && cassetteFlow != null) {
                    boolean combine = "1".equals(params.get("combine"))
                            || "true".equalsIgnoreCase(params.get("combine"));

                    JSONObject card = cassetteFlow.buildJCardData(tapeId, combine,
                            params.get("title"), params.get("artist"), null);

                    scripts.append("<script>window.CF_JCARD_DATA = ")
                            .append(card.toString())
                            .append(";</script>\n");
                }
            } catch (Exception ex) {
                Logger.getLogger(CassetteFlowServer.class.getName()).log(Level.WARNING,
                        "Unable to build the J-Card data for " + rawQuery, ex);
            }
        }

        scripts.append("<script src=\"/jcard/cf-bridge.js\"></script>\n");

        int insertAt = html.lastIndexOf("</body>");
        if (insertAt < 0) {
            // fall back to appending so the bridge is never silently lost
            return html + "\n" + scripts;
        }

        return html.substring(0, insertAt) + scripts + html.substring(insertAt);
    }

    /**
     * Handler that returns the default J-Card template. This reads the same
     * template.jcard.json that the desktop application loads at startup so the web
     * J-Card dialog writes its output in exactly the same format.
     */
    private class JCardTemplateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String template = null;
            try {
                template = CassetteFlowUtil.getResourceFileAsString("template.jcard.json");
            } catch (IOException ignored) {
            }

            if (template == null || template.trim().isEmpty()) {
                sendJsonError(exchange, 404, "template.jcard.json was not found on the classpath");
                return;
            }

            byte[] body = template.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Handler that returns the complete audio library as JSON. Unlike the audio db
     * text file this includes the tag based meta data (title, artist, album, genre,
     * year) that the web track finder needs. The response is gzipped when the
     * client supports it since the payload is a few MB for a large library.
     */
    private class LibraryJsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String jsonResponse = (cassetteFlow != null)
                    ? cassetteFlow.getAudioInfoDBAsJSON()
                    : "{\"version\":2,\"count\":0,\"tracks\":[]}";

            byte[] jsonBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            byte[] body = jsonBytes;

            String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            boolean useGzip = acceptEncoding != null && acceptEncoding.toLowerCase().contains("gzip");

            if (useGzip) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(1024, jsonBytes.length / 4));
                try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                    gzip.write(jsonBytes);
                }
                body = buffer.toByteArray();
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            if (useGzip) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            }

            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Handler to store a tape record (side A and/or side B) into the tape database
     * file. This is what backs the Store button in the library web app and keeps
     * the in memory tape database and tapedb.txt in sync with the browser.
     *
     * POST /api/tapedb
     * {"tapeId":"0042","sideA":["hash",...],"sideB":["hash",...],
     *  "overwrite":false,"backup":true}
     */
    private class StoreTapeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            // the parameters can either be sent as a json body or as url encoded
            // parameters which makes it easy to test from a browser or curl
            String body = "";
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String query = exchange.getRequestURI().getQuery();
                body = (query != null) ? query : "";
            } else {
                body = readRequestBody(exchange, 4 * 1024 * 1024);
            }

            String tapeId = "";
            ArrayList<String> sideAIds = new ArrayList<>();
            ArrayList<String> sideBIds = new ArrayList<>();
            boolean overwrite = false;
            boolean backup = true;

            try {
                if (body.startsWith("{")) {
                    JSONObject json = new JSONObject(body);
                    tapeId = json.has("tapeId") ? json.optString("tapeId", "") : json.optString("tapeID", "");
                    sideAIds = readHashList(json, "sideA");
                    sideBIds = readHashList(json, "sideB");
                    overwrite = json.optBoolean("overwrite", false);
                    backup = json.optBoolean("backup", true);
                } else if (body.contains("=")) {
                    Map<String, String> params = splitQuery(body);
                    tapeId = params.containsKey("tapeId") ? params.get("tapeId") : "";
                    if (params.containsKey("sideA")) {
                        sideAIds = splitHashList(params.get("sideA"));
                    }
                    if (params.containsKey("sideB")) {
                        sideBIds = splitHashList(params.get("sideB"));
                    }
                    overwrite = "1".equals(params.get("overwrite")) || "true".equalsIgnoreCase(params.get("overwrite"));
                    if (params.containsKey("backup")) {
                        backup = !("0".equals(params.get("backup")) || "false".equalsIgnoreCase(params.get("backup")));
                    }
                }
            } catch (Exception ex) {
                sendJsonError(exchange, 400, "Unable to read the store request: " + ex.getMessage());
                return;
            }

            if (cassetteFlow == null) {
                sendJsonError(exchange, 503, "CassetteFlow is not attached to the server");
                return;
            }

            tapeId = tapeId.trim().toUpperCase();

            if (!tapeId.matches("[A-Z0-9_]{1,10}")) {
                sendJsonError(exchange, 400,
                        "Invalid tape ID '" + tapeId + "'. Use up to 10 letters, digits or underscores.");
                return;
            }

            if (sideAIds.isEmpty() && sideBIds.isEmpty()) {
                sendJsonError(exchange, 400, "No tracks were supplied for side A or side B");
                return;
            }

            // resolve the hashes against the audio database
            ArrayList<String> unknownIds = new ArrayList<>();
            ArrayList<AudioInfo> sideAList = resolveAudioInfoList(sideAIds, unknownIds);
            ArrayList<AudioInfo> sideBList = resolveAudioInfoList(sideBIds, unknownIds);

            if (!unknownIds.isEmpty()) {
                int max = Math.min(10, unknownIds.size());
                String ids = String.join(", ", unknownIds.subList(0, max));
                if (unknownIds.size() > max) {
                    ids += " (+ " + (unknownIds.size() - max) + " more)";
                }
                sendJsonError(exchange, 400, "Unknown audio id(s): " + ids);
                return;
            }

            final String tapeKeyA = tapeId + "A";
            final String tapeKeyB = tapeId + "B";

            boolean exists = cassetteFlow.tapeDB.containsKey(tapeKeyA)
                    || cassetteFlow.tapeDB.containsKey(tapeKeyB);

            if (exists && !overwrite) {
                String found = cassetteFlow.tapeDB.containsKey(tapeKeyA) ? tapeKeyA : tapeKeyB;
                sendJsonError(exchange, 409, "Tape " + tapeId + " already exists as record " + found
                        + ". Send overwrite:true to replace it.");
                return;
            }

            // keep the previous records so the in memory database can be rolled back
            // if the tape db file could not be written
            final ArrayList<String> previousA = cassetteFlow.tapeDB.get(tapeKeyA);
            final ArrayList<String> previousB = cassetteFlow.tapeDB.get(tapeKeyB);

            File tapeDBFile = new File(CassetteFlow.TAPE_DB_FILENAME);
            File backupFile = null;

            try {
                // 1. write a backup of the current tape database first
                if (backup && tapeDBFile.exists()) {
                    backupFile = new File(tapeDBFile.getParentFile(), tapeDBFile.getName() + ".bak");
                    Files.copy(tapeDBFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                // 2. update the in memory database then write the whole file out
                cassetteFlow.addToTapeDB(tapeId, sideAList, sideBList, false);
                Files.write(tapeDBFile.toPath(), cassetteFlow.getTapeDBAsString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                restoreTapeRecord(tapeKeyA, previousA);
                restoreTapeRecord(tapeKeyB, previousB);

                Logger.getLogger(CassetteFlowServer.class.getName()).log(Level.SEVERE,
                        "Unable to store tape " + tapeId, ex);
                sendJsonError(exchange, 500, "Unable to write " + tapeDBFile.getAbsolutePath()
                        + " : " + ex.getMessage());
                return;
            }

            JSONObject written = new JSONObject();
            JSONObject response = new JSONObject();

            try {
                if (!sideAIds.isEmpty()) {
                    written.put(tapeKeyA, sideAIds.size());
                }
                if (!sideBIds.isEmpty()) {
                    written.put(tapeKeyB, sideBIds.size());
                }

                response.put("ok", true);
                response.put("tapeId", tapeId);
                response.put("file", tapeDBFile.getAbsolutePath());
                response.put("written", written);
                response.put("tapes", cassetteFlow.tapeDB.size());
                if (backupFile != null) {
                    response.put("backup", backupFile.getName());
                }
            } catch (JSONException ignored) {
            }

            System.out.println("Stored tape " + tapeId + " in " + tapeDBFile.getAbsolutePath()
                    + " (Side A: " + sideAIds.size() + ", Side B: " + sideBIds.size() + ")");

            sendJson(exchange, 200, response);
        }

        /**
         * Read the request body as UTF-8 text, capped at maxBytes so a client can't
         * make the server allocate an unbounded string. Written without
         * InputStream.readAllBytes() so it also runs on Java 8.
         *
         * @param exchange
         * @param maxBytes
         * @return the trimmed request body
         * @throws IOException
         */
        private String readRequestBody(HttpExchange exchange, int maxBytes) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;

            try (InputStream is = exchange.getRequestBody()) {
                int read;
                while ((read = is.read(chunk)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        break;
                    }
                    buffer.write(chunk, 0, read);
                }
            }

            return new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
        }

        /**
         * Read a list of audio ids from a json object. The value can either be a
         * json array or a comma separated string.
         *
         * @param json
         * @param key
         * @return
         */
        private ArrayList<String> readHashList(JSONObject json, String key) {
            ArrayList<String> ids = new ArrayList<>();

            if (!json.has(key)) {
                return ids;
            }

            Object value = json.opt(key);

            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    String id = array.optString(i);
                    if (id != null && !id.trim().isEmpty()) {
                        ids.add(id.trim());
                    }
                }
            } else if (value != null) {
                ids = splitHashList(value.toString());
            }

            return ids;
        }

        /**
         * Split a comma, tab or space separated list of audio ids
         */
        private ArrayList<String> splitHashList(String value) {
            ArrayList<String> ids = new ArrayList<>();

            if (value == null) {
                return ids;
            }

            // audio ids are made up of letters, digits, underscores and dashes so
            // split on anything else. Avoids escapes that older source levels reject.
            for (String id : value.split("[^A-Za-z0-9_-]+")) {
                id = id.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }

            return ids;
        }

        /**
         * Convert a list of audio ids to audio info objects collecting any ids that
         * could not be found
         */
        private ArrayList<AudioInfo> resolveAudioInfoList(ArrayList<String> audioIds, ArrayList<String> unknownIds) {
            ArrayList<AudioInfo> audioInfoList = new ArrayList<>();

            for (String audioId : audioIds) {
                AudioInfo audioInfo = cassetteFlow.audioInfoDB.get(audioId);
                if (audioInfo == null) {
                    unknownIds.add(audioId);
                } else {
                    audioInfoList.add(audioInfo);
                }
            }

            return audioInfoList;
        }

        /**
         * Put an in memory tape record back the way it was after a failed store
         */
        private void restoreTapeRecord(String tapeKey, ArrayList<String> previousRecords) {
            if (previousRecords == null) {
                cassetteFlow.tapeDB.remove(tapeKey);
            } else {
                cassetteFlow.tapeDB.put(tapeKey, previousRecords);
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Range, Content-Type, Accept, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges");
    }

    /**
     * Read an embedded html page from the classpath, falling back to the source
     * directory so that the page can be edited without rebuilding the project
     *
     * @param resourcePath the classpath resource name, e.g. /library.html
     * @param diskPaths the fallback file locations to try
     * @return the page bytes or null if the page could not be found
     */
    private static byte[] loadPageBytes(String resourcePath, String... diskPaths) {
        byte[] responseData = null;

        // 1. Try loading from the classpath resource
        try (InputStream is = CassetteFlowServer.class.getResourceAsStream(resourcePath)) {
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

        // 2. Fallback to the file system which is handy when running from the IDE
        if (responseData == null && diskPaths != null) {
            for (String diskPath : diskPaths) {
                if (diskPath == null) {
                    continue;
                }

                File localPage = new File(diskPath);
                if (localPage.exists() && localPage.isFile()) {
                    try (FileInputStream fis = new FileInputStream(localPage);
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                        byte[] temp = new byte[8192];
                        int r;
                        while ((r = fis.read(temp)) != -1) {
                            buffer.write(temp, 0, r);
                        }
                        responseData = buffer.toByteArray();
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return responseData;
    }

    /**
     * Send an embedded html page back to the client
     *
     * @param exchange
     * @param resourcePath the classpath resource name, e.g. /library.html
     * @param notFoundMessage the message to send if the page cannot be found
     * @param diskPaths the fallback file locations to try
     * @throws IOException
     */
    private static void sendPage(HttpExchange exchange, String resourcePath, String notFoundMessage,
            String... diskPaths) throws IOException {
        byte[] responseData = loadPageBytes(resourcePath, diskPaths);

        if (responseData != null) {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, responseData.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseData);
            }
        } else {
            byte[] errorBytes = notFoundMessage.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        }
    }

    /**
     * Send a JSON object back to the client with the given status code
     */
    private static void sendJson(HttpExchange exchange, int status, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Send a JSON error response back to the client
     */
    private static void sendJsonError(HttpExchange exchange, int status, String message) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", false);
            json.put("error", message);
        } catch (JSONException ignored) {
        }
        sendJson(exchange, status, json);
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
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".webmanifest")) return "application/manifest+json; charset=utf-8";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    /**
     * Used by client to test encoding
     */
    public void testEncode() {

    }

    /**
     * Serves the single-page Telemetry web application (/telemetry)
     */
    private class TelemetryViewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            byte[] responseData = null;

            // Try loading from classpath resource /telemetry.html
            try (InputStream is = getClass().getResourceAsStream("/telemetry.html")) {
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

            // Fallback: try loading from local disk src/telemetry.html
            if (responseData == null) {
                File localFile = new File("src" + File.separator + "telemetry.html");
                if (!localFile.exists()) localFile = new File("telemetry.html");
                if (localFile.exists() && localFile.isFile()) {
                    try (FileInputStream fis = new FileInputStream(localFile);
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
                String errorMsg = "404 Telemetry HTML Resource Not Found";
                exchange.sendResponseHeaders(404, errorMsg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * Handler to return complete JSON telemetry state for /api/telemetry
     */
    private class TelemetryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String jsonResponse = (cassetteFlow != null) ? cassetteFlow.getTelemetryState().toString() : "{}";
            byte[] jsonBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, jsonBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonBytes);
            }
        }
    }

    /**
     * Handler to list available audio playback devices
     */
    private class AudioDevicesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            org.json.JSONArray devArray = new org.json.JSONArray(CassettePlayer.getAvailablePlaybackDevices());
            byte[] jsonBytes = devArray.toString().getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, jsonBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonBytes);
            }
        }
    }

    /**
     * Handler to execute control commands (/api/cmd)
     */
    private class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String cmd = null;
            Object val = null;

            // 1. Check Query params
            String query = exchange.getRequestURI().getQuery();
            if (query != null && !query.isEmpty()) {
                Map<String, String> params = splitQuery(query);
                if (params.containsKey("cmd")) cmd = params.get("cmd");
                else if (params.containsKey("c")) cmd = params.get("c");
                if (params.containsKey("val")) val = params.get("val");
                else if (params.containsKey("v")) val = params.get("v");
            }

            // 2. Check JSON/POST body
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (body.startsWith("{") && body.endsWith("}")) {
                        try {
                            JSONObject json = new JSONObject(body);
                            if (json.has("cmd")) cmd = json.getString("cmd");
                            if (json.has("val")) val = json.get("val");
                        } catch (Exception ignored) {}
                    } else if (body.contains("=")) {
                        Map<String, String> formParams = splitQuery(body);
                        if (formParams.containsKey("cmd")) cmd = formParams.get("cmd");
                        if (formParams.containsKey("val")) val = formParams.get("val");
                    }
                }
            }

            if (cmd != null && cassetteFlow != null) {
                cassetteFlow.runTelemetryCommand(cmd, val);
            }

            String response = "{\"status\":\"ok\",\"cmd\":\"" + (cmd != null ? cmd : "") + "\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
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
