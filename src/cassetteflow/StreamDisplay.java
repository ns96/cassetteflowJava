package cassetteflow;

import java.util.ArrayList;

/**
 * The subset of the desktop STREAM PLAY panel that the streaming connectors need
 * in order to report what they are doing.
 *
 * CassetteFlowFrame implements this interface so the desktop keeps behaving
 * exactly as it does today, and StreamBackend passes the no-op singleton below
 * so the very same SpotifyConnector and DeckCastConnector code can run headless
 * behind the web server.
 *
 * @author Nathan
 */
public interface StreamDisplay {

    /**
     * Display the html table returned by the streaming backend
     *
     * @param text the html fragment
     */
    default void updateStreamEditorPane(String text) {
    }

    /**
     * Indicate that the stream player is connected
     *
     * @param connected
     */
    default void setStreamPlayerConnected(boolean connected) {
    }

    /**
     * Update the playback information area
     *
     * @param info
     * @param append
     */
    default void setPlaybackInfo(String info, boolean append) {
    }

    /**
     * Update the playback information area
     *
     * @param info
     * @param append
     * @param newLine
     */
    default void setPlaybackInfo(String info, boolean append, String newLine) {
    }

    /**
     * Show what is currently being played
     *
     * @param info
     */
    default void setPlayingAudioInfo(String info) {
    }

    /**
     * The track number that is being played
     *
     * @param track
     */
    default void setPlayingAudioTrack(String track) {
    }

    /**
     * The tape/cassette id that the stream tracks are mapped onto
     *
     * @param cassetteID
     */
    default void setPlayingCassetteID(String cassetteID) {
    }

    /**
     * Update the stream time labels
     *
     * @param streamId
     * @param streamTotalTime
     * @param streamPlayer
     */
    default void setStreamInformation(String streamId, int streamTotalTime, int streamPlayer) {
    }

    /**
     * Update the stream play time
     *
     * @param streamPlaytime
     * @param track
     */
    default void updateStreamPlaytime(int streamPlaytime, String track) {
    }

    /**
     * Move on to the next DCT time block (DCT padding support)
     */
    default void incrementDCTDecodeOffset() {
    }

    /**
     * Show the tracks that a streaming backend returned
     *
     * @param streamer the name of the streaming service
     * @param streamingTrackList the tracks that were loaded
     */
    default void loadStreamingTracks(String streamer, ArrayList<AudioInfo> streamingTrackList) {
    }

    /**
     * A display that ignores everything, used when the connectors run headless
     */
    StreamDisplay NONE = new StreamDisplay() {
    };
}
