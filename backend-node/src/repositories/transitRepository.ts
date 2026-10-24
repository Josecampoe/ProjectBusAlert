/**
 * Acceso a los datos de transporte. Es la única fuente de verdad sobre
 * rutas, paradas y recorridos: ni el prompt de la IA ni el cliente
 * tienen copias de estos datos.
 */
import type { Db } from '../db/database';
import { normalizeForSearch } from '../lib/text';
import type { MemoryCache } from '../services/cacheService';

/** Tipo de fuente que marca datos de ejemplo (no reales). */
export const DEMO_SOURCE_TYPE = 'demo_sample';

const NETWORK_CACHE_KEY = 'transit-network';

// ─── Tipos ──────────────────────────────────────────────────────────────────

export interface RouteSummary {
  id: number;
  code: string;
  name: string;
  type: string;
  description: string | null;
  origin: string | null;
  destination: string | null;
  verified: boolean;
  dataQuality: string | null;
  lastVerifiedAt: string | null;
  directionCount: number;
  color: string | null;
  isDemo: boolean;
}

export interface DirectionSummary {
  id: number;
  key: string;
  name: string;
  origin: string | null;
  destination: string | null;
  stopCount: number | null;
  verified: boolean;
}

export interface ScheduleRow {
  dayType: string;
  dayTypeLabel: string | null;
  startTime: string | null;
  endTime: string | null;
  frequencyMinutes: number | null;
  verified: boolean;
  notes: string | null;
}

export interface StopSummary {
  id: number;
  externalId: string | null;
  name: string;
  neighborhood: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  verified: boolean;
  isDemo: boolean;
}

export interface StopInDirection extends StopSummary {
  order: number;
}

export interface RouteAtStop {
  routeCode: string;
  routeName: string;
  directionKey: string;
  directionName: string;
  stopOrder: number;
}

/** Instantánea en memoria de la red, usada por el buscador y el planificador. */
/** Horario de servicio de una ruta para un tipo de día (LV, S, DF...). */
export interface RouteSchedule {
  dayType: string;
  startTime: string | null;
  endTime: string | null;
  frequencyMinutes: number | null;
  verified: boolean;
}

export interface NetworkRoute {
  id: number;
  code: string;
  name: string;
  /** Color de la ruta (#RRGGBB) definido en los datos, o null. */
  color: string | null;
  isDemo: boolean;
  schedules: RouteSchedule[];
}

export interface NetworkDirection {
  id: number;
  key: string;
  name: string;
  routeId: number;
  /** IDs de parada en orden de recorrido. */
  stopIds: number[];
}

export interface NetworkStop extends StopSummary {
  /** Nombre y alias normalizados para búsqueda. */
  searchNames: string[];
}

export interface TransitNetwork {
  routes: Map<number, NetworkRoute>;
  directions: NetworkDirection[];
  stops: Map<number, NetworkStop>;
  /** Direcciones que pasan por cada parada. */
  directionsByStop: Map<number, NetworkDirection[]>;
}

// ─── Filas crudas de SQLite ────────────────────────────────────────────────

interface StopDbRow {
  id: number;
  external_id: string | null;
  name: string;
  neighborhood: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  verified: number;
  is_demo: number;
}

const STOP_COLUMNS = `
    s.id, s.external_id, s.name, s.neighborhood, s.address, s.latitude, s.longitude, s.verified,
    CASE WHEN src.source_type = '${DEMO_SOURCE_TYPE}' THEN 1 ELSE 0 END AS is_demo`;

function toStop(row: StopDbRow): StopSummary {
  return {
    id: row.id,
    externalId: row.external_id,
    name: row.name,
    neighborhood: row.neighborhood,
    address: row.address,
    latitude: row.latitude,
    longitude: row.longitude,
    verified: row.verified === 1,
    isDemo: row.is_demo === 1,
  };
}

export class TransitRepository {
  constructor(
    private readonly db: Db,
    private readonly cache: MemoryCache,
  ) {}

  // ── Rutas ────────────────────────────────────────────────────────────

  listRoutes(): RouteSummary[] {
    return this.queryRoutes('', []);
  }

  searchRoutes(query: string): RouteSummary[] {
    const like = `%${query.toLowerCase()}%`;
    return this.queryRoutes('AND (lower(r.code) LIKE ? OR lower(r.name) LIKE ?)', [like, like]);
  }

  getRouteByCode(code: string): RouteSummary | undefined {
    return this.queryRoutes('AND upper(r.code) = upper(?)', [code])[0];
  }

  getDirections(routeId: number): DirectionSummary[] {
    const rows = this.db
      .prepare(
        `SELECT id, direction_key, name, origin, destination, stop_count, verified
                 FROM route_directions WHERE route_id = ? ORDER BY id`,
      )
      .all(routeId) as Array<{
      id: number;
      direction_key: string;
      name: string;
      origin: string | null;
      destination: string | null;
      stop_count: number | null;
      verified: number;
    }>;
    return rows.map((r) => ({
      id: r.id,
      key: r.direction_key,
      name: r.name,
      origin: r.origin,
      destination: r.destination,
      stopCount: r.stop_count,
      verified: r.verified === 1,
    }));
  }

  getSchedules(routeId: number): ScheduleRow[] {
    const rows = this.db
      .prepare(
        `SELECT day_type, day_type_label, start_time, end_time, frequency_minutes, verified, notes
                 FROM schedules WHERE route_id = ? ORDER BY id`,
      )
      .all(routeId) as Array<{
      day_type: string;
      day_type_label: string | null;
      start_time: string | null;
      end_time: string | null;
      frequency_minutes: number | null;
      verified: number;
      notes: string | null;
    }>;
    return rows.map((r) => ({
      dayType: r.day_type,
      dayTypeLabel: r.day_type_label,
      startTime: r.start_time,
      endTime: r.end_time,
      frequencyMinutes: r.frequency_minutes,
      verified: r.verified === 1,
      notes: r.notes,
    }));
  }

  getStopsForDirection(directionId: number): StopInDirection[] {
    const rows = this.db
      .prepare(
        `SELECT rs.stop_order, ${STOP_COLUMNS}
                 FROM route_stops rs
                 JOIN stops s ON s.id = rs.stop_id
                 LEFT JOIN sources src ON src.id = s.source
                 WHERE rs.route_direction_id = ?
                 ORDER BY rs.stop_order`,
      )
      .all(directionId) as unknown as Array<StopDbRow & { stop_order: number }>;
    return rows.map((r) => ({ ...toStop(r), order: r.stop_order }));
  }

  // ── Paradas ──────────────────────────────────────────────────────────

  listStops(): StopSummary[] {
    const rows = this.db
      .prepare(
        `SELECT ${STOP_COLUMNS} FROM stops s
                 LEFT JOIN sources src ON src.id = s.source
                 WHERE s.active = 1 ORDER BY s.name, s.id`,
      )
      .all() as unknown as StopDbRow[];
    return rows.map(toStop);
  }

  /** Busca por código externo (Ruti) o, si es numérico, por id interno. */
  getStop(idOrExternalId: string): StopSummary | undefined {
    const byExternal = this.db
      .prepare(
        `SELECT ${STOP_COLUMNS} FROM stops s LEFT JOIN sources src ON src.id = s.source
                 WHERE s.external_id = ? AND s.active = 1`,
      )
      .get(idOrExternalId) as StopDbRow | undefined;
    if (byExternal) return toStop(byExternal);
    if (!/^\d+$/.test(idOrExternalId)) return undefined;
    const byId = this.db
      .prepare(
        `SELECT ${STOP_COLUMNS} FROM stops s LEFT JOIN sources src ON src.id = s.source
                 WHERE s.id = ? AND s.active = 1`,
      )
      .get(Number(idOrExternalId)) as StopDbRow | undefined;
    return byId ? toStop(byId) : undefined;
  }

  getAliases(stopId: number): string[] {
    const rows = this.db
      .prepare('SELECT alias FROM stop_aliases WHERE stop_id = ? ORDER BY alias')
      .all(stopId) as Array<{ alias: string }>;
    return rows.map((r) => r.alias);
  }

  getRoutesAtStop(stopId: number): RouteAtStop[] {
    const rows = this.db
      .prepare(
        `SELECT r.code, r.name AS route_name, rd.direction_key, rd.name AS direction_name, rs.stop_order
                 FROM route_stops rs
                 JOIN route_directions rd ON rd.id = rs.route_direction_id
                 JOIN routes r ON r.id = rd.route_id
                 WHERE rs.stop_id = ? AND r.active = 1
                 ORDER BY r.code, rd.id, rs.stop_order`,
      )
      .all(stopId) as Array<{
      code: string;
      route_name: string;
      direction_key: string;
      direction_name: string;
      stop_order: number;
    }>;
    return rows.map((r) => ({
      routeCode: r.code,
      routeName: r.route_name,
      directionKey: r.direction_key,
      directionName: r.direction_name,
      stopOrder: r.stop_order,
    }));
  }

  // ── Red en memoria ───────────────────────────────────────────────────

  /** Devuelve la red completa desde caché (TTL configurable). */
  getNetwork(): TransitNetwork {
    return this.cache.getOrSet(NETWORK_CACHE_KEY, () => this.loadNetwork());
  }

  invalidateCache(): void {
    this.cache.invalidate(NETWORK_CACHE_KEY);
  }

  private loadNetwork(): TransitNetwork {
    const routeRows = this.db
      .prepare(
        `SELECT r.id, r.code, r.name, r.color,
                        CASE WHEN src.source_type = '${DEMO_SOURCE_TYPE}' THEN 1 ELSE 0 END AS is_demo
                 FROM routes r LEFT JOIN sources src ON src.id = r.source
                 WHERE r.active = 1`,
      )
      .all() as Array<{
      id: number;
      code: string;
      name: string;
      color: string | null;
      is_demo: number;
    }>;
    const routes = new Map<number, NetworkRoute>(
      routeRows.map((r) => [
        r.id,
        {
          id: r.id,
          code: r.code,
          name: r.name,
          color: r.color,
          isDemo: r.is_demo === 1,
          schedules: this.getSchedules(r.id).map((sch) => ({
            dayType: sch.dayType,
            startTime: sch.startTime,
            endTime: sch.endTime,
            frequencyMinutes: sch.frequencyMinutes,
            verified: sch.verified,
          })),
        },
      ]),
    );

    const aliasRows = this.db.prepare('SELECT stop_id, alias FROM stop_aliases').all() as Array<{
      stop_id: number;
      alias: string;
    }>;
    const aliasesByStop = new Map<number, string[]>();
    for (const a of aliasRows) {
      const list = aliasesByStop.get(a.stop_id) ?? [];
      list.push(a.alias);
      aliasesByStop.set(a.stop_id, list);
    }

    const stops = new Map<number, NetworkStop>();
    for (const stop of this.listStops()) {
      const names = [stop.name, ...(aliasesByStop.get(stop.id) ?? [])]
        .map(normalizeForSearch)
        .filter((n) => n.length > 0);
      stops.set(stop.id, { ...stop, searchNames: [...new Set(names)] });
    }

    const dirRows = this.db
      .prepare('SELECT id, direction_key, name, route_id FROM route_directions ORDER BY id')
      .all() as Array<{ id: number; direction_key: string; name: string; route_id: number }>;
    const stopRows = this.db
      .prepare(
        'SELECT route_direction_id, stop_id FROM route_stops ORDER BY route_direction_id, stop_order',
      )
      .all() as Array<{ route_direction_id: number; stop_id: number }>;
    const stopsByDirection = new Map<number, number[]>();
    for (const row of stopRows) {
      const list = stopsByDirection.get(row.route_direction_id) ?? [];
      list.push(row.stop_id);
      stopsByDirection.set(row.route_direction_id, list);
    }

    const directions: NetworkDirection[] = dirRows
      .filter((d) => routes.has(d.route_id))
      .map((d) => ({
        id: d.id,
        key: d.direction_key,
        name: d.name,
        routeId: d.route_id,
        stopIds: (stopsByDirection.get(d.id) ?? []).filter((id) => stops.has(id)),
      }));

    const directionsByStop = new Map<number, NetworkDirection[]>();
    for (const dir of directions) {
      for (const stopId of new Set(dir.stopIds)) {
        const list = directionsByStop.get(stopId) ?? [];
        list.push(dir);
        directionsByStop.set(stopId, list);
      }
    }

    return { routes, directions, stops, directionsByStop };
  }

  private queryRoutes(extraWhere: string, params: string[]): RouteSummary[] {
    const rows = this.db
      .prepare(
        `SELECT r.id, r.code, r.name, r.type, r.description, r.origin, r.destination, r.color,
                        r.verified, r.data_quality, r.last_verified_at,
                        (SELECT COUNT(*) FROM route_directions rd WHERE rd.route_id = r.id) AS direction_count,
                        CASE WHEN src.source_type = '${DEMO_SOURCE_TYPE}' THEN 1 ELSE 0 END AS is_demo
                 FROM routes r LEFT JOIN sources src ON src.id = r.source
                 WHERE r.active = 1 ${extraWhere}
                 ORDER BY r.code`,
      )
      .all(...params) as Array<{
      id: number;
      code: string;
      name: string;
      type: string;
      description: string | null;
      origin: string | null;
      destination: string | null;
      verified: number;
      data_quality: string | null;
      last_verified_at: string | null;
      direction_count: number;
      color: string | null;
      is_demo: number;
    }>;
    return rows.map((r) => ({
      id: r.id,
      code: r.code,
      name: r.name,
      type: r.type,
      description: r.description,
      origin: r.origin,
      destination: r.destination,
      verified: r.verified === 1,
      dataQuality: r.data_quality,
      lastVerifiedAt: r.last_verified_at,
      directionCount: r.direction_count,
      color: r.color,
      isDemo: r.is_demo === 1,
    }));
  }
}
