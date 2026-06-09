# Projet RMI noté : calcul parallèle

- BODAT Emilien
- DI RENZO VILLER Aurelio

## Version de test locale
1. Compiler proprement
```bash
javac -d bin (Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```
2. Lancer un Worker (peut être utilisée plusieurs fois pour plusieurs Workers)
```bash
java -cp bin LancerRaytracer worker simple.txt 720 720 localhost
```

3. Lancer le Master après que tous les Workers soient lancés
```bash
java -cp bin LancerRaytracer master simple.txt 720 720 localhost
```