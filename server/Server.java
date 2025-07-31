import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements Auction {
    protected List<AuctionItem> items = Collections.synchronizedList(new ArrayList<>());
    protected List<User> users = Collections.synchronizedList(new ArrayList<>());
    protected ConcurrentHashMap<Integer, String> bidderEmails = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Integer, Integer> reservePrices = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Integer, Integer> auctionCreators = new ConcurrentHashMap<>();
    protected final Object userLock = new Object();
    protected final Object itemLock = new Object();


    private class User {
        int userID;
        String email;

        User(int userID, String email) {
            this.userID = userID;
            this.email = email;
        }
    }


    public Server() {
        super();
    }

    public int register(String email) throws RemoteException { //Returning users get their key updated instead of being rejected, 
        synchronized (userLock) {                                              //returns their already existing userID for those who are returning already registered users
            for (User user : users) {
                if (user.email.equals(email)) {
                    return user.userID;
                }
            }

            int userID = users.size();
            users.add(new User(userID, email));
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
        synchronized (itemLock) {
            // Validate user exists
            boolean userExists = false;
            synchronized (userLock) {
                for (User user : users) {
                    if (user.userID == userID) {
                        userExists = true;
                        break;
                    }
                }
            }
            if (!userExists) {
                throw new RemoteException("Invalid user ID");
            }

            // Create new auction item
            AuctionItem newItem = new AuctionItem();
            newItem.itemID = items.size();
            newItem.name = item.name;
            newItem.description = item.description;
            newItem.highestBid = 0;

            reservePrices.put(newItem.itemID, item.reservePrice);
            items.add(newItem);
            auctionCreators.put(newItem.itemID, userID);

            return newItem.itemID;
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
        synchronized (itemLock) {
            // Validate creator
            Integer creatorID = auctionCreators.get(itemID);
            if (creatorID == null || creatorID != userID) {
                throw new RemoteException("Only the auction creator can close this auction");
            }

            // Find the item
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

            // Create result
            AuctionResult result = new AuctionResult();
            if (item.highestBid >= reservePrices.get(itemID)) {
                result.winningEmail = bidderEmails.get(itemID);
                result.winningPrice = item.highestBid;
            } else {
                result.winningEmail = null;
                result.winningPrice = 0;
            }

            // Cleanup
            items.remove(item);
            bidderEmails.remove(itemID);
            reservePrices.remove(itemID);
            auctionCreators.remove(itemID);

            return result;
        }
    }

    @Override
    public boolean bid(int userID, int itemID, int price) throws RemoteException {
        // Get bidder email
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
                            return true;
                        }
                        return false;
                    }
                }
            }
        }
        throw new RemoteException("Invalid item ID");
    }

    public int getPrimaryReplicaID() throws RemoteException {
        throw new RemoteException("Not implemented in base Server class");
    }

    public static void main(String[] args) {
        try {
            Server s = new Server();
            String name = "Auction";
            Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(name, stub);
            System.out.println("Server ready");
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }
}