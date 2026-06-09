import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegistryProxy extends Remote {
    void rebind(String name, Remote obj) throws RemoteException;
}
