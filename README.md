# Projet RMI noté : calcul parallèle

- BODAT Emilien
- DI RENZO VILLER Aurelio

## Ajouts apportés
- Enregistrement dynamique des workers auprès du Master via le registre RMI : Permet aux workers de s'enregistrer auprès du Master et de se désenregistrer en cas de fermeture.
- Tolérance aux pannes pré-calcul des workers : Si un worker crash avant de commencer le calcul, le master le détecte et redistribue le travail aux autres workers disponibles.
- Sécurisation de L'IP distante des workers : Permet aux workers de renvoyer leur IP exacte au Master.

## Déploiement de l'application
1. Compiler proprement
```bash
javac -d bin *.java
```
2. Lancer le Master sur la machine voulue :
- Les paramètres du master :
  - fichier : le nom du fichier scène, par défaut simple.txt
  - largeur : la largeur de l'image, par défaut 512
  - hauteur : la hauteur de l'image, par défaut 512
  - port : le port sur lequel le registre RMI sera créé, par défaut 1099

```bash
java -cp bin LancerRaytracer master simple.txt 720 720
```

3. Lancer un ou plusieurs Workers
- Les paramètres du worker :
  - ip-master : l'adresse IP du Master, par défaut localhost
  - port : le port sur lequel le registre RMI du Master est connecté, par défaut 1099

```bash
# Lancement sur le même poste que le Master
java -cp bin LancerRaytracer worker localhost

# Lancement sur un autre poste
java -cp bin LancerRaytracer worker [IP_DU_MASTER]
```

4. Retourner sur le Master et appuyer sur Entrée pour lancer le calcul.

L'image sera affichée à la fin du calcul en indicant quel worker a calculé quelle ligne.

Le temps de calcul sera également affiché.