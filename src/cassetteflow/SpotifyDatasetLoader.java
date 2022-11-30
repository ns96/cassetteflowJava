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
import kotlin.text.Typography;

/**
 * A class to load a tab delimited containing a strip down version of the 
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
    private CassetteFlow cassetteFlow;
    private final String[] MUSIC_GENRES;
    
    /**
     * Default constructor which contains  
     * 
     * @param cassetteFlow 
     */
    public SpotifyDatasetLoader(CassetteFlow cassetteFlow) {
        this.cassetteFlow = cassetteFlow;
        
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
            int directoryNumber = 0;
            int albumCount = 0;
            
            ArrayList<AudioInfo> trackList;
            int genreIndex;
            String genre = "unknown";
            AudioInfo audioInfo;
            
            while((line = br.readLine()) != null) {
                String[] sa = line.split(splitBy);
                String albumId = sa[1];
                String album = sa[2];
                String artist = sa[3]; 
                String filename = sa[7] + ".mp3";
                
                int length = Integer.parseInt(sa[8]);
                String lengthAsTime = CassetteFlowUtil.getTimeString(length);
                 
                if(albumMap.containsKey(albumId)) {
                    trackList = albumMap.get(albumId);
                } else {
                    albumCount++;
                    
                    genreIndex = random.nextInt(MUSIC_GENRES.length);
                    genre = MUSIC_GENRES[genreIndex];
                    
                    trackList = new ArrayList<>();
                    albumMap.put(albumId, trackList);
                }
                
                // let create a dummy directory
                if (albumCount % 200 == 0) {
                    directoryNumber++;
                }
                
                String dummyDirectory = "!Spotify" + String.format("%03d", directoryNumber);
                String sha10hex = CassetteFlowUtil.get10CharacterHash(filename);
                File dummyFile = new File(dummyDirectory, filename);
                
                audioInfo = new AudioInfo(dummyFile, sha10hex, length, lengthAsTime, 323);
                audioInfo.setArtist(artist);
                audioInfo.setGenre(genre);
                audioInfo.setAlbum(album);
                trackList.add(audioInfo);
                
                i++;
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
     * @return 
     */
    public ArrayList<AudioInfo> getSample(int sampleSize) {
        ArrayList<AudioInfo> sampleList = new ArrayList<>();
        Random generator = new Random();
        
        if(sampleSize == -1) {
            sampleSize = albumMap.size();
        }
        
        Object[] values = albumMap.values().toArray();
        for(int i = 0; i < sampleSize; i++) {
            ArrayList<AudioInfo> albumList = (ArrayList<AudioInfo>)values[generator.nextInt(values.length)];
            sampleList.addAll(albumList);
        }
        
        System.out.println( sampleSize + " Albums / " + sampleList.size() + " tracks ...");
        
        return sampleList;
    }
    
    
    /**
     * Main method for testing
     * @param args 
     */
    public static void main(String[] args) {
        File file = new File("C:\\Users\\Nathan\\Documents\\SpotifyDataset\\tracks.csv");
        SpotifyDatasetLoader spotifyDatasetLoader = new SpotifyDatasetLoader(null);
        spotifyDatasetLoader.loadDataset(file);
        spotifyDatasetLoader.getSample(12000);
    }
}
