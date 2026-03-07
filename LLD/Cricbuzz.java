import java.time.LocalDate;
import java.util.*;

// --- Enums ---

/** Defines the types of players in a cricket match. */
enum PlayerType {
    BATSMAN,
    BOWLER,
    ALLROUNDER,
    WICKET_KEEPER,
    CAPTAIN
}

/** Defines the different types of balls bowled. */
enum BallType {
    NORMAL,
    NO_BALL,
    WIDE_BALL
}

/** Defines the types of runs that can be scored on a ball. */
enum RunType {
    ZERO,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    WIDE_RUN,
    NO_BALL_RUN,
    LEG_BYE,
    BYE
}

/**
 * Defines different match types with their specific rules.
 * Lightweight strategy via enum properties.
 */
enum MatchType {
    ODI(50, 10),
    T20(20, 4);

    private final int totalOvers;
    private final int maxOversPerBowler;

    MatchType(int totalOvers, int maxOversPerBowler) {
        this.totalOvers = totalOvers;
        this.maxOversPerBowler = maxOversPerBowler;
    }

    public int getTotalOvers() { return totalOvers; }
    public int getMaxOversPerBowler() { return maxOversPerBowler; }
}

// --- Observer Pattern Interfaces & Implementations ---

/** Observer Interface for score updates. */
interface ScoreUpdaterObserver {
    void update(BallDetails ballDetails);
}

/** Updates a batsman's personal scorecard. */
class BattingScorecardUpdater implements ScoreUpdaterObserver {
    @Override
    public void update(BallDetails ballDetails) {
        Player batsman = ballDetails.getPlayedBy();
        RunType runType = ballDetails.getRunType();
        BallType ballType = ballDetails.getBallType();

        // Ball faced is counted only for legal deliveries (not wide/no-ball)
        if (ballType != BallType.WIDE_BALL && ballType != BallType.NO_BALL) {
            batsman.getBattingScorecard().addBallPlayed();
        }

        int runsScoredByBatsman = ballDetails.getRunsScored(); // bat runs only

        // Bat runs shouldn't be added for byes/leg-byes/wide/no-ball extra run-types
        if (runType != RunType.LEG_BYE
                && runType != RunType.BYE
                && runType != RunType.WIDE_RUN
                && runType != RunType.NO_BALL_RUN) {

            batsman.getBattingScorecard().addRuns(runsScoredByBatsman);

            if (runsScoredByBatsman == 4) {
                batsman.getBattingScorecard().addFour();
            } else if (runsScoredByBatsman == 6) {
                batsman.getBattingScorecard().addSix();
            }
        }

        batsman.getBattingScorecard().calculateStrikeRate();

        // ✅ OUT / Not-out is innings specific => update innings state here
        if (ballDetails.isWicket()) {
            InningsDetails innings = ballDetails.getInnings();
            if (innings != null) {
                innings.markOut(batsman, ballDetails.getWicketDescription());
            }
        }

        System.out.println(" [Batting Update] " + batsman.getName() + ": " + batsman.getBattingScorecard());
    }
}

/** Updates a bowler's personal scorecard. */
class BowlingScorecardUpdater implements ScoreUpdaterObserver {
    @Override
    public void update(BallDetails ballDetails) {
        Player bowler = ballDetails.getBowledBy();
        BallType ballType = ballDetails.getBallType();

        // Legal ball counts only for normal deliveries (not wide/no-ball)
        if (ballType != BallType.WIDE_BALL && ballType != BallType.NO_BALL) {
            bowler.getBowlingScorecard().addBallBowled();
        }

        // Runs given includes bat runs (extras handled below)
        bowler.getBowlingScorecard().addRunsGiven(ballDetails.getRunsScored());

        // Add 1 extra run for no-ball / wide (team extra)
        if (ballType == BallType.NO_BALL) {
            bowler.getBowlingScorecard().addNoBall();
            bowler.getBowlingScorecard().addRunsGiven(1);
        } else if (ballType == BallType.WIDE_BALL) {
            bowler.getBowlingScorecard().addWideBall();
            bowler.getBowlingScorecard().addRunsGiven(1);
        }

        if (ballDetails.isWicket()) {
            bowler.getBowlingScorecard().addWicket();
        }

        bowler.getBowlingScorecard().calculateEconomy();

        System.out.println(" [Bowling Update] " + bowler.getName() + ": " + bowler.getBowlingScorecard());
    }
}

// --- Model Classes ---

/** Represents a generic person. */
class Person {
    private final String name;
    private final int age;
    private final String address;

    public Person(String name, int age, String address) {
        this.name = Objects.requireNonNull(name);
        this.age = age;
        this.address = Objects.requireNonNull(address);
    }

    public String getName() { return name; }
    public int getAge() { return age; }
    public String getAddress() { return address; }
}

/** Represents a batsman's individual scorecard statistics. */
class BattingScorecard {
    private int totalRuns;
    private int totalBallsPlayed;
    private int totalFours;
    private int totalSixes;
    private double strikeRate;

    public void addRuns(int runs) { this.totalRuns += runs; }
    public void addBallPlayed() { this.totalBallsPlayed++; }
    public void addFour() { this.totalFours++; }
    public void addSix() { this.totalSixes++; }

    public void calculateStrikeRate() {
        this.strikeRate = (totalBallsPlayed > 0) ? ((double) totalRuns / totalBallsPlayed * 100) : 0.0;
    }

    public int getTotalRuns() { return totalRuns; }
    public int getTotalBallsPlayed() { return totalBallsPlayed; }
    public int getTotalFours() { return totalFours; }
    public int getTotalSixes() { return totalSixes; }
    public double getStrikeRate() { return strikeRate; }

    @Override
    public String toString() {
        return "Runs: " + totalRuns + ", Balls: " + totalBallsPlayed +
                ", 4s: " + totalFours + ", 6s: " + totalSixes +
                ", SR: " + String.format("%.2f", strikeRate);
    }
}

/** Represents a bowler's individual scorecard statistics. */
class BowlingScorecard {
    private int ballsBowled;
    private int runsGiven;
    private int wicketsTaken;
    private int noBallCount;
    private int wideBallCount;
    private double economy;

    public void addBallBowled() { this.ballsBowled++; }
    public void addRunsGiven(int runs) { this.runsGiven += runs; }
    public void addWicket() { this.wicketsTaken++; }
    public void addNoBall() { this.noBallCount++; }
    public void addWideBall() { this.wideBallCount++; }

    public void calculateEconomy() {
        if (ballsBowled > 0) {
            double overs = (double) ballsBowled / 6.0;
            this.economy = runsGiven / overs;
        } else {
            this.economy = 0.0;
        }
    }

    public String getTotalOversDelivered() {
        int fullOvers = ballsBowled / 6;
        int ballsInCurrentOver = ballsBowled % 6;
        return fullOvers + "." + ballsInCurrentOver;
    }

    public int getRunsGiven() { return runsGiven; }
    public int getWicketsTaken() { return wicketsTaken; }
    public int getNoBallCount() { return noBallCount; }
    public int getWideBallCount() { return wideBallCount; }
    public double getEconomy() { return economy; }
    public int getBallsBowled() { return ballsBowled; }

    @Override
    public String toString() {
        return "Overs: " + getTotalOversDelivered() + ", Runs: " + runsGiven +
                ", Wickets: " + wicketsTaken + ", Eco: " + String.format("%.2f", economy);
    }
}

/** Represents a cricket player. */
class Player extends Person {
    private final PlayerType playerType;
    private final BattingScorecard battingScorecard;
    private final BowlingScorecard bowlingScorecard;

    public Player(String name, int age, String address, PlayerType playerType) {
        super(name, age, address);
        this.playerType = Objects.requireNonNull(playerType);
        this.battingScorecard = new BattingScorecard();
        this.bowlingScorecard = new BowlingScorecard();
    }

    public PlayerType getPlayerType() { return playerType; }
    public BattingScorecard getBattingScorecard() { return battingScorecard; }
    public BowlingScorecard getBowlingScorecard() { return bowlingScorecard; }

    @Override
    public String toString() {
        return getName() + " (" + playerType + ")";
    }
}

/** Manages batting lineup and current batsmen. */
class PlayerBattingController {
    private Queue<Player> yetToPlay = new LinkedList<>();
    private Player striker;
    private Player nonStriker;

    public void setYetToPlay(Queue<Player> yetToPlay) {
        this.yetToPlay = (yetToPlay == null) ? new LinkedList<>() : yetToPlay;
    }

    public Player chooseNextBatsman() { return yetToPlay.poll(); }

    public Player getStriker() { return striker; }
    public void setStriker(Player striker) { this.striker = striker; }
    public Player getNonStriker() { return nonStriker; }
    public void setNonStriker(Player nonStriker) { this.nonStriker = nonStriker; }

    public void swapStrikers() {
        Player temp = striker;
        striker = nonStriker;
        nonStriker = temp;
    }
}

/** Manages bowling lineup and current bowler, tracking overs bowled. */
class PlayerBowlingController {
    private final Deque<Player> bowlersList = new LinkedList<>();
    private Player currentBowler;
    private final Map<Player, Integer> bowlerToOversMap = new HashMap<>();

    public void setBowlersList(List<Player> bowlers) {
        bowlersList.clear();
        bowlerToOversMap.clear();

        if (bowlers == null) return;

        bowlersList.addAll(bowlers);
        for (Player bowler : bowlers) {
            bowlerToOversMap.put(bowler, 0);
        }
    }

    /** Round-robin bowler selection respecting max overs. */
    public Player chooseNextBowler(int maxOversPerBowler) {
        if (bowlersList.isEmpty()) return null;

        Player chosenBowler = null;
        int originalSize = bowlersList.size();

        for (int i = 0; i < originalSize; i++) {
            Player candidate = bowlersList.pollFirst();
            bowlersList.addLast(candidate);

            int oversDone = bowlerToOversMap.getOrDefault(candidate, 0);
            if (oversDone < maxOversPerBowler) {
                chosenBowler = candidate;
                break;
            }
        }

        if (chosenBowler != null) {
            currentBowler = chosenBowler;
        }
        return chosenBowler;
    }

    public Player getCurrentBowler() { return currentBowler; }
    public void setCurrentBowler(Player currentBowler) { this.currentBowler = currentBowler; }

    public void incrementOversBowled(Player bowler) {
        bowlerToOversMap.put(bowler, bowlerToOversMap.getOrDefault(bowler, 0) + 1);
    }

    public int getOversBowledBy(Player bowler) {
        return bowlerToOversMap.getOrDefault(bowler, 0);
    }
}

/** Represents a cricket team participating in a match. */
class Team {
    private final String name;
    private final List<Player> playingXI = new ArrayList<>();
    private final List<Player> benchPlayers = new ArrayList<>();
    private final PlayerBattingController battingController = new PlayerBattingController();
    private final PlayerBowlingController bowlingController = new PlayerBowlingController();

    public Team(String name) { this.name = Objects.requireNonNull(name); }

    public String getName() { return name; }

    public void addPlayerToPlayingXI(Player player) {
        if (playingXI.size() < 11) playingXI.add(player);
        else System.out.println("Playing XI is full. Add to bench.");
    }

    public void addPlayerToBench(Player player) { benchPlayers.add(player); }

    public List<Player> getPlayingXI() { return playingXI; }
    public PlayerBattingController getBattingController() { return battingController; }
    public PlayerBowlingController getBowlingController() { return bowlingController; }

    /** Initializes batting and bowling controllers with players from the playing XI. */
    public void initializeControllers() {
        Queue<Player> batsmenQueue = new LinkedList<>();
        List<Player> bowlers = new ArrayList<>();

        for (Player player : playingXI) {
            if (player.getPlayerType() == PlayerType.BATSMAN || player.getPlayerType() == PlayerType.ALLROUNDER) {
                batsmenQueue.add(player);
            }
            if (player.getPlayerType() == PlayerType.BOWLER || player.getPlayerType() == PlayerType.ALLROUNDER) {
                bowlers.add(player);
            }
        }

        battingController.setYetToPlay(batsmenQueue);
        bowlingController.setBowlersList(bowlers);
    }

    @Override
    public String toString() { return "Team: " + name; }
}

/**
 * Represents the details of a single ball delivery.
 * Acts as Subject in Observer.
 */
class BallDetails {
    private final int ballNumber;
    private BallType ballType;
    private RunType runType;
    private final Player bowledBy;
    private final Player playedBy;

    // ✅ Added: which innings this ball belongs to (so observers can update innings state)
    private final InningsDetails innings;

    // Bat runs only (extras like +1 wide/no-ball are added elsewhere)
    private int runsScored;

    private boolean isWicket;
    private String wicketDescription;

    private final List<ScoreUpdaterObserver> observers = new ArrayList<>();

    public BallDetails(int ballNumber, Player bowledBy, Player playedBy, InningsDetails innings) {
        this.ballNumber = ballNumber;
        this.bowledBy = Objects.requireNonNull(bowledBy);
        this.playedBy = Objects.requireNonNull(playedBy);
        this.innings = innings;

        this.ballType = BallType.NORMAL; // default safety
        this.runType = RunType.ZERO;     // default safety
        this.isWicket = false;
        this.wicketDescription = "";
        this.runsScored = 0;
    }

    public void registerObserver(ScoreUpdaterObserver observer) { observers.add(observer); }
    public void removeObserver(ScoreUpdaterObserver observer) { observers.remove(observer); }

    public void notifyUpdaters() {
        for (ScoreUpdaterObserver observer : observers) observer.update(this);
    }

    public void setBallType(BallType ballType) { this.ballType = ballType; }

    public void setRunType(RunType runType) {
        this.runType = runType;
        this.runsScored = computeBatRunsFromRunType(runType);
    }

    /** For cases like NB/WD when you want explicit bat runs override (minimal fix for your main). */
    public void setRunsScored(int runsScored) { this.runsScored = Math.max(0, runsScored); }

    private int computeBatRunsFromRunType(RunType runType) {
        switch (runType) {
            case ONE: return 1;
            case TWO: return 2;
            case THREE: return 3;
            case FOUR: return 4;
            case FIVE: return 5;
            case SIX: return 6;
            default: return 0; // ZERO, extras types
        }
    }

    public void setWicket(boolean wicket) { isWicket = wicket; }
    public void setWicketDescription(String wicketDescription) { this.wicketDescription = wicketDescription; }

    public int getBallNumber() { return ballNumber; }
    public BallType getBallType() { return ballType; }
    public RunType getRunType() { return runType; }
    public Player getBowledBy() { return bowledBy; }
    public Player getPlayedBy() { return playedBy; }
    public InningsDetails getInnings() { return innings; }
    public int getRunsScored() { return runsScored; }
    public boolean isWicket() { return isWicket; }
    public String getWicketDescription() { return wicketDescription; }
}

/** Represents a single over in an innings. */
class OverDetails {
    private final int overNumber;
    private final Player bowler;
    private final List<BallDetails> balls = new ArrayList<>();
    private int runsThisOver;
    private int wicketsThisOver;

    public OverDetails(int overNumber, Player bowler) {
        this.overNumber = overNumber;
        this.bowler = Objects.requireNonNull(bowler);
        this.runsThisOver = 0;
        this.wicketsThisOver = 0;
    }

    public void addBall(BallDetails ball) {
        balls.add(ball);

        // Add bat runs
        this.runsThisOver += ball.getRunsScored();

        // Add +1 for wide/no-ball extra
        if (ball.getBallType() == BallType.NO_BALL || ball.getBallType() == BallType.WIDE_BALL) {
            this.runsThisOver += 1;
        }

        if (ball.isWicket()) {
            this.wicketsThisOver++;
        }
    }

    public int getOverNumber() { return overNumber; }
    public Player getBowler() { return bowler; }
    public List<BallDetails> getBalls() { return balls; }
    public int getRunsThisOver() { return runsThisOver; }
    public int getWicketsThisOver() { return wicketsThisOver; }
}

/** Represents a single innings in a cricket match. */
class InningsDetails {
    private final Team battingTeam;
    private final Team bowlingTeam;
    private final List<OverDetails> overs = new ArrayList<>();

    private int totalInningRuns;
    private int totalInningWickets;
    private int totalBallsBowled; // counts legal balls only

    // ✅ Innings-specific batsman status
    enum BatsmanInningsStatus { DID_NOT_BAT, PLAYING, OUT }

    static class BatsmanInningsState {
        private BatsmanInningsStatus status = BatsmanInningsStatus.DID_NOT_BAT;
        private String outDescription = "";

        public BatsmanInningsStatus getStatus() { return status; }
        public String getOutDescription() { return outDescription; }

        void setPlaying() {
            status = BatsmanInningsStatus.PLAYING;
            outDescription = "";
        }

        void setOut(String desc) {
            status = BatsmanInningsStatus.OUT;
            outDescription = (desc == null) ? "" : desc;
        }
    }

    private final Map<Player, BatsmanInningsState> batsmanState = new HashMap<>();

    public InningsDetails(Team battingTeam, Team bowlingTeam) {
        this.battingTeam = Objects.requireNonNull(battingTeam);
        this.bowlingTeam = Objects.requireNonNull(bowlingTeam);
        this.totalInningRuns = 0;
        this.totalInningWickets = 0;
        this.totalBallsBowled = 0;
    }

    public void addOver(OverDetails over) {
        overs.add(over);
        totalInningRuns += over.getRunsThisOver();
        totalInningWickets += over.getWicketsThisOver();

        // Count legal balls only: NORMAL counts, NO_BALL/WIDE do not count as legal delivery
        for (BallDetails ball : over.getBalls()) {
            if (ball.getBallType() == BallType.NORMAL) {
                totalBallsBowled++;
            }
        }
    }

    private BatsmanInningsState stateOf(Player p) {
        return batsmanState.computeIfAbsent(p, k -> new BatsmanInningsState());
    }

    public void markPlaying(Player p) {
        if (p != null) stateOf(p).setPlaying();
    }

    public void markOut(Player p, String desc) {
        if (p != null) stateOf(p).setOut(desc);
    }

    public BatsmanInningsState getState(Player p) {
        return batsmanState.getOrDefault(p, new BatsmanInningsState());
    }

    public Team getBattingTeam() { return battingTeam; }
    public Team getBowlingTeam() { return bowlingTeam; }
    public List<OverDetails> getOvers() { return overs; }
    public int getTotalInningRuns() { return totalInningRuns; }
    public int getTotalInningWickets() { return totalInningWickets; }
    public int getTotalBallsBowled() { return totalBallsBowled; }

    public String getTotalOversDelivered(int totalBalls) {
        int fullOvers = totalBalls / 6;
        int ballsInCurrentOver = totalBalls % 6;
        return fullOvers + "." + ballsInCurrentOver;
    }
}

/** Represents a cricket match. */
class Match {
    private final Team team1;
    private final Team team2;
    private final MatchType matchType;
    private final LocalDate date;
    private final String venue;

    private final List<InningsDetails> innings = new ArrayList<>();
    private Team tossWinner;
    private int currentInningIndex;

    public Match(Team team1, Team team2, MatchType matchType, LocalDate date, String venue) {
        this.team1 = Objects.requireNonNull(team1);
        this.team2 = Objects.requireNonNull(team2);
        this.matchType = Objects.requireNonNull(matchType);
        this.date = Objects.requireNonNull(date);
        this.venue = Objects.requireNonNull(venue);
        this.currentInningIndex = -1;
    }

    public void setTossWinner(Team tossWinner) { this.tossWinner = tossWinner; }
    public Team getTossWinner() { return tossWinner; }
    public Team getTeam1() { return team1; }
    public Team getTeam2() { return team2; }
    public MatchType getMatchType() { return matchType; }
    public LocalDate getDate() { return date; }
    public String getVenue() { return venue; }
    public List<InningsDetails> getInnings() { return innings; }

    public InningsDetails getCurrentInning() {
        if (currentInningIndex >= 0 && currentInningIndex < innings.size()) {
            return innings.get(currentInningIndex);
        }
        return null;
    }

    public void startNewInning(Team battingTeam, Team bowlingTeam) {
        InningsDetails newInning = new InningsDetails(battingTeam, bowlingTeam);
        innings.add(newInning);
        currentInningIndex++;
        System.out.println("\n--- Starting Innings " + (currentInningIndex + 1) + " ---");
        System.out.println("Batting Team: " + battingTeam.getName());
        System.out.println("Bowling Team: " + bowlingTeam.getName());
    }

    public void displayMatchScore() {
        System.out.println("\n--- Match Scorecard ---");
        System.out.println("Match: " + team1.getName() + " vs " + team2.getName() + " (" + matchType + ")");
        System.out.println("Venue: " + venue + ", Date: " + date);

        for (int i = 0; i < innings.size(); i++) {
            InningsDetails inning = innings.get(i);
            System.out.println("\nInnings " + (i + 1) + ": " + inning.getBattingTeam().getName() + " Batting");
            System.out.println("Score: " + inning.getTotalInningRuns() + "/" + inning.getTotalInningWickets() +
                    " (" + inning.getTotalOversDelivered(inning.getTotalBallsBowled()) + " Overs)");
        }
    }
}

/** Main class to simulate the Cricbuzz application. */
public class Main {
    public static void main(String[] args) {
        System.out.println("--- Cricbuzz Low-Level Design Simulation ---");

        Team india = new Team("India");
        Team australia = new Team("Australia");

        // India XI
        india.addPlayerToPlayingXI(new Player("Rohit Sharma", 36, "Mumbai", PlayerType.BATSMAN));
        india.addPlayerToPlayingXI(new Player("Virat Kohli", 35, "Delhi", PlayerType.BATSMAN));
        india.addPlayerToPlayingXI(new Player("Hardik Pandya", 30, "Surat", PlayerType.ALLROUNDER));
        india.addPlayerToPlayingXI(new Player("Jasprit Bumrah", 30, "Ahmedabad", PlayerType.BOWLER));
        india.addPlayerToPlayingXI(new Player("Ravindra Jadeja", 35, "Jamnagar", PlayerType.ALLROUNDER));
        india.addPlayerToPlayingXI(new Player("Mohammed Shami", 33, "Amroha", PlayerType.BOWLER));
        india.addPlayerToPlayingXI(new Player("Shubman Gill", 24, "Firozpur", PlayerType.BATSMAN));
        india.addPlayerToPlayingXI(new Player("KL Rahul", 31, "Bengaluru", PlayerType.BATSMAN));
        india.addPlayerToPlayingXI(new Player("Kuldeep Yadav", 29, "Kanpur", PlayerType.BOWLER));
        india.addPlayerToPlayingXI(new Player("Mohammed Siraj", 30, "Hyderabad", PlayerType.BOWLER));
        india.addPlayerToPlayingXI(new Player("Axar Patel", 30, "Anand", PlayerType.ALLROUNDER));
        india.initializeControllers();

        // Australia XI (simplified)
        australia.addPlayerToPlayingXI(new Player("David Warner", 37, "Sydney", PlayerType.BATSMAN));
        australia.addPlayerToPlayingXI(new Player("Steve Smith", 35, "Sydney", PlayerType.BATSMAN));
        australia.addPlayerToPlayingXI(new Player("Mitchell Starc", 34, "Sydney", PlayerType.BOWLER));
        australia.addPlayerToPlayingXI(new Player("Pat Cummins", 31, "Sydney", PlayerType.BOWLER));
        australia.addPlayerToPlayingXI(new Player("Glenn Maxwell", 35, "Melbourne", PlayerType.ALLROUNDER));
        australia.addPlayerToPlayingXI(new Player("Marnus Labuschagne", 29, "Brisbane", PlayerType.BATSMAN));
        australia.addPlayerToPlayingXI(new Player("Travis Head", 30, "Adelaide", PlayerType.BATSMAN));
        australia.addPlayerToPlayingXI(new Player("Alex Carey", 32, "Adelaide", PlayerType.BATSMAN));
        australia.addPlayerToPlayingXI(new Player("Josh Hazlewood", 33, "Tamworth", PlayerType.BOWLER));
        australia.addPlayerToPlayingXI(new Player("Adam Zampa", 32, "Lismore", PlayerType.BOWLER));
        australia.addPlayerToPlayingXI(new Player("Mitchell Marsh", 32, "Attadale", PlayerType.ALLROUNDER));
        australia.initializeControllers();

        Match match = new Match(india, australia, MatchType.T20, LocalDate.now(), "M. Chinnaswamy Stadium, Bengaluru");
        match.setTossWinner(india);

        System.out.println("\nMatch Details: " + match.getTeam1().getName() + " vs " + match.getTeam2().getName()
                + " - " + match.getMatchType() + " at " + match.getVenue());
        System.out.println(match.getTossWinner().getName() + " won the toss and elected to bat first.");

        // Observers
        ScoreUpdaterObserver battingUpdater = new BattingScorecardUpdater();
        ScoreUpdaterObserver bowlingUpdater = new BowlingScorecardUpdater();

        // Innings 1
        match.startNewInning(india, australia);
        InningsDetails inning1 = match.getCurrentInning();

        Player currentStrikerIndia = india.getBattingController().chooseNextBatsman();
        Player currentNonStrikerIndia = india.getBattingController().chooseNextBatsman();
        india.getBattingController().setStriker(currentStrikerIndia);
        india.getBattingController().setNonStriker(currentNonStrikerIndia);

        // ✅ Mark openers as PLAYING (innings state)
        inning1.markPlaying(currentStrikerIndia);
        inning1.markPlaying(currentNonStrikerIndia);

        Player currentBowlerAustralia = australia.getBowlingController()
                .chooseNextBowler(match.getMatchType().getMaxOversPerBowler());
        australia.getBowlingController().setCurrentBowler(currentBowlerAustralia);

        try (Scanner scanner = new Scanner(System.in)) {
            int oversToSimulate = 2;

            // live score (minimal fix so per-ball display is correct)
            int runningRuns = 0;
            int runningWickets = 0;

            for (int i = 0; i < oversToSimulate; i++) {
                System.out.println("\n--- Over " + (i + 1) + " (Bowler: " + currentBowlerAustralia.getName() + ") ---");

                OverDetails currentOver = new OverDetails(i + 1, currentBowlerAustralia);

                int ballsInCurrentOver = 0;
                while (ballsInCurrentOver < 6) {
                    if (currentStrikerIndia == null || runningWickets >= 10) {
                        System.out.println("All out or no more batsmen! Innings ends.");
                        break;
                    }

                    System.out.println("\nBall " + (ballsInCurrentOver + 1) + " (Striker: "
                            + currentStrikerIndia.getName() + ", Non-Striker: " + currentNonStrikerIndia.getName() + ")");

                    System.out.print("Enter runs (0-6, W for wicket, NB for no-ball, WD for wide): ");
                    String input = scanner.nextLine().trim().toUpperCase();

                    BallDetails ball = new BallDetails(ballsInCurrentOver + 1, currentBowlerAustralia, currentStrikerIndia, inning1);
                    ball.registerObserver(battingUpdater);
                    ball.registerObserver(bowlingUpdater);

                    boolean isLegalDelivery = true;

                    switch (input) {
                        case "W":
                            ball.setBallType(BallType.NORMAL);
                            ball.setWicket(true);
                            ball.setWicketDescription("bowled by " + currentBowlerAustralia.getName());
                            ball.setRunType(RunType.ZERO);

                            System.out.println("WICKET! " + currentStrikerIndia.getName() + " is OUT!");
                            runningWickets++;

                            // next batsman
                            currentStrikerIndia = india.getBattingController().chooseNextBatsman();
                            india.getBattingController().setStriker(currentStrikerIndia);

                            // ✅ Mark new batsman as PLAYING (if exists)
                            inning1.markPlaying(currentStrikerIndia);
                            break;

                        case "NB":
                            ball.setBallType(BallType.NO_BALL);
                            int nbRuns = readRuns(scanner, "Runs scored on no-ball (0-6): ");
                            ball.setRunType(runTypeFromRuns(nbRuns));
                            ball.setRunsScored(nbRuns);

                            System.out.println("No-Ball! " + nbRuns + " runs (+1 extra).");
                            runningRuns += nbRuns + 1; // +1 extra
                            isLegalDelivery = false;
                            break;

                        case "WD":
                            ball.setBallType(BallType.WIDE_BALL);
                            int wdRuns = readRuns(scanner, "Runs scored on wide (0-6, usually 0): ");
                            ball.setRunType(runTypeFromRuns(wdRuns));
                            ball.setRunsScored(wdRuns);

                            System.out.println("Wide Ball! " + wdRuns + " runs (+1 extra).");
                            runningRuns += wdRuns + 1; // +1 extra
                            isLegalDelivery = false;
                            break;

                        default:
                            int runs = safeParseInt(input, 0);
                            if (runs >= 0 && runs <= 6) {
                                ball.setBallType(BallType.NORMAL);
                                ball.setRunType(runTypeFromRuns(runs));
                                ball.setRunsScored(runs);

                                System.out.println(runs + " runs!");
                                runningRuns += runs;
                            } else {
                                System.out.println("Invalid input. Assuming 0 runs.");
                                ball.setBallType(BallType.NORMAL);
                                ball.setRunType(RunType.ZERO);
                            }
                            break;
                    }

                    currentOver.addBall(ball);
                    ball.notifyUpdaters();

                    if (isLegalDelivery) {
                        ballsInCurrentOver++;
                    }

                    // Rotate strike on odd runs on NORMAL deliveries
                    if (ball.getBallType() == BallType.NORMAL && (ball.getRunsScored() % 2 != 0)) {
                        india.getBattingController().swapStrikers();
                        Player tmp = currentStrikerIndia;
                        currentStrikerIndia = currentNonStrikerIndia;
                        currentNonStrikerIndia = tmp;
                        System.out.println("Strike rotated.");
                    }

                    System.out.println("Current Inning Score: " + runningRuns + "/" + runningWickets);

                    if (runningWickets >= 10 || currentStrikerIndia == null) {
                        break;
                    }
                }

                inning1.addOver(currentOver);

                // End-of-over strike rotation (standard)
                if (runningWickets < 10 && currentStrikerIndia != null) {
                    india.getBattingController().swapStrikers();
                    Player tmp = currentStrikerIndia;
                    currentStrikerIndia = currentNonStrikerIndia;
                    currentNonStrikerIndia = tmp;
                    System.out.println("Strike rotated at end of over.");
                }

                // next bowler
                australia.getBowlingController().incrementOversBowled(currentBowlerAustralia);
                currentBowlerAustralia = australia.getBowlingController()
                        .chooseNextBowler(match.getMatchType().getMaxOversPerBowler());

                if (currentBowlerAustralia == null) {
                    System.out.println("No more bowlers available (max overs reached for all).");
                    break;
                }

                australia.getBowlingController().setCurrentBowler(currentBowlerAustralia);

                if (runningWickets >= 10 || currentStrikerIndia == null) {
                    break;
                }
            }

            System.out.println("\n--- Innings 1 Concluded ---");
            System.out.println(india.getName() + " scored: " + inning1.getTotalInningRuns() + "/"
                    + inning1.getTotalInningWickets()
                    + " in " + inning1.getTotalOversDelivered(inning1.getTotalBallsBowled()) + " overs.");

            System.out.println("\n--- Final Scorecards (for India batsmen/Australia bowlers) ---");
            System.out.println("India Batting:");
            for (Player p : india.getPlayingXI()) {
                InningsDetails.BatsmanInningsState st = inning1.getState(p);

                boolean batted = p.getBattingScorecard().getTotalBallsPlayed() > 0;
                if (st.getStatus() == InningsDetails.BatsmanInningsStatus.OUT) {
                    System.out.println(" " + p.getName() + ": " + p.getBattingScorecard()
                            + " (" + st.getOutDescription() + ")");
                } else if (batted || st.getStatus() == InningsDetails.BatsmanInningsStatus.PLAYING) {
                    System.out.println(" " + p.getName() + ": " + p.getBattingScorecard() + " (Not Out)");
                } else {
                    System.out.println(" " + p.getName() + ": " + p.getBattingScorecard() + " (Did Not Bat)");
                }
            }

            System.out.println("\nAustralia Bowling:");
            for (Player p : australia.getPlayingXI()) {
                if (p.getBowlingScorecard().getBallsBowled() > 0) {
                    System.out.println(" " + p.getName() + ": " + p.getBowlingScorecard());
                }
            }

            match.displayMatchScore();
        }
    }

    private static int readRuns(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String s = scanner.nextLine().trim();
        int v = safeParseInt(s, 0);
        if (v < 0 || v > 6) return 0;
        return v;
    }

    private static int safeParseInt(String s, int fallback) {
        try { return Integer.parseInt(s); }
        catch (Exception e) { return fallback; }
    }

    private static RunType runTypeFromRuns(int runs) {
        switch (runs) {
            case 1: return RunType.ONE;
            case 2: return RunType.TWO;
            case 3: return RunType.THREE;
            case 4: return RunType.FOUR;
            case 5: return RunType.FIVE;
            case 6: return RunType.SIX;
            default: return RunType.ZERO;
        }
    }
}
