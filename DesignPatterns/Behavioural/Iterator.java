// The Iterator Design Pattern is a behavioral design pattern that allows us to traverse
//  a collection of objects (like arrays or lists) without exposing the underlying implementation details

public interface PlaylistIterator {
    boolean hasNext();
    String next();
}

public class SimplePlaylistIterator implements PlaylistIterator {
  private Playlist playlist;
  private int index;
  public SimplePlaylistIterator(Playlist playlist) {
    this.playlist = playlist;
    this.index = 0;
  }
  @Override
  public boolean hasNext() {
    return index < playlist.getSongs().size();
  }
  @Override
  public String next() {
    return playlist.getSongs().get(index++);
  }
}

public class ShuffledPlaylistIterator implements PlaylistIterator {
  private Playlist playlist;
  private int index;
  private ArrayList<String> shuffledSongs;
  public ShuffledPlaylistIterator(Playlist playlist) {
    this.playlist = playlist;
    this.shuffledSongs = new ArrayList<>(playlist.getSongs());
    Collections.shuffle(shuffledSongs); // Shuffle the songs randomly
    this.index = 0;
  }
  @Override
  public boolean hasNext() {
    return index < shuffledSongs.size();
  }
  @Override
  public String next() {
    return shuffledSongs.get(index++);
  }
}

public class FavoritesPlaylistIterator implements PlaylistIterator {
  private Playlist playlist;
  private int index;
  public FavoritesPlaylistIterator(Playlist playlist) {
    this.playlist = playlist;
    this.index = 0;
  }
  @Override
  public boolean hasNext() {
    // Only return the next song if it's marked as a favorite
    while (index < playlist.getSongs().size()) {
      if (playlist.getSongs().get(index).contains(
              "Fav")) { // Mark favorites with 'Fav' in name
        return true;
      }
      index++;
    }
    return false;
  }
  @Override
  public String next() {
    return playlist.getSongs().get(index++);
  }
}

public class Playlist {
  private ArrayList<String> songs;
  public Playlist() {
    songs = new ArrayList<>();
  }
  public void addSong(String song) {
    songs.add(song);
  }
  public PlaylistIterator iterator(String type) {
    switch (type) {
      case "simple":
        return new SimplePlaylistIterator(this);
      case "shuffled":
        return new ShuffledPlaylistIterator(this);
      case "favorites":
        return new FavoritesPlaylistIterator(this);
      default:
        return null;
    }
  }
  public ArrayList<String> getSongs() {
    return songs;
  }
}

public class Main {
  public static void main(String[] args) {
    // Create a playlist
    Playlist playlist = new Playlist();
    playlist.addSong("Song 1");
    playlist.addSong("Song 2 Fav");
    playlist.addSong("Song 3");
    playlist.addSong("Song 4 Fav");
    playlist.addSong("Song 5");

    // Simple Playlist Iterator
    System.out.println("Simple Playlist:");
    PlaylistIterator simpleIterator = playlist.iterator("simple");
    while (simpleIterator.hasNext()) {
      System.out.println("Playing: " + simpleIterator.next());
    }

    // Shuffled Playlist Iterator
    System.out.println("nShuffled Playlist:");
    PlaylistIterator shuffledIterator = playlist.iterator("shuffled");
    while (shuffledIterator.hasNext()) {
      System.out.println("Playing: " + shuffledIterator.next());
    }

    // Favorites Playlist Iterator
    System.out.println("nFavorites Playlist:");
    PlaylistIterator favoritesIterator = playlist.iterator("favorites");
    while (favoritesIterator.hasNext()) {
      System.out.println("Playing: " + favoritesIterator.next());
    }
  }
}