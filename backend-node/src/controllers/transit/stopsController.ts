/**
 * stopsController.ts
 * Endpoints para consultar paraderos del SETP de Pasto.
 *
 * GET /api/stops              → lista todas las paradas activas
 * GET /api/stops/search?q=   → búsqueda por nombre o alias
 * GET /api/stops/:id          → detalle de una parada (por db-id o external_id Ruti)
 * GET /api/stops/:id/routes   → rutas que pasan por esta parada
 */

import { Router, Request, Response } from 'express';
import { getDb, findStopsByName, getRoutesByStop } from '../../db/db';

export const stopsRouter = Router();

// ─── GET /api/stops ───────────────────────────────────────────────────────────
stopsRouter.get('/', (_req: Request, res: Response) => {
    try {
        const db = getDb();
        const stops = db.prepare(`
            SELECT id, external_id, name, neighborhood, latitude, longitude, verified
            FROM stops
            WHERE active = 1
            ORDER BY name
        `).all();
        res.json({ count: stops.length, stops });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar paradas.' });
    }
});

// ─── GET /api/stops/search?q= ─────────────────────────────────────────────────
stopsRouter.get('/search', (req: Request, res: Response) => {
    const q = (req.query.q as string | undefined)?.trim();
    if (!q) return res.status(400).json({ error: 'Parámetro q requerido.' });

    try {
        const db = getDb();
        const stops = findStopsByName(db, q);

        // Para cada parada encontrada, incluir las rutas que pasan por ella
        const enriched = stops.map(stop => {
            const routes = getRoutesByStop(db, stop.id);
            return { ...stop, routes };
        });

        res.json({ query: q, count: enriched.length, stops: enriched });
    } catch (err) {
        res.status(500).json({ error: 'Error en búsqueda de paradas.' });
    }
});

// ─── GET /api/stops/:id ───────────────────────────────────────────────────────
// Acepta tanto el db-id numérico como el external_id (código Ruti)
stopsRouter.get('/:id', (req: Request, res: Response) => {
    const { id } = req.params;
    try {
        const db = getDb();

        // Busca primero por external_id (código Ruti), luego por pk numérico
        const isNumeric = /^\d+$/.test(id);
        let stop = db.prepare(`SELECT * FROM stops WHERE external_id = ? AND active = 1`).get(id);
        if (!stop && isNumeric) {
            stop = db.prepare(`SELECT * FROM stops WHERE id = ? AND active = 1`).get(Number(id));
        }

        if (!stop) return res.status(404).json({ error: `Parada "${id}" no encontrada.` });

        const stopRow = stop as { id: number };

        // Aliases
        const aliases = db.prepare(`SELECT alias FROM stop_aliases WHERE stop_id = ?`).all(stopRow.id);

        res.json({ stop, aliases: aliases.map((a: any) => a.alias) });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar parada.' });
    }
});

// ─── GET /api/stops/:id/routes ────────────────────────────────────────────────
stopsRouter.get('/:id/routes', (req: Request, res: Response) => {
    const { id } = req.params;
    try {
        const db = getDb();

        const isNumeric = /^\d+$/.test(id);
        let stopRow = db.prepare(`SELECT id, name FROM stops WHERE external_id = ? AND active = 1`).get(id);
        if (!stopRow && isNumeric) {
            stopRow = db.prepare(`SELECT id, name FROM stops WHERE id = ? AND active = 1`).get(Number(id));
        }

        if (!stopRow) return res.status(404).json({ error: `Parada "${id}" no encontrada.` });

        const stop = stopRow as { id: number; name: string };
        const routes = getRoutesByStop(db, stop.id);

        res.json({
            stop_id:   stop.id,
            stop_name: stop.name,
            count:     routes.length,
            routes,
        });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar rutas de parada.' });
    }
});
