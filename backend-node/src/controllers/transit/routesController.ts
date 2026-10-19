/**
 * routesController.ts
 * Endpoints para consultar rutas del SETP de Pasto.
 *
 * GET /api/routes                    → lista todas las rutas activas
 * GET /api/routes/:code              → detalle de una ruta
 * GET /api/routes/:code/stops        → paradas de cada dirección de la ruta
 * GET /api/routes/:code/directions   → solo las direcciones (sin paradas)
 * GET /api/routes/search?q=          → búsqueda por nombre o código
 */

import { Router, Request, Response } from 'express';
import { getDb } from '../../db/db';

export const routesRouter = Router();

// ─── GET /api/routes ──────────────────────────────────────────────────────────
routesRouter.get('/', (_req: Request, res: Response) => {
    try {
        const db = getDb();
        const routes = db.prepare(`
            SELECT
                r.id, r.code, r.name, r.type, r.description,
                r.origin, r.destination, r.active,
                r.verified, r.data_quality, r.last_verified_at,
                (SELECT COUNT(*) FROM route_directions rd WHERE rd.route_id = r.id) AS direction_count
            FROM routes r
            WHERE r.active = 1
            ORDER BY r.type DESC, r.code
        `).all();
        res.json({ count: routes.length, routes });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar rutas.' });
    }
});

// ─── GET /api/routes/search?q= ────────────────────────────────────────────────
routesRouter.get('/search', (req: Request, res: Response) => {
    const q = (req.query.q as string | undefined)?.trim();
    if (!q) return res.status(400).json({ error: 'Parámetro q requerido.' });

    try {
        const db = getDb();
        const routes = db.prepare(`
            SELECT id, code, name, type, origin, destination, verified, data_quality
            FROM routes
            WHERE active = 1
            AND (lower(code) LIKE lower(?) OR lower(name) LIKE lower(?))
            ORDER BY code
        `).all(`%${q}%`, `%${q}%`);
        res.json({ query: q, count: routes.length, routes });
    } catch (err) {
        res.status(500).json({ error: 'Error en búsqueda.' });
    }
});

// ─── GET /api/routes/:code ────────────────────────────────────────────────────
routesRouter.get('/:code', (req: Request, res: Response) => {
    const { code } = req.params;
    try {
        const db = getDb();
        const route = db.prepare(`
            SELECT r.*, s.name AS source_name, s.url AS source_url
            FROM routes r
            LEFT JOIN sources s ON s.id = r.source
            WHERE upper(r.code) = upper(?)
        `).get(code.toUpperCase());

        if (!route) return res.status(404).json({ error: `Ruta "${code}" no encontrada.` });

        const directions = db.prepare(`
            SELECT id, direction_key, name, origin, destination, stop_count, verified
            FROM route_directions
            WHERE route_id = (SELECT id FROM routes WHERE upper(code) = upper(?))
            ORDER BY id
        `).all(code.toUpperCase());

        const schedules = db.prepare(`
            SELECT day_type, day_type_label, start_time, end_time, frequency_minutes, verified, notes
            FROM schedules
            WHERE route_id = (SELECT id FROM routes WHERE upper(code) = upper(?))
        `).all(code.toUpperCase());

        res.json({ route, directions, schedules });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar ruta.' });
    }
});

// ─── GET /api/routes/:code/directions ────────────────────────────────────────
routesRouter.get('/:code/directions', (req: Request, res: Response) => {
    const { code } = req.params;
    try {
        const db = getDb();
        const directions = db.prepare(`
            SELECT rd.*
            FROM route_directions rd
            JOIN routes r ON r.id = rd.route_id
            WHERE upper(r.code) = upper(?)
            ORDER BY rd.id
        `).all(code.toUpperCase());

        if (directions.length === 0)
            return res.status(404).json({ error: `Sin direcciones para ruta "${code}".` });

        res.json({ route_code: code.toUpperCase(), count: directions.length, directions });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar direcciones.' });
    }
});

// ─── GET /api/routes/:code/stops ─────────────────────────────────────────────
routesRouter.get('/:code/stops', (req: Request, res: Response) => {
    const { code } = req.params;
    try {
        const db = getDb();

        const directions = db.prepare(`
            SELECT rd.id, rd.direction_key, rd.name, rd.origin, rd.destination
            FROM route_directions rd
            JOIN routes r ON r.id = rd.route_id
            WHERE upper(r.code) = upper(?)
            ORDER BY rd.id
        `).all(code.toUpperCase()) as Array<{
            id: number; direction_key: string; name: string;
            origin: string; destination: string;
        }>;

        if (directions.length === 0)
            return res.status(404).json({ error: `Ruta "${code}" no encontrada.` });

        const result = directions.map(dir => {
            const stops = db.prepare(`
                SELECT
                    rs.stop_order,
                    s.id AS stop_id,
                    s.external_id,
                    s.name,
                    s.neighborhood,
                    s.latitude,
                    s.longitude,
                    s.address
                FROM route_stops rs
                JOIN stops s ON s.id = rs.stop_id
                WHERE rs.route_direction_id = ?
                ORDER BY rs.stop_order
            `).all(dir.id);

            return {
                direction_key: dir.direction_key,
                direction_name: dir.name,
                origin: dir.origin,
                destination: dir.destination,
                stop_count: stops.length,
                stops,
            };
        });

        res.json({ route_code: code.toUpperCase(), directions: result });
    } catch (err) {
        res.status(500).json({ error: 'Error al consultar paradas de ruta.' });
    }
});
