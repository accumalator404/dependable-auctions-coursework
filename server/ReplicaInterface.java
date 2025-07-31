import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReplicaInterface extends Remote, Auction {
    void setPrimary(boolean isPrimary) throws RemoteException;
    boolean isPrimary() throws RemoteException;
    void receiveState(ReplicaState state) throws RemoteException;
    ReplicaState getState() throws RemoteException;  // Add this
    boolean isAlive() throws RemoteException;        // Add this
    void rejoin() throws RemoteException;            // Add this
}