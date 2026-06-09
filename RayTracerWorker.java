import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
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
        return scene.compute(x, y, width, height);
    }

    private static class RegistryProxyImpl extends java.rmi.server.UnicastRemoteObject implements RegistryProxy {
        private Registry registry;
        public RegistryProxyImpl(Registry reg) throws RemoteException {
            this.registry = reg;
        }
        public void rebind(String n, java.rmi.Remote obj) throws RemoteException {
            registry.rebind(n, obj);
        }
    }

    private static RegistryProxyImpl proxyInstance;

    public static void main(String[] args) throws Exception {
        String sceneFile = args.length > 0 ? args[0] : "simple.txt";
        int totalWidth = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        int totalHeight = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        String host = args.length > 3 ? args[3] : "localhost";
        int port = args.length > 4 ? Integer.parseInt(args[4]) : 1099;

        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        }

        String name = null;
        Registry registryForNaming = null;
        try {
            registryForNaming = LocateRegistry.getRegistry(host, port);
            String[] bindings = registryForNaming.list();
            int count = 0;
            for (String b : bindings)
                if (b.startsWith("worker-"))
                    count++;
            name = "worker-" + (count + 1);
        } catch (Throwable t) {
            name = "worker-1";
        }

        RaytracerWorker worker = new RaytracerWorker(sceneFile, totalWidth, totalHeight);
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(host, port);
            registry.list();
        } catch (RemoteException e) {
            if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
                throw e;
            }
            registry = LocateRegistry.createRegistry(port);
        }

        try {
            // Tentative de rebind direct (fonctionne si local)
            registry.rebind(name, worker);
            
            // Si on a réussi, on est local, on peut aussi publier le proxy
            try {
                if (proxyInstance == null) {
                    proxyInstance = new RegistryProxyImpl(registry);
                    registry.rebind("RegistryProxy", proxyInstance);
                }
            } catch (Exception e) {}
            
        } catch (java.rmi.AccessException e) {
            // Echec car distant, utilisation du proxy
            try {
                RegistryProxy proxy = (RegistryProxy) registry.lookup("RegistryProxy");
                proxy.rebind(name, worker);
            } catch (Exception ex) {
                System.err.println("Erreur: Impossible de contacter le registre distant et aucun RegistryProxy n'est disponible.");
                System.err.println("Solution: Démarrez d'abord un RaytracerWorker sur la machine maître (localhost).");
                throw ex;
            }
        }
        System.out.println("Worker prêt : " + name + " sur " + host + ":" + port);
    }
}