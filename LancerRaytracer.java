import java.time.Instant;
import java.time.Duration;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import raytracer.Disp;
import raytracer.Scene;
import raytracer.Image;

public class LancerRaytracer {

    public static String aide = "Raytracer : synthèse d'image par lancé de rayons (https://en.wikipedia.org/wiki/Ray_tracing_(graphics))\n\nUsage : java LancerRaytracer [fichier-scène] [largeur] [hauteur]\n\tjava LancerRaytracer master [fichier-scène] [largeur] [hauteur] [hote-registre] [port]\n\tjava LancerRaytracer worker [fichier-scène] [largeur] [hauteur] [nom] [hote-registre] [port]\n";

    public static void main(String args[]) {
        if (args.length > 0 && args[0].equalsIgnoreCase("worker")) {
            runWorker(args);
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("master")) {
            runMaster(args);
            return;
        }
        
        System.out.println(aide);


    }
    
    private static void runWorker(String[] args) {
        // Renvoi vers votre classe RaytracerWorker qui gère son propre registre
        try {
            RaytracerWorker.main(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void runMaster(String[] args) {
        String fichier_description = args.length > 1 ? args[1] : "simple.txt";
        int largeur = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        int hauteur = args.length > 3 ? Integer.parseInt(args[3]) : 512;
        String host = args.length > 4 ? args[4] : "10.247.22.44"; // IP du Worker
        int port = args.length > 5 ? Integer.parseInt(args[5]) : 1099;

        System.out.println("Master : Connexion au registre sur " + host + ":" + port);

        // LE MASTER EST UN CLIENT RMI : Il ne crée pas de registre, il le cherche
        Registry registry;
        InterfaceRaytracer worker;
        try {
            registry = LocateRegistry.getRegistry(host, port);
            // On cherche le nom défini dans RaytracerWorker (ex: "RaytracerWorker")
            worker = (InterfaceRaytracer) registry.lookup("RaytracerWorker");
            System.out.println("Connexion réussie au worker !");
        } catch (Exception e) {
            throw new RuntimeException("Impossible de se connecter au worker à " + host + ":" + port, e);
        }

        // Calcul
        Disp disp = new Disp("Raytracer RMI", largeur, hauteur);
        Instant debut = Instant.now();

        try {
            // Ici, vous pourriez boucler sur une liste de workers si vous en aviez plusieurs
            Image image = worker.computeRMI(0, 0, largeur, hauteur);
            disp.setImage(image, 0, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Instant fin = Instant.now();
        System.out.println("Image calculée en : " + Duration.between(debut, fin).toMillis() + " ms");
    }

    private static class Tile {
        private final int y0;
        private final Image image;

        private Tile(int y0, Image image) {
            this.y0 = y0;
            this.image = image;
        }
    }
}
