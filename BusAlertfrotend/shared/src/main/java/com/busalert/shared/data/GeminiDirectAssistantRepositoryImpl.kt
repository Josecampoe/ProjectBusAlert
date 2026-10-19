package com.busalert.shared.data

import com.busalert.shared.data.api.gemini.GeminiApiService
import com.busalert.shared.data.api.gemini.GeminiConfig
import com.busalert.shared.data.api.gemini.GeminiContent
import com.busalert.shared.data.api.gemini.GeminiPart
import com.busalert.shared.data.api.gemini.GeminiRequest
import com.busalert.shared.domain.AIAssistantRepository
import com.busalert.shared.domain.NavigationInstruction
import com.google.gson.Gson

/**
 * Implementación directa con la IA de Google (Gemini).
 * No requiere ningún servidor backend Node.js intermedio.
 * El smartwatch se comunica directamente con la API de Gemini por internet.
 */
class GeminiDirectAssistantRepositoryImpl(
    private val geminiApi: GeminiApiService,
    private val apiKey: String = GeminiConfig.API_KEY,
    private val gson: Gson = Gson()
) : AIAssistantRepository {

    companion object {
        private const val PASTO_TRANSIT_KNOWLEDGE = """
Eres el asistente de transporte público de la ciudad de San Juan de Pasto, Colombia.
Tu trabajo es ayudar a los usuarios a saber qué bus tomar y cuál es la parada más cercana
según donde estén y a dónde quieren ir.

## RUTAS PRINCIPALES DEL SETP DE PASTO

### RUTA E1 — ESTRATÉGICA 1
- Conecta: Dolores / Vía La Laguna ↔ Universidad de Nariño (Udenar Torobajo)
- Sentido Udenar → Dolores: Udenar Torobajo → Universidad Cooperativa → Villa María → El Dorado → Sagrada Familia → Cedenar → Av. Estudiantes → Las Cuadras → Carmelitas → San Agustín → San Juan Bosco → Parroquia Fátima → Parque Bolívar → Santacruz → Bernal → Las Mercedes → Villa Flor II → Santa Mónica → Caicedonia → Las Brisas → Pinal del Río → CAI Popular → Vía La Laguna
- Sentido Dolores → Udenar: Vía La Laguna → Dolores → Corpominga → Mocondino → CAI Popular → Las Brisas → Santa Mónica → Hospital Departamental → Parque Bolívar → Parroquia Fátima → San Juan Bosco → Av. Las Américas → María Goretti → Parque Infantil → Av. Estudiantes → Morasurco → Clínica Bellatriz → Universidad Cooperativa → Udenar Torobajo
- Frecuencia: cada 10-15 minutos. Horario: 5:00am - 10:30pm

### RUTA C16 — COMPLEMENTARIA 16
- Conecta: Centro (Parque Nariño) ↔ Lorenzo de Aldana ↔ Barrio La Rosa
- Paradas clave: Parque Nariño, Av. Boyacá con Cll 20, Terminal de Transportes, Lorenzo de Aldana, La Rosa, Olímpica sur
- Frecuencia: cada 8-12 minutos. Horario: 5:30am - 10:00pm

### RUTA E2 — ESTRATÉGICA 2
- Conecta: Barrio El Tejar ↔ Centro ↔ Barrio Santa Bárbara ↔ Jongovito
- Paradas clave: El Tejar, Parque Infantil, Centro (Cra 26 con Cll 19), Plazuela Agustinos, Av. Colombia, Santa Bárbara, Jongovito
- Frecuencia: cada 12-18 minutos. Horario: 5:30am - 9:30pm

### RUTA E4 — ESTRATÉGICA 4
- Conecta: Barrio Chambú ↔ Terminal ↔ Centro ↔ Lorenzo ↔ Barbachoa
- Paradas clave: Chambú, Av. Panamericana, Terminal de Transportes, Parque Nariño, Barrio Obrero, Lorenzo de Aldana, Barbachoa
- Frecuencia: cada 10-15 minutos. Horario: 5:30am - 10:00pm

### RUTA P1 — POPULAR 1
- Conecta: Barrio Madrigal ↔ Lorenzo ↔ San Vicente ↔ Centro
- Paradas clave: Madrigal, Lorenzo, Parque Popular, San Vicente de Paúl, Hospital Civil, Parque Nariño
- Frecuencia: cada 15-20 minutos. Horario: 5:30am - 9:00pm

### RUTA T1 — TRANSVERSAL 1
- Conecta: Barrio Bolivariano ↔ Universidades ↔ Av. Boyacá
- Paradas clave: Bolivariano, Torres de Fátima, Parque Bolívar, SENA Cll 20, Universidad Cooperativa, Udenar Torobajo, INEM
- Frecuencia: cada 12-18 minutos. Horario: 6:00am - 9:00pm

## REGLAS DE RESPUESTA:
1. Responde siempre en el idioma indicado. Tono amigable, claro y conciso.
2. Indica la parada más cercana y el código de ruta exacto (E1, C16, etc.).
3. Máximo 2 a 3 oraciones para spokenText.
4. displayTitle: nombre corto (ej: "Ruta E1").
5. displaySubtitle: formato "Parada más cercana • tiempo estimado".

## FORMATO DE RESPUESTA — SOLO JSON ESTRICTO:
{
  "spokenText": "texto natural para leer en voz alta",
  "displayTitle": "Ruta XX",
  "displaySubtitle": "Parada • tiempo",
  "estimatedMinutes": 8,
  "recommendedRouteId": "E1"
}
"""
    }

    override suspend fun processVoiceCommand(
        voiceText: String,
        currentLocationName: String,
        language: String
    ): NavigationInstruction {
        val isEn = language == "en"

        // Instrucción de idioma
        val langInstruction = if (isEn) {
            "Respond ONLY in clear ENGLISH. Fields 'spokenText', 'displayTitle' and 'displaySubtitle' must be in ENGLISH."
        } else {
            "Responde SIEMPRE en español, tono amigable, claro y local de Pasto."
        }

        val prompt = """
$PASTO_TRANSIT_KNOWLEDGE

$langInstruction

## CONSULTA DEL USUARIO:
- Ubicación actual / Current location: "$currentLocationName"
- Lo que dijo / Voice command: "$voiceText"

Responde ÚNICAMENTE con el objeto JSON solicitado:
""".trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        // Intento 1 con el modelo primario, intento 2 con el modelo fallback
        val modelsToTry = listOf(GeminiConfig.PRIMARY_MODEL, GeminiConfig.FALLBACK_MODEL)
        for (model in modelsToTry) {
            try {
                val response = geminiApi.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )

                val rawText = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

                if (!rawText.isNullOrBlank()) {
                    val parsed = parseJsonInstruction(rawText)
                    if (parsed != null) {
                        return parsed
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Si falla internet o la API, usamos la respuesta de respaldo local
        return getFallbackResponse(voiceText, currentLocationName, language)
    }

    private fun parseJsonInstruction(rawText: String): NavigationInstruction? {
        return try {
            val cleanJson = extractJson(rawText)
            gson.fromJson(cleanJson, NavigationInstruction::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        // Remover bloques ```json ... ```
        val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
        val match = fenceRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
    }

    /**
     * Respaldo inteligente local para cuando el smartwatch no tiene internet
     * o la cuota de la API está temporalmente ocupada.
     */
    private fun getFallbackResponse(
        voiceText: String,
        location: String,
        language: String
    ): NavigationInstruction {
        val text = voiceText.lowercase()
        val isEn = language == "en"

        return when {
            text.contains("cooperativa") || text.contains("ucc") -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "To go to Universidad Cooperativa, take route E1. The bus stop is on Av. Boyacá and Calle 22. It arrives in about 8 minutes."
                    else
                        "Para ir a la Universidad Cooperativa toma la ruta E1. La parada está en la Av. Boyacá con Calle 22. El bus llega en unos 8 minutos.",
                    displayTitle = if (isEn) "Route E1" else "Ruta E1",
                    displaySubtitle = "Av. Boyacá Cll 22 • 8 min",
                    estimatedMinutes = 8,
                    recommendedRouteId = "E1"
                )
            }
            text.contains("udenar") || text.contains("nariño") || text.contains("torobajo") -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "For Universidad de Nariño, take route E1. The stop is on Av. Boyacá in front of INEM. Arrives in 10 minutes."
                    else
                        "Para la Universidad de Nariño toma la ruta E1. La parada está en la Av. Boyacá frente al INEM. El bus llega en unos 10 minutos.",
                    displayTitle = if (isEn) "Route E1" else "Ruta E1",
                    displaySubtitle = "Av. Boyacá INEM • 10 min",
                    estimatedMinutes = 10,
                    recommendedRouteId = "E1"
                )
            }
            text.contains("lorenzo") -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "For Lorenzo de Aldana, take route C16 or E4 from Parque Nariño. Arrives in 10 minutes."
                    else
                        "Para Lorenzo de Aldana toma la ruta C16 o E4 desde el Parque Nariño. El bus llega en unos 10 minutos.",
                    displayTitle = if (isEn) "Route C16" else "Ruta C16",
                    displaySubtitle = "Parque Nariño • 10 min",
                    estimatedMinutes = 10,
                    recommendedRouteId = "C16"
                )
            }
            text.contains("terminal") -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "For the Bus Terminal, take route E4 or C16 on Av. Panamericana."
                    else
                        "Para la Terminal de Transportes toma la ruta E4 o la C16 en la Av. Panamericana.",
                    displayTitle = if (isEn) "Route E4 / C16" else "Ruta E4 / C16",
                    displaySubtitle = "Av. Panamericana • 7 min",
                    estimatedMinutes = 7,
                    recommendedRouteId = "E4"
                )
            }
            text.contains("hospital") -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "For the Departamental Hospital, take route E1 on Av. de los Estudiantes."
                    else
                        "Para el Hospital Departamental toma la ruta E1 sobre la Av. de los Estudiantes.",
                    displayTitle = if (isEn) "Route E1" else "Ruta E1",
                    displaySubtitle = "Av. Estudiantes • 8 min",
                    estimatedMinutes = 8,
                    recommendedRouteId = "E1"
                )
            }
            text.contains("centro") || text.contains("parque nariño") -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "For downtown Pasto, routes E1, E2, C16 or E4 pass through Parque Nariño. Wait time is around 5 minutes."
                    else
                        "Para el centro de Pasto puedes tomar la ruta E1, E2, C16 o E4 por el Parque Nariño. La espera es de unos 5 minutos.",
                    displayTitle = if (isEn) "Multiple Routes" else "Múltiples rutas",
                    displaySubtitle = "Parque Nariño • 5 min",
                    estimatedMinutes = 5,
                    recommendedRouteId = "E1"
                )
            }
            else -> {
                NavigationInstruction(
                    spokenText = if (isEn)
                        "From $location to your destination, take route E1. Nearest stop is approximately 3 blocks away."
                    else
                        "Desde $location hacia tu destino te recomiendo tomar la ruta E1 en la parada principal a 3 cuadras.",
                    displayTitle = if (isEn) "Route E1" else "Ruta E1",
                    displaySubtitle = if (isEn) "Nearest stop • 8 min" else "Parada cercana • 8 min",
                    estimatedMinutes = 8,
                    recommendedRouteId = "E1"
                )
            }
        }
    }
}

// Optimizacion: timeouts configurados para conexiones de red movil en Wear OS
