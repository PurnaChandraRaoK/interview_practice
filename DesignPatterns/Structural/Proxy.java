// The Proxy Design Pattern is a powerful tool for
//  controlling access to objects while adding additional functionality

public interface Internet {
    void connectTo(String serverHost) throws Exception;
}

public class RealInternet implements Internet {

    @Override
    public void connectTo(String serverHost) throws Exception {
        System.out.println("Connected to "+serverHost);
    }
}

public class ProxyInternet implements Internet {

    Internet internet;
    public ProxyInternet(Internet internet){
        this.internet = internet;
    }

    @Override
    public void connectTo(String serverHost) throws Exception {
        // get properties
        if(Properties.bannedSites.contains(serverHost)){
            throw new Exception("Access Denied to "+serverHost);
        }
        internet.connectTo(serverHost);
    }
}

public class Properties {
    public static Set<String> bannedSites = Stream.of("xyz.com", "tamilrockers.com", "torrent.com")
            .collect(Collectors.toCollection(HashSet::new));
}

public class Driver {
    public static void main(String[] args) {
        Internet internet = new ProxyInternet(new RealInternet());

        try {
            internet.connectTo("google.com");
            internet.connectTo("xyz.com");
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }
}