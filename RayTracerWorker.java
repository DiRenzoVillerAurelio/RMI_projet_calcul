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

    public void configureScene(int totalWidth, int totalHeight) throws RemoteException {
        // On initialise la scène avec les vraies dimensions fournies par le Master
        this.scene = new Scene(this.sceneFile, totalWidth, totalHeight);
    }
    
    @Override
    public Image computeRMI(int x, int y, int width, int height) throws RemoteException {
        // C'est cette méthode qui exécute le calcul réel du Raytracer à la demande du
        // Master
        
        if (this.scene == null) {
            // Optionnel : prendre des dimensions par défaut ou lever une erreur
            this.scene = new Scene(this.sceneFile, 512, 512); 
        }

        Image resultat = scene.compute(x, y, width, height);
        // Ajoutez ceci pour le diagnostic :
        System.out.println("Taille de l'image calculée : " + resultat.getWidth() + "x" + resultat.getHeight());
        System.out.println("Couleur du premier pixel localement : " + resultat.getPixel(0, 0)); // (Adaptez la méthode
                                                                                                // selon votre code)

        return resultat; 
    }

    public static void main(String[] args) {
        // Récupération des arguments (Fichier, dimensions)
        String myIp = args.length > 1 ? args[1] : "" ;
        String sceneFile = args.length > 2 ? args[2] : "simple.txt";
        

    int port = 1099;

    try {
        System.setProperty("java.rmi.server.hostname", myIp);

        Registry registry;
        try {
            // 1. On essaie de récupérer le registre existant
            registry = LocateRegistry.getRegistry(port);
            registry.list(); // Appel test pour voir si le registre est bien là
            System.out.println("Registre existant trouvé, connexion...");
        } catch (Exception e) {
            // 2. Si ça échoue, on le crée (c'est le premier worker)
            System.out.println("Aucun registre trouvé, création du registre...");
            registry = LocateRegistry.createRegistry(port);
        }

        // 3. Logique pour le nommage (toujours le même code)
        String name = "RaytracerWorker";
        try {
            String[] bindings = registry.list();
            int count = 0;
            for (String b : bindings) { if (b.startsWith("worker-")) count++; }
            name = "worker-" + (count + 1);
        } catch (Exception e) { name = "worker-1"; }

        // 4. Enregistrement
        RaytracerWorker worker = new RaytracerWorker(sceneFile);
        registry.rebind(name, worker);
        
        System.out.println("Worker prêt sous le nom : " + name);

   
            
            registry.rebind(name, worker);

            // Optionnel : On enregistre aussi une liaison générique pour faciliter la
            // détection par le Master
            registry.rebind("RaytracerWorker", worker);

            System.out.println("Worker prêt et enregistré localement !");
            System.out.println(" -> Identifiant unique : " + name);
            System.out.println(" -> Adresse réseau     : " + myIp + ":" + port);

        } catch (Exception e) {
            System.err.println("ERREUR CRITIQUE : Impossible de charger " + sceneFile);
            System.err.println("Chemin courant du processus : " + System.getProperty("user.dir"));
            e.printStackTrace(); // Cela affichera le vrai chemin où Java cherche le fichier
            throw new RuntimeException("Fichier de scène introuvable", e);
        }
    }
}