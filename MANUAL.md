# CassetteFlow Comprehensive User & Developer Manual

---

## 1. System Overview & Architecture

**CassetteFlow** is a hybrid analog-digital audio system that bridges physical compact cassette tapes with digital audio playback. By recording an inaudible or dedicated-track Frequency Shift Keying (FSK) timecode and metadata stream onto an analog cassette tape, CassetteFlow decodes the tape head position in real time using DSP demodulation and synchronizes lossless digital audio playback (MP3, FLAC, WAV) locally or across a local network.

### High-Level Architecture

```
+-----------------------------------------------------------------------------------+
|                              CassetteFlow Core Engine                              |
|                                                                                   |
|  [ Line-In / Mic ] ──► JMinimodem (1200 Baud FSK Demodulator / DSP)               |
|                                │                                                  |
|                                ▼                                                  |
|                        Tape Decoder Engine                                        |
|                     (Timecode / Track Hash Sync)                                  |
|                                │                                                  |
|            ┌───────────────────┴───────────────────┐                              |
|            ▼                                       ▼                              |
|   Local Playback Engine                Built-in CassetteFlowServer                |
|   (WavPlayer / JavaSound)                     (Port 8192)                         |
|                                ┌───────────────────┼───────────────────┐          |
|                                ▼                   ▼                   ▼          |
|                           /music/*              /raw &              /player       |
|                         (HTTP 206           /dcs_lite Telemetry    (Web App)      |
|                       Audio Streaming)             │                   │          |
+────────────────────────────────────────────────────┼───────────────────┼──────────+
                                                     ▼                   ▼
                                       ┌───────────────────────────────────┐
                                       | Any Browser / Phone / M5Core / PC |
                                       |     (http://<host>:8192/player)   |
                                       └───────────────────────────────────┘
```

---

## 2. Desktop Application & GUI Tabs

The CassetteFlow desktop interface is organized into distinct workflow tabs:

### 2.1. ENCODE Tab
The **ENCODE** tab manages your digital music library, builds mixtape playlists for Side A and Side B, and generates both printable cassette J-Cards and encoded FSK audio signals.

* **Audio Library Browser**:
  * Scans your configured audio directory (`C:\mp3files` by default) and displays all indexed MP3 and FLAC tracks.
  * Shows track duration, bitrate, filename, and ID3 tags (Title, Artist, Album, Year, Genre).
  * Includes a real-time search filter to quickly find tracks.
* **Side A & Side B Tracklist Builder**:
  * Add selected tracks to **Side A** or **Side B** with dedicated controls.
  * Displays total running time for each side and compares it against standard tape lengths (C-46, C-60, C-90, C-120) with remaining minute/second indicators.
  * Allows reordering tracks and setting custom inter-track mute padding (default 4 seconds).
* **Dynamic Content Track (DCT) vs. Standard FSK**:
  * **Standard FSK**: Encodes individual track hashes, track numbers, and playback seconds directly onto the tape.
  * **DCT (Dynamic Content Track)**: Encodes a continuous 29-character tape timecode (`DCT0A_01_aaaaaaaaaa_0945_0945`), allowing the tape to be mapped dynamically to any playlist in software without re-recording the analog data track.
* **J-Card Generator & Export**:
  * Generates formatted J-Card cassette inserts matching the exact tracklist, timings, and tape side distributions.
  * Integrates with J-Card templates for clean printing and cassette jewel case inserts.
  * Exports tracklist and tape configurations to JSON and text formats (`template.jcard.json`, `tracklist.txt`).

---

### 2.2. DECODE Tab
The **DECODE** tab provides live tape decoding, DSP demodulation, and automated digital playback synchronization.

* **JMinimodem Demodulator**:
  * Captures line-in audio from your tape deck and decodes 1200-baud Bell 202 FSK audio tones directly in software without external modem hardware.
* **Live Speed & Telemetry Monitoring**:
  * **Instantaneous Baud Rate**: Real-time edge-transition tracking (smoothed to ~1200 Baud).
  * **Speed Offset / Drift ($\pm\%$ )**: Measures exact tape motor speed variation relative to nominal speed (e.g. `+0.8%` or `-1.2%`).
  * **Modem SNR (Confidence Ratio)**: Measures signal-to-noise ratio in decibels (dB) to evaluate tape head alignment and signal quality.
  * **Signal Strength (%)**: Displays incoming audio carrier amplitude percentage.
* **Tape Synchronization & Playback Controls**:
  * **START**: Initializes the line-in audio stream and begins continuous decoding.
  * **STOP**: Halts decoding and pauses audio playback.
  * **RESET**: Clears telemetry counters, error tallies, and carrier holdover timers.
  * **Carrier Detect**: Detects active FSK carrier tone and automatically manages holdover timers during inter-track pauses or head lifts.

---

### 2.3. STREAM PLAY Tab
The **STREAM PLAY** tab enables synchronization between physical cassette tape playback and online/network streaming sources.

* **DeckCast & Spotify Integration**:
  * Connects tape timecode offsets to online streams and local streaming daemons.
  * Synchronizes Spotify track selection and playback position directly from tape head timecode.
* **Stream Controls**:
  * Supports dynamic stream offsets, track jumping, and remote streaming endpoints.

---

### 2.4. SETUP / CONSOLE Tab
The **SETUP / CONSOLE** tab configures hardware devices, directory paths, modem parameters, and provides raw system diagnostic logs.

* **Audio Hardware Configuration**:
  * **Input Mixer (Microphone / Line-In)**: Selects the audio input device capturing the cassette deck output.
  * **Output Mixer**: Selects the sound card or speaker output for digital audio playback.
* **Modem Parameters**:
  * Configures nominal baud rate (default: `1200`), mark frequency (`1200 Hz`), and space frequency (`2200 Hz`).
* **Directory & Property Management**:
  * Configure default audio directory path (`audio.directory` in `cassetteFlow.properties`).
  * Set log file paths and external service URLs.
* **Console Monitor**:
  * Live terminal logging showing raw FSK records, tape timecodes, decoded metadata, and HTTP server events.

---

## 3. Integrated Utilities & Standalone Tools

### 3.1. Tape Deck Tester (`TapeDeckTester`)
A dedicated diagnostic tool designed to test, measure, and calibrate cassette deck hardware.

* **Capabilities**:
  * Measures motor speed accuracy, drift, and wow & flutter over time.
  * Computes real-time modem confidence ratio (SNR) to test azimuth and head cleanliness.
  * Interactive CLI console commands:
    * `[S]` - Display comprehensive speed & telemetry statistics.
    * `[R]` - Reset statistical accumulators.
    * `[X]` - Exit tester.
* **Synthetic Stream Calibration (`DurationInputStream`)**:
  * Generates test DCT audio streams of precise durations for calibration and loopback verification.

---

### 3.2. Tape Database Manager (`TapeDatabaseFrame`) & Track Finder (`TrackFinderFrame`)
* **TapeDatabaseFrame**: Inspects, edits, and manages multi-tape collections saved in `tapedb.txt`.
* **TrackFinderFrame**: Searches indexed tracks across large offline libraries and matches them against tape playlists.

---

## 4. Headless & Command-Line Interface (CLI) Mode

CassetteFlow can run completely headless without a graphical user interface—ideal for Raspberry Pi, home servers, or background services.

### Running in CLI Mode
```bash
# Basic CLI mode (interactive audio output prompt)
java -jar dist/CassetteFlow.jar -cli

# Headless mode with audio output device 0 pre-selected
java -jar dist/CassetteFlow.jar -cli -d 0

# Headless mode with audio index rebuilt on startup
java -jar dist/CassetteFlow.jar -cli -index -d 0
```

### CLI Command Options
| Flag | Parameter | Description |
|---|---|---|
| `-cli` | *None* | Runs in Command Line Interface (headless) mode |
| `-d`, `--device` | `<index>` | Selects audio output mixer by numeric index (e.g. `-d 0`) |
| `-index` | *None* | Scans audio directory and rebuilds `audiodb.bin` & `audiodb.txt` |
| `-dir` | `<path>` | Loads audio files from specified custom directory |
| `-dct` | `<tapeId>` | Maps DCT timecode stream to a specific Tape ID (e.g. `-dct CS00`) |
| `-dct-load` | *None* | Explicitly loads saved `tape.dct` file on startup |
| `-nodct` | *None* | Explicitly disables DCT loading (clean raw stream mode) |
| `-h`, `--help` | *None* | Displays CLI help message |

### Monitoring CLI Resource Usage
* **Windows (PowerShell Live Monitor)**:
  ```powershell
  while($true) { Clear-Host; Get-Process java | Select-Object Id, CPU, @{Name="RAM (MB)"; Expression={[math]::round($_.CPU, 1)}}, @{Name="RAM (MB)"; Expression={[math]::round($_.WorkingSet64/1MB, 1)}}; Start-Sleep 2 }
  ```
* **Linux / Raspberry Pi**:
  ```bash
  htop -p $(pgrep -f CassetteFlow)
  ```

---

## 5. Built-in Web Server & REST API (`CassetteFlowServer` - Port 8192)

CassetteFlow includes a high-performance, multithreaded embedded HTTP server (`CassetteFlowServer.java`) listening on **port 8192**. It serves the web player, streams audio with byte-range seek support, and exposes REST endpoints for microcontrollers (ESP32, M5Core) and web clients.

### API Endpoint Reference Table

| Endpoint | Method | Response Format | Description |
|---|---|---|---|
| `/player` | `GET` | `text/html` | Serves the single-page CassetteFlow Web Player |
| `/player.html` | `GET` | `text/html` | Web player alias |
| `/music/*` | `GET`, `HEAD` | Audio (`audio/mpeg`, `audio/flac`, etc.) | **HTTP 206 Partial Content** audio stream supporting byte-range scrubbing and seek (`Range: bytes=start-end`) |
| `/music?id=<hash>` | `GET` | Audio | Streams audio track resolved directly by 10-character hash |
| `/raw` | `GET` | `text/plain` (Chunked) | Real-time persistent stream of raw decoded FSK line records |
| `/rawdct` | `GET` | `text/plain` (Chunked) | Real-time persistent stream of raw FSK records with DCT translation |
| `/dcs_lite` | `GET` | `application/json` | Compact telemetry JSON for ESP32/web clients (`baud`, `speedOffset`, `snr`, `sig`, `data_errors`, `total_recs`) |
| `/dcs` | `GET` | `application/json` | Full decode state JSON including track lists and playback status |
| `/api/status` | `GET` | `application/json` | Alias for `/dcs_lite` providing unified telemetry |
| `/diag` | `GET` | `text/plain` | Formatted tape diagnostics and speed telemetry summary |
| `/audiodb.txt` | `GET` | `text/plain` | 5-column standard audio database (from disk or memory) |
| `/tapedb.txt` | `GET` | `text/plain` | Tape collection database (from disk or memory) |
| `/tracklist.txt` | `GET` | `text/plain` | Current active tracklist |
| `/dcc?command=<cmd>` | `GET` | `text/plain` | Executes remote decoder commands (`start`, `stop`, `reset`) |
| `/play?track=<num>` | `GET` | `text/plain` | Triggers playback of specified track |
| `/stop` | `GET` | `text/plain` | Stops playback |

---

## 6. Embedded Web Player (`player.html`)

Access the player by navigating to `http://localhost:8192/player` (or `http://<server-ip>:8192/player`) on any device on your network.

### Player Features
1. **Cassette Visualizer**: Animated vintage cassette reels that turn dynamically with tape playback state.
2. **Tracks View**: View active Side A, Side B, or Combined tracklists with track numbers, titles, artists, and durations.
3. **Tape DB View**: Switch between any tape in your collection (`Tape CS00`, `Tape EX01`, etc.) and preview sides.
4. **Node Folders View**: Browse albums and subfolders naturally. The **Root** folder isolates top-level test tracks, while nested albums appear in clean folder groupings.
5. **Modem & Signal Telemetry**:
   * **Baud Rate**: Live instantaneous modem baud rate.
   * **Speed Drift**: Live percentage offset ($\pm\%$).
   * **Modem SNR**: Live carrier SNR in dB.
   * **Decode Errors**: Displays `errors / total_records` (e.g. `0 / 142`).
6. **Built-in Terminal**: Live scrollable terminal logging every incoming raw FSK line record, side auto-flips, and drift adjustments.

---

## 7. Data Formats & Protocol Specifications

### 7.1. FSK Line Record Formats

#### Standard Track Record (5 Underscore-Delimited Fields)
```
EX01A_01_a516a5e955_012_012
 │    │       │      │   └─ Total tape elapsed seconds (012)
 │    │       │      └───── Track playback seconds (012, or 012M for mute)
 │    │       └──────────── 10-character track hash ID
 │    └──────────────────── Track number (01)
 └───────────────────────── Tape ID and Side (EX01 Side A)
```

#### Dynamic Content Track (DCT) Record (29 Characters)
```
DCT0A_01_aaaaaaaaaa_0945_0945
 │    │       │       │    └─ Total tape elapsed seconds (4 digits: 0945)
 │    │       │       └────── Side elapsed seconds (4 digits: 0945)
 │    │       └────────────── 10-character dummy placeholder hash (aaaaaaaaaa)
 │    └────────────────────── Track indicator / segment number (2 digits: 01)
 └─────────────────────────── DCT Header & Side (DCT0A = Side A, DCT0B = Side B)
```

#### Carrier Loss / Noise Identifiers
* `### NOCARRIER ###` - Carrier tone dropped (head lifted, tape stopped, or end of side).
* `### NOISY ###` - Signal degraded below demodulation threshold.

---

### 7.2. Database Formats

#### `audiodb.txt` (5-Column Tab-Delimited Standard)
```
<Hash10C>\t<DurationSec>\t<Bitrate>\t/sdcard/<SDFilename>\t<RelativePath>
```
* **Example**:
  ```
  a516a5e955	245	320	/sdcard/01_Song.mp3	Rock/Led Zeppelin/IV/01_Song.mp3
  ```

#### `tapedb.txt` (Tab-Delimited Tape Sides)
```
<TapeSideID>\t<Hash1>\t<Hash2>\t<Hash3>...
```
* **Example**:
  ```
  EX01A	a516a5e955	b627c8f102	c938d4e311
  EX01B	d149e5f422	e250f6a533	f361a7b644
  ```

---

## 8. Configuration File Reference (`cassetteFlow.properties`)

```properties
# CassetteFlow Global Configuration
audio.directory=C\:\\mp3files
baud.rate=1200
download.server=http\://192.168.1.45/~pi/mp3/
jcard.site=https\://ed7n.github.io/jcard-template/
minimodem.log.file=C\:\\mp3files\\TapeFiles\\tape.log
```
