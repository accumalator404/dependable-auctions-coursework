import java.io.Serializable;
import java.util.*;

public class ReplicaState implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<AuctionItem> items;
    private final List<Replica.User> users;
    private final Map<Integer, String> bidderEmails;
    private final Map<Integer, Integer> reservePrices;
    private final Map<Integer, Integer> auctionCreators;
    private final long stateVersion;

    public ReplicaState(List<AuctionItem> items, List<Replica.User> users,
                       Map<Integer, String> bidderEmails,
                       Map<Integer, Integer> reservePrices,
                       Map<Integer, Integer> auctionCreators) {
        this.items = new ArrayList<>(items);
        this.users = new ArrayList<>(users);
        this.bidderEmails = new HashMap<>(bidderEmails);
        this.reservePrices = new HashMap<>(reservePrices);
        this.auctionCreators = new HashMap<>(auctionCreators);
        this.stateVersion = System.currentTimeMillis();
    }

    public List<AuctionItem> getItems() { return new ArrayList<>(items); }
    public List<Replica.User> getUsers() { return new ArrayList<>(users); }
    public Map<Integer, String> getBidderEmails() { return new HashMap<>(bidderEmails); }
    public Map<Integer, Integer> getReservePrices() { return new HashMap<>(reservePrices); }
    public Map<Integer, Integer> getAuctionCreators() { return new HashMap<>(auctionCreators); }

    public long getStateVersion() {
        return stateVersion;
    }
    
    public boolean isNewerThan(ReplicaState other) {
        return other == null || this.stateVersion > other.stateVersion;
    }
}