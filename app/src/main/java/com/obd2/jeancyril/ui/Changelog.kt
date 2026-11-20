package com.obd2.jeancyril.ui

const val CHANGELOG_CREDITS = "Développé par Jean-Cyril Poiraud avec OpenCode et Deepseek v4.1 Flash 1017"

data class ChangelogEntry(val version: String, val date: String, val items: List<String>)

object Changelog {
    val entries = listOf(
        ChangelogEntry(
            version = "1.1",
            date = "20/11/2026",
            items = listOf(
                "Retour arrière dans toutes les sous-pages (connexion, journal, mesures, sauvegarde…).",
                "Numéro de version dans l'en-tête et page de changelog.",
                "Icône de l'application à côté du nom.",
                "Thème clair, en plus du thème sombre.",
                "Statut de connexion compact sous la prise, avec voyant coloré.",
                "Affichage des capteurs au choix : chiffres, jauge ou cadran.",
                "Plaque d'immatriculation dans la fiche véhicule.",
                "Sauvegarde complète : véhicules, photos, journal, tableau de bord et thème.",
                "Liaison BLE (GATT FFE0/FFE1) en plus du Bluetooth classique.",
                "Codes constructeur pour Toyota, Citroën/Peugeot, Renault/Dacia et Volkswagen.",
                "Consommation disponible dès le début de la session (lecture MAF prioritaire).",
            ),
        ),
        ChangelogEntry(
            version = "1.0",
            date = "20/11/2026",
            items = listOf(
                "Version initiale : lecture et effacement des codes défauts, autodiagnostic UCE.",
                "Tableau de bord personnalisable, capteurs en direct, courbes temporelles.",
                "Mesure d'accélération 0–100 km/h et statistiques de consommation.",
                "Profils véhicules avec photos, journal des actions, VIN.",
                "Sauvegarde et restauration des données, simulateur ELM327 intégré.",
            ),
        ),
    )
}
