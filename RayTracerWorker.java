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
        // C'est cette méthode qui exécute le calcul réel du Raytracer à la demande du Master
        return scene.compute(x, y, width, height);
    }

    public static void main(String[] args) {
        // 1. Récupération des arguments (Fichier, dimensions)
        String sceneFile = args.length > 0 ? args[0] : "simple.txt";
        int totalWidth = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        int totalHeight = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        
        // IP de CETTE machine (la machine où tourne ce worker précis)
        String myIp = "10.247.22.44"; 
        int port = 1099;

        try {
            // 2. Configuration RMI fondamentale pour le réseau
            System.setProperty("java.rmi.server.hostname", myIp);

            // 3. Création du registre LOCAL sur cette machine
            Registry registry = LocateRegistry.createRegistry(port);

            // 4. RÉINTÉGRATION DE VOTRE LOGIQUE : Détermination dynamique du nom du Worker
            String name = "RaytracerWorker";
            try {
                String[] bindings = registry.list();
                int count = 0;
                for (String b : bindings) {
                    if (b.startsWith("worker-")) {
                        count++;
                    }
                }
                name = "worker-" + (count + 1);
            } catch (Throwable t) {
                name = "worker-1"; // Nom par défaut si le listing échoue
            }

            // 5. Instanciation et publication du Worker sous son nom attribué
            RaytracerWorker worker = new RaytracerWorker(sceneFile, totalWidth, totalHeight);
            registry.rebind(name, worker);

            // Optionnel : On enregistre aussi une liaison générique pour faciliter la détection par le Master
            registry.rebind("RaytracerWorker", worker);

            System.out.println("Worker prêt et enregistré localement !");
            System.out.println(" -> Identifiant unique : " + name);
            System.out.println(" -> Adresse réseau     : " + myIp + ":" + port);

        } catch (Exception e) {
            System.err.println("Erreur critique au démarrage du Worker :");
            e.printStackTrace();
        }
    }
}