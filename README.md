# Projet RMI noté : calcul parallèle

- BODAT Emilien
- DI RENZO VILLER Aurelio

## Version de test locale
- Compiler proprement
```bash
javac -d bin (Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```
- Lancer pour chaque Worker (changer worker-1, par worker-2, worker-3,...)
```bash
java -cp bin RayTracerWorker simple.txt 64 64 worker-1 localhost 1099
```