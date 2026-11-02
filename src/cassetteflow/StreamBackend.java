package cassetteflow;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Drives the Spotify and DeckCast streaming backends for the web library app.
 *
 * Playback works exactly like the desktop STREAM PLAY tab: the Spotify client and
 * the DeckCast player own the audio, this class only sends them commands. The
 * desktop feeds those commands a tape time decoded from the tape, here a ticker
 * thread advances the same tape time once a second and calls playStream, so all
 * of the track change, mute section and DCT padding logic is reused as is.
 *
 * @author Nathan
 */
public class StreamBackend {
    private static final int AUTH_TIMEOUT_SECONDS = 120;
    private static final int DECKCAST_QUEUE_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_PIN = 1;

    private static final String SPOTIFY = "Spotify";
    private static final String DECKCAST = "DeckCast";

    // AudioInfo returns this placeholder when no artwork was stored
    private static final String DEFAULT_STREAM_IMAGE = "https://img.youtube.com/vi/MwsGULCvMBk/0.jpg";

    private final CassetteFlow cassetteFlow;

    private SpotifyConnector spotifyConnector;
    private DeckCastConnector deckCastConnector;

    private String provider = "";
    private String serverUrl = "";
    private String pin = "";

    private String authUrl = "";
    private boolean authPending = false;
    private boolean authLaunched = false;
    private long authStartedAt = 0;

    private volatile String pendingLoadUrl = null;
    private volatile CountDownLatch queueLatch = null;
    private volatile ArrayList<AudioInfo> lastQueue = null;

    private ArrayList<AudioInfo> queue = new ArrayList<>();
    private int totalTapeTime = 0;

    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private volatile boolean finished = false;
    private int tapeTime = 0;
    private int startTapeTime = 0;
    private int stopAtSec = -1;
    private long startedAtMs = 0;

    private String message = "";
    private long loadToken = 0;
    private boolean lastConnected = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> ticker;

    public StreamBackend(CassetteFlow cassetteFlow) {
        this.cassetteFlow = cassetteFlow;
    }

    // =========================================================================
    // Connection handling
    // =========================================================================

    /**
     * Connect to a streaming backend
     *
     * @param url the stream server url or "spotify"
     * @param providerHint Spotify, DeckCast or empty/auto
     * @param pin the DeckCast pin
     * @return json describing the connection attempt
     */
    public synchronized String connect(String url, String providerHint, String pin) {
        disconnectInternal();

        String streamUrl = url == null ? "" : url.trim();
        String hint = providerHint == null ? "" : providerHint.trim();

        if (hint.isEmpty() || "Auto".equalsIgnoreCase(hint)) {
            hint = streamUrl.toLowerCase().contains("spotify") ? SPOTIFY : DECKCAST;
        }

        JSONObject response = new JSONObject();

        try {
            if (SPOTIFY.equalsIgnoreCase(hint)) {
                provider = SPOTIFY;
                serverUrl = streamUrl;
                this.pin = "";

                spotifyConnector = new SpotifyConnector(StreamDisplay.NONE, cassetteFlow);
                authUrl = spotifyConnector.getAuthorizationCodeUri().toString();
                authStartedAt = System.currentTimeMillis();
                authPending = true;
                authLaunched = openInBrowser(authUrl);

                if (spotifyConnector.isAuthServerRunning()) {
                    message = "Waiting for the Spotify login to be approved";
                } else {
                    message = "Spotify login server port 3000 is in use - the desktop app may already be connected";
                }
            } else {
                provider = DECKCAST;
                serverUrl = streamUrl;
                this.pin = (pin == null || pin.trim().isEmpty()) ? String.format("%04d", DEFAULT_PIN) : pin.trim();

                deckCastConnector = new DeckCastConnector(StreamDisplay.NONE, cassetteFlow, streamUrl, this.pin);
                deckCastConnector.setQueueListener(queueList -> {
                    lastQueue = queueList;

                    CountDownLatch latch = queueLatch;
                    if (latch != null) {
                        latch.countDown();
                    }
                });

                message = "Connecting to the DeckCast server";
            }

            response.put("ok", true);
            response.put("provider", provider);
            response.put("authUrl", authUrl);
            response.put("launched", authLaunched);
            response.put("authServerRunning", spotifyConnector != null && spotifyConnector.isAuthServerRunning());
            response.put("connected", isConnected());
            response.put("message", message);
            response.put("queueCount", queue.size());
            response.put("tableAvailable", isTableAvailable());
        } catch (Exception ex) {
            Logger.getLogger(StreamBackend.class.getName()).log(Level.SEVERE, "Stream connect failed", ex);
            message = "Could not connect to " + hint + ": " + ex.getMessage();
            return errorJson(message);
        }

        return response.toString();
    }

    /**
     * Disconnect from the streaming backend and stop any playback
     *
     * @return json status
     */
    public synchronized String disconnect() {
        disconnectInternal();

        try {
            JSONObject response = new JSONObject();
            response.put("ok", true);
            response.put("connected", false);
            response.put("provider", provider);
            response.put("message", message);

            return response.toString();
        } catch (JSONException ex) {
            return errorJson("Could not build the disconnect response");
        }
    }

    /**
     * Tear down the current backend
     */
    private void disconnectInternal() {
        stopTicker();
        playing = false;
        paused = false;

        if (spotifyConnector != null) {
            try {
                spotifyConnector.stopStream();
                spotifyConnector.stop();
            } catch (RuntimeException ex) {
                Logger.getLogger(StreamBackend.class.getName()).log(Level.WARNING, "Spotify disconnect failed", ex);
            }
            spotifyConnector = null;
        }

        if (deckCastConnector != null) {
            try {
                deckCastConnector.disConnect();
            } catch (RuntimeException ex) {
                Logger.getLogger(StreamBackend.class.getName()).log(Level.WARNING, "DeckCast disconnect failed", ex);
            }
            deckCastConnector = null;
        }

        provider = "";
        serverUrl = "";
        pin = "";
        authUrl = "";
        authPending = false;
        authLaunched = false;
        authStartedAt = 0;
        pendingLoadUrl = null;
        queueLatch = null;
        lastQueue = null;
        queue = new ArrayList<>();
        totalTapeTime = 0;
        tapeTime = 0;
        startTapeTime = 0;
        stopAtSec = -1;
        finished = false;
        lastConnected = false;
        message = "Stream player disconnected";
    }

    /**
     * Check if the current backend is connected
     *
     * @return
     */
    public boolean isConnected() {
        if (spotifyConnector != null) {
            return spotifyConnector.isConnected();
        }

        if (deckCastConnector != null) {
            return deckCastConnector.isConnected();
        }

        return false;
    }

    /**
     * The name of the current streaming backend
     *
     * @return Spotify, DeckCast or an empty string
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Open a url in the browser that is running on the server machine, exactly
     * like the desktop application does for the Spotify login
     *
     * @param url
     * @return true when a browser was launched
     */
    private boolean openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception ex) {
            Logger.getLogger(StreamBackend.class.getName()).log(Level.WARNING, "Could not launch the browser", ex);
        }

        return false;
    }

    // =========================================================================
    // Loading playlists
    // =========================================================================

    /**
     * Load a Spotify or YouTube playlist, album or track
     *
     * @param url the playlist/track url
     * @return json with the ids of the tracks that were added
     */
    public String load(String url) {
        if (provider.isEmpty() || (spotifyConnector == null && deckCastConnector == null)) {
            return errorJson("No streaming backend is connected");
        }

        if (spotifyConnector != null && !spotifyConnector.isConnected()) {
            // hold on to the request and run it as soon as the login completes
            pendingLoadUrl = url;
            authPending = true;
            message = "Waiting for the Spotify login before loading the track list";

            try {
                JSONObject response = new JSONObject();
                response.put("ok", true);
                response.put("queued", true);
                response.put("provider", provider);
                response.put("message", message);

                return response.toString();
            } catch (JSONException ex) {
                return errorJson(message);
            }
        }

        try {
            if (spotifyConnector != null) {
                return loadSpotify(url);
            }

            return loadDeckCast(url);
        } catch (Exception ex) {
            Logger.getLogger(StreamBackend.class.getName()).log(Level.SEVERE, "Stream load failed", ex);
            message = "Could not load the track list: " + ex.getMessage();
            return errorJson(message);
        }
    }

    /**
     * Load a Spotify playlist, album or track
     */
    private String loadSpotify(String url) {
        String[] media = parseSpotifyMedia(url);
        String kind = media[0];
        String mediaId = media[1];

        if (mediaId == null || mediaId.isEmpty()) {
            return errorJson("Could not work out the Spotify id from: " + url);
        }

        ArrayList<AudioInfo> tracks;
        if ("album".equals(kind)) {
            tracks = spotifyConnector.loadAlbum(mediaId, false);
        } else if ("track".equals(kind)) {
            tracks = spotifyConnector.loadTrack(mediaId, false);
        } else {
            tracks = spotifyConnector.loadPlaylist(mediaId, false);
        }

        if (tracks == null || tracks.isEmpty()) {
            return errorJson("Spotify returned no playable tracks");
        }

        return finishLoad(tracks, SPOTIFY);
    }

    /**
     * Ask the DeckCast server for the que list of a playlist url
     */
    private String loadDeckCast(String url) throws InterruptedException {
        lastQueue = null;
        CountDownLatch latch = new CountDownLatch(1);
        queueLatch = latch;

        deckCastConnector.loadPlaylist(url);

        boolean arrived = latch.await(DECKCAST_QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        queueLatch = null;

        ArrayList<AudioInfo> tracks = lastQueue;
        if (!arrived || tracks == null || tracks.isEmpty()) {
            return errorJson("DeckCast did not return a track list - check the stream url, pin and that the DeckCast player is open");
        }

        return finishLoad(tracks, "YouTube");
    }

    /**
     * Store the loaded tracks so the web app can pick them up
     */
    private synchronized String finishLoad(ArrayList<AudioInfo> tracks, String label) {
        queue = new ArrayList<>(tracks);
        totalTapeTime = getDctSize();
        tapeTime = 0;
        startTapeTime = 0;
        playing = false;
        paused = false;
        finished = false;
        stopAtSec = -1;
        stopTicker();

        applyProviderPrefix(label);
        loadToken++;

        String count = Integer.toString(queue.size());
        message = "Loaded " + count + " " + label + " tracks";

        try {
            JSONObject response = new JSONObject();
            JSONArray ids = new JSONArray();
            for (AudioInfo audioInfo : queue) {
                ids.put(audioInfo.getHash10C());
            }

            response.put("ok", true);
            response.put("provider", provider);
            response.put("label", label);
            response.put("count", queue.size());
            response.put("ids", ids);
            response.put("lengthSec", totalTapeTime);
            response.put("token", loadToken);
            response.put("message", message);

            return response.toString();
        } catch (JSONException ex) {
            return errorJson("Could not build the load response");
        }
    }

    /**
     * Add the Spotify/YouTube marker to the track names so they are recognisable
     * in the library list, the tape sides and the j-card
     */
    private void applyProviderPrefix(String label) {
        String prefix = "[" + label + "] ";

        for (AudioInfo audioInfo : queue) {
            String title = audioInfo.getTitle();

            if (title == null || title.isEmpty()) {
                title = audioInfo.getHash10C() == null ? "" : audioInfo.getHash10C();
            }

            if (!title.startsWith("[")) {
                audioInfo.setTitle(prefix + title);
            }
        }

        if (!queue.isEmpty()) {
            cassetteFlow.saveStreamAudioDBIndex();
        }
    }

    /**
     * Split a spotify url/uri into the media kind and id
     *
     * @param url
     * @return [kind, id]
     */
    private String[] parseSpotifyMedia(String url) {
        String value = url == null ? "" : url.trim();

        if (value.startsWith("spotify:")) {
            String[] parts = value.split(":");
            if (parts.length >= 3) {
                return new String[]{parts[1], parts[2]};
            }
        }

        if (value.startsWith("open.spotify.com/")) {
            value = "https://" + value;
        }

        return spotifyConnector.getSpotifyMediaId(value);
    }

    // =========================================================================
    // Playback
    // =========================================================================

    /**
     * Start playing a track from the loaded list
     *
     * @param id the library id of the track (may be null)
     * @param index the 0 based index in the loaded list
     * @param offsetSec seconds into the track to start at
     * @return json status
     */
    public synchronized String play(String id, int index, int offsetSec) {
        return play(id, index, offsetSec, true);
    }

    /**
     * Start playing a track from the loaded list
     *
     * @param id the library id of the track (may be null)
     * @param index the 0 based index in the loaded list
     * @param offsetSec seconds into the track to start at
     * @param single true to stop at the end of the track instead of carrying on
     * with the rest of the loaded list
     * @return json status
     */
    public synchronized String play(String id, int index, int offsetSec, boolean single) {
        if (provider.isEmpty() || (spotifyConnector == null && deckCastConnector == null)) {
            return errorJson("No streaming backend is connected");
        }

        if (queue.isEmpty()) {
            return errorJson("No tracks are loaded - paste a playlist url in the Stream Play dialog first");
        }

        int start = findTrackStart(id);
        AudioInfo track = findQueueItem(id);

        if (start < 0 && index >= 0 && index < queue.size()) {
            track = queue.get(index);
            start = findTrackStart(track.getHash10C());
        }

        // a single DeckCast video has no DCT list, it is just played from the start
        if (start < 0 && getDctList() == null && queue.size() == 1) {
            track = queue.get(0);

            if (id == null || id.isEmpty() || id.equals(track.getHash10C())) {
                start = 0;
            }
        }

        if (start < 0) {
            return errorJson("That track is not in the loaded " + provider
                    + " list - paste its playlist url in the Stream Play dialog first");
        }

        int offset = Math.max(0, offsetSec);
        int trackStart = start + offset;

        int size = getDctSize();
        if (size > 0 && trackStart >= size) {
            trackStart = size - 1;
        }

        stopAtSec = -1;
        if (single && track != null && track.getLength() > 0) {
            stopAtSec = start + track.getLength();
        }

        startPlayback(trackStart);

        return status();
    }

    /**
     * Find the tape time of the first playable second of a track
     *
     * @param id the library id of the track
     * @return the tape time or -1 when the track is not in the loaded list
     */
    private int findTrackStart(String id) {
        if (id == null || id.isEmpty()) {
            return -1;
        }

        ArrayList<String> dctList = getDctList();
        if (dctList == null) {
            return -1;
        }

        for (int i = 0; i < dctList.size(); i++) {
            String[] parts = dctList.get(i).split("_");

            if (parts.length < 4) {
                continue;
            }

            String hash = parts[2].replace("$", "_");
            if (hash.equals(id) && parts[3].chars().allMatch(Character::isDigit)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Find the loaded track that matches a library id
     *
     * @param id
     * @return the track or null
     */
    private AudioInfo findQueueItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        for (AudioInfo audioInfo : queue) {
            if (id.equals(audioInfo.getHash10C())) {
                return audioInfo;
            }
        }

        return null;
    }

    /**
     * Restart playback at the given tape time
     */
    private void startPlayback(int startSec) {
        stopTicker();

        if (isSpotify()) {
            spotifyConnector.resetPlaybackPosition();
        } else if (deckCastConnector != null) {
            deckCastConnector.resetPlaybackPosition();
        }

        tapeTime = Math.max(0, startSec);
        startTapeTime = tapeTime;
        startedAtMs = System.currentTimeMillis();
        playing = true;
        paused = false;
        finished = false;

        advance(tapeTime);
        message = "Streaming";

        ScheduledExecutorService exec = getScheduler();
        ticker = exec.scheduleAtFixedRate(this::tick, 1000, 250, TimeUnit.MILLISECONDS);
    }

    /**
     * Pause the current playback. The position is remembered so resume can carry
     * on from the same tape time.
     *
     * @return json status
     */
    public synchronized String pause() {
        if (!playing || paused) {
            return status();
        }

        int position = getLivePosition();
        paused = true;
        playing = false;
        tapeTime = position;
        stopTicker();

        if (isSpotify()) {
            spotifyConnector.stopStream();
        } else if (deckCastConnector != null) {
            deckCastConnector.stopStream();
        }

        message = "Paused";

        return status();
    }

    /**
     * Continue from the paused position
     *
     * @return json status
     */
    public synchronized String resume() {
        if (!paused || queue.isEmpty()) {
            return status();
        }

        startPlayback(tapeTime);

        return status();
    }

    /**
     * Stop playback and reset the position
     *
     * @return json status
     */
    public synchronized String stop() {
        stopTicker();

        if (isSpotify()) {
            spotifyConnector.stopStream();
        } else if (deckCastConnector != null) {
            deckCastConnector.stopStream();
        }

        playing = false;
        paused = false;
        finished = false;
        stopAtSec = -1;
        tapeTime = 0;
        startTapeTime = 0;
        message = "Stopped";

        return status();
    }

    /**
     * Advance the virtual tape and let the connector do the rest
     */
    private void tick() {
        synchronized (this) {
            if (!playing || paused) {
                return;
            }

            int size = getDctSize();
            int position = getLivePosition();

            int end = size;
            if (stopAtSec > 0 && (end <= 0 || stopAtSec < end)) {
                end = stopAtSec;
            }

            if (end > 0 && position >= end) {
                tapeTime = end;
                playing = false;
                finished = true;
                stopTicker();
                message = "Finished";

                if (isSpotify()) {
                    spotifyConnector.stopStream();
                } else if (deckCastConnector != null) {
                    deckCastConnector.stopStream();
                }

                return;
            }

            if (position <= tapeTime) {
                return;
            }

            tapeTime = position;
            advance(tapeTime);
        }
    }

    /**
     * Send the current tape time to the connector, this is the same call the tape
     * decoder makes in the desktop application
     */
    private void advance(int time) {
        if (isSpotify()) {
            spotifyConnector.playStream(time);
        } else if (deckCastConnector != null) {
            deckCastConnector.playStream(time);
        }
    }

    /**
     * The current position in seconds, taking the playing clock into account
     */
    private int getLivePosition() {
        if (!playing || paused) {
            return tapeTime;
        }

        int elapsed = (int) ((System.currentTimeMillis() - startedAtMs) / 1000L);
        return startTapeTime + Math.max(0, elapsed);
    }

    private boolean isSpotify() {
        return spotifyConnector != null;
    }

    private ScheduledExecutorService getScheduler() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "cassetteflow-stream-ticker");
                thread.setDaemon(true);
                return thread;
            });
        }

        return scheduler;
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel(false);
            ticker = null;
        }
    }

    // =========================================================================
    // State
    // =========================================================================

    /**
     * Get the status of the streaming backend
     *
     * @return json status
     */
    public synchronized String status() {
        syncConnectionState();
        runPendingLoad();

        try {
            JSONObject json = new JSONObject();
            json.put("ok", true);
            json.put("provider", provider);
            json.put("connected", isConnected());
            json.put("authPending", authPending);
            json.put("authUrl", authUrl);
            json.put("launched", authLaunched);
            json.put("authServerRunning", spotifyConnector != null && spotifyConnector.isAuthServerRunning());
            json.put("authSecondsRemaining", getAuthSecondsRemaining());
            json.put("pin", pin);
            json.put("serverUrl", serverUrl);
            json.put("playing", playing);
            json.put("paused", paused);
            json.put("positionSec", getLivePosition());
            json.put("lengthSec", getLengthSec());
            json.put("index", getCurrentTrackNumber());
            json.put("queueCount", queue.size());
            json.put("mute", isMuteSection());
            json.put("finished", finished);
            json.put("trackPositionSec", getTrackPositionSec());
            json.put("trackStartSec", getTrackStartSec());
            json.put("tableAvailable", isTableAvailable());
            json.put("token", loadToken);
            json.put("message", message);

            AudioInfo current = getCurrentAudioInfo();
            if (current != null) {
                json.put("title", current.getTitle() == null ? "" : current.getTitle());
                json.put("id", current.getHash10C() == null ? "" : current.getHash10C());
                json.put("trackLength", current.getLength());
            } else {
                json.put("title", "");
                json.put("id", "");
                json.put("trackLength", 0);
            }

            return json.toString();
        } catch (JSONException ex) {
            return errorJson("Could not build the stream status");
        }
    }

    /**
     * Work out if the login completed and flip the pending state over
     */
    private void syncConnectionState() {
        boolean connectedNow = isConnected();

        if (connectedNow && authPending) {
            authPending = false;
        }

        if (connectedNow != lastConnected) {
            lastConnected = connectedNow;

            if (connectedNow) {
                message = provider + " connected";
            } else if (!provider.isEmpty()) {
                message = provider + " is not connected";
            }
        }
    }

    /**
     * Run a load request that arrived before the Spotify login completed
     */
    private void runPendingLoad() {
        String url = pendingLoadUrl;

        if (url == null || !isConnected()) {
            return;
        }

        pendingLoadUrl = null;

        Thread loader = new Thread(() -> load(url), "cassetteflow-stream-pending-load");
        loader.setDaemon(true);
        loader.start();
    }

    private int getAuthSecondsRemaining() {
        if (!authPending) {
            return 0;
        }

        long elapsed = (System.currentTimeMillis() - authStartedAt) / 1000L;
        return (int) Math.max(0, AUTH_TIMEOUT_SECONDS - elapsed);
    }

    /**
     * The ids of the tracks that are currently loaded
     *
     * @return json array of ids
     */
    public synchronized String getQueueIds() {
        try {
            JSONObject json = new JSONObject();
            JSONArray ids = new JSONArray();

            for (AudioInfo audioInfo : queue) {
                ids.put(audioInfo.getHash10C());
            }

            json.put("ok", true);
            json.put("provider", provider);
            json.put("count", queue.size());
            json.put("ids", ids);
            json.put("token", loadToken);

            return json.toString();
        } catch (JSONException ex) {
            return errorJson("Could not build the track id list");
        }
    }

    /**
     * The tracks in the loaded stream list, as a self contained html table. Build
     * it from the audio info rather than the html the streaming backends produce,
     * because that html is a live control panel (player 1/2, remove and reorder
     * buttons, icon fonts) that only works inside the backend's own page.
     *
     * @return the table html or an empty string when nothing is loaded
     */
    public synchronized String getTrackTableHtml() {
        if (queue.isEmpty()) {
            return "";
        }

        int current = getCurrentTrackNumber();
        StringBuilder table = new StringBuilder();

        table.append("<table class=\"cf-table\"><thead><tr>")
                .append("<th class=\"cf-num\">#</th>")
                .append("<th class=\"cf-art\"></th>")
                .append("<th class=\"cf-title\">Track</th>")
                .append("<th class=\"cf-dur\">Time</th>")
                .append("</tr></thead><tbody>");

        for (int i = 0; i < queue.size(); i++) {
            AudioInfo audioInfo = queue.get(i);
            int number = i + 1;
            String image = getTrackImage(audioInfo);

            table.append("<tr");
            if (number == current) {
                table.append(" class=\"playing\"");
            }
            table.append(">");

            table.append("<td class=\"cf-num\">").append(number).append("</td>");
            table.append("<td class=\"cf-art\">");
            if (!image.isEmpty()) {
                // a background image rather than an <img> so a dead thumbnail shows
                // the neutral tile instead of a broken image icon
                table.append("<div class=\"cf-thumb\" style=\"background-image:url('")
                        .append(escapeCssUrl(image)).append("')\"></div>");
            }
            table.append("</td>");
            table.append("<td class=\"cf-title\">").append(escapeHtml(getTrackLabel(audioInfo))).append("</td>");
            table.append("<td class=\"cf-dur\">").append(escapeHtml(audioInfo.getLengthAsTime())).append("</td>");
            table.append("</tr>");
        }

        table.append("</tbody></table>");

        return table.toString();
    }

    /**
     * The number of tracks in the loaded stream list
     *
     * @return
     */
    public synchronized int getQueueCount() {
        return queue.size();
    }

    /**
     * The total play time of the loaded stream list in seconds
     *
     * @return
     */
    public synchronized int getLengthSec() {
        int size = getDctSize();

        if (size > 0) {
            return size;
        }

        // a single DeckCast video has no dct list, add the track lengths up instead
        int total = 0;
        for (AudioInfo audioInfo : queue) {
            total += audioInfo.getLength();
        }

        return total;
    }

    /**
     * The track name without the provider marker, the table header already says
     * which service the list came from
     */
    private String getTrackLabel(AudioInfo audioInfo) {
        String title = audioInfo.getTitle();

        if (title == null || title.isEmpty()) {
            title = audioInfo.getHash10C() == null ? "" : audioInfo.getHash10C();
        }

        if (title.startsWith("[Spotify] ")) {
            return title.substring("[Spotify] ".length());
        }

        if (title.startsWith("[YouTube] ")) {
            return title.substring("[YouTube] ".length());
        }

        return title;
    }

    /**
     * The thumbnail for a track. YouTube videos use their own still frame, Spotify
     * tracks use the album art the connector stored.
     */
    private String getTrackImage(AudioInfo audioInfo) {
        String url = audioInfo.getUrl() == null ? "" : audioInfo.getUrl();
        String streamId = audioInfo.getStreamId();

        if (url.contains("youtube") && streamId != null && !streamId.isEmpty()) {
            return "https://img.youtube.com/vi/" + streamId + "/mqdefault.jpg";
        }

        String image = audioInfo.getImageUrl();
        if (image == null || image.equals(DEFAULT_STREAM_IMAGE)) {
            return "";
        }

        return image;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Escape a url so it can be used inside a css url('...') value
     */
    private static String escapeCssUrl(String url) {
        return escapeHtml(url)
                .replace("'", "%27")
                .replace("(", "%28")
                .replace(")", "%29")
                .replace(" ", "%20");
    }

    /**
     * The title of the playlist/album that is loaded
     *
     * @return
     */
    public synchronized String getStreamTitle() {
        if (spotifyConnector != null) {
            return spotifyConnector.getStreamTitle();
        }

        return "";
    }

    /**
     * Check if there is a track table to show
     *
     * @return
     */
    public boolean isTableAvailable() {
        return !queue.isEmpty();
    }

    /**
     * The audio info of the track that is currently playing
     */
    private AudioInfo getCurrentAudioInfo() {
        AudioInfo current = null;

        if (spotifyConnector != null) {
            current = spotifyConnector.getCurrentAudioInfo();
        } else if (deckCastConnector != null) {
            current = deckCastConnector.getCurrentAudioInfo();
        }

        if (current != null) {
            return current;
        }

        String id = getCurrentTrackId();
        return id.isEmpty() ? null : cassetteFlow.audioInfoDB.get(id);
    }

    /**
     * The library id of the track at the current tape time
     */
    private String getCurrentTrackId() {
        ArrayList<String> dctList = getDctList();

        if (dctList == null || tapeTime < 0 || tapeTime >= dctList.size()) {
            return "";
        }

        String[] parts = dctList.get(tapeTime).split("_");
        if (parts.length < 3) {
            return "";
        }

        return parts[2].replace("$", "_");
    }

    /**
     * The 1 based track number that is playing
     */
    private int getCurrentTrackNumber() {
        ArrayList<String> dctList = getDctList();

        if (dctList == null || tapeTime < 0 || tapeTime >= dctList.size()) {
            return 0;
        }

        String[] parts = dctList.get(tapeTime).split("_");
        if (parts.length < 2) {
            return 0;
        }

        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * The dct fields for the current tape time, or null when there is none
     */
    private String[] getCurrentDctParts() {
        ArrayList<String> dctList = getDctList();

        if (dctList == null || tapeTime < 0 || tapeTime >= dctList.size()) {
            return null;
        }

        String[] parts = dctList.get(tapeTime).split("_");
        return parts.length >= 4 ? parts : null;
    }

    /**
     * How far into the current track playback is, in seconds
     */
    private int getTrackPositionSec() {
        String[] parts = getCurrentDctParts();

        if (parts == null) {
            return 0;
        }

        try {
            return Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * The tape time at which the current track started
     */
    private int getTrackStartSec() {
        return Math.max(0, tapeTime - getTrackPositionSec());
    }

    /**
     * Check if the current tape time is inside a mute section
     */
    private boolean isMuteSection() {
        ArrayList<String> dctList = getDctList();

        if (dctList == null || tapeTime < 0 || tapeTime >= dctList.size()) {
            return false;
        }

        String[] parts = dctList.get(tapeTime).split("_");
        return parts.length >= 4 && parts[3].contains("M");
    }

    private ArrayList<String> getDctList() {
        if (spotifyConnector != null) {
            return spotifyConnector.getDCTList();
        }

        if (deckCastConnector != null) {
            return deckCastConnector.getDCTList();
        }

        return null;
    }

    private int getDctSize() {
        ArrayList<String> dctList = getDctList();

        if (dctList != null) {
            return dctList.size();
        }

        return totalTapeTime;
    }

    private String errorJson(String error) {
        JSONObject json = new JSONObject();

        try {
            json.put("ok", false);
            json.put("error", error);
            json.put("provider", provider);
            json.put("connected", isConnected());
            json.put("message", error);
        } catch (JSONException ex) {
            return "{\"ok\":false,\"error\":\"stream backend error\"}";
        }

        return json.toString();
    }
}
