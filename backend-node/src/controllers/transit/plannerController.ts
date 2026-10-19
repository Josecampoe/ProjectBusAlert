/**
 * plannerController.ts
 * Planificador de rutas origen → destino.
 *
 * GET /api/route-planner?origin=...&destination=...
 *
 * Recibe nombres de parada (o texto libre) y devuelve las rutas
 * que conectan el origen con el destino, con el orden de paradas intermedias.
 */

import { Router, Request, Response } from 'express';
import { getDb, findStopsByName, planRoute, getStopsForDirection } from '../../db/db';

export const plannerRouter = Router();

plannerRouter.get('/', (req: Request, res: Response) => {
    const origin      = (req.query.origin      as string | undefined)?.trim();
    const destination = (req.query.destination as string | undefined)?.trim();

    if (!origin || !destination) {
        return res.status(400).json({
            error: 'Se requieren los parámetros origin y destination.'
        });
    }

    try {
        const db = getDb();

        // 1. Buscar paradas que coincidan con origen y destino
        const originStops = findStopsByName(db, origin);
        const destStops   = findStopsByName(db, destination);

        if (originStops.length === 0) {
            return res.status(404).json({
                error: `No se encontraron paradas para el origen: "${origin}"`,
                suggestion: 'Intenta con un nombre más específico o verifica el nombre de la parada.'
            });
        }
        if (destStops.length === 0) {
            return res.status(404).json({
                error: `No se encontraron paradas para el destino: "${destination}"`,
                suggestion: 'Intenta con un nombre más específico o verifica el nombre de la parada.'
            });
        }

        // 2. Buscar rutas que conecten cualquier combo origen-destino
        const plans: any[] = [];
        const seen = new Set<string>();

        for (const orig of originStops) {
            for (const dest of destStops) {
                const results = planRoute(db, orig.id, dest.id);
                for (const r of results) {
                    const key = `${r.direction_key}-${r.origin_order}-${r.dest_order}`;
                    if (seen.has(key)) continue;
                    seen.add(key);

                    // 3. Obtener las paradas intermedias del recorrido
                    const allStops = getStopsForDirection(db, r.direction_key);
                    const segment  = allStops.filter(
                        s => s.stop_order >= r.origin_order && s.stop_order <= r.dest_order
                    );

                    plans.push({
                        route_code:      r.code,
                        route_name:      r.route_name,
                        direction_key:   r.direction_key,
                        direction_name:  r.direction_name,
                        board_at:        r.origin_stop_name,
                        alight_at:       r.dest_stop_name,
                        stops_count:     r.stops_between,
                        segment_stops:   segment,
                    });
                }
            }
        }

        // Ordenar por menor número de paradas
        plans.sort((a, b) => a.stops_count - b.stops_count);

        if (plans.length === 0) {
            return res.json({
                origin,
                destination,
                found: false,
                message: 'No se encontró una ruta directa. Puede requerir transbordo.',
                origin_stops_found:  originStops.map(s => s.name),
                dest_stops_found:    destStops.map(s => s.name),
                plans: [],
            });
        }

        res.json({
            origin,
            destination,
            found:  true,
            count:  plans.length,
            plans,
        });

    } catch (err) {
        console.error('[Planner]', err);
        res.status(500).json({ error: 'Error al calcular la ruta.' });
    }
});
