package cassetteflow;

import java.io.File;

public class FlacAudioPlayerTest {

    public static void main(String[] args) {
        System.out.println("Starting FlacAudioPlayer validation...");
        File flacFile = new File("c:\\Users\\Nathan\\Documents\\NetBeansProjects\\jflac\\jflac-codec\\01-02-Ulises_Hermosa-Lambada-LLS.flac");

        if (!flacFile.exists()) {
            System.err.println("Test file not found: " + flacFile.getAbsolutePath());
            System.exit(1);
        }

        try {
            FlacAudioPlayer player = new FlacAudioPlayer();
            player.setMixerName("Default Playback Device");
            player.setSpeedFactor(1.0);

            // Test 1: Open file
            System.out.println("1. Opening file: " + flacFile.getName());
            player.open(flacFile);
            System.out.println("   Successfully opened file.");

            // Test 2: Seek to 10 seconds
            System.out.println("2. Seeking to 10 seconds...");
            player.seekTo(10);
            System.out.println("   Successfully seeked to 10s.");

            // Test 3: Seek to 137 seconds (user's target test case)
            System.out.println("3. Seeking to 137 seconds...");
            player.seekTo(137);
            System.out.println("   Successfully seeked to 137s.");

            // Test 4: Seek to near end (190 seconds)
            System.out.println("4. Seeking to 190 seconds...");
            player.seekTo(190);
            System.out.println("   Successfully seeked to 190s.");

            // Test 5: Re-open and seek to 0 seconds
            System.out.println("5. Re-opening and seeking to 0s...");
            player.open(flacFile);
            player.seekTo(0);
            System.out.println("   Successfully seeked to 0s.");

            // Test 6: Clean stop
            System.out.println("6. Stopping player...");
            player.stop();
            System.out.println("   Successfully stopped player.");

            System.out.println("\nAll FlacAudioPlayer tests PASSED!");
        } catch (Exception e) {
            System.err.println("Test failed with exception:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
