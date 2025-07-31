import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Replica implements ReplicaInterface {
    // Core auction data structures
    private List<AuctionItem> items = Collections.synchronizedList(new ArrayList<>());
    private List<User> users = Collections.synchronizedList(new ArrayList<>());
    private ConcurrentHashMap<Integer, String> bidderEmails = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Integer> reservePrices = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Integer> auctionCreators = new ConcurrentHashMap<>();

    // Replica-specific fields
    private final int replicaID;
    private boolean isPrimary = false;
    private static final String REPLICA_PREFIX = "Replica_";
    private final Registry registry;

    // Synchronization locks
    private final Object userLock = new Object();
    private final Object itemLock = new Object();

    protected static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        int userID;
        String email;

        User(int userID, String email) {
            this.userID = userID;
            this.email = email;
        }
    }

    public Replica(int id) throws RemoteException {
        this.replicaID = id;
        this.registry = LocateRegistry.getRegistry();
        try {
            rejoin();
        } catch (Exception e) {
            System.out.println("New replica - starting fresh");
        }
    }

    public void setPrimary(boolean primary) throws RemoteException { //Sets primaru status for replica
        this.isPrimary = primary;
        System.out.println("Replica " + replicaID + " primary status set to: " + primary);
    }

    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        return isPrimary ? replicaID : -1;
    }

    private void syncWithBackups() throws RemoteException {
        if (!isPrimary)
            return;

        // Create state snapshot
        ReplicaState state;
        synchronized (itemLock) {
            synchronized (userLock) {
                state = new ReplicaState(items, users, bidderEmails, reservePrices, auctionCreators);
            }
        }

        // Sync with all other replicas
        try {
            String[] boundNames = registry.list();
            boolean anySuccess = false; // Track if at least one sync succeeds

            for (String name : boundNames) {
                if (name.startsWith(REPLICA_PREFIX) && !name.equals(REPLICA_PREFIX + replicaID)) {
                    try {
                        ReplicaInterface backup = (ReplicaInterface) registry.lookup(name);
                        backup.receiveState(state);
                        anySuccess = true; // Mark that at least one sync worked
                        System.out.println("Successfully synced with " + name);
                    } catch (Exception e) {
                        System.err.println("Failed to sync with replica " + name + ": " + e.getMessage());
                    }
                }
            }

            // If no syncs succeeded at all, that's a problem
            if (!anySuccess) {
                throw new RemoteException("Failed to sync with any backup replicas");
            }

        } catch (Exception e) {
            System.err.println("Sync operation error: " + e.getMessage());
            // We might want to continue operation even if sync fails
            // depending on your requirements for Level 5
        }
    }

    public void receiveState(ReplicaState state) throws RemoteException { //Updates replica state during sync
        if (isPrimary)
            return; // Primary doesn't receive state updates

        synchronized (itemLock) {
            synchronized (userLock) {
                items.clear();
                items.addAll(state.getItems());
                users.clear();
                users.addAll(state.getUsers());
                bidderEmails.clear();
                bidderEmails.putAll(state.getBidderEmails());
                reservePrices.clear();
                reservePrices.putAll(state.getReservePrices());
                auctionCreators.clear();
                auctionCreators.putAll(state.getAuctionCreators());
            }
        }
    }

    // Auction Interface Implementation

    @Override
    public int register(String email) throws RemoteException {
        if (!isPrimary) {
            throw new RemoteException("Not primary replica");
        }

        synchronized (userLock) {
            for (User user : users) {
                if (user.email.equals(email)) {
                    return user.userID;
                }
            }

            int userID = users.size();
            users.add(new User(userID, email));
            syncWithBackups();
            System.out.println("Replica " + replicaID + " Processing register request for " + email);
            return userID;
        }
    }

    @Override
    public AuctionItem getSpec(int itemID) throws RemoteException {
        synchronized (itemLock) {
            for (AuctionItem item : items) {
                if (item.itemID == itemID) {
                    return item;
                }
            }
            throw new RemoteException("Item not found");
        }
    }

    @Override
    public int newAuction(int userID, AuctionSaleItem item) throws RemoteException {
        if (!isPrimary) {
            throw new RemoteException("Not primary replica");
        }

        synchronized (itemLock) {
            synchronized (userLock) {
                // Validate user exists
                boolean userExists = false;
                for (User user : users) {
                    if (user.userID == userID) {
                        userExists = true;
                        break;
                    }
                }
                if (!userExists) {
                    throw new RemoteException("Invalid user ID");
                }

                // Create new auction
                AuctionItem newItem = new AuctionItem();
                newItem.itemID = items.size();
                newItem.name = item.name;
                newItem.description = item.description;
                newItem.highestBid = 0;

                reservePrices.put(newItem.itemID, item.reservePrice);
                items.add(newItem);
                auctionCreators.put(newItem.itemID, userID);

                syncWithBackups();
                System.out.println("Replica " + replicaID + " Processing request");
                return newItem.itemID;
            }
        }
    }

    @Override
    public AuctionItem[] listItems() throws RemoteException {
        synchronized (itemLock) {
            return items.toArray(new AuctionItem[0]);
        }
    }

    @Override
    public AuctionResult closeAuction(int userID, int itemID) throws RemoteException {
        if (!isPrimary) {
            throw new RemoteException("Not primary replica");
        }

        synchronized (itemLock) {
            Integer creatorID = auctionCreators.get(itemID);
            if (creatorID == null || creatorID != userID) {
                throw new RemoteException("Only the auction creator can close this auction");
            }

            AuctionItem item = null;
            for (AuctionItem i : items) {
                if (i.itemID == itemID) {
                    item = i;
                    break;
                }
            }

            if (item == null) {
                throw new RemoteException("Invalid item ID");
            }

            AuctionResult result = new AuctionResult();
            if (item.highestBid >= reservePrices.get(itemID)) {
                result.winningEmail = bidderEmails.get(itemID);
                result.winningPrice = item.highestBid;
            } else {
                result.winningEmail = null;
                result.winningPrice = 0;
            }

            items.remove(item);
            bidderEmails.remove(itemID);
            reservePrices.remove(itemID);
            auctionCreators.remove(itemID);

            syncWithBackups();
            System.out.println("Replica " + replicaID + " Processing request");
            return result;
        }
    }

    @Override
    public boolean bid(int userID, int itemID, int price) throws RemoteException {
        if (!isPrimary) {
            throw new RemoteException("Not primary replica");
        }

        String bidderEmail = null;
        synchronized (userLock) {
            for (User user : users) {
                if (user.userID == userID) {
                    bidderEmail = user.email;
                    break;
                }
            }
        }

        if (bidderEmail == null) {
            throw new RemoteException("Invalid user ID");
        }

        synchronized (itemLock) {
            for (AuctionItem item : items) {
                if (item.itemID == itemID) {
                    synchronized (item) {
                        if (price > item.highestBid) {
                            item.highestBid = price;
                            bidderEmails.put(itemID, bidderEmail);
                            syncWithBackups();
                            System.out.println("Replica " + replicaID + " Processing register request");
                            return true;
                        }
                        return false;
                    }
                }
            }
        }
        throw new RemoteException("Invalid item ID");
    }

    @Override
    public ReplicaState getState() throws RemoteException {
        synchronized (itemLock) {
            synchronized (userLock) {
                return new ReplicaState(items, users, bidderEmails, reservePrices, auctionCreators);
            }
        }
    }

    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }

    @Override
    public void rejoin() throws RemoteException {
        isPrimary = false;  // Reset primary status
        try {
            String[] boundNames = registry.list();
            for (String name : boundNames) {
                if (name.startsWith(REPLICA_PREFIX) && !name.equals(REPLICA_PREFIX + replicaID)) {
                    ReplicaInterface RI = (ReplicaInterface) registry.lookup(name);
                    if (RI.isPrimary()) {
                        // Sync state from current primary
                        ReplicaState state = RI.getState();
                        receiveState(state);
                        System.out.println("Replica " + replicaID + " joined and synced with primary");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            throw new RemoteException("Failed to join: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Replica <replicaId>");
            System.exit(1);
        }

        try {
            int replicaId = Integer.parseInt(args[0]);
            Registry registry = LocateRegistry.getRegistry();
            String name = REPLICA_PREFIX + replicaId;

            // Check if replica already exists
            try {
                registry.lookup(name);
                System.err.println("Replica " + replicaId + " already exists");
                System.exit(1);
            } catch (NotBoundException e) {
                // Good - replica doesn't exist
                Replica replica = new Replica(replicaId);
                ReplicaInterface stub = (ReplicaInterface) UnicastRemoteObject.exportObject(replica, 0);
                registry.bind(name, stub);

                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        try {
                            // Try to get registry and unbind
                            Registry reg = LocateRegistry.getRegistry();
                            reg.unbind(name);
                            UnicastRemoteObject.unexportObject(replica, true);
                            System.out.println("Replica " + replicaId + " shutdown cleanly");
                        } catch (java.rmi.ConnectException ce) {
                            // Registry not available, just unexport the object
                            UnicastRemoteObject.unexportObject(replica, true);
                            System.out.println("Replica " + replicaId + " unexported (no registry available)");
                        }
                    } catch (Exception ex) {
                        System.err.println("Error during shutdown: " + ex.getMessage());
                    }
                }));

                System.out.println("Replica " + replicaId + " is ready");
            }
        } catch (Exception e) {
            System.err.println("Replica exception:");
            e.printStackTrace();
        }
    }

    @Override
    public boolean isPrimary() throws RemoteException {
        return isPrimary;
    }
}
