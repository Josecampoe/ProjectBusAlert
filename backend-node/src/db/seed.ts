/**
 * seed.ts
 * Importa los archivos JSON de data/ hacia la base de datos SQLite.
 *
 * Uso:
 *   npx ts-node src/db/seed.ts
 *
 * Es idempotente: si los datos ya existen (por code/external_id) los omite.
 * Los datos con verified:false quedan cargados pero marcados como pendientes.
 */

import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { getDb } from './db';

// ─── Rutas a los archivos JSON ────────────────────────────────────────────────
const DATA_DIR = path.join(__dirname, '..', '..', 'data');

const FILES = {
    sources:         path.join(DATA_DIR, 'sources',  'sources.json'),
    routes:          path.join(DATA_DIR, 'routes',   'routes.json'),
    route_directions:path.join(DATA_DIR, 'routes',   'route_directions.json'),
    route_stops:     path.join(DATA_DIR, 'routes',   'route_stops.json'),
    schedules:       path.join(DATA_DIR, 'schedules','schedules.json'),
    stops_e1:        path.join(DATA_DIR, 'stops',    'stops_e1.json'),
};

// ─── Carga de JSON ────────────────────────────────────────────────────────────
function loadJson<T>(filePath: string): T {
    const raw = fs.readFileSync(filePath, 'utf-8');
    return JSON.parse(raw) as T;
}

// ─── Tipos de los JSON ────────────────────────────────────────────────────────
interface SourceJson {
    id: string; name: string; url: string | null;
    source_type: string; last_checked: string | null;
    reliability: string; notes: string | null;
}

interface RouteJson {
    code: string; name: string; type: string;
    description: string | null; origin: string | null; destination: string | null;
    active: boolean; source: string; verified: boolean;
    data_quality: string; notes: string | null; last_verified_at: string | null;
}

interface DirectionJson {
    route_code: string; direction_key: string; name: string;
    origin: string | null; destination: string | null;
    description: string | null; stop_count: number | null;
    verified: boolean; source: string;
}

interface RouteStopsJson {
    [dirKey: string]: {
        direction_key: string; route_code: string; name: string;
        stops: Array<{
            order: number;
            stop_external_id: string | null;
            stop_name: string;
        }>;
    };
}

interface StopJson {
    external_id: string | null; name: string;
    aliases: string[];
    latitude: number | null; longitude: number | null;
    address: string | null; neighborhood: string | null;
    active: boolean; source: string; verified: boolean;
    notes?: string | null;
}

interface ScheduleJson {
    route_code: string; day_type: string; day_type_label: string;
    start_time: string | null; end_time: string | null;
    frequency_minutes: number | null;
    source: string; verified: boolean; notes: string | null;
}

// ─── Función principal de seed ────────────────────────────────────────────────
function seed(): void {
    const db = getDb();
    console.log('\n[Seed] Iniciando carga de datos...\n');

    // 1 ── SOURCES ──────────────────────────────────────────────────────────────
    console.log('[Seed] Cargando fuentes...');
    const sources = loadJson<SourceJson[]>(FILES.sources);
    const insertSource = db.prepare(`
        INSERT OR IGNORE INTO sources (id, name, url, source_type, last_checked, reliability, notes)
        VALUES (@id, @name, @url, @source_type, @last_checked, @reliability, @notes)
    `);
    const seedSources = db.transaction(() => {
        let count = 0;
        for (const s of sources) {
            const info = insertSource.run(s);
            if (info.changes > 0) count++;
        }
        return count;
    });
    console.log(`  → ${seedSources()} fuentes insertadas.`);

    // 2 ── ROUTES ───────────────────────────────────────────────────────────────
    console.log('[Seed] Cargando rutas...');
    const routes = loadJson<RouteJson[]>(FILES.routes);
    const insertRoute = db.prepare(`
        INSERT OR IGNORE INTO routes
            (code, name, type, description, origin, destination, active, source, verified, data_quality, notes, last_verified_at)
        VALUES
            (@code, @name, @type, @description, @origin, @destination, @active, @source, @verified, @data_quality, @notes, @last_verified_at)
    `);
    const seedRoutes = db.transaction(() => {
        let count = 0;
        for (const r of routes) {
            const info = insertRoute.run({ ...r, active: r.active ? 1 : 0, verified: r.verified ? 1 : 0 });
            if (info.changes > 0) count++;
        }
        return count;
    });
    console.log(`  → ${seedRoutes()} rutas insertadas.`);

    // 3 ── STOPS ────────────────────────────────────────────────────────────────
    console.log('[Seed] Cargando paradas (E1)...');
    const stops = loadJson<StopJson[]>(FILES.stops_e1);
    const insertStop = db.prepare(`
        INSERT OR IGNORE INTO stops
            (external_id, name, latitude, longitude, address, neighborhood, active, source, verified, notes)
        VALUES
            (@external_id, @name, @latitude, @longitude, @address, @neighborhood, @active, @source, @verified, @notes)
    `);
    const insertAlias = db.prepare(`
        INSERT OR IGNORE INTO stop_aliases (stop_id, alias, source)
        VALUES (@stop_id, @alias, @source)
    `);

    // Mapa external_id → db_id (para luego resolver route_stops)
    const stopIdByExternalId = new Map<string, number>();
    // También por nombre normalizado para paradas sin external_id
    const stopIdByName = new Map<string, number>();

    const seedStops = db.transaction(() => {
        let count = 0;
        for (const s of stops) {
            // Evitar duplicados de external_id ya existentes
            if (s.external_id) {
                const existing = db.prepare(
                    `SELECT id FROM stops WHERE external_id = ?`
                ).get(s.external_id) as { id: number } | undefined;
                if (existing) {
                    stopIdByExternalId.set(s.external_id, existing.id);
                    continue;
                }
            }

            const info = insertStop.run({
                ...s,
                active:   s.active ? 1 : 0,
                verified: s.verified ? 1 : 0,
                notes:    s.notes ?? null,
            });

            if (info.changes > 0) {
                const newId = info.lastInsertRowid as number;
                count++;

                if (s.external_id) stopIdByExternalId.set(s.external_id, newId);
                stopIdByName.set(s.name.toLowerCase(), newId);

                // Insertar aliases
                for (const alias of s.aliases) {
                    insertAlias.run({ stop_id: newId, alias, source: s.source });
                }
            }
        }
        return count;
    });
    const insertedStops = seedStops();
    console.log(`  → ${insertedStops} paradas insertadas.`);

    // Poblar mapas con las que ya existían
    for (const s of stops) {
        if (s.external_id && !stopIdByExternalId.has(s.external_id)) {
            const row = db.prepare(`SELECT id FROM stops WHERE external_id = ?`).get(s.external_id) as { id: number } | undefined;
            if (row) stopIdByExternalId.set(s.external_id, row.id);
        }
        if (!stopIdByName.has(s.name.toLowerCase())) {
            const row = db.prepare(`SELECT id FROM stops WHERE lower(name) = ?`).get(s.name.toLowerCase()) as { id: number } | undefined;
            if (row) stopIdByName.set(s.name.toLowerCase(), row.id);
        }
    }

    // 4 ── ROUTE_DIRECTIONS ─────────────────────────────────────────────────────
    console.log('[Seed] Cargando direcciones de rutas...');
    const directions = loadJson<DirectionJson[]>(FILES.route_directions);
    const insertDirection = db.prepare(`
        INSERT OR IGNORE INTO route_directions
            (route_id, direction_key, name, origin, destination, description, stop_count, verified, source)
        VALUES
            (@route_id, @direction_key, @name, @origin, @destination, @description, @stop_count, @verified, @source)
    `);

    // Mapa direction_key → db_id
    const directionIdByKey = new Map<string, number>();

    const seedDirections = db.transaction(() => {
        let count = 0;
        for (const d of directions) {
            const routeRow = db.prepare(`SELECT id FROM routes WHERE code = ?`).get(d.route_code) as { id: number } | undefined;
            if (!routeRow) {
                console.warn(`  ⚠ Ruta "${d.route_code}" no encontrada para dirección "${d.direction_key}"`);
                continue;
            }
            const info = insertDirection.run({
                route_id:      routeRow.id,
                direction_key: d.direction_key,
                name:          d.name,
                origin:        d.origin,
                destination:   d.destination,
                description:   d.description,
                stop_count:    d.stop_count,
                verified:      d.verified ? 1 : 0,
                source:        d.source,
            });
            if (info.changes > 0) {
                directionIdByKey.set(d.direction_key, info.lastInsertRowid as number);
                count++;
            }
        }
        return count;
    });
    console.log(`  → ${seedDirections()} direcciones insertadas.`);

    // Poblar mapa con las ya existentes
    for (const d of directions) {
        if (!directionIdByKey.has(d.direction_key)) {
            const row = db.prepare(`SELECT id FROM route_directions WHERE direction_key = ?`).get(d.direction_key) as { id: number } | undefined;
            if (row) directionIdByKey.set(d.direction_key, row.id);
        }
    }

    // 5 ── ROUTE_STOPS ──────────────────────────────────────────────────────────
    console.log('[Seed] Cargando paradas por dirección...');
    const routeStopsData = loadJson<RouteStopsJson>(FILES.route_stops);
    const insertRouteStop = db.prepare(`
        INSERT OR IGNORE INTO route_stops
            (route_direction_id, stop_id, stop_order)
        VALUES
            (@route_direction_id, @stop_id, @stop_order)
    `);

    const seedRouteStops = db.transaction(() => {
        let total = 0;
        let missing = 0;

        for (const [dirKey, dirData] of Object.entries(routeStopsData)) {
            if (dirKey === '_comment') continue;

            const dirId = directionIdByKey.get(dirKey);
            if (!dirId) {
                console.warn(`  ⚠ Dirección "${dirKey}" no encontrada en DB`);
                continue;
            }

            for (const entry of dirData.stops) {
                // Resolver stop_id: primero por external_id, después por nombre
                let stopDbId: number | undefined;

                if (entry.stop_external_id) {
                    stopDbId = stopIdByExternalId.get(entry.stop_external_id);
                }
                if (!stopDbId) {
                    stopDbId = stopIdByName.get(entry.stop_name.toLowerCase());
                }
                // Último recurso: buscar en DB por nombre exacto
                if (!stopDbId) {
                    const row = db.prepare(`SELECT id FROM stops WHERE lower(name) = ?`)
                        .get(entry.stop_name.toLowerCase()) as { id: number } | undefined;
                    stopDbId = row?.id;
                }

                if (!stopDbId) {
                    console.warn(`  ⚠ [${dirKey}] Parada no encontrada: "${entry.stop_name}" (ext_id=${entry.stop_external_id})`);
                    missing++;
                    continue;
                }

                const info = insertRouteStop.run({
                    route_direction_id: dirId,
                    stop_id:            stopDbId,
                    stop_order:         entry.order,
                });
                if (info.changes > 0) total++;
            }
        }

        console.log(`  ⚠ ${missing} paradas sin resolver en route_stops`);
        return total;
    });
    console.log(`  → ${seedRouteStops()} entradas route_stops insertadas.`);

    // 6 ── SCHEDULES ────────────────────────────────────────────────────────────
    console.log('[Seed] Cargando horarios...');
    const schedules = loadJson<ScheduleJson[]>(FILES.schedules);
    const insertSchedule = db.prepare(`
        INSERT OR IGNORE INTO schedules
            (route_id, day_type, day_type_label, start_time, end_time, frequency_minutes, source, verified, notes)
        VALUES
            (@route_id, @day_type, @day_type_label, @start_time, @end_time, @frequency_minutes, @source, @verified, @notes)
    `);
    const seedSchedules = db.transaction(() => {
        let count = 0;
        for (const s of schedules) {
            const routeRow = db.prepare(`SELECT id FROM routes WHERE code = ?`).get(s.route_code) as { id: number } | undefined;
            if (!routeRow) { continue; }
            const info = insertSchedule.run({ ...s, route_id: routeRow.id, verified: s.verified ? 1 : 0 });
            if (info.changes > 0) count++;
        }
        return count;
    });
    console.log(`  → ${seedSchedules()} horarios insertados.`);

    // ── RESUMEN ────────────────────────────────────────────────────────────────
    printSummary(db);
}

// ─── Resumen post-seed ────────────────────────────────────────────────────────
function printSummary(db: Database.Database): void {
    const routes    = (db.prepare(`SELECT COUNT(*) AS c FROM routes`).get() as { c: number }).c;
    const stops     = (db.prepare(`SELECT COUNT(*) AS c FROM stops`).get() as { c: number }).c;
    const dirs      = (db.prepare(`SELECT COUNT(*) AS c FROM route_directions`).get() as { c: number }).c;
    const rs        = (db.prepare(`SELECT COUNT(*) AS c FROM route_stops`).get() as { c: number }).c;
    const noCoords  = (db.prepare(`SELECT COUNT(*) AS c FROM stops WHERE latitude IS NULL`).get() as { c: number }).c;
    const noExtId   = (db.prepare(`SELECT COUNT(*) AS c FROM stops WHERE external_id IS NULL`).get() as { c: number }).c;
    const sched     = (db.prepare(`SELECT COUNT(*) AS c FROM schedules`).get() as { c: number }).c;
    const schedUnv  = (db.prepare(`SELECT COUNT(*) AS c FROM schedules WHERE verified = 0`).get() as { c: number }).c;

    console.log(`
=================================
  RESUMEN DE BASE DE DATOS
=================================
  Rutas cargadas:          ${routes}
  Paradas cargadas:        ${stops}
  Direcciones:             ${dirs}
  Entradas route_stops:    ${rs}
  Horarios:                ${sched} (${schedUnv} sin verificar)
---------------------------------
  Paradas sin coordenadas: ${noCoords}
  Paradas sin código Ruti: ${noExtId}
=================================
`);
}

// ─── Ejecutar ─────────────────────────────────────────────────────────────────
try {
    seed();
    console.log('[Seed] ✅ Completado.');
} catch (err) {
    console.error('[Seed] ❌ Error:', err);
    process.exit(1);
}
