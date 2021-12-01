/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cassetteflow;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Make use of the java FX player for mp3 playback
 * @author Nathan
 */
public class FXPlayer  extends Application {
    public static MediaPlayer mediaPlayer;
       
    @Override
    public void start(Stage primaryStage) throws Exception {
        String path = "C:\\mp3files\\Feel Nice - Rick Steel.mp3";  
          
        //Instantiating Media class  
        Media media = new Media(new File(path).toURI().toString());  
          
        //Instantiating MediaPlayer class   
        mediaPlayer = new MediaPlayer(media); 
          
        //by setting this property to true, the audio will be played   
        mediaPlayer.setAutoPlay(true);  
    }
    
    public static void main(String[] args) {
        Thread thread = new Thread(() -> {
            while(true) {
                if(mediaPlayer != null) {
                    Duration duration = mediaPlayer.getCurrentTime();
                    System.out.println("Currenr Playitme: " + duration.toSeconds());    
                }
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(FXPlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        thread.start();
        
        launch(args);
    }
}
