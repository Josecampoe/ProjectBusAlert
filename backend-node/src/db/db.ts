/**
 * db.ts
 * Módulo singleton de base de datos SQLite para BusAlert.
 * Usa better-sqlite3 (síncrono, sin servidor externo).
 */

import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { SCHEMA_SQL } from './schema';

// ─── Ruta del archivo de base de datos ────────────────────────────────────────
const DB_DIR  = path.join(__dirname, '..', '..', 'data');
const DB_PATH = path.join(DB_DIR, 'busalert.db');

// ─── Singleton ────────────────────────────────────────────────────────────────
let _db: Database.Database | null = null;

export function getDb(): Database.Database {
    if (_db) return _db;

    // Asegura que el directorio data/ exista
    if (!fs.existsSync(DB_DIR)) {
        fs.mkdirSync(DB_DIR, { recursive: true });
    }

    _db = new Database(DB_PATH, {
        // verbose: console.log   // descomentar para debug de queries
    });

    // Mejora de rendimiento: WAL mode + foreign keys
    _db.pragma('journal_mode = WAL');
    _db.pragma('foreign_keys = ON');

    // Crear tablas si no existen
    _db.exec(SCHEMA_SQL);

    console.log(`[DB] SQLite inicializado en: ${DB_PATH}`);
    return _db;
}

// ─── Helpers tipados ─────────────────────────────────────────────────────────

/**
 * Busca una parada por nombre (exacto o alias).
 * La búsqueda es case-insensitive.
 */
export function findStopsByName(db: Database.Database, query: string): StopRow[] {
    const normalized = query.trim().toLowerCase();
    const byName = db.prepare(`
        SELECT DISTINCT s.*
        FROM stops s
        WHERE lower(s.name) LIKE ?
        AND s.active = 1
    `).all(`%${normalized}%`) as StopRow[];

    const byAlias = db.prepare(`
        SELECT DISTINCT s.*
        FROM stops s
        JOIN stop_aliases a ON a.stop_id = s.id
        WHERE lower(a.alias) LIKE ?
        AND s.active = 1
    `).all(`%${normalized}%`) as StopRow[];

    // Combinar y deduplicar por id
    const seen = new Set<number>();
    const result: StopRow[] = [];
    for (const stop of [...byName, ...byAlias]) {
        if (!seen.has(stop.id)) {
            seen.add(stop.id);
            result.push(stop);
        }
    }
    return result;
}

/**
 * Devuelve todas las rutas que pasan por una parada (por stop_id).
 */
export function getRoutesByStop(db: Database.Database, stopId: number): RouteByStopRow[] {
    return db.prepare(`
        SELECT DISTINCT
            r.code,
            r.name  AS route_name,
            rd.name AS direction_name,
            rd.direction_key,
            rd.origin,
            rd.destination,
            rs.stop_order
        FROM route_stops rs
        JOIN route_directions rd ON rd.id = rs.route_direction_id
        JOIN routes r            ON r.id  = rd.route_id
        WHERE rs.stop_id = ?
        AND r.active = 1
        ORDER BY r.code, rs.stop_order
    `).all(stopId) as RouteByStopRow[];
}

/**
 * Devuelve las paradas de una dirección en orden.
 */
export function getStopsForDirection(
    db: Database.Database,
    directionKey: string
): StopInDirectionRow[] {
    return db.prepare(`
        SELECT
            rs.stop_order,
            s.id            AS stop_id,
            s.external_id,
            s.name          AS stop_name,
            s.neighborhood,
            s.latitude,
            s.longitude
        FROM route_stops rs
        JOIN stops s ON s.id = rs.stop_id
        JOIN route_directions rd ON rd.id = rs.route_direction_id
        WHERE rd.direction_key = ?
        ORDER BY rs.stop_order
    `).all(directionKey) as StopInDirectionRow[];
}

/**
 * Planificador básico origen → destino.
 * Devuelve las direcciones que contienen AMBAS paradas,
 * con la condición de que el origen esté antes que el destino.
 */
export function planRoute(
    db: Database.Database,
    originStopId: number,
    destStopId: number
): RoutePlanRow[] {
    return db.prepare(`
        SELECT
            r.code,
            r.name          AS route_name,
            rd.direction_key,
            rd.name         AS direction_name,
            o.stop_order    AS origin_order,
            d.stop_order    AS dest_order,
            (d.stop_order - o.stop_order) AS stops_between,
            s_origin.name   AS origin_stop_name,
            s_dest.name     AS dest_stop_name
        FROM route_stops o
        JOIN route_stops d          ON d.route_direction_id = o.route_direction_id
        JOIN route_directions rd    ON rd.id = o.route_direction_id
        JOIN routes r               ON r.id  = rd.route_id
        JOIN stops s_origin         ON s_origin.id = o.stop_id
        JOIN stops s_dest           ON s_dest.id   = d.stop_id
        WHERE o.stop_id = ?
        AND   d.stop_id = ?
        AND   d.stop_order > o.stop_order
        AND   r.active = 1
        ORDER BY stops_between
    `).all(originStopId, destStopId) as RoutePlanRow[];
}

// ─── Tipos de filas ───────────────────────────────────────────────────────────

export interface StopRow {
    id:           number;
    external_id:  string | null;
    name:         string;
    latitude:     number | null;
    longitude:    number | null;
    address:      string | null;
    neighborhood: string | null;
    active:       number;
    source:       string | null;
    verified:     number;
    notes:        string | null;
}

export interface RouteByStopRow {
    code:           string;
    route_name:     string;
    direction_name: string;
    direction_key:  string;
    origin:         string | null;
    destination:    string | null;
    stop_order:     number;
}

export interface StopInDirectionRow {
    stop_order:   number;
    stop_id:      number;
    external_id:  string | null;
    stop_name:    string;
    neighborhood: string | null;
    latitude:     number | null;
    longitude:    number | null;
}

export interface RoutePlanRow {
    code:             string;
    route_name:       string;
    direction_key:    string;
    direction_name:   string;
    origin_order:     number;
    dest_order:       number;
    stops_between:    number;
    origin_stop_name: string;
    dest_stop_name:   string;
}
