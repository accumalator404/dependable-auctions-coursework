import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Set;

public class FrontEnd implements Auction {
    private int primaryReplicaId = -1;
    private static final String REPLICA_PREFIX = "Replica_";
    private final Registry registry;
    private ReplicaInterface primaryReplica;
    private Set<Integer> availableReplicas = new HashSet<>();

    private synchronized void handlePrimaryFailure() throws RemoteException {
        availableReplicas.clear(); // Handles primary replica failure by selecting new primary from available
                                   // replicas
        updateAvailableReplicas();
        availableReplicas.remove(primaryReplicaId);
        primaryReplica = null;

        // Try each available replica in order iteratively
        for (Integer replicaId : availableReplicas) {
            try {
                ReplicaInterface replica = (ReplicaInterface) registry.lookup(REPLICA_PREFIX + replicaId);
                replica.setPrimary(true);
                primaryReplicaId = replicaId;
                primaryReplica = replica;
                System.out.println("New primary selected: Replica " + replicaId);
                return;
            } catch (Exception e) {
                continue;
            }
        }
        throw new RemoteException("No available replicas");
    }

    private synchronized ReplicaInterface getPrimaryReplica() throws RemoteException {// Returns current primary and if
                                                                                      // there is failures it'll handle
                                                                                      // if needed.
        if (primaryReplica == null) {
            handlePrimaryFailure();
        }
        try {
            primaryReplica.isPrimary();
            return primaryReplica;
        } catch (Exception e) {
            handlePrimaryFailure();
            return primaryReplica;
        }
    }

    private void updateAvailableReplicas() { // Updates sets of available replicas from registry
        try {
            String[] boundNames = registry.list();
            for (String name : boundNames) {
                if (name.startsWith(REPLICA_PREFIX)) {
                    int replicaId = Integer.parseInt(name.substring(REPLICA_PREFIX.length()));
                    availableReplicas.add(replicaId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating replicas: " + e.getMessage());
        }
    }

    private void selectInitialPrimary() throws RemoteException {// Selects a primary replica on startup
        try {
            String[] boundNames = registry.list();
            for (String name : boundNames) {
                if (name.startsWith(REPLICA_PREFIX)) {
                    int replicaId = Integer.parseInt(name.substring(REPLICA_PREFIX.length()));
                    ReplicaInterface replica = (ReplicaInterface) registry.lookup(name);
                    replica.setPrimary(true);
                    primaryReplicaId = replicaId;
                    primaryReplica = replica;
                    System.out.println("Selected replica " + replicaId + " as primary");
                    return;
                }
            }
            throw new RemoteException("No replicas available");
        } catch (Exception e) {
            throw new RemoteException("Failed to select primary replica", e);
        }
    }

    public FrontEnd() throws RemoteException {
        this.registry = LocateRegistry.getRegistry();
        updateAvailableReplicas();
        selectInitialPrimary();
    }

    // Functionality methods
    @Override
    public int register(String email) throws RemoteException {
        while (true) {
            try {
                return getPrimaryReplica().register(email);
            } catch (RemoteException e) {
                continue;
            }
        }
    }

    @Override
    public AuctionItem getSpec(int itemID) throws RemoteException {
        while (true) {
            try {
                return getPrimaryReplica().getSpec(itemID);
            } catch (RemoteException e) {
                continue;
            }
        }
    }

    @Override
    public int newAuction(int userID, AuctionSaleItem item) throws RemoteException {
        while (true) {
            try {
                return getPrimaryReplica().newAuction(userID, item);
            } catch (RemoteException e) {
                continue;
            }
        }
    }

    @Override
    public AuctionItem[] listItems() throws RemoteException {
        while (true) {
            try {
                return getPrimaryReplica().listItems();
            } catch (RemoteException e) {
                continue;
            }
        }
    }

    @Override
    public AuctionResult closeAuction(int userID, int itemID) throws RemoteException {
        while (true) {
            try {
                return getPrimaryReplica().closeAuction(userID, itemID);
            } catch (RemoteException e) {
                continue;
            }
        }
    }

    @Override
    public boolean bid(int userID, int itemID, int price) throws RemoteException {
        while (true) {
            try {
                return getPrimaryReplica().bid(userID, itemID, price);
            } catch (RemoteException e) {
                continue;
            }
        }
    }

    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        return primaryReplicaId;
    }

    public static void main(String[] args) {
        try {
            FrontEnd frontEnd = new FrontEnd();
            Auction stub = (Auction) UnicastRemoteObject.exportObject(frontEnd, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.bind("FrontEnd", stub);
            System.out.println("FrontEnd ready");
        } catch (Exception e) {
            System.err.println("FrontEnd exception:");
            e.printStackTrace();
        }
    }
}