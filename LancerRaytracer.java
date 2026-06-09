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

        // Vos machines sur le réseau
        String[] hosts = {
                "10.247.22.44",
                // Ajoutez d'autres IPs ici
        };

        System.out.println("Master : Recherche dynamique de tous les workers...");
        List<InterfaceRaytracer> allWorkers = new ArrayList<>();

        // 1. Connexion et découverte dynamique
        for (String ip : hosts) {
            try {
                Registry registry = LocateRegistry.getRegistry(ip, port);
                String[] bindings = registry.list(); // On récupère tous les noms enregistrés

                for (String name : bindings) {
                    // On ne prend que les workers dynamiques (worker-1, worker-2, etc.)
                    if (name.startsWith("worker-")) {
                        InterfaceRaytracer worker = (InterfaceRaytracer) registry.lookup(name);
                        allWorkers.add(worker);
                        System.out.println(" -> Ajouté : " + name + " sur " + ip);
                    }
                }
            } catch (Exception e) {
                System.err.println(" -> Impossible de scanner la machine " + ip + " : " + e.getMessage());
            }
        }

        Disp disp = new Disp("Raytracer RMI", largeur, hauteur);
        Scene scene = new Scene(fichier_description, largeur, hauteur);

        // 2. Vérification
        if (allWorkers.isEmpty()) {
            System.out.println("Aucun worker trouvé. Calcul local");
            return;
        }

        // 3. Répartition globale
        int totalWorkerCount = allWorkers.size();
        System.out.println("Calcul réparti sur " + totalWorkerCount + " workers au total.");

        int stripeHeight = (hauteur + totalWorkerCount - 1) / totalWorkerCount;
        ExecutorService executor = Executors.newFixedThreadPool(totalWorkerCount);
        CompletionService<Tile> completion = new ExecutorCompletionService<>(executor);

        Instant debut = Instant.now();
        int tasksSubmitted = 0;

        // On boucle sur TOUS les workers trouvés
        for (int index = 0; index < totalWorkerCount; index++) {
            final int y0 = index * stripeHeight;
            final int tileHeight = Math.min(stripeHeight, hauteur - y0);
            final InterfaceRaytracer worker = allWorkers.get(index);

            if (tileHeight <= 0)
                continue;

            tasksSubmitted++;
            completion.submit(() -> {
                System.out.println("Envoi bandes " + y0 + " à " + (y0 + tileHeight));
                try {
                    Image image = worker.computeRMI(0, y0, largeur, tileHeight);
                    return new Tile(y0, image);
                } catch (Exception e) {
                    return new Tile(y0, null);
                }
            });
        }

        // 4. Récupération (identique à votre code précédent)
        int received = 0;
        while (received < tasksSubmitted) {
            try {
                Tile tile = completion.take().get();
                if (tile.image != null)
                    disp.setImage(tile.image, 0, tile.y0);
                received++;
            } catch (Exception e) {
                received++;
            }
        }

        executor.shutdown();
        System.out.println("Calcul terminé en : " + Duration.between(debut, Instant.now()).toMillis() + " ms");
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