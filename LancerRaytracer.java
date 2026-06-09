import java.time.Instant;
import java.time.Duration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        // machines sur le réseau
        List<String> hosts = loadHostsFromFile("workers.txt");

        if (hosts.isEmpty()) {
            System.out.println("Aucun worker trouvé dans workers.txt");
            return ;
        }

        System.out.println("Master : Recherche de tous les workers");
        List<InterfaceRaytracer> allWorkers = new ArrayList<>();

        // Connexion au registre RMI de chaque machine et récupération de tous les workers disponibles
        for (String ip : hosts) {
            try {
                Registry registry = LocateRegistry.getRegistry(ip, port);
                String[] bindings = registry.list(); // On récupère tous les noms enregistrés

                for (String name : bindings) {
                    
                    if (name.startsWith("worker-")) {
                        InterfaceRaytracer worker = (InterfaceRaytracer) registry.lookup(name);
                        allWorkers.add(worker);
                        System.out.println(" -> Ajouté : " + name + " sur " + ip);
                    }
                }
            } catch (Exception e) {
                System.err.println(" - Impossible de scanner la machine " + ip + " : " + e.getMessage());
            }
        }

        // Le Master impose la résolution à tous les workers connectés
        for (InterfaceRaytracer worker : allWorkers) {
            try {
                worker.configureScene(largeur, hauteur);
                System.out.println("Worker configuré en " + largeur + "x" + hauteur);
            } catch (Exception e) {
                System.err.println("Impossible de configurer le worker : " + e.getMessage());
            }
        }

        Disp disp = new Disp("Raytracer RMI", largeur, hauteur);
        Scene scene = new Scene(fichier_description, largeur, hauteur);

        // Vérification
        if (allWorkers.isEmpty()) {
            System.out.println("Aucun worker trouvé.");
            return;
        }

        // Répartition globale
        int totalWorkerCount = allWorkers.size();
        System.out.println("Calcul réparti sur " + totalWorkerCount + " workers au total.");

        int stripeHeight = (hauteur + totalWorkerCount - 1) / totalWorkerCount;
        ExecutorService executor = Executors.newFixedThreadPool(totalWorkerCount);
        CompletionService<Tile> completion = new ExecutorCompletionService<>(executor);

        Instant debut = Instant.now();
        int tasksSubmitted = 0;

        // On boucle sur tous les workers disponibles
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

        // Récupération des résultats au fur et à mesure de leur disponibilité
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

    // Lis workers.txt et retourne une liste d'adresses IP 
    private static List<String> loadHostsFromFile(String filename) {
        List<String> hosts = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename));
            for (String line : lines) {
                line = line.trim();
                // On ignore les lignes vides et les commentaires
                if (!line.isEmpty() && !line.startsWith("#")) {
                    hosts.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur : Impossible de lire le fichier " + filename + " - " + e.getMessage());
        }
        return hosts;
    }
}