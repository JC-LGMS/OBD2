# OBD2

Application Android de diagnostic automobile OBD-II via un dongle ELM327 (Bluetooth classique
ou BLE) : lecture et effacement des codes défauts, capteurs en direct, tableau de bord
personnalisable, courbes, autodiagnostic UCE, mesure d'accélération, consommation, profils
véhicules et sauvegarde.

## Fonctionnalités

- Connexion ELM327 : Bluetooth classique (SPP) et BLE GATT (FFE0/FFE1), état de la liaison.
- Codes défauts : mémorisés, en attente, permanents ; descriptions génériques et constructeur
  (Toyota, Citroën/Peugeot, Renault/Dacia, Volkswagen) ; effacement avec confirmation.
- Autodiagnostic UCE : témoin moteur, nombre de codes, moniteurs prêts/non prêts.
- 22 capteurs OBD-II avec explications, icônes, plages et affichage au choix : chiffres, jauge
  ou cadran.
- Courbes temporelles, mesure 0–100 km/h et accélération en g, consommation instantanée/moyenne.
- Profils véhicules : marque, modèle, année, moteur, VIN, plaque, plusieurs photos, journal
  complet des actions (recherche, filtres, chargement par pages).
- Consommation réelle plein à plein, y compris sur diesel ; rapport de codes partageable.
- Thème sombre, clair ou système ; recherche Bluetooth intégrée pour les dongles non associés.
- Sauvegarde/restauration ZIP (véhicules, photos, journal, pleins, réglages).
- Simulateur ELM327 intégré pour tester sans dongle ni véhicule.

## Compilation

Prérequis : JDK 17, Android SDK 37, AGP 9.5.1 (Kotlin intégré). Le wrapper Gradle 9.9.1 est fourni.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest   # émulateur ou appareil
.\gradlew.bat :app:assembleRelease
```

L'APK de release est nommé `ODB2-<version>-release-<date>.apk` et signé avec la clé locale
(`keystore.properties`, non versionné).

## Utilisation

1. Associer l'ELM327 dans les réglages Bluetooth du téléphone (PIN souvent 1234 ou 0000).
2. Brancher le dongle sur la prise OBD-II, mettre le contact.
3. Ouvrir l'app, toucher l'icône de prise, sélectionner le dongle.
4. Sans dongle : « Lancer le simulateur ELM327 ».

## Limites connues

- Le support BLE GATT est implémenté pour le service FFE0/FFE1 ; certains dongles exotiques
  peuvent utiliser un autre service.
- La base de codes constructeur est curatée, pas exhaustive ; les codes inconnus affichent une
  explication générique.
- L'application lit les PID OBD-II normalisés ; les codes constructeur avancés nécessitant un
  protocole propriétaire ne sont pas couverts.
