package com.obd2.jeancyril.obd

/**
 * Décodage des codes défauts (norme OBD-II) et explications en français.
 * Les codes P0xxx sont normalisés ; les codes P1xxx/P2xxx/P3xxx sont propres
 * au constructeur et ne peuvent pas être traduits de façon fiable sans sa documentation.
 */
object Dtcs {
    fun decode(firstByte: Int, secondByte: Int): String {
        val b1 = firstByte and 0xFF
        val b2 = secondByte and 0xFF
        val letter = "PCBU"[(b1 shr 6) and 0x03]
        return buildString {
            append(letter)
            append((b1 shr 4) and 0x03)
            append(b1 and 0x0F)
            append("%02X".format(b2))
        }
    }

    private val descriptions = mapOf(
        "P0011" to "Calage d'arbre à cames (banc 1) hors spécification : distribution ou déphaseur à contrôler.",
        "P0087" to "Pression de carburant trop basse : filtre, pompe ou régulateur (fréquent sur diesel common rail).",
        "P0088" to "Pression de carburant trop haute : régulateur de pression ou capteur de rampe.",
        "P0093" to "Fuite de carburant détectée dans le système haute pression (diesel).",
        "P0100" to "Circuit du débitmètre d'air : câblage, connecteur ou débitmètre.",
        "P0101" to "Plage du débitmètre d'air incorrecte : débitmètre sale ou prise d'air.",
        "P0102" to "Signal du débitmètre trop bas : débitmètre, câblage ou filtre à air obturé.",
        "P0103" to "Signal du débitmètre trop haut : débitmètre ou câblage.",
        "P0110" to "Circuit de la sonde de température d'air d'admission.",
        "P0113" to "Signal de température d'air trop haut : sonde ou câblage.",
        "P0115" to "Circuit de la sonde de température du liquide de refroidissement.",
        "P0117" to "Température moteur trop basse : sonde, thermostat bloqué ouvert.",
        "P0118" to "Température moteur trop haute : sonde, niveau de liquide, thermostat.",
        "P0120" to "Circuit du capteur de position du papillon.",
        "P0121" to "Plage du capteur de papillon incohérente : nettoyer ou remplacer le boîtier papillon.",
        "P0122" to "Signal du capteur de papillon trop bas.",
        "P0123" to "Signal du capteur de papillon trop haut.",
        "P0130" to "Circuit de la sonde lambda amont (banc 1).",
        "P0131" to "Tension de sonde lambda amont trop basse : mélange pauvre ou sonde fatiguée.",
        "P0132" to "Tension de sonde lambda amont trop haute : mélange riche ou sonde fatiguée.",
        "P0133" to "Réponse de la sonde lambda amont trop lente : sonde à remplacer.",
        "P0135" to "Circuit de chauffage de la sonde lambda amont (banc 1).",
        "P0141" to "Circuit de chauffage de la sonde lambda aval (banc 1).",
        "P0171" to "Mélange trop pauvre (banc 1) : prise d'air, injecteurs, pression de carburant.",
        "P0172" to "Mélange trop riche (banc 1) : injecteur qui fuit, capteur de pression ou débitmètre.",
        "P0201" to "Circuit de l'injecteur du cylindre 1.",
        "P0202" to "Circuit de l'injecteur du cylindre 2.",
        "P0203" to "Circuit de l'injecteur du cylindre 3.",
        "P0204" to "Circuit de l'injecteur du cylindre 4.",
        "P0230" to "Circuit de la pompe à carburant.",
        "P0234" to "Surpression de turbo : géométrie variable ou wastegate bloquée.",
        "P0299" to "Sous-régime de turbo : fuite d'admission, wastegate ou géométrie variable.",
        "P0300" to "Ratés d'allumage multiples : allumage, injecteurs, compression, prise d'air.",
        "P0301" to "Raté d'allumage cylindre 1 : bougie, bobine, injecteur ou compression.",
        "P0302" to "Raté d'allumage cylindre 2 : bougie, bobine, injecteur ou compression.",
        "P0303" to "Raté d'allumage cylindre 3 : bougie, bobine, injecteur ou compression.",
        "P0304" to "Raté d'allumage cylindre 4 : bougie, bobine, injecteur ou compression.",
        "P0325" to "Circuit du capteur de cognement (banc 1).",
        "P0335" to "Circuit du capteur de position de vilebrequin : moteur qui cale ou ne démarre pas.",
        "P0340" to "Circuit du capteur d'arbre à cames.",
        "P0380" to "Circuit de préchauffage (bougies ou boîtier) : démarrage difficile à froid sur diesel.",
        "P0400" to "Débit de recirculation des gaz d'échappement (EGR) incorrect.",
        "P0401" to "Débit EGR insuffisant : vanne encrassée (très fréquent sur diesel).",
        "P0402" to "Débit EGR excessif : vanne bloquée ouverte.",
        "P0403" to "Circuit de commande de la vanne EGR.",
        "P0420" to "Rendement du catalyseur (banc 1) insuffisant : catalyseur usé ou sonde aval.",
        "P0430" to "Rendement du catalyseur (banc 2) insuffisant.",
        "P0440" to "Circuit de récupération des vapeurs de carburant (EVAP).",
        "P0442" to "Petite fuite dans le système EVAP : bouchon de réservoir ou durite.",
        "P0455" to "Grosse fuite EVAP : bouchon de réservoir desserré ou durite débranchée.",
        "P0500" to "Capteur de vitesse véhicule : compteur ou boîte de vitesses.",
        "P0505" to "Régulation du ralenti : corps de papillon encrassé ou prise d'air.",
        "P0562" to "Tension d'alimentation du calculateur trop basse : batterie ou alternateur.",
        "P0600" to "Défaut de communication interne au calculateur.",
        "P0700" to "Le calculateur de boîte de vitesses signale un défaut : lire les codes de la boîte.",
        "P1135" to "Circuit de chauffage de la sonde lambda (souvent Toyota) : sonde à contrôler.",
        "P1351" to "Circuit de préchauffage (souvent Citroën/Peugeot diesel) : bougies ou relais.",
        "P1602" to "Alimentation du calculateur interrompue (souvent Volkswagen) : batterie ou câblage.",
        "P2002" to "Rendement du filtre à particules (FAP) insuffisant : régénération ou FAP encrassé.",
        "P2463" to "FAP saturé : régénération forcée ou nettoyage nécessaire.",
    )

    fun describe(code: String): String = describe(code, null)

    fun describe(code: String, brand: String?): String {
        val brandText = brandKey(brand)?.let { brandCodes[it]?.get(code) }
        return brandText ?: descriptions[code] ?: when {
            code.startsWith("P1") || code.startsWith("P2") || code.startsWith("P3") ->
                "Code spécifique constructeur. Vérifier la documentation du véhicule (Toyota, PSA, Renault/Dacia, Volkswagen…) : sa signification varie d'un modèle à l'autre."
            code.startsWith("U") ->
                "Défaut du réseau de communication embarqué (CAN). Contrôler les connecteurs, les masses et les calculateurs concernés."
            code.startsWith("B") ->
                "Défaut de carrosserie (airbag, habitacle, éclairage). Souvent propre au constructeur."
            code.startsWith("C") ->
                "Défaut de châssis (ABS, ESP, suspension). Souvent propre au constructeur."
            else -> "Code non documenté dans cette application."
        }
    }

    private fun brandKey(brand: String?): String? {
        val text = brand.orEmpty()
        if (text.isBlank()) return null
        return when {
            text.contains("toyota", ignoreCase = true) || text.contains("lexus", ignoreCase = true) -> "Toyota"
            text.contains("citro", ignoreCase = true) || text.contains("peugeot", ignoreCase = true) -> "PSA"
            text.contains("renault", ignoreCase = true) || text.contains("dacia", ignoreCase = true) -> "Renault"
            text.contains("volkswagen", ignoreCase = true) || text.contains("vw", ignoreCase = true) ||
                text.contains("audi", ignoreCase = true) || text.contains("seat", ignoreCase = true) ||
                text.contains("skoda", ignoreCase = true) -> "VW"
            else -> null
        }
    }

    /** Codes propres aux constructeurs les plus courants. L'explication reste indicative. */
    private val brandCodes: Map<String, Map<String, String>> = mapOf(
        "Toyota" to mapOf(
            "P1135" to "Toyota : circuit de chauffage de la sonde A/F (banc 1, sonde 1). Vérifier la sonde et son faisceau.",
            "P1150" to "Toyota : circuit de la sonde A/F (banc 1, sonde 2).",
            "P1155" to "Toyota : circuit de chauffage de la sonde O2 (banc 1, sonde 2).",
            "P1165" to "Toyota : circuit de la sonde A/F (banc 2, sonde 1).",
            "P1349" to "Toyota : système VVT-i, calage de l'arbre à cames hors spécification (huile, déphaseur, distribution).",
            "P1420" to "Toyota : pompe d'air secondaire ou son circuit. Souvent après un démarrage à froid.",
            "P2237" to "Toyota : courant de pompe de la sonde A/F trop élevé (banc 1, sonde 1).",
            "P2238" to "Toyota : courant de pompe de la sonde A/F trop bas (banc 1, sonde 1).",
        ),
        "PSA" to mapOf(
            "P1351" to "Citroën/Peugeot : circuit du relais de préchauffage. Bougies, relais ou faisceau (diesel).",
            "P1350" to "Citroën/Peugeot : commande de préchauffage incohérente. Contrôler les bougies et le boîtier.",
            "P1434" to "Citroën/Peugeot : pompe d'additif du filtre à particules. Niveau ou fonctionnement de la pompe.",
            "P1435" to "Citroën/Peugeot : niveau d'additif FAP trop bas ou capteur défaillant.",
            "P1446" to "Citroën/Peugeot : électrovanne de recirculation EGR (circuit de commande).",
            "P242F" to "Filtre à particules colmaté (fréquent PSA). Régénération ou nettoyage nécessaire.",
        ),
        "Renault" to mapOf(
            "P0638" to "Renault/Dacia : commande du boîtier papillon hors plage. Nettoyer le papillon, contrôler son moteur.",
            "P1525" to "Renault/Dacia : incohérence entre les capteurs de pédale et le papillon. Contrôler le capteur de pédale.",
            "P2002" to "Renault/Dacia : rendement du filtre à particules insuffisant. Lancer une régénération.",
            "P242F" to "Renault/Dacia : filtre à particules colmaté. Régénération forcée ou nettoyage.",
        ),
        "VW" to mapOf(
            "P1602" to "Volkswagen : alimentation du calculateur interrompue (batterie débranchée ou câblage).",
            "P1420" to "Volkswagen : pompe d'air secondaire ou relais. Souvent au démarrage à froid.",
            "P0411" to "Volkswagen : débit d'air secondaire incorrect. Contrôler la pompe et les durites.",
            "P0243" to "Volkswagen : électrovanne de wastegate du turbo (banc 1).",
            "P1296" to "Volkswagen : défaut de refroidissement moteur (souvent thermostat ou sonde).",
        ),
    )

}
