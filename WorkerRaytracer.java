import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import raytracer.Scene;
import raytracer.Image;

public class WorkerRaytracer extends UnicastRemoteObject implements InterfaceRaytracer {
    private Scene scene;
    private String sceneFile;

    public WorkerRaytracer() throws RemoteException {
        super();
    }

    @Override
    public void configureScene(String sceneFile, int totalWidth, int totalHeight) throws RemoteException {
        this.sceneFile = sceneFile;
        this.scene = new Scene(this.sceneFile, totalWidth, totalHeight);
    }

    @Override
    public Image computeRMI(int x, int y, int width, int height) throws RemoteException {
        if (this.scene == null) {
            this.scene = new Scene(this.sceneFile, 512, 512);
        }
        return scene.compute(x, y, width, height);
    }

    public static void main(String[] args) {
        // En mode worker, args[0] = "worker", args[1] = IP du Master, args[2] = port
        String masterIp = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 1099;

        // Configurer le hostname avant tout appel RMI sinon RMI met en cache la
        // mauvaise adresse locale
        try (java.net.Socket s = new java.net.Socket(masterIp, port)) {
            System.setProperty("java.rmi.server.hostname", s.getLocalAddress().getHostAddress());
        } catch (Exception e) {
        }

        try {
            System.out.println("Connexion au registre du Master à l'adresse : " + masterIp);

            // Connexion au registre distant du Master
            Registry registry = LocateRegistry.getRegistry(masterIp, port);

            // Récupération du guichet d'enregistrement du Master
            ServiceRaytracer manager = (ServiceRaytracer) registry.lookup("RaytracerManager");

            // Instanciation et exportation du worker
            WorkerRaytracer worker = new WorkerRaytracer();

            // Enregistrement dynamique auprès du Master
            String workerName = manager.enregistrerWorker(worker);

            System.out.println("Worker enregistré avec succès auprès du Master sous le nom : " + workerName);

        } catch (Exception e) {
            System.err.println("ERREUR : Impossible de s'enregistrer auprès du Master.");
            e.printStackTrace();
        }
    }
}