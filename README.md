# Projet RMI noté : calcul parallèle

- BODAT Emilien
- DI RENZO VILLER Aurelio

## Version de test locale
1. Compiler proprement
```bash
javac -d bin *.java
```
2. Lancer un Worker (peut être utilisée plusieurs fois pour plusieurs Workers)
```bash
java -cp bin LancerRaytracer worker votreip simple.txt 
```

4. Ajouter les ip de vos machines worker dans le fichier workers.txt

3. Lancer le Master après que tous les Workers soient lancés
```bash
java -cp bin LancerRaytracer master simple.txt 720 720
```