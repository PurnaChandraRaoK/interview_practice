import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spotify LLD (simple, interview-ready)
 * Added: Artist, Album, basic relations, playAlbum/playArtist
 * Focus: flow + correctness (queue, shuffle, repeat, player state)
 */
public class SpotifyLLD {

    // ---------- Domain ----------
    static final class Song {
        final String id;
        final String title;
        final int durationSec;
        final String albumId;                 // link
        final List<String> artistIds;         // link

        Song(String id, String title, int durationSec, String albumId, List<String> artistIds) {
            this.id = Objects.requireNonNull(id);
            this.title = Objects.requireNonNull(title);
            this.durationSec = durationSec;
            this.albumId = albumId; // can be null
            this.artistIds = (artistIds == null) ? List.of() : List.copyOf(artistIds);
        }

        @Override public String toString() { return title + " (" + id + ")"; }
    }

    static final class Artist {
        final String id;
        final String name;

        Artist(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }

        @Override public String toString() { return name + " (" + id + ")"; }
    }

    static final class Album {
        final String id;
        final String name;
        final List<String> artistIds;
        final List<String> songIds = new ArrayList<>();

        Album(String id, String name, List<String> artistIds) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.artistIds = (artistIds == null) ? List.of() : List.copyOf(artistIds);
        }

        public List<String> songIdsView() { return Collections.unmodifiableList(songIds); }
        void addSong(String songId) { songIds.add(songId); }

        @Override public String toString() { return name + " (" + id + ")"; }
    }

    static final class User {
        final String id;
        final String name;

        User(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }
    }

    static final class Playlist {
        final String id;
        final String ownerUserId;
        final String name;
        private final List<String> songIds = new ArrayList<>();
        private boolean isPublic;

        Playlist(String id, String ownerUserId, String name, boolean isPublic) {
            this.id = Objects.requireNonNull(id);
            this.ownerUserId = Objects.requireNonNull(ownerUserId);
            this.name = Objects.requireNonNull(name);
            this.isPublic = isPublic;
        }

        public List<String> songIdsView() { return Collections.unmodifiableList(songIds); }
        public boolean isPublic() { return isPublic; }
        public void setPublic(boolean aPublic) { isPublic = aPublic; }

        void addSong(String songId) { songIds.add(songId); }
        void removeSong(String songId) { songIds.remove(songId); }
        void move(int fromIdx, int toIdx) {
            if (fromIdx < 0 || fromIdx >= songIds.size() || toIdx < 0 || toIdx >= songIds.size()) return;
            String s = songIds.remove(fromIdx);
            songIds.add(toIdx, s);
        }
    }

    enum RepeatMode { OFF, ONE, ALL }

    // ---------- Catalog ----------
    interface Catalog {
        Song getSong(String songId);
        Artist getArtist(String artistId);
        Album getAlbum(String albumId);

        List<Song> searchSongByTitlePrefix(String prefix);
        List<Artist> searchArtistByNamePrefix(String prefix);
        List<Album> searchAlbumByNamePrefix(String prefix);

        List<String> songsOfAlbum(String albumId);
        List<String> songsOfArtist(String artistId);
    }

    /**
     * In-memory catalog. Minimal indexes (prefix scan + precomputed mapping artist->songs).
     */
    static final class InMemoryCatalog implements Catalog {
        private final Map<String, Song> songs = new HashMap<>();
        private final Map<String, Artist> artists = new HashMap<>();
        private final Map<String, Album> albums = new HashMap<>();

        private final List<Song> allSongs = new ArrayList<>();
        private final List<Artist> allArtists = new ArrayList<>();
        private final List<Album> allAlbums = new ArrayList<>();

        // artistId -> songIds (derived at insert time)
        private final Map<String, List<String>> artistToSongs = new HashMap<>();

        public void addArtist(Artist a) {
            artists.put(a.id, a);
            allArtists.add(a);
        }

        public void addAlbum(Album a) {
            albums.put(a.id, a);
            allAlbums.add(a);
        }

        public void addSong(Song s) {
            songs.put(s.id, s);
            allSongs.add(s);

            // wire song into album
            if (s.albumId != null) {
                Album al = albums.get(s.albumId);
                if (al != null) al.addSong(s.id);
            }

            // wire song into artists mapping
            for (String artistId : s.artistIds) {
                artistToSongs.computeIfAbsent(artistId, k -> new ArrayList<>()).add(s.id);
            }
        }

        @Override public Song getSong(String songId) { return songs.get(songId); }
        @Override public Artist getArtist(String artistId) { return artists.get(artistId); }
        @Override public Album getAlbum(String albumId) { return albums.get(albumId); }

        @Override public List<Song> searchSongByTitlePrefix(String prefix) {
            String p = (prefix == null) ? "" : prefix.toLowerCase();
            List<Song> res = new ArrayList<>();
            for (Song s : allSongs) if (s.title.toLowerCase().startsWith(p)) res.add(s);
            return res;
        }

        @Override public List<Artist> searchArtistByNamePrefix(String prefix) {
            String p = (prefix == null) ? "" : prefix.toLowerCase();
            List<Artist> res = new ArrayList<>();
            for (Artist a : allArtists) if (a.name.toLowerCase().startsWith(p)) res.add(a);
            return res;
        }

        @Override public List<Album> searchAlbumByNamePrefix(String prefix) {
            String p = (prefix == null) ? "" : prefix.toLowerCase();
            List<Album> res = new ArrayList<>();
            for (Album a : allAlbums) if (a.name.toLowerCase().startsWith(p)) res.add(a);
            return res;
        }

        @Override public List<String> songsOfAlbum(String albumId) {
            Album a = albums.get(albumId);
            return (a == null) ? List.of() : a.songIdsView();
        }

        @Override public List<String> songsOfArtist(String artistId) {
            return List.copyOf(artistToSongs.getOrDefault(artistId, List.of()));
        }
    }

    // ---------- Library (likes + recents) ----------
    static final class LibraryService {
        private final int recentLimit;
        private final Map<String, LinkedHashSet<String>> liked = new ConcurrentHashMap<>();
        private final Map<String, Deque<String>> recents = new ConcurrentHashMap<>();

        LibraryService(int recentLimit) { this.recentLimit = recentLimit; }

        public void like(String userId, String songId) {
            liked.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add(songId);
        }

        public void unlike(String userId, String songId) {
            liked.computeIfAbsent(userId, k -> new LinkedHashSet<>()).remove(songId);
        }

        public List<String> likedSongs(String userId) {
            return new ArrayList<>(liked.getOrDefault(userId, new LinkedHashSet<>()));
        }

        public void addRecent(String userId, String songId) {
            Deque<String> dq = recents.computeIfAbsent(userId, k -> new ArrayDeque<>());
            dq.remove(songId);      // unique
            dq.addFirst(songId);
            while (dq.size() > recentLimit) dq.removeLast();
        }

        public List<String> recentlyPlayed(String userId) {
            return new ArrayList<>(recents.getOrDefault(userId, new ArrayDeque<>()));
        }
    }

    // ---------- Playlist Service ----------
    static final class PlaylistService {
        private final Map<String, Playlist> playlists = new ConcurrentHashMap<>();

        public Playlist create(String ownerUserId, String name, boolean isPublic) {
            String id = UUID.randomUUID().toString();
            Playlist p = new Playlist(id, ownerUserId, name, isPublic);
            playlists.put(id, p);
            return p;
        }

        public Playlist get(String playlistId) { return playlists.get(playlistId); }

        public void delete(String playlistId, String requesterUserId) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) playlists.remove(playlistId);
        }

        public void addSong(String playlistId, String requesterUserId, String songId) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) p.addSong(songId);
        }

        public void removeSong(String playlistId, String requesterUserId, String songId) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) p.removeSong(songId);
        }

        public void reorder(String playlistId, String requesterUserId, int fromIdx, int toIdx) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) p.move(fromIdx, toIdx);
        }
    }

    // ---------- Playback ----------
    interface PlaybackListener {
        void onNowPlaying(String userId, Song song);
        void onStateChanged(String userId, String state);
        void onPositionChanged(String userId, int positionSec);
    }

    // Strategy
    interface OrderStrategy {
        int nextIndex(int currentIndex, int size, Random rnd);
        int prevIndex(int currentIndex, int size, Random rnd);
        String name();
    }

    static final class NormalOrder implements OrderStrategy {
        public int nextIndex(int currentIndex, int size, Random rnd) { return currentIndex + 1; }
        public int prevIndex(int currentIndex, int size, Random rnd) { return currentIndex - 1; }
        public String name() { return "NORMAL"; }
    }

    static final class ShuffleOrder implements OrderStrategy {
        public int nextIndex(int currentIndex, int size, Random rnd) {
            if (size <= 1) return currentIndex;
            int nxt;
            do { nxt = rnd.nextInt(size); } while (nxt == currentIndex);
            return nxt;
        }
        public int prevIndex(int currentIndex, int size, Random rnd) {
            // keep simple: random prev (no history stack)
            return nextIndex(currentIndex, size, rnd);
        }
        public String name() { return "SHUFFLE"; }
    }

    // State
    interface PlayerState {
        void play(PlaybackSession s);
        void pause(PlaybackSession s);
        void stop(PlaybackSession s);
        String name();
    }

    static final class PlayingState implements PlayerState {
        public void play(PlaybackSession s) { /* already */ }
        public void pause(PlaybackSession s) { s.setState(new PausedState()); }
        public void stop(PlaybackSession s) { s.setState(new StoppedState()); s.seek(0); }
        public String name() { return "PLAYING"; }
    }

    static final class PausedState implements PlayerState {
        public void play(PlaybackSession s) { s.setState(new PlayingState()); }
        public void pause(PlaybackSession s) { /* already */ }
        public void stop(PlaybackSession s) { s.setState(new StoppedState()); s.seek(0); }
        public String name() { return "PAUSED"; }
    }

    static final class StoppedState implements PlayerState {
        public void play(PlaybackSession s) { s.setState(new PlayingState()); }
        public void pause(PlaybackSession s) { /* no-op */ }
        public void stop(PlaybackSession s) { /* already */ }
        public String name() { return "STOPPED"; }
    }

    /**
     * PlaybackSession = per user.
     * Context can be: single song, playlist, album, artist (list of songIds).
     * Queue overrides context: playNext then upNext.
     */
    static final class PlaybackSession {
        private final String userId;
        private final Catalog catalog;
        private final LibraryService library;
        private final Random rnd = new Random();

        private PlayerState state = new StoppedState();
        private RepeatMode repeatMode = RepeatMode.OFF;
        private OrderStrategy orderStrategy = new NormalOrder();

        // context list (playlist/album/artist)
        private List<String> contextSongIds = List.of();
        private int contextIndex = -1;

        // queue
        private final Deque<String> playNext = new ArrayDeque<>();
        private final Deque<String> upNext = new ArrayDeque<>();

        private String currentSongId = null;
        private int positionSec = 0;

        private final List<PlaybackListener> listeners = new ArrayList<>();

        PlaybackSession(String userId, Catalog catalog, LibraryService library) {
            this.userId = userId;
            this.catalog = catalog;
            this.library = library;
        }

        public void addListener(PlaybackListener l) { if (l != null) listeners.add(l); }

        void setState(PlayerState newState) {
            this.state = newState;
            for (PlaybackListener l : listeners) l.onStateChanged(userId, state.name());
        }

        // --- commands ---
        public void playSong(String songId) {
            if (catalog.getSong(songId) == null) return;
            this.contextSongIds = List.of(songId);
            this.contextIndex = 0;
            startSong(songId);
            state.play(this);
        }

        public void playContext(List<String> songIds, int startIndex) {
            if (songIds == null || songIds.isEmpty()) return;
            // filter invalid songs (correctness)
            List<String> valid = new ArrayList<>();
            for (String id : songIds) if (catalog.getSong(id) != null) valid.add(id);
            if (valid.isEmpty()) return;

            this.contextSongIds = List.copyOf(valid);
            this.contextIndex = Math.max(0, Math.min(startIndex, contextSongIds.size() - 1));
            startSong(contextSongIds.get(contextIndex));
            state.play(this);
        }

        public void resume() { state.play(this); }
        public void pause() { state.pause(this); }
        public void stop() { state.stop(this); }

        public void seek(int newPositionSec) {
            if (currentSongId == null) return;
            Song s = catalog.getSong(currentSongId);
            if (s == null) return;
            positionSec = Math.max(0, Math.min(newPositionSec, s.durationSec));
            for (PlaybackListener l : listeners) l.onPositionChanged(userId, positionSec);
        }

        public void setShuffle(boolean enabled) {
            this.orderStrategy = enabled ? new ShuffleOrder() : new NormalOrder();
        }

        public void setRepeatMode(RepeatMode mode) {
            this.repeatMode = (mode == null) ? RepeatMode.OFF : mode;
        }

        public void addToQueueNext(String songId) {
            if (catalog.getSong(songId) == null) return;
            playNext.addLast(songId);
        }

        public void addToQueueEnd(String songId) {
            if (catalog.getSong(songId) == null) return;
            upNext.addLast(songId);
        }

        public void clearQueue() { playNext.clear(); upNext.clear(); }

        public List<String> viewUpNext() {
            List<String> res = new ArrayList<>(playNext);
            res.addAll(upNext);
            return res;
        }

        public Song nowPlaying() { return currentSongId == null ? null : catalog.getSong(currentSongId); }

        public void next() {
            String nextId = pollNextFromQueue();
            if (nextId != null) {
                startSong(nextId);
                state.play(this);
                return;
            }

            if (currentSongId == null) return;

            if (repeatMode == RepeatMode.ONE) {
                startSong(currentSongId);
                state.play(this);
                return;
            }

            int size = contextSongIds.size();
            if (size == 0 || contextIndex < 0) return;

            int candidate = orderStrategy.nextIndex(contextIndex, size, rnd);

            if (orderStrategy instanceof NormalOrder) {
                if (candidate >= size) {
                    if (repeatMode == RepeatMode.ALL) candidate = 0;
                    else { stop(); return; }
                }
            } else {
                candidate = Math.max(0, Math.min(candidate, size - 1));
            }

            contextIndex = candidate;
            startSong(contextSongIds.get(contextIndex));
            state.play(this);
        }

        public void previous() {
            if (currentSongId == null) return;

            if (positionSec > 3) { seek(0); return; }

            if (repeatMode == RepeatMode.ONE) {
                startSong(currentSongId);
                state.play(this);
                return;
            }

            int size = contextSongIds.size();
            if (size == 0 || contextIndex < 0) return;

            int candidate = orderStrategy.prevIndex(contextIndex, size, rnd);

            if (orderStrategy instanceof NormalOrder) {
                if (candidate < 0) {
                    if (repeatMode == RepeatMode.ALL) candidate = size - 1;
                    else { seek(0); return; }
                }
            } else {
                candidate = Math.max(0, Math.min(candidate, size - 1));
            }

            contextIndex = candidate;
            startSong(contextSongIds.get(contextIndex));
            state.play(this);
        }

        private String pollNextFromQueue() {
            if (!playNext.isEmpty()) return playNext.pollFirst();
            if (!upNext.isEmpty()) return upNext.pollFirst();
            return null;
        }

        private void startSong(String songId) {
            this.currentSongId = songId;
            this.positionSec = 0;
            library.addRecent(userId, songId);

            Song s = catalog.getSong(songId);
            if (s != null) for (PlaybackListener l : listeners) l.onNowPlaying(userId, s);
            for (PlaybackListener l : listeners) l.onPositionChanged(userId, positionSec);
        }
    }

    // ---------- Playback Service ----------
    static final class PlaybackService {
        private final Catalog catalog;
        private final LibraryService library;
        private final Map<String, PlaybackSession> sessions = new ConcurrentHashMap<>();

        PlaybackService(Catalog catalog, LibraryService library) {
            this.catalog = catalog;
            this.library = library;
        }

        public PlaybackSession session(String userId) {
            return sessions.computeIfAbsent(userId, id -> new PlaybackSession(id, catalog, library));
        }
    }

    // ---------- Facade ----------
    static final class SpotifyApp {
        private final Catalog catalog;
        private final LibraryService library;
        private final PlaylistService playlists;
        private final PlaybackService playback;

        SpotifyApp(Catalog catalog) {
            this.catalog = catalog;
            this.library = new LibraryService(20);
            this.playlists = new PlaylistService();
            this.playback = new PlaybackService(catalog, library);
        }

        // ---- Playlist APIs ----
        public Playlist createPlaylist(String userId, String name, boolean isPublic) {
            return playlists.create(userId, name, isPublic);
        }

        public void addSongToPlaylist(String userId, String playlistId, String songId) {
            playlists.addSong(playlistId, userId, songId);
        }

        public void playPlaylist(String userId, String playlistId) {
            Playlist p = playlists.get(playlistId);
            if (p == null) return;
            playback.session(userId).playContext(p.songIdsView(), 0);
        }

        // ---- Play Album / Artist ----
        public void playAlbum(String userId, String albumId) {
            List<String> songs = catalog.songsOfAlbum(albumId);
            playback.session(userId).playContext(songs, 0);
        }

        public void playArtist(String userId, String artistId) {
            List<String> songs = catalog.songsOfArtist(artistId);
            playback.session(userId).playContext(songs, 0);
        }

        // ---- Playback APIs ----
        public void playSong(String userId, String songId) { playback.session(userId).playSong(songId); }
        public void pause(String userId) { playback.session(userId).pause(); }
        public void resume(String userId) { playback.session(userId).resume(); }
        public void next(String userId) { playback.session(userId).next(); }
        public void prev(String userId) { playback.session(userId).previous(); }
        public void seek(String userId, int sec) { playback.session(userId).seek(sec); }
        public void setShuffle(String userId, boolean enabled) { playback.session(userId).setShuffle(enabled); }
        public void setRepeat(String userId, RepeatMode mode) { playback.session(userId).setRepeatMode(mode); }
        public void queueNext(String userId, String songId) { playback.session(userId).addToQueueNext(songId); }
        public void queueEnd(String userId, String songId) { playback.session(userId).addToQueueEnd(songId); }

        // ---- Library APIs ----
        public void like(String userId, String songId) { library.like(userId, songId); }
        public void unlike(String userId, String songId) { library.unlike(userId, songId); }
        public List<String> likedSongs(String userId) { return library.likedSongs(userId); }
        public List<String> recent(String userId) { return library.recentlyPlayed(userId); }

        // ---- Search APIs ----
        public List<Song> searchSongs(String prefix) { return catalog.searchSongByTitlePrefix(prefix); }
        public List<Artist> searchArtists(String prefix) { return catalog.searchArtistByNamePrefix(prefix); }
        public List<Album> searchAlbums(String prefix) { return catalog.searchAlbumByNamePrefix(prefix); }

        public void addPlaybackListener(String userId, PlaybackListener l) {
            playback.session(userId).addListener(l);
        }
    }

    // ---------- Example Usage (Optional) ----------
    public static void main(String[] args) {
        InMemoryCatalog catalog = new InMemoryCatalog();

        // Artists
        catalog.addArtist(new Artist("a1", "Imagine Dragons"));
        catalog.addArtist(new Artist("a2", "Queen"));

        // Albums
        catalog.addAlbum(new Album("al1", "Evolve", List.of("a1")));
        catalog.addAlbum(new Album("al2", "A Night at the Opera", List.of("a2")));

        // Songs
        catalog.addSong(new Song("s1", "Believer", 204, "al1", List.of("a1")));
        catalog.addSong(new Song("s2", "Thunder", 187, "al1", List.of("a1")));
        catalog.addSong(new Song("s3", "Bohemian Rhapsody", 355, "al2", List.of("a2")));

        SpotifyApp app = new SpotifyApp(catalog);
        String userId = "u1";

        app.addPlaybackListener(userId, new PlaybackListener() {
            public void onNowPlaying(String u, Song s) { System.out.println("Now playing: " + s); }
            public void onStateChanged(String u, String st) { System.out.println("State: " + st); }
            public void onPositionChanged(String u, int pos) { /* ignore */ }
        });

        // Play Album
        app.playAlbum(userId, "al1");
        app.setShuffle(userId, true);
        app.setRepeat(userId, RepeatMode.ALL);
        app.next(userId);

        // Play Artist
        app.playArtist(userId, "a2");

        // Like + Recents
        app.like(userId, "s3");
        System.out.println("Liked: " + app.likedSongs(userId));
        System.out.println("Recent: " + app.recent(userId));
    }
}
