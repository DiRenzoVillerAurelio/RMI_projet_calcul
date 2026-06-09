import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import raytracer.Scene;
import raytracer.Image;

public class RaytracerWorker extends UnicastRemoteObject implements InterfaceRaytracer {
    private Scene scene;

    public RaytracerWorker(String sceneFile, int totalWidth, int totalHeight) throws RemoteException {
        super();
        this.scene = new Scene(sceneFile, totalWidth, totalHeight);
    }

    @Override
    public Image computeRMI(int x, int y, int width, int height) throws RemoteException {
        return scene.compute(x, y, width, height);
    }

    public static void main(String[] args) {
        String sceneFile = args.length > 0 ? args[0] : "simple.txt";
        int totalWidth = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        int totalHeight = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        
        // IP de CETTE machine (la machine où tourne le worker)
        String myIp = "10.247.22.44"; 
        int port = 1099;

        try {
            // Indiquez au système l'adresse à publier
            System.setProperty("java.rmi.server.hostname", myIp);

            // création du registre LOCAL (c'est ici que le rebind est autorisé)
            Registry registry = LocateRegistry.createRegistry(port);

            // Création et enregistrement
            RaytracerWorker worker = new RaytracerWorker(sceneFile, totalWidth, totalHeight);
            registry.rebind("RaytracerWorker", worker);

            System.out.println("Worker démarré et enregistré localement sur " + myIp + ":" + port);
        } catch (Exception e) {
            System.err.println("Erreur au démarrage du Worker:");
            e.printStackTrace();
        }
    }
}