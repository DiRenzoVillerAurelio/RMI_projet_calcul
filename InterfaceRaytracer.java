import java.rmi.Remote;
import java.rmi.RemoteException;
import raytracer.Image;

public interface InterfaceRaytracer extends Remote {
    // Les méthodes que le Maître appellera à distance
    Image computeRMI(int x, int y, int width, int height) throws RemoteException;

    void configureScene(String sceneFile, int totalWidth, int totalHeight) throws java.rmi.RemoteException;
}