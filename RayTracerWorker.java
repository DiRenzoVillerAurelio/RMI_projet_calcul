import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import raytracer.Scene;
import raytracer.Image;

public class RaytracerWorker extends UnicastRemoteObject implements InterfaceRaytracer {
    private Scene scene;
    private String sceneFile;

    public RaytracerWorker(String sceneFile) throws RemoteException {
        super();
        this.sceneFile = sceneFile;
    }

    // On initialise la scène avec les dimensions fournies par le Master
    public void configureScene(int totalWidth, int totalHeight) throws RemoteException {

        this.scene = new Scene(this.sceneFile, totalWidth, totalHeight);
    }

    // Exécute le calcul du Raytracer à la demande duMaster
    @Override
    public Image computeRMI(int x, int y, int width, int height) throws RemoteException {

        if (this.scene == null) {
            // prendre des dimensions par défaut
            this.scene = new Scene(this.sceneFile, 512, 512);
        }

        Image resultat = scene.compute(x, y, width, height);

        System.out.println("Taille de l'image calculée : " + resultat.getWidth() + "x" + resultat.getHeight());
        return resultat;
    }

    public static void main(String[] args) {
        // Récupération des arguments (Ip, Fichier)
        String Ip = args.length > 1 ? args[1] : "";
        String sceneFile = args.length > 2 ? args[2] : "simple.txt";
        int port = 1099;

        try {

            System.setProperty("java.rmi.server.hostname", Ip);
            Registry registry;
            try {
                // Test pour récupérer le registre existant
                registry = LocateRegistry.getRegistry(port);
                registry.list();
                System.out.println("Registre existant trouvé, connexion...");
            } catch (Exception e) {

                System.out.println("Aucun registre trouvé, création du registre...");
                registry = LocateRegistry.createRegistry(port);
            }

            String name = "RaytracerWorker";
            try {
                String[] bindings = registry.list();
                int count = 0;
                for (String b : bindings) {
                    if (b.startsWith("worker-"))
                        count++;
                }
                name = "worker-" + (count + 1);
            } catch (Exception e) {
                name = "worker-1";
            }

            // Enregistrement
            RaytracerWorker worker = new RaytracerWorker(sceneFile);
            registry.rebind(name, worker);

            System.out.println("Worker prêt sous le nom : " + name);

            registry.rebind(name, worker);

            registry.rebind("RaytracerWorker", worker);

            System.out.println("Worker prêt et enregistré localement !");
            System.out.println(" - Identifiant unique : " + name);
            System.out.println(" - Adresse réseau     : " + Ip + ":" + port);

        } catch (Exception e) {
            System.err.println("ERREUR CRITIQUE : Impossible de charger " + sceneFile);
            e.printStackTrace(); 
        }
    }
}