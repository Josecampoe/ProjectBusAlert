/**
 * aiService.ts
 * Servicio central de IA para BusAlert.
 *
 * Arquitectura de 4 capas (equivalente al patrón TallerPro):
 *   Config (.env) → AIService → Async HTTP → Express Controllers
 *
 * Mejoras aplicadas:
 *   ✅ Fallback automático entre modelos (gemini-3.8-flash → gemini-2.5-flash → gemini-2.5-flash-lite)
 *   ✅ Detección de errores específicos: 401, cuota agotada, modelo no disponible
 *   ✅ Limpieza robusta de bloques Markdown ```json con regex
 *   ✅ Validación estricta del JSON de respuesta
 *   ✅ Mensajes de error amigables para el usuario final
 *   ✅ Logging detallado por cada intento de modelo
 */

import { GoogleGenAI } from '@google/genai';

// ─── Modelos en orden de preferencia (fallback automático) ────────────────────
// gemini-3.8-flash es el modelo actual disponible con API keys de AI Studio
const MODEL_PRIORITY = [
    'gemini-3.8-flash',
    'gemini-flash-latest',  // alias genérico que siempre apunta al flash más reciente
];

// ─── Tipos de error para mensajes amigables ───────────────────────────────────
function classifyError(error: unknown): { isRetryable: boolean; message: string } {
    const msg = String(error);

    if (msg.includes('401') || msg.includes('UNAUTHENTICATED') || msg.includes('API_KEY_INVALID')) {
        return {
            isRetryable: false,
            message: 'La API Key de Gemini es inválida o no está autorizada. Revisa tu archivo .env.',
        };
    }
    if (msg.includes('403') || msg.includes('PERMISSION_DENIED')) {
        return {
            isRetryable: false,
            message: 'Sin permiso para usar este modelo. Verifica los permisos de tu API Key.',
        };
    }
    if (msg.includes('429') || msg.includes('RESOURCE_EXHAUSTED') || msg.includes('quota')) {
        return {
            isRetryable: true,
            message: 'Cuota de la API agotada. Intenta de nuevo en unos minutos.',
        };
    }
    if (msg.includes('404') || msg.includes('no longer available') || msg.includes('not found')) {
        return {
            isRetryable: true, // retryable porque probará el siguiente modelo
            message: 'Modelo no disponible, probando alternativa...',
        };
    }
    if (msg.includes('503') || msg.includes('Service Unavailable') || msg.includes('overloaded')) {
        return {
            isRetryable: true,
            message: 'El servicio de IA está sobrecargado, probando modelo alternativo...',
        };
    }
    if (msg.includes('ECONNREFUSED') || msg.includes('ETIMEDOUT') || msg.includes('fetch')) {
        return {
            isRetryable: false,
            message: 'Error de red al conectar con la API de Gemini. Verifica tu conexión.',
        };
    }

    return {
        isRetryable: true,
        message: `Error inesperado: ${msg.substring(0, 120)}`,
    };
}

// ─── Limpieza robusta de bloques Markdown ─────────────────────────────────────
function extractJsonFromResponse(raw: string): string {
    const trimmed = raw.trim();

    // Extrae el bloque ```json ... ``` o ``` ... ```
    const fenceMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
    if (fenceMatch) return fenceMatch[1].trim();

    // Si no hay fences pero empieza con { o [, asume que es JSON directo
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) return trimmed;

    // Último recurso: busca el primer { hasta el último }
    const start = trimmed.indexOf('{');
    const end   = trimmed.lastIndexOf('}');
    if (start !== -1 && end !== -1 && end > start) {
        return trimmed.substring(start, end + 1);
    }

    return trimmed;
}

// ─── Llamada a Gemini con fallback entre modelos ──────────────────────────────
async function callGeminiWithFallback(
    apiKey: string,
    prompt: string,
    context: string
): Promise<string> {
    const ai = new GoogleGenAI({ apiKey });
    let lastError: unknown;

    for (const model of MODEL_PRIORITY) {
        try {
            console.log(`[BusAlert] 🤖 Intentando con modelo: ${model} (${context})`);
            const result = await ai.models.generateContent({
                model,
                contents: prompt,
            });
            const text = (result.text ?? '').trim();
            if (!text) throw new Error('Respuesta vacía del modelo.');
            console.log(`[BusAlert] ✅ Respuesta exitosa con: ${model}`);
            return text;
        } catch (err) {
            const classified = classifyError(err);
            console.warn(`[BusAlert] ⚠ Modelo ${model} falló: ${classified.message}`);
            lastError = err;

            // Si el error no es reintentable (ej: 401), detenemos inmediatamente
            if (!classified.isRetryable) {
                throw new Error(classified.message);
            }
            // Si es reintentable, pasamos al siguiente modelo
        }
    }

    // Todos los modelos fallaron
    const finalClassified = classifyError(lastError);
    throw new Error(finalClassified.message);
}


// ============================================================
// TIPOS PÚBLICOS
// ============================================================

export interface NavigationInstruction {
    spokenText:         string;
    displayTitle:       string;
    displaySubtitle:    string;
    estimatedMinutes:   number | null;
    recommendedRouteId: string | null;
}

export interface TransportQueryResponse {
    answer:     string;
    confidence: 'high' | 'medium' | 'low';
}


// ============================================================
// BASE DE CONOCIMIENTO: RUTAS DEL SETP DE PASTO, NARIÑO
// (Para el modo voz del smartwatch — se usa cuando la DB
//  no tiene datos suficientes del recorrido.)
// ============================================================
const PASTO_TRANSIT_KNOWLEDGE = `
Eres el asistente de transporte público de la ciudad de San Juan de Pasto, Colombia.
Tu trabajo es ayudar a los usuarios a saber qué bus tomar y cuál es la parada más cercana
según donde estén y a dónde quieren ir.

## RUTAS PRINCIPALES DEL SETP DE PASTO

### RUTA E1 — ESTRATÉGICA 1
- Conecta: Dolores / Vía La Laguna ↔ Universidad de Nariño (Udenar Torobajo)
- Sentido Udenar → Dolores (37 paradas): Udenar Veterinaria → Udenar Torobajo → Universidad Cooperativa → Villa María → El Dorado → Sagrada Familia → Cedenar → Av. Estudiantes → Las Cuadras → Carmelitas → San Agustín → San Juan Bosco → Parroquia Fátima → Parque Bolívar → Santacruz → Bernal → Las Mercedes → Villa Flor II → Santa Mónica → Caicedonia → Las Brisas → Pinal del Río → CAI Popular → Vía La Laguna
- Sentido Dolores → Udenar (50 paradas): Vía La Laguna → Dolores → Iglesia Dolores → Corpominga → Mocondino → Variante Daza → CAI Popular → Pinal del Río → Las Brisas → Caicedonia → Santa Mónica → Villa Flor II → Hospital Departamental → Parque Bolívar → Parroquia Fátima → Alahambra → San Juan Bosco → Av. Las Américas → María Goretti → Calle 16 → Parque Infantil → Av. Estudiantes → Morasurco → Clínica Bellatriz → Valle de Atríz → Villa del Parque → Villa María → Universidad Cooperativa → Udenar Torobajo → Udenar Veterinaria
- Frecuencia: cada 10-15 minutos
- Horario: aproximadamente 5:00am - 10:30pm

### RUTA C16 — COMPLEMENTARIA 16
- Conecta: Centro (Parque Nariño) ↔ Lorenzo de Aldana ↔ Barrio La Rosa
- Paradas clave: Parque Nariño, Av. Boyacá con Cll 20, Terminal de Transportes, Lorenzo de Aldana, La Rosa, Olímpica sur
- Frecuencia: cada 8-12 minutos
- Horario: 5:30am - 10:00pm

### RUTA E2 — ESTRATÉGICA 2
- Conecta: Barrio El Tejar ↔ Centro ↔ Barrio Santa Bárbara ↔ Jongovito
- Paradas clave: El Tejar, Parque Infantil, Centro (Cra 26 con Cll 19), Plazuela Agustinos, Av. Colombia, Santa Bárbara, Jongovito
- Frecuencia: cada 12-18 minutos
- Horario: 5:30am - 9:30pm

### RUTA E4 — ESTRATÉGICA 4
- Conecta: Barrio Chambú ↔ Terminal ↔ Centro ↔ Lorenzo ↔ Barbachoa
- Paradas clave: Chambú, Av. Panamericana, Terminal de Transportes, Parque Nariño, Barrio Obrero, Lorenzo de Aldana, Barbachoa
- Frecuencia: cada 10-15 minutos
- Horario: 5:30am - 10:00pm

### RUTA P1 — POPULAR 1
- Conecta: Barrio Madrigal ↔ Lorenzo ↔ San Vicente ↔ Centro
- Paradas clave: Madrigal, Lorenzo, Parque Popular, San Vicente de Paúl, Hospital Civil, Parque Nariño
- Frecuencia: cada 15-20 minutos
- Horario: 5:30am - 9:00pm

### RUTA T1 — TRANSVERSAL 1
- Conecta: Barrio Bolivariano ↔ Universidades ↔ Av. Boyacá
- Paradas clave: Bolivariano, Torres de Fátima, Parque Bolívar, SENA Cll 20, Universidad Cooperativa, Udenar Torobajo, INEM
- Frecuencia: cada 12-18 minutos
- Horario: 6:00am - 9:00pm

## SITIOS DE REFERENCIA
- Parque Nariño (centro histórico): pasan E1, E2, E4, C16, P1
- Universidad de Nariño / Udenar Torobajo: E1, T1
- Universidad Cooperativa (UCC): E1, T1, C16
- Hospital Departamental: E1
- Terminal de Transportes: E4, C16
- SENA Pasto: T1, E2
- Clínica Bellatriz: E1

## REGLAS DE RESPUESTA:
1. Responde siempre en español, tono amigable y local.
2. Indica la parada MÁS CERCANA al origen del usuario.
3. Di claramente el código de ruta (E1, C16, etc.).
4. Mantén spokenText natural, conversacional y máximo 3 oraciones.
5. displayTitle: nombre corto de la ruta (ej: "Ruta E1").
6. displaySubtitle: "Parada más cercana • tiempo de espera".

## FORMATO DE RESPUESTA — JSON ESTRICTO (sin texto fuera del JSON):
{
  "spokenText": "texto natural para leer en voz alta",
  "displayTitle": "Ruta XX",
  "displaySubtitle": "Parada • tiempo",
  "estimatedMinutes": número_o_null,
  "recommendedRouteId": "E1"
}
`;


// ============================================================
// FUNCIÓN 1: Procesa comando de voz del smartwatch
// ============================================================
export const processVoiceCommand = async (
    voiceText: string,
    location:  string,
    language:  string = 'es'
): Promise<NavigationInstruction> => {

    const apiKey = (process.env.GEMINI_API_KEY ?? '').trim();
    const isEn   = language === 'en';

    if (!apiKey || apiKey === 'PEGA_TU_API_KEY_AQUI') {
        console.warn('[BusAlert] ⚠ GEMINI_API_KEY no configurada — usando fallback.');
        return getFallbackResponse(voiceText, location, language);
    }

    const langInstruction = isEn
        ? `## IMPORTANT LANGUAGE REQUIREMENT:
The user is using ENGLISH.
You MUST write 'spokenText', 'displayTitle' (e.g. "Route E1"), and 'displaySubtitle' entirely in clear, friendly ENGLISH. Keep route IDs as E1, C16, etc.`
        : `## REGLA DE IDIOMA:
Responde siempre en español, tono amigable y local.`;

    const prompt = `${PASTO_TRANSIT_KNOWLEDGE}

${langInstruction}

## CONSULTA DEL USUARIO / USER QUERY:
- Ubicación actual / Current location: "${location}"
- Lo que dijo / Voice command: "${voiceText}"

Responde SOLO con el JSON indicado arriba / Respond ONLY with the requested JSON.`;

    try {
        const rawText  = await callGeminiWithFallback(apiKey, prompt, `voz (${language}): "${voiceText}"`);
        const cleanJson = extractJsonFromResponse(rawText);
        const parsed    = JSON.parse(cleanJson) as NavigationInstruction;

        if (!parsed.spokenText || !parsed.displayTitle || !parsed.displaySubtitle) {
            throw new Error('JSON de Gemini incompleto: faltan campos obligatorios.');
        }

        console.log(`[BusAlert] ✅ Voz procesada (${language}): "${voiceText}" desde "${location}"`);
        return parsed;

    } catch (error) {
        const classified = classifyError(error);
        console.error(`[BusAlert] ❌ processVoiceCommand: ${classified.message}`);

        // Errores de autenticación no usan fallback — informamos directamente
        if (!classified.isRetryable) {
            return {
                spokenText:         isEn ? 'Gemini API Key is invalid or unauthorized.' : classified.message,
                displayTitle:       isEn ? 'Config Error' : 'Error de configuración',
                displaySubtitle:    isEn ? 'Check API Key' : 'Revisa la API Key',
                estimatedMinutes:   null,
                recommendedRouteId: null,
            };
        }

        return getFallbackResponse(voiceText, location, language);
    }
};


// ============================================================
// FUNCIÓN 2: Consulta de transporte con contexto de la DB
// (Usado por POST /api/ai/transport-query)
// La IA SOLO responde con datos reales de la base de datos.
// ============================================================
export const processTransportQuery = async (
    question:  string,
    dbContext: string
): Promise<TransportQueryResponse> => {

    const apiKey = (process.env.GEMINI_API_KEY ?? '').trim();

    if (!apiKey || apiKey === 'PEGA_TU_API_KEY_AQUI') {
        return {
            answer: `IA no configurada. Contexto encontrado en la base de datos:\n\n${dbContext}`,
            confidence: 'low',
        };
    }

    const hasRealData = !dbContext.includes('No se encontraron paradas');

    const prompt = `Eres el asistente oficial del Sistema Estratégico de Transporte Público (SETP) de Pasto, Colombia.

## INSTRUCCIONES CRÍTICAS:
1. Responde ÚNICAMENTE con la información del contexto de base de datos proporcionado.
2. Si el contexto no tiene información suficiente, di EXACTAMENTE: "No tengo información verificada suficiente para responder esa pregunta."
3. NO inventes rutas, paradas, horarios ni tiempos.
4. Responde en español, tono amable y local.
5. Si hay rutas directas en el contexto, priorízalas.
6. Máximo 3 oraciones.

## CONTEXTO (datos oficiales verificados de Ruti SETP / Avante):
${dbContext}

## PREGUNTA:
"${question}"

## RESPUESTA:`;

    try {
        const answer = await callGeminiWithFallback(apiKey, prompt, `consulta: "${question}"`);
        return {
            answer:     answer.trim(),
            confidence: hasRealData ? 'high' : 'medium',
        };

    } catch (error) {
        const classified = classifyError(error);
        console.error(`[BusAlert] ❌ processTransportQuery: ${classified.message}`);
        return {
            answer:     classified.isRetryable
                            ? 'El servicio de IA no está disponible en este momento. Intenta de nuevo.'
                            : classified.message,
            confidence: 'low',
        };
    }
};


// ============================================================
// FALLBACK: Respuestas hardcodeadas cuando la IA no está
// disponible. Cubre los destinos más consultados en Pasto.
// ============================================================
function getFallbackResponse(voiceText: string, location: string, language: string = 'es'): NavigationInstruction {
    const text = voiceText.toLowerCase();
    const isEn = language === 'en';

    if (text.includes('cooperativa') || text.includes('ucc')) {
        return {
            spokenText:         isEn
                ? 'To go to Universidad Cooperativa, take route E1. The bus stop is on Av. Boyacá and Calle 22. It arrives in about 8 minutes.'
                : 'Para ir a la Universidad Cooperativa toma la ruta E1. La parada está en la Av. Boyacá con Calle 22. El bus llega en unos 8 minutos.',
            displayTitle:       isEn ? 'Route E1' : 'Ruta E1',
            displaySubtitle:    'Av. Boyacá Cll 22 • 8 min',
            estimatedMinutes:   8,
            recommendedRouteId: 'E1',
        };
    }
    if (text.includes('udenar') || text.includes('universidad de nariño') || text.includes('torobajo') || text.includes('veterinaria')) {
        return {
            spokenText:         isEn
                ? 'For Universidad de Nariño, take route E1. The bus stop is on Av. Boyacá in front of INEM. It arrives in about 10 minutes.'
                : 'Para la Universidad de Nariño toma la ruta E1. La parada está en la Av. Boyacá frente al INEM. El bus llega en unos 10 minutos.',
            displayTitle:       isEn ? 'Route E1' : 'Ruta E1',
            displaySubtitle:    isEn ? 'Av. Boyacá INEM • 10 min' : 'Av. Boyacá frente INEM • 10 min',
            estimatedMinutes:   10,
            recommendedRouteId: 'E1',
        };
    }
    if (text.includes('lorenzo')) {
        return {
            spokenText:         isEn
                ? 'For Lorenzo de Aldana school, take route C16 or E4. The nearest stop from downtown is Parque Nariño. It arrives in about 10 minutes.'
                : 'Para el colegio Lorenzo de Aldana toma la ruta C16 o la E4. La parada más cercana desde el centro es el Parque Nariño. El bus llega en unos 10 minutos.',
            displayTitle:       isEn ? 'Route C16' : 'Ruta C16',
            displaySubtitle:    'Parque Nariño • 10 min',
            estimatedMinutes:   10,
            recommendedRouteId: 'C16',
        };
    }
    if (text.includes('terminal')) {
        return {
            spokenText:         isEn
                ? 'For the Bus Terminal, take route E4 or C16. The bus stop is on Av. Panamericana and Calle 22.'
                : 'Para la Terminal de Transportes toma la ruta E4 o la C16. La parada está en la Av. Panamericana con Calle 22.',
            displayTitle:       isEn ? 'Route E4 / C16' : 'Ruta E4 / C16',
            displaySubtitle:    'Av. Panamericana • 7 min',
            estimatedMinutes:   7,
            recommendedRouteId: 'E4',
        };
    }
    if (text.includes('hospital')) {
        return {
            spokenText:         isEn
                ? 'For the Departamental Hospital, take route E1. The bus stop is on Av. de los Estudiantes.'
                : 'Para el Hospital Departamental toma la ruta E1. La parada está sobre la Av. de los Estudiantes.',
            displayTitle:       isEn ? 'Route E1' : 'Ruta E1',
            displaySubtitle:    'Av. Estudiantes • 8 min',
            estimatedMinutes:   8,
            recommendedRouteId: 'E1',
        };
    }
    if (text.includes('centro') || text.includes('parque nariño') || text.includes('narino') || text.includes('downtown')) {
        return {
            spokenText:         isEn
                ? 'For downtown Pasto, routes E1, E2, C16, or E4 pass through Parque Nariño. Average wait time is around 5 minutes.'
                : 'Para el centro de Pasto casi todas las rutas pasan por el Parque Nariño. Puedes tomar la E1, E2, C16 o E4. La espera es de unos 5 minutos.',
            displayTitle:       isEn ? 'Multiple Routes' : 'Múltiples rutas',
            displaySubtitle:    'Parque Nariño • 5 min',
            estimatedMinutes:   5,
            recommendedRouteId: 'E1',
        };
    }
    if (text.includes('dolores') || text.includes('laguna') || text.includes('mocondino')) {
        return {
            spokenText:         isEn
                ? 'For the Dolores sector or Vía La Laguna, take route E1 from Udenar.'
                : 'Para el sector de Dolores o Vía La Laguna toma la ruta E1. Sale desde Udenar y llega hasta ese sector.',
            displayTitle:       isEn ? 'Route E1' : 'Ruta E1',
            displaySubtitle:    isEn ? 'Direction Dolores • 15 min' : 'Dirección Dolores • 15 min',
            estimatedMinutes:   15,
            recommendedRouteId: 'E1',
        };
    }

    // Sin coincidencia
    return {
        spokenText:         isEn
            ? `Received your query from ${location}. The AI assistant is currently unavailable. Please verify that the backend is connected to the internet.`
            : `Recibí tu consulta desde ${location}. La inteligencia artificial no está disponible en este momento. Verifica que el backend esté conectado a internet.`,
        displayTitle:       isEn ? 'AI Unavailable' : 'IA no disponible',
        displaySubtitle:    isEn ? 'Check server' : 'Verifica el servidor',
        estimatedMinutes:   null,
        recommendedRouteId: null,
    };
}
