package com.obd2.jeancyril.obd

private fun u(b: IntArray, i: Int): Int = if (i < b.size) b[i] and 0xFF else 0

data class Pid(
    val id: String,
    val name: String,
    val unit: String,
    val icon: String,
    val description: String,
    val min: Double,
    val max: Double,
    val decimals: Int,
    val decode: (IntArray) -> Double?,
)

/**
 * Catalogue OBD-II générique (mode 01). Ces PID sont normalisés : ils fonctionnent
 * sur Toyota, PSA, Renault/Dacia, Volkswagen… dès que le calculateur les expose.
 */
object Pids {
    val all: List<Pid> = listOf(
        Pid(
            "010C", "Régime moteur", "tr/min", "🌀",
            "Nombre de tours du moteur par minute. Au ralenti : 700 à 900 tr/min. En accélération, il monte jusqu'au régime de puissance maximale.",
            0.0, 7000.0, 0
        ) { b -> ((u(b, 0) * 256) + u(b, 1)) / 4.0 },
        Pid(
            "010D", "Vitesse véhicule", "km/h", "🚗",
            "Vitesse lue sur le calculateur (souvent celle du capteur de boîte). Sert aussi à la consommation et à la mesure d'accélération.",
            0.0, 255.0, 0
        ) { b -> u(b, 0).toDouble() },
        Pid(
            "0105", "Température moteur", "°C", "🌡️",
            "Température du liquide de refroidissement. À chaud, elle se stabilise vers 85–95 °C. Une valeur qui ne monte pas indique un thermostat bloqué ouvert.",
            -40.0, 130.0, 0
        ) { b -> u(b, 0) - 40.0 },
        Pid(
            "0104", "Charge moteur", "%", "⚙️",
            "Part de la capacité du moteur utilisée, calculée par le calculateur. 100 % à pleine charge ; faible au ralenti.",
            0.0, 100.0, 0
        ) { b -> u(b, 0) * 100.0 / 255.0 },
        Pid(
            "0111", "Position papillon", "%", "🦶",
            "Ouverture du papillon des gaz. 0 % pied levé, 100 % à fond. Un écart avec la pédale peut signaler un problème de capteur ou de boîtier papillon.",
            0.0, 100.0, 1
        ) { b -> u(b, 0) * 100.0 / 255.0 },
        Pid(
            "010F", "Température air admission", "°C", "🌬️",
            "Température de l'air qui entre dans le moteur. Utile pour vérifier le refroidisseur d'air (turbo) et détecter une admission qui aspire de l'air chaud.",
            -40.0, 120.0, 0
        ) { b -> u(b, 0) - 40.0 },
        Pid(
            "0110", "Débit d'air (MAF)", "g/s", "💨",
            "Masse d'air mesurée par le débitmètre. Sert à calculer la consommation réelle. Un débit anormalement bas ou haut peut expliquer un manque de puissance.",
            0.0, 300.0, 1
        ) { b -> ((u(b, 0) * 256) + u(b, 1)) / 100.0 },
        Pid(
            "012F", "Niveau carburant", "%", "⛽",
            "Niveau dans le réservoir lu par la jauge. Sert de repère ; la consommation, elle, est calculée à partir du débit d'air.",
            0.0, 100.0, 0
        ) { b -> u(b, 0) * 100.0 / 255.0 },
        Pid(
            "010E", "Avance à l'allumage", "°", "⚡",
            "Avance de l'allumage par rapport au point mort haut. Le calculateur la fait varier en permanence selon la charge et le carburant.",
            -64.0, 64.0, 1
        ) { b -> u(b, 0) / 2.0 - 64.0 },
        Pid(
            "0106", "Correction carburant court terme", "%", "🎯",
            "Ajustement immédiat de la richesse par le calculateur (banc 1). Proche de 0 % quand tout va bien ; au-delà de ±10 %, chercher une prise d'air ou un injecteur.",
            -100.0, 100.0, 1
        ) { b -> (u(b, 0) - 128) * 100.0 / 128.0 },
        Pid(
            "0107", "Correction carburant long terme", "%", "🎚️",
            "Correction mémorisée par le calculateur. Une valeur durablement positive indique un mélange trop pauvre, négative un mélange trop riche.",
            -100.0, 100.0, 1
        ) { b -> (u(b, 0) - 128) * 100.0 / 128.0 },
        Pid(
            "0114", "Sonde O2 banc 1 capteur 1", "V", "🔬",
            "Tension de la sonde lambda amont. Elle oscille entre environ 0,1 et 0,9 V sur une sonde à saut ; une valeur figée annonce une sonde fatiguée.",
            0.0, 1.3, 2
        ) { b -> u(b, 0) / 200.0 },
        Pid(
            "010B", "Pression d'admission", "kPa", "🌫️",
            "Pression dans le collecteur d'admission. Au ralenti elle est basse (~30 kPa) et monte vers la pression atmosphérique à pleine charge.",
            0.0, 255.0, 0
        ) { b -> u(b, 0).toDouble() },
        Pid(
            "011F", "Temps depuis le démarrage", "s", "⏱️",
            "Durée écoulée depuis que le calculateur est sous tension. Pratique pour dater un incident pendant un trajet.",
            0.0, 65535.0, 0
        ) { b -> ((u(b, 0) * 256) + u(b, 1)).toDouble() },
        Pid(
            "0121", "Distance avec témoin moteur", "km", "📏",
            "Distance parcourue depuis que le témoin de défaut moteur s'est allumé.",
            0.0, 65535.0, 0
        ) { b -> ((u(b, 0) * 256) + u(b, 1)).toDouble() },
        Pid(
            "0131", "Distance depuis effacement", "km", "🧭",
            "Distance parcourue depuis le dernier effacement des codes défauts.",
            0.0, 65535.0, 0
        ) { b -> ((u(b, 0) * 256) + u(b, 1)).toDouble() },
        Pid(
            "0133", "Pression barométrique", "kPa", "🌍",
            "Pression atmosphérique mesurée par le calculateur. Elle baisse avec l'altitude et sert de référence aux calculs de remplissage.",
            0.0, 255.0, 0
        ) { b -> u(b, 0).toDouble() },
        Pid(
            "0142", "Tension calculateur", "V", "🔋",
            "Tension d'alimentation du calculateur. Moteur tournant : 13,5–14,5 V. En dessous de 12 V, contrôler la batterie et l'alternateur.",
            0.0, 18.0, 2
        ) { b -> ((u(b, 0) * 256) + u(b, 1)) / 1000.0 },
        Pid(
            "0146", "Température ambiante", "°C", "☀️",
            "Température extérieure vue par le calculateur. Sert de référence à la climatisation et à la gestion moteur.",
            -40.0, 80.0, 0
        ) { b -> u(b, 0) - 40.0 },
        Pid(
            "015C", "Température d'huile moteur", "°C", "🛢️",
            "Température de l'huile. Elle monte plus lentement que l'eau ; une huile à plus de 120 °C mérite une pause.",
            -40.0, 210.0, 0
        ) { b -> u(b, 0) - 40.0 },
        Pid(
            "015E", "Débit carburant", "L/h", "⛽",
            "Consommation instantanée rapportée par le calculateur, quand il expose ce PID. Sinon l'app la déduit du débitmètre d'air.",
            0.0, 60.0, 2
        ) { b -> ((u(b, 0) * 256) + u(b, 1)) / 20.0 },
        Pid(
            "0123", "Pression rampe carburant", "kPa", "🔧",
            "Pression du carburant dans la rampe commune (diesel) ou l'injection directe. Très variable selon le moteur et le régime.",
            0.0, 250000.0, 0
        ) { b -> ((u(b, 0) * 256) + u(b, 1)) * 10.0 },
    )

    fun byId(id: String): Pid? = all.firstOrNull { it.id == id }
}
