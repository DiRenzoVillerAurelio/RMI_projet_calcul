import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import raytracer.Scene;
import raytracer.Image;

public class RayTracerWorker extends UnicastRemoteObject implements InterfaceRaytracer {
    private Scene scene;

    public RayTracerWorker(String sceneFile, int totalWidth, int totalHeight) throws RemoteException {
        super();
        this.scene = new Scene(sceneFile, totalWidth, totalHeight);
    }

    @Override
    public Image computeRMI(int x, int y, int width, int height) throws RemoteException {
        // Appelle la méthode compute que vous aviez déjà
        return scene.compute(x, y, width, height);
    }
}