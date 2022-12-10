package cassetteflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class to load a tab delimited containing a subset version of the 
 * Spotify dataset from kaggle
 * 
 * https://www.kaggle.com/datasets/rodolfofigueroa/spotify-12m-songs
 * 
 * The purpose of this is for testing purposes
 * 
 * @author Nathan
 */
public class SpotifyDatasetLoader {
    private Random random = new Random();
    private HashMap<String, ArrayList<AudioInfo>> albumMap = new HashMap<>();
    private String[] MUSIC_GENRES;
    
    private boolean stop = false; // use to prevent infite loop situation when building sample
    
    /**
     * Default constructor which creates an array of genres 
     */
    public SpotifyDatasetLoader() {
        // used to randonly set the genre
        MUSIC_GENRES = new String[]{"POP", "Rock", "Hip-Hop & Rap", 
                "Country", "R&B", "Folk", "Jazz", "Heavy Metal", "EDM",
                "Soul", "Funk", "Reggae", "Disco", "Classical", "House", 
                "Techno", "Grunge", "Ambient", "Gospel", "Latin Music", 
                "Trap"};
    }
    
    /**
     * Read in a certain number of albums
     * 
     * @param file
     */
    public void loadDataset(File file) {
        BufferedReader br = null;
        try {
            String line = "";
            String splitBy = "\t";
            
            br = new BufferedReader(new FileReader(file));
            br.readLine(); // read header
            
            int i = 0;
            int albumCount = 0;
            int folderCount = 0;
            
            ArrayList<AudioInfo> trackList;
            int genreIndex;
            String genre = "unknown";
            String ext = ".mp3";
            AudioInfo audioInfo;
            
            while((line = br.readLine()) != null) {
                String[] sa = line.split(splitBy);
                String albumId = sa[1];
                String album = sa[2];
                String artist = sa[3];
                String filename = sa[7] + ext;
                
                String year = sa[6];
                int length = Integer.parseInt(sa[8]);
                String lengthAsTime = CassetteFlowUtil.getTimeString(length);
                 
                if(albumMap.containsKey(albumId)) {
                    trackList = albumMap.get(albumId);
                } else {
                    albumCount++;
                    
                    if(albumCount%100 == 0) {
                        ext = ".flac";
                    } else {
                        ext = ".mp3";
                    }
                    
                    genreIndex = random.nextInt(MUSIC_GENRES.length);
                    genre = MUSIC_GENRES[genreIndex];
                    
                    trackList = new ArrayList<>();
                    albumMap.put(albumId, trackList);
                }
                                
                String dummyDirectory = "!Spotify" + String.format("%06d", folderCount);
                String sha10hex = CassetteFlowUtil.get10CharacterHash(filename);
                File dummyFile = new File(dummyDirectory, filename);
                
                audioInfo = new AudioInfo(dummyFile, sha10hex, length, lengthAsTime, 323);
                audioInfo.setArtist(artist);
                audioInfo.setGenre(genre);
                audioInfo.setAlbum(album);
                audioInfo.setYear(year);
                trackList.add(audioInfo);
                
                i++;
                
                // add 400 tracks to each dummy folder
                if(i%501 == 0) {
                    folderCount++;
                }
                
                if(i%10000 == 0) {
                    System.out.println(i + " Lines Processed ...");
                }
            }
            
            System.out.println("\nNumber of Albums: " + albumMap.size());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SpotifyDatasetLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SpotifyDatasetLoader.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                Logger.getLogger(SpotifyDatasetLoader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Return a sample of albums
     * @param sampleSize
     * @param audioInfoDB Stores the sample records here
     * @return number of tracks found
     */
    public int getSample(int sampleSize, HashMap<String, AudioInfo> audioInfoDB) {
        Random generator = new Random();
        
        if(sampleSize == -1) {
            sampleSize = albumMap.size();
        }
        
        Object[] values = albumMap.values().toArray();
        int tracks = 0;
        int i = 0;
        
        do {
            ArrayList<AudioInfo> albumList = (ArrayList<AudioInfo>)values[generator.nextInt(values.length)];
            
            if (albumList.size() > 5) { // only take albums with at least 5 tracks
                for (AudioInfo audioInfo : albumList) {
                    String key = audioInfo.getHash10C();
                    if (!audioInfoDB.containsKey(key)) {
                        audioInfoDB.put(audioInfo.getHash10C(), audioInfo);
                        tracks++;
                    }
                }
                i++;
            }
        } while (i < sampleSize && !stop);
        
        System.out.println( sampleSize + " Albums / " + tracks + " tracks ...");
        
        return tracks;
    }
    
    /**
     * Set the stop variable to prevent infinite loops
     * 
     * @param stop 
     */
    public void setStop(boolean stop) {
        this.stop  =stop;
    }
    
    /**
     * Main method for testing
     * @param args 
     */
    public static void main(String[] args) {
        File file = new File("C:\\mp3files\\spotify_tracks.tsv");
        SpotifyDatasetLoader spotifyDatasetLoader = new SpotifyDatasetLoader();
        spotifyDatasetLoader.loadDataset(file);
        spotifyDatasetLoader.getSample(12000, new HashMap<>());
    }
}
