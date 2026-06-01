# Projet RMI noté : calcul parallèle

- BODAT Emilien
- DI RENZO VILLER Aurelio

## Version de test locale
1. Compiler proprement
```bash
javac -d bin (Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```
2. Lancer pour chaque Worker (changer worker-1, par worker-2, worker-3,...)
```bash
java -cp bin RayTracerWorker simple.txt 720 720 worker-1
```

3. Lancer le Master après que tous les Workers soient lancés
```bash
java -cp bin LancerRaytracer master simple.txt 720 720 localhost 1099
``` 