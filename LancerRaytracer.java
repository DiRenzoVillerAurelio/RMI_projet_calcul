import java.time.Instant;
import java.time.Duration;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import raytracer.Disp;
import raytracer.Scene;
import raytracer.Image;

public class LancerRaytracer {

    public static String aide = "Usage : java LancerRaytracer master [fichier-scène] [largeur] [hauteur] [nb-workers-attendus]\n"
            + "        java LancerRaytracer worker [ip-master]\n";

    private static final List<InterfaceRaytracer> allWorkers = new CopyOnWriteArrayList<>();
    private static final List<String> allWorkerNames = new CopyOnWriteArrayList<>();

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
            WorkerRaytracer.main(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void runMaster(String[] args) {
        String fichier = args.length > 1 ? args[1] : "simple.txt";
        int largeur = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        int hauteur = args.length > 3 ? Integer.parseInt(args[3]) : 512;
        int port = args.length > 4 ? Integer.parseInt(args[4]) : 1099;

        try {
            // Création du registre RMI central par le Master
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println("Registre RMI créé sur le port " + port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
                System.out.println("Registre RMI existant récupéré.");
            }

            // Implémentation anonyme du gestionnaire d'enregistrement
            ServiceRaytracer manager = new ServiceRaytracer() {
                @Override
                public String enregistrerWorker(InterfaceRaytracer worker) throws java.rmi.RemoteException {
                    allWorkers.add(worker);
                    String name = "worker-" + allWorkers.size();
                    allWorkerNames.add(name);
                    System.out.println(" -> Noeud connecté : " + name);
                    return name;
                }
            };

            // Exportation du manager
            ServiceRaytracer stub = (ServiceRaytracer) UnicastRemoteObject.exportObject(manager, 0);
            registry.rebind("RaytracerManager", stub);
            System.out.println("Master prêt. Appuyez sur Entrée pour commencer...");
            System.in.read();

            System.out.println("Lancement du calcul asynchrone...");
            int idx = 0;
            while (idx < allWorkers.size()) {
                try {
                    allWorkers.get(idx).configureScene(fichier, largeur, hauteur);
                    idx++;
                } catch (Exception e) {
                    System.err.println("Le noeud " + allWorkerNames.get(idx) + " est injoignable, suppression.");
                    allWorkers.remove(idx);
                    allWorkerNames.remove(idx);
                }
            }

            if (allWorkers.isEmpty()) {
                System.err.println("Plus aucun worker n'est disponible.");
                return;
            }

            Disp disp = new Disp(fichier, largeur, hauteur);
            int totalWorkerCount = allWorkers.size();
            int stripeHeight = (hauteur + totalWorkerCount - 1) / totalWorkerCount;

            ExecutorService executor = Executors.newFixedThreadPool(totalWorkerCount);
            CompletionService<Tile> completion = new ExecutorCompletionService<>(executor);

            Instant debut = Instant.now();
            int tasksSubmitted = 0;

            int currentY = 0;
            int i = 0;
            for (InterfaceRaytracer worker : allWorkers) {
                final int y0 = currentY;
                final int tileHeight = Math.min(stripeHeight, hauteur - y0);
                String workerName = allWorkerNames.get(i++);

                if (tileHeight <= 0)
                    break;

                System.out.println(workerName + " assigné des lignes " + y0 + " à " + (y0 + tileHeight - 1));

                tasksSubmitted++;
                completion.submit(() -> {
                    try {
                        Image image = worker.computeRMI(0, y0, largeur, tileHeight);
                        return new Tile(y0, image);
                    } catch (Exception e) {
                        System.err.println(" ! Erreur de calcul pour " + workerName);
                        return new Tile(y0, null);
                    }
                });

                currentY += stripeHeight;
            }

            int received = 0;
            while (received < tasksSubmitted) {
                try {
                    Tile tile = completion.take().get();
                    if (tile.image != null) {
                        disp.setImage(tile.image, 0, tile.y0);
                    }
                    received++;
                } catch (Exception e) {
                    received++;
                }
            }

            executor.shutdown();
            System.out.println("Calcul terminé en " + Duration.between(debut, Instant.now()).toMillis() + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
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