// Problem Statement: Simplifying Complexity with a Unified Interface 
// Imagine you’re designing a multimedia application. The app needs to provide users with an 
// easy way to perform actions like playing music, watching videos, or viewing images.
//  However, each type of media has its own complex subsystem:

// • Music Player: Requires initializing audio drivers, decoding audio formats, and managing playback.

// • Video Player: Involves setting up rendering engines, handling codecs, and managing screen resolutions.

// • Image Viewer: Needs to load image files, apply scaling, and render them on the screen.

// The Problem: Users want a simple, intuitive interface to interact with the application, 
// but the underlying subsystems are complex and diverse. Exposing these subsystems directly 
// to users would overwhelm them and increase the likelihood of errors.

import java.util.Scanner;
public class MultimediaApp {
  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    System.out.println("Choose an action: playMusic, playVideo, viewImage");
    String action = scanner.nextLine();
    if (action.equalsIgnoreCase("playMusic")) {
      MusicPlayer musicPlayer = new MusicPlayer();
      musicPlayer.initializeAudioDrivers();
      musicPlayer.decodeAudio();
      musicPlayer.startPlayback();
    } else if (action.equalsIgnoreCase("playVideo")) {
      VideoPlayer videoPlayer = new VideoPlayer();
      videoPlayer.setupRenderingEngine();
      videoPlayer.loadVideoFile();
      videoPlayer.playVideo();
    } else if (action.equalsIgnoreCase("viewImage")) {
      ImageViewer imageViewer = new ImageViewer();
      imageViewer.loadImageFile();
      imageViewer.applyScaling();
      imageViewer.displayImage();
    } else {
      System.out.println("Invalid action!");
    }
    scanner.close();
  }
}

// Solution

public class MusicPlayer {
  public void initializeAudioDrivers() {
    System.out.println("Audio drivers initialized.");
  }
  public void decodeAudio() {
    System.out.println("Audio decoded.");
  }
  public void startPlayback() {
    System.out.println("Music playback started.");
  }
}

public class VideoPlayer {
    public void setupRenderingEngine() {
        System.out.println("Rendering engine set up.");
    }
    public void loadVideoFile() {
        System.out.println("Video file loaded.");
    }
    public void playVideo() {
        System.out.println("Video playback started.");
    }
}

public class ImageViewer {
    public void loadImageFile() {
        System.out.println("Image file loaded.");
    }
    public void applyScaling() {
        System.out.println("Image scaled.");
    }
    public void displayImage() {
        System.out.println("Image displayed.");
    }
}

public class MediaFacade {
  private MusicPlayer musicPlayer;
  private VideoPlayer videoPlayer;
  private ImageViewer imageViewer;
  public MediaFacade() {
    this.musicPlayer = new MusicPlayer();
    this.videoPlayer = new VideoPlayer();
    this.imageViewer = new ImageViewer();
  }
  public void performAction(String action) {
    switch (action.toLowerCase()) {
      case "playmusic":
        musicPlayer.initializeAudioDrivers();
        musicPlayer.decodeAudio();
        musicPlayer.startPlayback();
        break;
      case "playvideo":
        videoPlayer.setupRenderingEngine();
        videoPlayer.loadVideoFile();
        videoPlayer.playVideo();
        break;
      case "viewimage":
        imageViewer.loadImageFile();
        imageViewer.applyScaling();
        imageViewer.displayImage();
        break;
      default:
        System.out.println("Invalid action!");
    }
  }
}

public class MultimediaApp {
  public static void main(String[] args) {
    MediaFacade mediaFacade = new MediaFacade();
    Scanner scanner = new Scanner(System.in);
    System.out.println("Welcome to Multimedia App!");
    System.out.println("Choose an action: playMusic, playVideo, viewImage");
    String action = scanner.nextLine();
    mediaFacade.performAction(action);
    scanner.close();
  }
}