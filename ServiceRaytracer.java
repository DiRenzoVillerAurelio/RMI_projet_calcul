import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServiceRaytracer extends Remote {
    String enregistrerWorker(InterfaceRaytracer worker) throws RemoteException;
}