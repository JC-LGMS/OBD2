# OBD2 — notes pour les agents

Application Android de diagnostic OBD-II : lecture/effacement des codes défauts, capteurs en
direct, tableau de bord personnalisable, courbes, autodiagnostic UCE, accélération, consommation,
profils véhicules (photos + journal) et sauvegarde ZIP. Kotlin + Jetpack Compose, Material3.

- package / applicationId : `com.obd2.jeancyril` ; activité launcher `com.obd2.jeancyril.MainActivity`
- compileSdk / targetSdk 37, minSdk 26, version 1.2
- Gradle 9.9.1, AGP 9.5.1, **Kotlin 2.2.10 fourni par AGP** (built-in) : ne pas appliquer
  `org.jetbrains.kotlin.android`, il entrerait en conflit. JDK 17.
- build-tools 36.0.0, Compose BOM 2026.09.00, Material3. Aucune autre dépendance runtime.

## Chaîne d'outils (spécifique à la machine)

Le wrapper est maintenant versionné (`gradlew.bat`, `gradle/wrapper/`) et fonctionne hors-ligne
avec la distribution 9.9.1 déjà en cache. Sinon, `gradle` **n'est pas dans le PATH** : utiliser la
distribution téléchargée (le hachage du dossier peut changer :
`Get-ChildItem ~\.gradle\wrapper\dists -Recurse -Filter gradle.bat`) :

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Temp\opencode\tools\jdk-17.0.21+9"   # PAS jdk-17.0.20.1+1 (sans javac)
$g   = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.9.1-bin\dol8b552jwt4yhd7ihm3iahwb\gradle-9.9.1\bin\gradle.bat"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
```

SDK : `%LOCALAPPDATA%\Android\Sdk` (plateforme `android-37.0`, build-tools 36.0.0). AVD
**Pixel_10** (API 37), souvent déjà lancé sur `emulator-5554`.

## Compilation, tests, installation

```powershell
& $g :app:compileDebugKotlin   --console=plain
& $g :app:assembleDebug        --console=plain
& $g :app:testDebugUnitTest    --console=plain   # 19 tests : protocole ELM, PID, DTC, conso, pleins, styles
& $g :app:connectedDebugAndroidTest --console=plain  # 5 tests : persistance, quarantaine, backup, migration, pagination
& $g :app:assembleRelease      --console=plain
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am force-stop com.obd2.jeancyril
& $adb shell am start -n com.obd2.jeancyril/.MainActivity
```

AGP/Kotlin/Compose sont en cache : `--offline` suffit en général ; en cas de dépendance
nouvelle, relancer une fois sans `--offline`.

## Vérification sans dongle : le simulateur

`obd/SimulatorLink.kt` répond à toutes les commandes ELM327. Connexion → « Lancer le simulateur
ELM327 » → l'état passe à `Mode simulateur`. Trajet joué en boucle (arrêt, 0→100, maintien,
freinage), deux codes présents (**P0133**, **P0420**, MIL allumé, EVAP non prêt). « Effacer »
les retire ; ils reviennent à la prochaine session simulateur.

C'est le seul moyen de vérifier l'app sur l'émulateur (pas de Bluetooth ni de dongle). Un build
qui compile ne suffit pas : installer, lancer le simulateur, contrôler tableau de bord, codes,
capteurs, courbes, journal.

Pilotage UI : `adb shell uiautomator dump /sdcard/ui.xml` + `adb pull`, puis `input tap X Y`.
Re-dumper après chaque action ; les libellés accentués ressortent mal dans la console mais les
bounds sont fiables.

## Architecture

- `obd/Elm327.kt` : session ELM327 (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`, `ATAT1`),
  lecture PID, DTC (modes 03/07/0A), moniteurs (0101), VIN (0902), effacement (04).
  `BluetoothLink` = SPP classique (UUID `00001101-…`), lecture bloquante avec prompt `>`.
  `obd/BleLink.kt` = BLE GATT (service FFE0, caractéristique FFE1, notifications), utilisé quand
  le dongle est BLE ; `connectBluetooth` tente le type annoncé puis l'autre en secours.
- `obd/Protocol.kt` : parsing pur des réponses (sans espaces, multi-trames `0:`, anciens
  protocoles avec octet de comptage). `obd/Pids.kt` : catalogue de 22 PID avec formules,
  icônes, plages, explications. `obd/Dtcs.kt` : décodage P/C/B/U + descriptions françaises
  (génériques P0xxx + codes constructeur Toyota, PSA, Renault/Dacia, Volkswagen ; `describe`
  prend la marque du véhicule actif).
- `ObdViewModel.kt` : connexion, polling séquentiel (250 ms + temps ELM ≈ 1 s/tour), vitesse et
  MAF toujours interrogées, historique 300 points/PID, consommation, chrono 0–100, DTC, VIN,
  CRUD véhicules, thème, styles de tuiles, backup.
- `data/GarageStore.kt` : persistance JSON + photos dans `filesDir/garage/`. Le journal est
  **append-only** dans `logs/<carId>.jsonl` (aucune réécriture, historique complet conservé,
  chargement par pages de 100 avec recherche/filtre). Les pleins plein-à-plein sont dans
  `fills/<carId>.jsonl`. `cars.json` ne garde que `logCount`, et les anciens journaux inclus dans
  `cars.json` sont migrés au chargement. Sauvegarde ZIP (`cars.json`, `settings.json`,
  `images/`, `logs/`, `fills/`, maximum 1 Go), restauration validée avant remplacement.
- `ui/` : `App.kt` (Scaffold, 5 onglets + sous-pages avec `BackHandler`, en-tête icône/version et
  statut sous la prise), `Changelog.kt` + `ChangelogScreen`, `DashboardScreen`, `SensorsScreen`,
  `DtcScreen`, `ChartsScreen`, `GarageScreen` (plaque, photos, journal, thème), `ToolsScreens`
  (connexion, accélération, consommation, journal, sauvegarde), composants et thèmes clair/sombre.

## Pièges vérifiés

- **Photo picker** : `PickMultipleVisualMedia` exige un compte Google sur l'émulateur (« Compte
  requis pour voir les photos »). Utiliser `ActivityResultContracts.OpenMultipleDocuments`
  + `image/*` (SAF) — fonctionne dès l'API 26, sélection multiple, copie immédiate dans
  `filesDir/garage/images`.
- **Bluetooth classique et BLE** : `BleLink` (GATT FFE0/FFE1) n'a pas pu être testé sur un vrai
  dongle dans cet environnement (émulateur sans Bluetooth). Toute modification de cette classe
  doit être vérifiée sur téléphone + dongle réel. Les dongles classiques passent par SPP.
- Le simulateur est **cyclique** : un 0–100 lancé pendant une phase de freinage attend l'arrêt
  (jusqu'à ~25 s), puis met ~25 s à atteindre 100 km/h. C'est normal.
- Le démarrage à froid d'un build **debug** peut afficher le splash plusieurs secondes sur
  l'émulateur ; attendre avant de conclure à un blocage (vérifier `pidof` / logcat).
- **Retour arrière** : les sous-pages sont un état `extra` dans `ObdApp` ; `BackHandler` le vide.
  Toute nouvelle sous-page doit passer par ce même état, pas par une activité séparée.
- `loadCars`/`loadSettings` tolèrent un JSON corrompu en le renommant `.bad` (message affiché) ;
  les écritures sont atomiques (`.tmp` + rename), et le thème comme les styles de tuiles sont
  sauvegardés dedans. Le journal n'est jamais dans `cars.json` : il est ajouté ligne par ligne
  dans `logs/`, donc garder tout l'historique ne ralentit pas les autres écritures.
- `android:allowBackup="false"` est volontaire : la seule sauvegarde est le ZIP de l'app (VIN,
  plaque, photos et journal ne doivent pas partir dans le cloud Android sans action).
- `packageRelease` échoue si `keystore.properties` manque : pas d'APK « release » non signé.
- Le wrapper Gradle est versionné ; `.\gradlew.bat` fonctionne hors-ligne avec la distribution
  déjà en cache.
- **Pas de réflexion** dans l'app : R8 n'a besoin d'aucune règle pour l'instant
  (`app/proguard-rules.pro` vide).
- Les codes constructeur sont une base **curatée** (Toyota, PSA, Renault/Dacia, VW), pas une
  base exhaustive : l'UI le dit et retombe sur une explication générique.

## Release (signée, R8)

- APK : `ODB2-<version>-release-<date>.apk` (ex. `ODB2-1.1-release-20261120.apk`), via
  `androidComponents.onVariants` dans `app/build.gradle.kts`. Ne pas revenir à `app-release.apk`.
- Keystore `odb2-release.keystore` (racine), alias `odb2`, mots de passe dans
  `keystore.properties` (non commité). **Même clé pour toutes les mises à jour** ; sauvegarder
  les deux fichiers, sinon plus de mise à jour possible.
- Certificat vérifié : `CN=Jean-Cyril, O=OBD2, C=FR`, SHA-256
  `41a5b0c4ed29f8ff8d961082bcff734c3882b00f436c5265bc600c2decf0c829`.
  Contrôle : `apksigner.bat verify --print-certs app\build\outputs\apk\release\ODB2-*-release-*.apk`.
- R8 actif (`isMinifyEnabled = true`) ; conserver
  `app/build/outputs/mapping/release/mapping.txt` avec chaque release.
- Incrémenter `versionCode` à chaque livraison. Signatures debug ≠ release : installer une
  release sur un appareil qui a la debug exige une désinstallation (données perdues).
