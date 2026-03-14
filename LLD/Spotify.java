public class SpotifyLLD_Final {

    // -------------------- Domain --------------------
    static final class Artist {
        final String id, name;
        Artist(String id, String name) { this.id = id; this.name = name; }
    }

    static final class Album {
        final String id, name;
        final List<String> artistIds;
        final List<String> songIds = new ArrayList<>();

        Album(String id, String name, List<String> artistIds) {
            this.id = id;
            this.name = name;
            this.artistIds = artistIds == null ? List.of() : List.copyOf(artistIds);
        }

        void addSong(String songId) { songIds.add(songId); }
    }

    static final class Song {
        final String id, title;
        final int durationSec;
        final String albumId;
        final List<String> artistIds;

        Song(String id, String title, int durationSec, String albumId, List<String> artistIds) {
            this.id = id;
            this.title = title;
            this.durationSec = durationSec;
            this.albumId = albumId;
            this.artistIds = artistIds == null ? List.of() : List.copyOf(artistIds);
        }

        @Override public String toString() { return title + "(" + id + ")"; }
    }

    // -------------------- Catalog --------------------
    interface Catalog {
        Song song(String id);
        List<String> songsOfAlbum(String albumId);
        List<String> songsOfArtist(String artistId);
        List<Song> searchSongPrefix(String prefix);
    }

    static final class InMemoryCatalog implements Catalog {
        private final Map<String, Song> songs = new HashMap<>();
        private final Map<String, Album> albums = new HashMap<>();
        private final Map<String, Artist> artists = new HashMap<>();
        private final Map<String, List<String>> artistToSongs = new HashMap<>();
        private final List<Song> allSongs = new ArrayList<>();

        void addArtist(Artist a) { artists.put(a.id, a); }
        void addAlbum(Album a) { albums.put(a.id, a); }

        void addSong(Song s) {
            songs.put(s.id, s);
            allSongs.add(s);

            if (s.albumId != null && albums.containsKey(s.albumId)) {
                albums.get(s.albumId).addSong(s.id);
            }
            for (String aid : s.artistIds) {
                artistToSongs.computeIfAbsent(aid, k -> new ArrayList<>()).add(s.id);
            }
        }

        @Override public Song song(String id) { return songs.get(id); }

        @Override public List<String> songsOfAlbum(String albumId) {
            Album a = albums.get(albumId);
            return a == null ? List.of() : List.copyOf(a.songIds);
        }

        @Override public List<String> songsOfArtist(String artistId) {
            return List.copyOf(artistToSongs.getOrDefault(artistId, List.of()));
        }

        @Override public List<Song> searchSongPrefix(String prefix) {
            String p = prefix == null ? "" : prefix.toLowerCase();
            List<Song> res = new ArrayList<>();
            for (Song s : allSongs) {
                if (s.title.toLowerCase().startsWith(p)) res.add(s);
            }
            return res;
        }
    }

    // -------------------- Library (Likes + Recents) --------------------
    static final class Library {
        private final int recentLimit;
        private final Map<String, Set<String>> liked = new ConcurrentHashMap<>();
        private final Map<String, Deque<String>> recents = new ConcurrentHashMap<>();

        Library(int recentLimit) { this.recentLimit = recentLimit; }

        void like(String userId, String songId) {
            liked.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(songId);
        }

        void unlike(String userId, String songId) {
            liked.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).remove(songId);
        }

        Set<String> likedSet(String userId) {
            return liked.getOrDefault(userId, Set.of());
        }

        void addRecent(String userId, String songId) {
            if (songId == null) return;
            Deque<String> dq = recents.computeIfAbsent(userId, k -> new ArrayDeque<>());
            // ArrayDeque isn't thread-safe; lock per-user deque (smallest change)
            synchronized (dq) {
                dq.remove(songId);
                dq.addFirst(songId);
                while (dq.size() > recentLimit) dq.removeLast();
            }
        }

        List<String> recent(String userId) {
            Deque<String> dq = recents.get(userId);
            if (dq == null) return List.of();
            synchronized (dq) {
                return new ArrayList<>(dq);
            }
        }
    }

    // -------------------- Subscription (Plans + Pricing + Onboarding) --------------------
    enum SubscriptionPlan {
        FREE(0),
        INDIVIDUAL(119),
        FAMILY(179),
        STUDENT(59);

        final int monthlyCost;
        SubscriptionPlan(int monthlyCost) { this.monthlyCost = monthlyCost; }
    }

    static final class Subscription {
        final SubscriptionPlan plan;
        final long startEpochMillis;

        Subscription(SubscriptionPlan plan) {
            this.plan = plan;
            this.startEpochMillis = System.currentTimeMillis();
        }

        boolean isPaid() { return plan != SubscriptionPlan.FREE; }
    }

    static final class SubscriptionService {
        private final Map<String, Subscription> userSubscriptions = new ConcurrentHashMap<>();

        // User onboarding: defaults to FREE
        void onboardUser(String userId) {
            userSubscriptions.putIfAbsent(userId, new Subscription(SubscriptionPlan.FREE));
        }

        // Upgrade / subscribe
        void subscribe(String userId, SubscriptionPlan plan) {
            if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
            if (plan == null) throw new IllegalArgumentException("plan required");
            userSubscriptions.put(userId, new Subscription(plan));
        }

        boolean isSubscribedToPaid(String userId) {
            Subscription sub = userSubscriptions.get(userId);
            return sub != null && sub.isPaid();
        }

        SubscriptionPlan planOf(String userId) {
            Subscription sub = userSubscriptions.get(userId);
            return sub == null ? null : sub.plan;
        }

        int monthlyCostOf(String userId) {
            Subscription sub = userSubscriptions.get(userId);
            return sub == null ? 0 : sub.plan.monthlyCost;
        }
    }

    // -------------------- Playlist --------------------
    enum PlaylistView { SIMPLE, SHUFFLED, FAVORITES }

    static final class Playlist {
        final String id, ownerUserId, name;
        private final List<String> songIds = Collections.synchronizedList(new ArrayList<>());

        Playlist(String id, String ownerUserId, String name) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.name = name;
        }

        void addSong(String songId) { songIds.add(songId); }
        void removeSong(String songId) { songIds.remove(songId); }

        List<String> snapshot() {
            synchronized (songIds) {
                return new ArrayList<>(songIds);
            }
        }

        // Shuffle/Favorites are handled while building context (no PlayOrder)
        List<String> buildContext(PlaylistView view, Library lib, String userId) {
            PlaylistView v = view == null ? PlaylistView.SIMPLE : view;
            List<String> list = snapshot();

            switch (v) {
                case SHUFFLED:
                    Collections.shuffle(list);
                    return list;
                case FAVORITES:
                    Set<String> liked = lib.likedSet(userId);
                    List<String> fav = new ArrayList<>();
                    for (String id : list) if (liked.contains(id)) fav.add(id);
                    return fav;
                case SIMPLE:
                default:
                    return list;
            }
        }
    }

    static final class PlaylistService {
        private final Map<String, Playlist> store = new ConcurrentHashMap<>();

        Playlist create(String ownerUserId, String name) {
            String id = UUID.randomUUID().toString();
            Playlist p = new Playlist(id, ownerUserId, name);
            store.put(id, p);
            return p;
        }

        Playlist get(String id) { return store.get(id); }

        void addSong(String playlistId, String requester, String songId) {
            Playlist p = store.get(playlistId);
            if (p != null && p.ownerUserId.equals(requester)) p.addSong(songId);
        }
    }

    // -------------------- Playback Session (Repeat + QueueNext) --------------------
    enum RepeatMode { OFF, ONE, ALL }

    static final class Session {
        private final String userId;
        private final Library library;

        private List<String> context = List.of();   // resolved songIds
        private int index = -1;
        private String currentSongId;

        private boolean isPlaying = false;
        private RepeatMode repeatMode = RepeatMode.OFF;

        // Minimal queue: items that should play before next context track
        private final Deque<String> playNextQueue = new ArrayDeque<>();

        Session(String userId, Library library) {
            this.userId = userId;
            this.library = library;
        }

        void setRepeat(RepeatMode mode) {
            this.repeatMode = (mode == null) ? RepeatMode.OFF : mode;
        }

        void playSong(String songId) {
            if (songId == null) return;
            context = List.of(songId);
            index = 0;
            playNextQueue.clear();
            start(songId);
        }

        void playContext(List<String> songIds) {
            if (songIds == null || songIds.isEmpty()) return;
            context = List.copyOf(songIds); // defensive copy
            index = 0;
            playNextQueue.clear();
            start(context.get(index));
        }

        void pause() { isPlaying = false; }

        void resume() {
            if (currentSongId != null) isPlaying = true;
        }

        void queueNext(String songId) {
            if (songId == null) return;
            playNextQueue.addLast(songId);
        }

        void next() {
            if (context.isEmpty() && playNextQueue.isEmpty()) return;

            // queued-next overrides once
            if (!playNextQueue.isEmpty()) {
                start(playNextQueue.pollFirst());
                return;
            }

            // Repeat ONE
            if (repeatMode == RepeatMode.ONE) {
                if (currentSongId != null) start(currentSongId);
                return;
            }

            index++;

            if (index >= context.size()) {
                if (repeatMode == RepeatMode.ALL) {
                    index = 0;
                } else {
                    isPlaying = false;
                    return;
                }
            }

            start(context.get(index));
        }

        void prev() {
            if (context.isEmpty()) return;

            // Repeat ONE
            if (repeatMode == RepeatMode.ONE) {
                if (currentSongId != null) start(currentSongId);
                return;
            }

            index--;

            if (index < 0) {
                if (repeatMode == RepeatMode.ALL) {
                    index = context.size() - 1;
                } else {
                    index = 0;
                }
            }

            start(context.get(index));
        }

        private void start(String songId) {
            if (songId == null) return;
            currentSongId = songId;
            isPlaying = true;
            library.addRecent(userId, songId);

            System.out.println("Now Playing songId: " + songId);
        }
    }

    static final class PlaybackService {
        private final Library library;
        private final Map<String, Session> sessions = new ConcurrentHashMap<>();

        PlaybackService(Library library) { this.library = library; }

        Session session(String userId) {
            return sessions.computeIfAbsent(userId, u -> new Session(u, library));
        }
    }

    // -------------------- Facade (APIs) --------------------
    static final class SpotifyApp {
        private final Catalog catalog;
        private final Library library = new Library(20);
        private final PlaylistService playlists = new PlaylistService();
        private final PlaybackService playback = new PlaybackService(library);
        private final SubscriptionService subs = new SubscriptionService();

        SpotifyApp(Catalog catalog) { this.catalog = catalog; }

        // ---------- User Onboarding + Subscription ----------
        public void onboardUser(String userId) { subs.onboardUser(userId); }

        public void subscribe(String userId, SubscriptionPlan plan) { subs.subscribe(userId, plan); }

        public SubscriptionPlan subscriptionPlan(String userId) { return subs.planOf(userId); }

        public int monthlyCost(String userId) { return subs.monthlyCostOf(userId); }

        private void ensureSubscribedToPaid(String userId) {
            // If user never onboarded, treat as FREE by onboarding implicitly (optional but practical)
            subs.onboardUser(userId);

            if (!subs.isSubscribedToPaid(userId)) {
                throw new IllegalStateException("Upgrade subscription to play music (current=" + subs.planOf(userId) + ")");
            }
        }

        // ---------- Playlist ----------
        public Playlist createPlaylist(String userId, String name) { return playlists.create(userId, name); }

        public void addSongToPlaylist(String userId, String playlistId, String songId) {
            playlists.addSong(playlistId, userId, songId);
        }

        // ---------- Library ----------
        public void like(String userId, String songId) { library.like(userId, songId); }
        public void unlike(String userId, String songId) { library.unlike(userId, songId); }
        public List<String> recent(String userId) { return library.recent(userId); }

        // ---------- Playback entry points (GATED) ----------
        public void playSong(String userId, String songId) {
            ensureSubscribedToPaid(userId);
            playback.session(userId).playSong(songId);
        }

        public void playPlaylist(String userId, String playlistId, PlaylistView view) {
            ensureSubscribedToPaid(userId);
            Playlist p = playlists.get(playlistId);
            if (p == null) return;

            List<String> context = p.buildContext(view, library, userId);
            playback.session(userId).playContext(context);
        }

        public void playAlbum(String userId, String albumId) {
            ensureSubscribedToPaid(userId);
            playback.session(userId).playContext(catalog.songsOfAlbum(albumId));
        }

        public void playArtist(String userId, String artistId) {
            ensureSubscribedToPaid(userId);
            playback.session(userId).playContext(catalog.songsOfArtist(artistId));
        }

        // ---------- Controls (not re-gated) ----------
        public void pause(String userId) { playback.session(userId).pause(); }
        public void resume(String userId) { playback.session(userId).resume(); }
        public void next(String userId) { playback.session(userId).next(); }
        public void prev(String userId) { playback.session(userId).prev(); }
        public void setRepeat(String userId, RepeatMode mode) { playback.session(userId).setRepeat(mode); }

        // Queue is part of listening → keep gated
        public void queueNext(String userId, String songId) {
            ensureSubscribedToPaid(userId);
            playback.session(userId).queueNext(songId);
        }

        // ---------- Search ----------
        public List<Song> searchSongPrefix(String prefix) { return catalog.searchSongPrefix(prefix); }
    }

    // -------------------- Demo --------------------
    public static void main(String[] args) {
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.addArtist(new Artist("a1", "Imagine Dragons"));
        catalog.addAlbum(new Album("al1", "Evolve", List.of("a1")));

        catalog.addSong(new Song("s1", "Believer", 204, "al1", List.of("a1")));
        catalog.addSong(new Song("s2", "Thunder", 187, "al1", List.of("a1")));
        catalog.addSong(new Song("s3", "Whatever It Takes", 201, "al1", List.of("a1")));

        SpotifyApp app = new SpotifyApp(catalog);
        String user = "u1";

        // Onboard -> FREE by default
        app.onboardUser(user);
        System.out.println("Onboarded plan: " + app.subscriptionPlan(user) + ", cost=" + app.monthlyCost(user));

        // Upgrade to paid plan
        app.subscribe(user, SubscriptionPlan.INDIVIDUAL);
        System.out.println("Upgraded plan: " + app.subscriptionPlan(user) + ", cost=" + app.monthlyCost(user));

        Playlist p = app.createPlaylist(user, "MyPlaylist");
        app.addSongToPlaylist(user, p.id, "s1");
        app.addSongToPlaylist(user, p.id, "s2");
        app.addSongToPlaylist(user, p.id, "s3");
        app.like(user, "s2");

        System.out.println("--- Play SIMPLE ---");
        app.playPlaylist(user, p.id, PlaylistView.SIMPLE);
        app.next(user);

        System.out.println("--- Repeat ONE ---");
        app.setRepeat(user, RepeatMode.ONE);
        app.next(user);
        app.next(user);

        System.out.println("--- Repeat ALL + Next ---");
        app.setRepeat(user, RepeatMode.ALL);
        app.playAlbum(user, "al1");
        app.next(user);
        app.next(user);
        app.next(user);
        app.next(user);

        System.out.println("--- QueueNext overrides ---");
        app.queueNext(user, "s2");
        app.next(user);

        System.out.println("Recent: " + app.recent(user));
    }
}
