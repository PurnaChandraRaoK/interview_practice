// Observer Design Pattern: How to Stay Updated Without Constantly Checking 

public interface Observer {
  void update(String video); // This is the method the observer will use to get updated with the new video
}

public class YouTubeObserver implements Observer {
  private String name; // Name of the Observer

  public YouTubeObserver(String name) {
    this.name = name; // Initialize the Observer with their name
  }

  @Override
  public void update(String video) {
    // When notified, this method will execute, and the Observer watches the
    // new video
    System.out.println(name + " is watching the video: " + video);
  }
}

public class EmailObserver implements Observer {
  private String email;
  public EmailObserver(String email) {
    this.email = email;
  }

  @Override
  public void update(String video) {
    System.out.println(
        "Sending email to " + email + ": New video uploaded: " + video);
  }
}

public class PushNotificationObserver implements Observer {
  private String userDevice;
  public PushNotificationObserver(String userDevice) {
    this.userDevice = userDevice;
  }

  @Override
  public void update(String video) {
    System.out.println("Sending push notification to " + userDevice
        + ": New video uploaded: " + video);
  }
}

public interface YouTubeChannelSubject {
  void addObserver(Observer Observer); // Method to add a new Observer
  void removeObserver(Observer Observer); // Method to remove a Observer
  void notifyObservers(); // Method to notify all Observers
}

public class YouTubeChannelImpl implements YouTubeChannelSubject {
  private List<Observer> Observers =
      new ArrayList<>(); // List of Observers
  private String video; // The video that will be uploaded

  @Override
  public void addObserver(Observer Observer) {
    Observers.add(Observer); // Add a Observer to the channel
  }

  @Override
  public void removeObserver(Observer Observer) {
    Observers.remove(Observer); // Remove a Observer from the channel
  }

  @Override
  public void notifyObservers() {
    // Notify all Observers about the new video
    for (Observer Observer : Observers) {
      Observer.update(video); // Call update() for each Observer
    }
  }

  public void uploadNewVideo(String video) {
    this.video = video; // Set the video that is being uploaded
    notifyObservers(); // Notify all Observers about the new video
  }
}

public class Main {
  public static void main(String[] args) {
    // Create a YouTube channel
    YouTubeChannelImpl channel = new YouTubeChannelImpl();
    // Create Observers
    YouTubeObserver alice = new YouTubeObserver("Alice");
    YouTubeObserver bob = new YouTubeObserver("Bob");
    // Subscribe to the channel
    channel.addObserver(alice);
    channel.addObserver(bob);
    // Upload a new video and notify Observers
    channel.uploadNewVideo("Java Design Patterns Tutorial");
    // Output:
    // Alice is watching the video: Java Design Patterns Tutorial
    // Bob is watching the video: Java Design Patterns Tutorial
    // You can also remove a Observer and upload another video
    channel.removeObserver(bob);
    channel.uploadNewVideo("Observer Pattern in Action");
    // Output:
    // Alice is watching the video: Observer Pattern in Action
  }
}