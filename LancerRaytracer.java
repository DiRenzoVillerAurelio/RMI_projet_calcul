import java.time.Instant;
import java.time.Duration;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
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

    public static String aide = "Raytracer : synthèse d'image par lancé de rayons\n\nUsage : java LancerRaytracer [fichier-scène] [largeur] [hauteur]\n\tjava LancerRaytracer master [fichier-scène] [largeur] [hauteur]\n\tjava LancerRaytracer worker [fichier-scène] [largeur] [hauteur]\n";

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
        int port = 1099;
        
        // TABLEAU DES WORKERS : Ajoutez ici les IPs de tous vos ordinateurs disponibles
        String[] hosts = {
            "10.247.22.44",
            // "10.247.22.56", // Décommentez ou ajoutez d'autres IPs ici
        };

        System.out.println("Master : Recherche des workers...");
        List<InterfaceRaytracer> workers = new ArrayList<>();
        List<String> workerNames = new ArrayList<>();

        // 1. Connexion à tous les workers listés dans le tableau
        for (String ip : hosts) {
            try {
                Registry registry = LocateRegistry.getRegistry(ip, port);
                InterfaceRaytracer worker = (InterfaceRaytracer) registry.lookup("RaytracerWorker");
                
                // Petit test pour s'assurer que le worker répond bien
                worker.computeRMI(0, 0, 1, 1); 
                
                workers.add(worker);
                workerNames.add(ip);
                System.out.println(" -> Connexion réussie au worker sur " + ip);
            } catch (Exception e) {
                System.err.println(" -> Worker injoignable sur " + ip + " : " + e.getMessage());
            }
        }

        Disp disp = new Disp("Raytracer RMI", largeur, hauteur);
        Scene scene = new Scene(fichier_description, largeur, hauteur);

        // 2. Si aucun worker n'est dispo, on calcule tout en local
        if (workers.isEmpty()) {
            System.out.println("Aucun worker disponible. Calcul en local (séquentiel)...");
            Instant debut = Instant.now();
            Image image = scene.compute(0, 0, largeur, hauteur);
            Instant fin = Instant.now();
            System.out.println("Image calculée en : " + Duration.between(debut, fin).toMillis() + " ms");
            disp.setImage(image, 0, 0);
            return;
        }

        // 3. Répartition du travail (Logique de parallélisation)
        int workerCount = workers.size();
        System.out.println("Calcul de l'image (" + largeur + "x" + hauteur + ") réparti sur " + workerCount + " noeud(s).");
        
        // Découper l'image en bandes horizontales pour les envoyer en parallèle
        int stripeHeight = (hauteur + workerCount - 1) / workerCount;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<Tile> completion = new ExecutorCompletionService<>(executor);

        Instant debut = Instant.now();
        int tasksSubmitted = 0;

        for (int index = 0; index < workerCount; index++) {
            final int y0 = index * stripeHeight;
            final int tileHeight = Math.min(stripeHeight, hauteur - y0);
            final InterfaceRaytracer worker = workers.get(index);
            final String workerName = workerNames.get(index);

            if (tileHeight <= 0) {
                continue;
            }
            
            tasksSubmitted++;
            completion.submit(new Callable<Tile>() {
                @Override
                public Tile call() throws Exception {
                    System.out.println("Envoi au worker (" + workerName + ") des lignes " + y0 + " à " + (y0 + tileHeight - 1));
                    try {
                        Image image = worker.computeRMI(0, y0, largeur, tileHeight);
                        return new Tile(y0, image);
                    } catch (Exception callEx) {
                        System.err.println("Échec du calcul sur le worker (" + workerName + ") : " + callEx.getMessage());
                        return new Tile(y0, null);
                    }
                }
            });
        }

        // 4. Récupération et affichage des morceaux au fur et à mesure
        int received = 0;
        while (received < tasksSubmitted) {
            try {
                Future<Tile> f = completion.take(); // Attend que le premier worker qui a fini donne son résultat
                Tile tile = f.get();
                if (tile.image != null) {
                    disp.setImage(tile.image, 0, tile.y0);
                } else {
                    System.err.println("La bande commençant à la ligne " + tile.y0 + " n'a pas pu être rendue.");
                }
                received++;
            } catch (Exception e) {
                System.err.println("Une tâche RMI a échoué : " + e.getMessage());
                received++;
            }
        }

        executor.shutdown();
        Instant fin = Instant.now();
        System.out.println("Image calculée en : " + Duration.between(debut, fin).toMillis() + " ms");
    }

    // Classe interne pour stocker un morceau d'image et sa position Y
    private static class Tile {
        private final int y0;
        private final Image image;

        private Tile(int y0, Image image) {
            this.y0 = y0;
            this.image = image;
        }
    }
}