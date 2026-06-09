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
        // Distinguer le lancement local du mode maître et du mode worker.
        if (args.length > 0 && args[0].equalsIgnoreCase("worker")) {
            runWorker(args);
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("master")) {
            runMaster(args);
            return;
        }

        // Le fichier de description de la scène si pas fournie
        String fichier_description = "simple.txt";

        // largeur et hauteur par défaut de l'image à reconstruire
        int largeur = 512, hauteur = 512;

        if (args.length > 0) {
            fichier_description = args[0];
            if (args.length > 1) {
                largeur = Integer.parseInt(args[1]);
                if (args.length > 2)
                    hauteur = Integer.parseInt(args[2]);
            }
        } else {
            System.out.println(aide);
        }

        // création d'une fenêtre
        Disp disp = new Disp("Raytracer", largeur, hauteur);

        // Initialisation d'une scène depuis le modèle
        Scene scene = new Scene(fichier_description, largeur, hauteur);

        // Calcul de l'image de la scène les paramètres :
        // - x0 et y0 : correspondant au coin haut à gauche
        // - l et h : hauteur et largeur de l'image calculée
        // Ici on calcule toute l'image (0,0) -> (largeur, hauteur)

        int x0 = 0, y0 = 0;

        // Chronométrage du temps de calcul
        Instant debut = Instant.now();
        System.out.println("Calcul de l'image :\n - Coordonnées : " + x0 + "," + y0
                + "\n - Taille " + largeur + "x" + hauteur);
        Image image = scene.compute(x0, y0, largeur, hauteur);
        Instant fin = Instant.now();

        long duree = Duration.between(debut, fin).toMillis();

        System.out.println("Image calculée en :" + duree + " ms");

        // Affichage de l'image calculée
        disp.setImage(image, x0, y0);
    }

    private static void runWorker(String[] args) {
        String[] workerArgs = new String[Math.max(0, args.length - 1)];
        for (int i = 1; i < args.length; i++) {
            workerArgs[i - 1] = args[i];
        }
        try {
            RaytracerWorker.main(workerArgs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void runMaster(String[] args) {
        String fichier_description = args.length > 1 ? args[1] : "simple.txt";
        int largeur = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        int hauteur = args.length > 3 ? Integer.parseInt(args[3]) : 512;
        String host = args.length > 4 ? args[4] : "localhost";
        int port = args.length > 5 ? Integer.parseInt(args[5]) : 1099;

        Disp disp = new Disp("Raytracer RMI", largeur, hauteur);
        Scene scene = new Scene(fichier_description, largeur, hauteur);
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(host, port);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        String[] names;
        try {
            names = registry.list();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        // Récupérer les références distantes des workers publiés dans le registre.
        List<InterfaceRaytracer> workers = new ArrayList<>();
        List<String> assignedNames = new ArrayList<>();
        List<String> bindingNames = new ArrayList<>();
        int assignIndex = 1;
        for (String name : names) {
            try {
                InterfaceRaytracer w = (InterfaceRaytracer) registry.lookup(name);
                try {
                    // tester la joignabilité
                    w.computeRMI(0, 0, 1, 1);
                    workers.add(w);
                    assignedNames.add("worker-" + (assignIndex++));
                    bindingNames.add(name);
                } catch (Exception checkEx) {
                    try {
                        registry.unbind(name);
                        System.out.println("Removed dead binding: " + name);
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
                // ignorer bindings inaccessibles
            }
        }

        if (workers.isEmpty()) {
            Image image = scene.compute(0, 0, largeur, hauteur);
            disp.setImage(image, 0, 0);
            return;
        }

        int workerCount = workers.size();
        // Découper l'image en bandes horizontales pour les envoyer en parallèle.
        int stripeHeight = (hauteur + workerCount - 1) / workerCount;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<Tile> completion = new ExecutorCompletionService<>(executor);

        Instant debut = Instant.now();
        int tasksSubmitted = 0;
        for (int index = 0; index < workerCount; index++) {
            final int y0 = index * stripeHeight;
            final int tileHeight = Math.min(stripeHeight, hauteur - y0);
            final InterfaceRaytracer worker = workers.get(index);
            final String assignedName = assignedNames.get(index);
            final String bindingName = bindingNames.get(index);
            if (tileHeight <= 0) {
                continue;
            }
            tasksSubmitted++;
            completion.submit(new Callable<Tile>() {
                @Override
                public Tile call() throws Exception {
                    System.out.println("Invoking " + assignedName + " for rows " + y0 + ".." + (y0 + tileHeight - 1));
                    try {
                        Image image = worker.computeRMI(0, y0, largeur, tileHeight);
                        return new Tile(y0, image);
                    } catch (Exception callEx) {
                        System.out.println(
                                "Worker failure " + assignedName + " (" + bindingName + "): " + callEx.getMessage());
                        try {
                            registry.unbind(bindingName);
                            System.out.println("Unbound dead worker: " + bindingName);
                        } catch (Exception e) {
                        }
                        return new Tile(y0, null);
                    }
                }
            });
        }

        int received = 0;
        while (received < tasksSubmitted) {
            try {
                Future<Tile> f = completion.take();
                Tile tile = f.get();
                if (tile.image != null) {
                    disp.setImage(tile.image, 0, tile.y0);
                } else {
                    System.out.println("Tile for rows " + tile.y0 + " was not rendered due to worker failure.");
                }
                received++;
            } catch (Exception e) {
                System.out.println("A task failed: " + e.getMessage());
                received++;
            }
        }

        executor.shutdown();
        Instant fin = Instant.now();
        System.out.println("Image calculée en :" + Duration.between(debut, fin).toMillis() + " ms");
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
