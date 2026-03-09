import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FINAL: Spotify LLD (simple, interview-ready)
 * Includes:
 * - Artist, Album, Song, Playlist
 * - Playback: queue, shuffle, repeat, state machine
 * - Library: likes, recents
 * - Subscription + Charges: user must subscribe before listening
 *
 * NOTE: Minimal implementation by design (LLD focus).
 */
public class SpotifyFinalLLD {

    // -------------------- Domain --------------------
    static final class User {
        final String id;
        final String name;

        User(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }
    }

    static final class Artist {
        final String id;
        final String name;

        Artist(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }

        @Override public String toString() { return name + "(" + id + ")"; }
    }

    static final class Album {
        final String id;
        final String name;
        final List<String> artistIds;
        private final List<String> songIds = new ArrayList<>();

        Album(String id, String name, List<String> artistIds) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.artistIds = (artistIds == null) ? List.of() : List.copyOf(artistIds);
        }

        void addSong(String songId) { songIds.add(songId); }
        List<String> songIdsView() { return Collections.unmodifiableList(songIds); }

        @Override public String toString() { return name + "(" + id + ")"; }
    }

    static final class Song {
        final String id;
        final String title;
        final int durationSec;
        final String albumId;           // link
        final List<String> artistIds;   // link

        Song(String id, String title, int durationSec, String albumId, List<String> artistIds) {
            this.id = Objects.requireNonNull(id);
            this.title = Objects.requireNonNull(title);
            this.durationSec = durationSec;
            this.albumId = albumId; // can be null
            this.artistIds = (artistIds == null) ? List.of() : List.copyOf(artistIds);
        }

        @Override public String toString() { return title + "(" + id + ")"; }
    }

    static final class Playlist {
        final String id;
        final String ownerUserId;
        final String name;
        private boolean isPublic;
        private final List<String> songIds = new ArrayList<>();

        Playlist(String id, String ownerUserId, String name, boolean isPublic) {
            this.id = Objects.requireNonNull(id);
            this.ownerUserId = Objects.requireNonNull(ownerUserId);
            this.name = Objects.requireNonNull(name);
            this.isPublic = isPublic;
        }

        void addSong(String songId) { songIds.add(songId); }
        void removeSong(String songId) { songIds.remove(songId); }

        void move(int fromIdx, int toIdx) {
            if (fromIdx < 0 || fromIdx >= songIds.size() || toIdx < 0 || toIdx >= songIds.size()) return;
            String s = songIds.remove(fromIdx);
            songIds.add(toIdx, s);
        }

        List<String> songIdsView() { return Collections.unmodifiableList(songIds); }
        boolean isPublic() { return isPublic; }
        void setPublic(boolean val) { isPublic = val; }
    }

    // -------------------- Catalog --------------------
    interface Catalog {
        Song getSong(String songId);
        Artist getArtist(String artistId);
        Album getAlbum(String albumId);

        List<String> songsOfAlbum(String albumId);
        List<String> songsOfArtist(String artistId);

        List<Song> searchSongTitlePrefix(String prefix);
        List<Artist> searchArtistNamePrefix(String prefix);
        List<Album> searchAlbumNamePrefix(String prefix);
    }

    static final class InMemoryCatalog implements Catalog {
        private final Map<String, Song> songs = new HashMap<>();
        private final Map<String, Artist> artists = new HashMap<>();
        private final Map<String, Album> albums = new HashMap<>();

        private final List<Song> allSongs = new ArrayList<>();
        private final List<Artist> allArtists = new ArrayList<>();
        private final List<Album> allAlbums = new ArrayList<>();

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

            if (s.albumId != null) {
                Album al = albums.get(s.albumId);
                if (al != null) al.addSong(s.id);
            }

            for (String artistId : s.artistIds) {
                artistToSongs.computeIfAbsent(artistId, k -> new ArrayList<>()).add(s.id);
            }
        }

        @Override public Song getSong(String songId) { return songs.get(songId); }
        @Override public Artist getArtist(String artistId) { return artists.get(artistId); }
        @Override public Album getAlbum(String albumId) { return albums.get(albumId); }

        @Override public List<String> songsOfAlbum(String albumId) {
            Album a = albums.get(albumId);
            return (a == null) ? List.of() : a.songIdsView();
        }

        @Override public List<String> songsOfArtist(String artistId) {
            return List.copyOf(artistToSongs.getOrDefault(artistId, List.of()));
        }

        @Override public List<Song> searchSongTitlePrefix(String prefix) {
            String p = (prefix == null) ? "" : prefix.toLowerCase();
            List<Song> res = new ArrayList<>();
            for (Song s : allSongs) if (s.title.toLowerCase().startsWith(p)) res.add(s);
            return res;
        }

        @Override public List<Artist> searchArtistNamePrefix(String prefix) {
            String p = (prefix == null) ? "" : prefix.toLowerCase();
            List<Artist> res = new ArrayList<>();
            for (Artist a : allArtists) if (a.name.toLowerCase().startsWith(p)) res.add(a);
            return res;
        }

        @Override public List<Album> searchAlbumNamePrefix(String prefix) {
            String p = (prefix == null) ? "" : prefix.toLowerCase();
            List<Album> res = new ArrayList<>();
            for (Album a : allAlbums) if (a.name.toLowerCase().startsWith(p)) res.add(a);
            return res;
        }
    }

    // -------------------- Library (Likes + Recents) --------------------
    static final class LibraryService {
        private final int recentLimit;
        private final Map<String, LinkedHashSet<String>> liked = new ConcurrentHashMap<>();
        private final Map<String, Deque<String>> recents = new ConcurrentHashMap<>();

        LibraryService(int recentLimit) { this.recentLimit = recentLimit; }

        void like(String userId, String songId) {
            liked.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add(songId);
        }

        void unlike(String userId, String songId) {
            liked.computeIfAbsent(userId, k -> new LinkedHashSet<>()).remove(songId);
        }

        List<String> likedSongs(String userId) {
            return new ArrayList<>(liked.getOrDefault(userId, new LinkedHashSet<>()));
        }

        void addRecent(String userId, String songId) {
            Deque<String> dq = recents.computeIfAbsent(userId, k -> new ArrayDeque<>());
            dq.remove(songId);
            dq.addFirst(songId);
            while (dq.size() > recentLimit) dq.removeLast();
        }

        List<String> recentlyPlayed(String userId) {
            return new ArrayList<>(recents.getOrDefault(userId, new ArrayDeque<>()));
        }
    }

    // -------------------- Playlist Service --------------------
    static final class PlaylistService {
        private final Map<String, Playlist> playlists = new ConcurrentHashMap<>();

        Playlist create(String ownerUserId, String name, boolean isPublic) {
            String id = UUID.randomUUID().toString();
            Playlist p = new Playlist(id, ownerUserId, name, isPublic);
            playlists.put(id, p);
            return p;
        }

        Playlist get(String playlistId) { return playlists.get(playlistId); }

        void delete(String playlistId, String requesterUserId) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) playlists.remove(playlistId);
        }

        void addSong(String playlistId, String requesterUserId, String songId) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) p.addSong(songId);
        }

        void removeSong(String playlistId, String requesterUserId, String songId) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) p.removeSong(songId);
        }

        void reorder(String playlistId, String requesterUserId, int fromIdx, int toIdx) {
            Playlist p = playlists.get(playlistId);
            if (p != null && p.ownerUserId.equals(requesterUserId)) p.move(fromIdx, toIdx);
        }
    }

    // -------------------- Subscription + Charges --------------------
    enum SubscriptionPlan { FREE, PREMIUM }
    enum SubscriptionStatus { ACTIVE, EXPIRED, CANCELLED }
    enum ChargeStatus { SUCCESS, FAILED }
    enum Currency { INR, USD }

    static final class Subscription {
        final String userId;
        final SubscriptionPlan plan;
        final Date start;
        final Date end;
        SubscriptionStatus status;

        Subscription(String userId, SubscriptionPlan plan, Date start, Date end) {
            this.userId = userId;
            this.plan = plan;
            this.start = start;
            this.end = end;
            this.status = SubscriptionStatus.ACTIVE;
        }

        boolean isActiveNow() {
            return status == SubscriptionStatus.ACTIVE && new Date().before(end);
        }
    }

    static final class Charge {
        final String id;
        final String userId;
        final double amount;
        final Currency currency;
        final Date at;
        final ChargeStatus status;
        final String description;

        Charge(String userId, double amount, Currency currency, ChargeStatus status, String description) {
            this.id = UUID.randomUUID().toString();
            this.userId = userId;
            this.amount = amount;
            this.currency = currency;
            this.at = new Date();
            this.status = status;
            this.description = description;
        }

        @Override public String toString() {
            return "Charge{id=" + id + ", amount=" + amount + " " + currency + ", status=" + status + ", desc=" + description + "}";
        }
    }

    // Strategy (pricing)
    interface PricingStrategy {
        double priceFor(SubscriptionPlan plan);
        Currency currency();
    }

    static final class DefaultPricingStrategy implements PricingStrategy {
        public double priceFor(SubscriptionPlan plan) {
            switch (plan) {
                case FREE: return 0.0;
                case PREMIUM: return 199.0; // monthly (LLD)
                default: return 0.0;
            }
        }
        public Currency currency() { return Currency.INR; }
    }

    interface PaymentService {
        boolean charge(String userId, double amount, Currency currency);
    }

    static final class DummyPaymentService implements PaymentService {
        public boolean charge(String userId, double amount, Currency currency) {
            return true; // always success (LLD)
        }
    }

    static final class SubscriptionService {
        private final Map<String, Subscription> subs = new ConcurrentHashMap<>();
        private final Map<String, List<Charge>> charges = new ConcurrentHashMap<>();
        private final PricingStrategy pricing;
        private final PaymentService payment;

        SubscriptionService(PricingStrategy pricing, PaymentService payment) {
            this.pricing = pricing;
            this.payment = payment;
        }

        Subscription subscribeMonthly(String userId, SubscriptionPlan plan) {
            double amount = pricing.priceFor(plan);
            Currency cur = pricing.currency();

            boolean ok = payment.charge(userId, amount, cur);
            Charge c = new Charge(userId, amount, cur, ok ? ChargeStatus.SUCCESS : ChargeStatus.FAILED,
                    "Subscription purchase: " + plan);
            charges.computeIfAbsent(userId, k -> new ArrayList<>()).add(c);

            if (!ok) throw new IllegalStateException("Payment failed");

            Date now = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.add(Calendar.MONTH, 1);

            Subscription s = new Subscription(userId, plan, now, cal.getTime());
            subs.put(userId, s);
            return s;
        }

        boolean hasActiveSubscription(String userId) {
            Subscription s = subs.get(userId);
            if (s == null) return false;
            if (!s.isActiveNow()) {
                s.status = SubscriptionStatus.EXPIRED;
                return false;
            }
            return true;
        }

        Subscription getSubscription(String userId) { return subs.get(userId); }

        List<Charge> getCharges(String userId) {
            return List.copyOf(charges.getOrDefault(userId, List.of()));
        }
    }

    // -------------------- Playback --------------------
    enum RepeatMode { OFF, ONE, ALL }

    interface PlaybackListener {
        void onNowPlaying(String userId, Song song);
        void onStateChanged(String userId, String state);
        void onPositionChanged(String userId, int positionSec);
    }

    // Strategy: order (normal/shuffle)
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
            return nextIndex(currentIndex, size, rnd); // no history (simple)
        }
        public String name() { return "SHUFFLE"; }
    }

    // State: playing/paused/stopped
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

    static final class PlaybackSession {
        private final String userId;
        private final Catalog catalog;
        private final LibraryService library;
        private final Random rnd = new Random();

        private PlayerState state = new StoppedState();
        private RepeatMode repeatMode = RepeatMode.OFF;
        private OrderStrategy order = new NormalOrder();

        // context list (playlist/album/artist/single)
        private List<String> contextSongIds = List.of();
        private int contextIndex = -1;

        // queue overrides context
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

        void addListener(PlaybackListener l) { if (l != null) listeners.add(l); }

        void setState(PlayerState newState) {
            this.state = newState;
            for (PlaybackListener l : listeners) l.onStateChanged(userId, state.name());
        }

        // --- playback entry points ---
        void playSong(String songId) {
            if (catalog.getSong(songId) == null) return;
            this.contextSongIds = List.of(songId);
            this.contextIndex = 0;
            startSong(songId);
            state.play(this);
        }

        void playContext(List<String> songIds, int startIndex) {
            if (songIds == null || songIds.isEmpty()) return;

            // filter invalid song ids (correctness)
            List<String> valid = new ArrayList<>();
            for (String id : songIds) if (catalog.getSong(id) != null) valid.add(id);
            if (valid.isEmpty()) return;

            this.contextSongIds = List.copyOf(valid);
            this.contextIndex = Math.max(0, Math.min(startIndex, contextSongIds.size() - 1));
            startSong(contextSongIds.get(contextIndex));
            state.play(this);
        }

        void resume() { state.play(this); }
        void pause() { state.pause(this); }
        void stop() { state.stop(this); }

        void seek(int newPositionSec) {
            if (currentSongId == null) return;
            Song s = catalog.getSong(currentSongId);
            if (s == null) return;
            positionSec = Math.max(0, Math.min(newPositionSec, s.durationSec));
            for (PlaybackListener l : listeners) l.onPositionChanged(userId, positionSec);
        }

        void setShuffle(boolean enabled) { this.order = enabled ? new ShuffleOrder() : new NormalOrder(); }
        void setRepeatMode(RepeatMode mode) { this.repeatMode = (mode == null) ? RepeatMode.OFF : mode; }

        void addToQueueNext(String songId) {
            if (catalog.getSong(songId) == null) return;
            playNext.addLast(songId);
        }

        void addToQueueEnd(String songId) {
            if (catalog.getSong(songId) == null) return;
            upNext.addLast(songId);
        }

        void clearQueue() { playNext.clear(); upNext.clear(); }

        List<String> viewUpNext() {
            List<String> res = new ArrayList<>(playNext);
            res.addAll(upNext);
            return res;
        }

        Song nowPlaying() { return currentSongId == null ? null : catalog.getSong(currentSongId); }

        void next() {
            String nextId = pollQueue();
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

            int candidate = order.nextIndex(contextIndex, size, rnd);

            if (order instanceof NormalOrder) {
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

        void previous() {
            if (currentSongId == null) return;

            if (positionSec > 3) { seek(0); return; }

            if (repeatMode == RepeatMode.ONE) {
                startSong(currentSongId);
                state.play(this);
                return;
            }

            int size = contextSongIds.size();
            if (size == 0 || contextIndex < 0) return;

            int candidate = order.prevIndex(contextIndex, size, rnd);

            if (order instanceof NormalOrder) {
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

        // --- internals ---
        private String pollQueue() {
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

    static final class PlaybackService {
        private final Catalog catalog;
        private final LibraryService library;
        private final Map<String, PlaybackSession> sessions = new ConcurrentHashMap<>();

        PlaybackService(Catalog catalog, LibraryService library) {
            this.catalog = catalog;
            this.library = library;
        }

        PlaybackSession session(String userId) {
            return sessions.computeIfAbsent(userId, id -> new PlaybackSession(id, catalog, library));
        }
    }

    // -------------------- Facade (Spotify APIs) --------------------
    static final class SpotifyApp {
        private final Catalog catalog;
        private final LibraryService library;
        private final PlaylistService playlists;
        private final PlaybackService playback;
        private final SubscriptionService subscriptions;

        SpotifyApp(Catalog catalog) {
            this.catalog = catalog;
            this.library = new LibraryService(20);
            this.playlists = new PlaylistService();
            this.playback = new PlaybackService(catalog, library);
            this.subscriptions = new SubscriptionService(new DefaultPricingStrategy(), new DummyPaymentService());
        }

        // ---- Subscription APIs ----
        public Subscription subscribeMonthly(String userId, SubscriptionPlan plan) {
            return subscriptions.subscribeMonthly(userId, plan);
        }

        public Subscription getSubscription(String userId) { return subscriptions.getSubscription(userId); }
        public List<Charge> getCharges(String userId) { return subscriptions.getCharges(userId); }

        private void ensureSubscribed(String userId) {
            if (!subscriptions.hasActiveSubscription(userId)) {
                throw new IllegalStateException("User has no active subscription");
            }
        }

        // ---- Playlist APIs ----
        public Playlist createPlaylist(String userId, String name, boolean isPublic) {
            return playlists.create(userId, name, isPublic);
        }

        public void addSongToPlaylist(String userId, String playlistId, String songId) {
            playlists.addSong(playlistId, userId, songId);
        }

        // ---- Playback entry points (subscription required) ----
        public void playSong(String userId, String songId) {
            ensureSubscribed(userId);
            playback.session(userId).playSong(songId);
        }

        public void playPlaylist(String userId, String playlistId) {
            ensureSubscribed(userId);
            Playlist p = playlists.get(playlistId);
            if (p == null) return;
            playback.session(userId).playContext(p.songIdsView(), 0);
        }

        public void playAlbum(String userId, String albumId) {
            ensureSubscribed(userId);
            playback.session(userId).playContext(catalog.songsOfAlbum(albumId), 0);
        }

        public void playArtist(String userId, String artistId) {
            ensureSubscribed(userId);
            playback.session(userId).playContext(catalog.songsOfArtist(artistId), 0);
        }

        // ---- Playback controls (no need to re-check subscription) ----
        public void pause(String userId) { playback.session(userId).pause(); }
        public void resume(String userId) { playback.session(userId).resume(); }
        public void next(String userId) { playback.session(userId).next(); }
        public void prev(String userId) { playback.session(userId).previous(); }
        public void seek(String userId, int sec) { playback.session(userId).seek(sec); }
        public void setShuffle(String userId, boolean enabled) { playback.session(userId).setShuffle(enabled); }
        public void setRepeat(String userId, RepeatMode mode) { playback.session(userId).setRepeatMode(mode); }

        // Queue (considered part of listening -> keep it guarded)
        public void queueNext(String userId, String songId) {
            ensureSubscribed(userId);
            playback.session(userId).addToQueueNext(songId);
        }

        public void queueEnd(String userId, String songId) {
            ensureSubscribed(userId);
            playback.session(userId).addToQueueEnd(songId);
        }

        public List<String> viewUpNext(String userId) { return playback.session(userId).viewUpNext(); }

        // ---- Library APIs ----
        public void like(String userId, String songId) { library.like(userId, songId); }
        public void unlike(String userId, String songId) { library.unlike(userId, songId); }
        public List<String> likedSongs(String userId) { return library.likedSongs(userId); }
        public List<String> recent(String userId) { return library.recentlyPlayed(userId); }

        // ---- Search APIs ----
        public List<Song> searchSongs(String prefix) { return catalog.searchSongTitlePrefix(prefix); }
        public List<Artist> searchArtists(String prefix) { return catalog.searchArtistNamePrefix(prefix); }
        public List<Album> searchAlbums(String prefix) { return catalog.searchAlbumNamePrefix(prefix); }

        // ---- Listener hook ----
        public void addPlaybackListener(String userId, PlaybackListener l) {
            playback.session(userId).addListener(l);
        }
    }

    // -------------------- Demo (optional) --------------------
    public static void main(String[] args) {
        InMemoryCatalog catalog = new InMemoryCatalog();

        catalog.addArtist(new Artist("a1", "Imagine Dragons"));
        catalog.addArtist(new Artist("a2", "Queen"));

        catalog.addAlbum(new Album("al1", "Evolve", List.of("a1")));
        catalog.addAlbum(new Album("al2", "A Night at the Opera", List.of("a2")));

        catalog.addSong(new Song("s1", "Believer", 204, "al1", List.of("a1")));
        catalog.addSong(new Song("s2", "Thunder", 187, "al1", List.of("a1")));
        catalog.addSong(new Song("s3", "Bohemian Rhapsody", 355, "al2", List.of("a2")));

        SpotifyApp app = new SpotifyApp(catalog);
        String userId = "u1";

        app.addPlaybackListener(userId, new PlaybackListener() {
            public void onNowPlaying(String u, Song s) { System.out.println("NowPlaying: " + s); }
            public void onStateChanged(String u, String st) { System.out.println("State: " + st); }
            public void onPositionChanged(String u, int pos) { /* ignore */ }
        });

        // Must subscribe before listening
        app.subscribeMonthly(userId, SubscriptionPlan.PREMIUM);
        System.out.println("Charges: " + app.getCharges(userId));

        app.playAlbum(userId, "al1");
        app.setShuffle(userId, true);
        app.setRepeat(userId, RepeatMode.ALL);
        app.next(userId);

        app.queueNext(userId, "s3");
        app.next(userId);

        app.like(userId, "s3");
        System.out.println("Liked: " + app.likedSongs(userId));
        System.out.println("Recent: " + app.recent(userId));
    }
}
