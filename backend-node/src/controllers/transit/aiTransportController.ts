/**
 * aiTransportController.ts
 * Endpoint especializado para consultas de IA sobre el transporte SETP.
 *
 * POST /api/ai/transport-query
 *
 * Flujo:
 *   1. Analiza la pregunta del usuario.
 *   2. Busca en la DB las rutas/paradas relevantes.
 *   3. Construye un contexto estructurado y verificado.
 *   4. Llama a Gemini con ese contexto (no con datos inventados).
 *   5. Devuelve la respuesta con fuente y nivel de confianza.
 */

import { Router, Request, Response } from 'express';
import {
    getDb,
    findStopsByName,
    getRoutesByStop,
    planRoute,
    getStopsForDirection,
    StopRow,
    RouteByStopRow,
} from '../../db/db';
import { processTransportQuery } from '../../services/aiService';

export const aiTransportRouter = Router();

// ─── Tipos de respuesta ───────────────────────────────────────────────────────
interface TransportContext {
    query:            string;
    stops_found:      StopRow[];
    routes_found:     RouteByStopRow[];
    plan_results:     any[];
    data_available:   boolean;
    context_text:     string;             // resumen en texto plano para la IA
}

// ─── Construye el contexto de DB para una pregunta ────────────────────────────
function buildTransportContext(question: string): TransportContext {
    const db = getDb();

    // Extraer posibles nombres de paradas/barrios de la pregunta
    // (búsqueda amplia — la IA luego interpreta)
    const words = question
        .replace(/[¿?.,!]/g, ' ')
        .split(/\s+/)
        .filter(w => w.length > 3);

    const stopSet = new Map<number, StopRow>();
    const routeSet = new Map<string, RouteByStopRow>();

    for (const word of words) {
        const found = findStopsByName(db, word);
        for (const s of found) {
            if (!stopSet.has(s.id)) stopSet.set(s.id, s);
        }
    }

    // Rutas que pasan por las paradas encontradas
    for (const stop of stopSet.values()) {
        const routes = getRoutesByStop(db, stop.id);
        for (const r of routes) {
            const key = `${r.code}_${r.direction_key}`;
            if (!routeSet.has(key)) routeSet.set(key, r);
        }
    }

    // Intentar planificación si hay dos grupos semánticos claros
    const planResults: any[] = [];
    const stopList = Array.from(stopSet.values());
    if (stopList.length >= 2) {
        for (let i = 0; i < Math.min(stopList.length, 3); i++) {
            for (let j = i + 1; j < Math.min(stopList.length, 3); j++) {
                const plans = planRoute(db, stopList[i].id, stopList[j].id);
                planResults.push(...plans.slice(0, 2));
            }
        }
    }

    const stopsArr  = Array.from(stopSet.values());
    const routesArr = Array.from(routeSet.values());

    // Construir texto de contexto para la IA
    let contextText = '';

    if (stopsArr.length > 0) {
        contextText += `## Paradas encontradas en la base de datos:\n`;
        for (const s of stopsArr) {
            contextText += `- ID Ruti: ${s.external_id ?? 'sin código'} | Nombre: ${s.name}`;
            if (s.neighborhood) contextText += ` | Barrio: ${s.neighborhood}`;
            contextText += '\n';
        }
    }

    if (routesArr.length > 0) {
        contextText += `\n## Rutas que pasan por esas paradas:\n`;

        // Agrupar por código de ruta
        const byCode = new Map<string, RouteByStopRow[]>();
        for (const r of routesArr) {
            if (!byCode.has(r.code)) byCode.set(r.code, []);
            byCode.get(r.code)!.push(r);
        }

        for (const [code, dirs] of byCode) {
            contextText += `\n### Ruta ${code}\n`;
            for (const d of dirs) {
                contextText += `  - Sentido: ${d.direction_name} (${d.origin} → ${d.destination})\n`;
                contextText += `    Posición de la parada en este sentido: ${d.stop_order}\n`;

                // Agregar las paradas del segmento alrededor
                const allStops = getStopsForDirection(db, d.direction_key);
                const nearby = allStops.filter(
                    s => Math.abs(s.stop_order - d.stop_order) <= 3
                );
                contextText += `    Paradas cercanas en el recorrido: ${nearby.map(s => s.stop_name).join(' → ')}\n`;
            }
        }
    }

    if (planResults.length > 0) {
        contextText += `\n## Rutas directas encontradas:\n`;
        for (const p of planResults.slice(0, 3)) {
            contextText += `- Ruta ${p.code} (${p.direction_name}): `;
            contextText += `subir en "${p.origin_stop_name}" (parada ${p.origin_order}), `;
            contextText += `bajar en "${p.dest_stop_name}" (parada ${p.dest_order}). `;
            contextText += `${p.stops_between} paradas de diferencia.\n`;
        }
    }

    if (contextText === '') {
        contextText = 'No se encontraron paradas ni rutas relacionadas en la base de datos para esta consulta.';
    }

    return {
        query:          question,
        stops_found:    stopsArr,
        routes_found:   routesArr,
        plan_results:   planResults,
        data_available: stopsArr.length > 0 || routesArr.length > 0,
        context_text:   contextText,
    };
}

// ─── POST /api/ai/transport-query ─────────────────────────────────────────────
aiTransportRouter.post('/', async (req: Request, res: Response) => {
    const { question } = req.body;

    if (!question || typeof question !== 'string' || question.trim().length === 0) {
        return res.status(400).json({ error: 'El campo question es requerido.' });
    }

    if (question.trim().length > 500) {
        return res.status(400).json({ error: 'La pregunta es demasiado larga (máximo 500 caracteres).' });
    }

    try {
        // 1. Buscar contexto en la DB
        const context = buildTransportContext(question.trim());

        // 2. Llamar a la IA con el contexto de DB
        const aiResponse = await processTransportQuery(question.trim(), context.context_text);

        // 3. Respuesta estructurada
        res.json({
            question,
            answer:      aiResponse.answer,
            routes:      [...new Set(context.routes_found.map(r => r.code))],
            stops:       context.stops_found.map(s => s.name),
            confidence:  context.data_available ? 'high' : 'low',
            data_from_db: context.data_available,
            sources:     ['Ruti SETP / Avante'],
            raw_context: {
                stops_found:  context.stops_found.length,
                routes_found: [...new Set(context.routes_found.map(r => r.code))],
                plans_found:  context.plan_results.length,
            },
        });

    } catch (err) {
        console.error('[AI Transport]', err);
        res.status(500).json({
            error:  'Error al procesar la consulta.',
            answer: 'No pude procesar tu consulta en este momento. Intenta de nuevo.',
        });
    }
});
