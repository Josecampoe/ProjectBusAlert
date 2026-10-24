package com.busalert.wear.voice

/**
 * Separa una frase con origen y destino ("estoy en Fátima y voy a la Cooperativa")
 * en sus dos partes. Devuelve null si la frase no trae las dos.
 */
fun interface TripPhraseParser {
    fun split(text: String): TripPhrase?

    companion object {
        /** No separa nada: cada respuesta se toma tal cual. */
        val NONE = TripPhraseParser { null }
    }
}

data class TripPhrase(val origin: String, val destination: String)

/**
 * Busca el primer conector ("voy a", "quiero ir a", "hasta"…) como palabras completas,
 * sin distinguir mayúsculas. Los conectores vienen de recursos (`R.array.trip_destination_connectors`).
 */
class ConnectorTripPhraseParser(connectors: List<String>) : TripPhraseParser {

    private val pattern: Regex? = connectors
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // El más largo primero: en la misma posición gana "y voy a" sobre "voy a".
        .sortedByDescending { it.length }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("|") { connector ->
            connector.split(WHITESPACE).joinToString("""\s+""") { Regex.escape(it) }
        }
        ?.let { Regex("""(?<![\p{L}\p{N}])(?:$it)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }

    override fun split(text: String): TripPhrase? {
        val match = pattern?.find(text) ?: return null
        val origin = text.substring(0, match.range.first).trim(*TRIM_CHARS)
        val destination = text.substring(match.range.last + 1).trim(*TRIM_CHARS)
        if (origin.isEmpty() || destination.isEmpty()) return null
        return TripPhrase(origin, destination)
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val TRIM_CHARS = charArrayOf(' ', ',', '.', ';', '¿', '?', '¡', '!')
    }
}
