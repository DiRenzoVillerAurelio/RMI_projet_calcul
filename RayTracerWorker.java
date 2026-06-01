import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
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
        return scene.compute(x, y, width, height);
    }

    public static void main(String[] args) throws Exception {
        String sceneFile = args.length > 0 ? args[0] : "simple.txt";
        int totalWidth = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        int totalHeight = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        String name = args.length > 3 ? args[3] : "worker-1";
        String host = args.length > 4 ? args[4] : "localhost";
        int port = args.length > 5 ? Integer.parseInt(args[5]) : 1099;

        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        }

        RayTracerWorker worker = new RayTracerWorker(sceneFile, totalWidth, totalHeight);
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(host, port);
            registry.list();
        } catch (RemoteException e) {
            if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
                throw e;
            }
            registry = LocateRegistry.createRegistry(port);
        }
        registry.rebind(name, worker);
        System.out.println("Worker ready: " + name + " on " + host + ":" + port);
    }
}