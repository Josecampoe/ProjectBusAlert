/**
 * Importador de datos de transporte desde archivos JSON.
 *
 * - Valida todos los archivos con esquemas antes de escribir nada.
 * - Escribe en una sola transacción: o se importa todo o nada.
 * - Es idempotente: crear o actualizar según la clave natural
 *   (código de ruta, código de parada, clave de dirección).
 *
 * Estructura esperada de `dataDir`:
 *   sources/*.json     → arreglo de fuentes
 *   routes/routes.json, routes/route_directions.json, routes/route_stops.json
 *   stops/*.json       → arreglo de paradas
 *   schedules/*.json   → arreglo de horarios
 */
import fs from 'node:fs';
import path from 'node:path';
import { z } from 'zod';
import { transaction, type Db } from './database';

// ─── Esquemas ──────────────────────────────────────────────────────────────

const nullableText = z.string().trim().min(1).nullable().default(null);
const latitude = z.number().min(-90).max(90).nullable().default(null);
const longitude = z.number().min(-180).max(180).nullable().default(null);
const hhmm = z
  .string()
  .regex(/^([01]\d|2[0-3]):[0-5]\d$/)
  .nullable()
  .default(null);

export const SourceSchema = z.strictObject({
  id: z.string().trim().min(1),
  name: z.string().trim().min(1),
  url: z.url().nullable().default(null),
  source_type: z.enum(['official_app', 'official_website', 'verified_user_input', 'demo_sample']),
  last_checked: nullableText,
  reliability: z.enum(['high', 'medium', 'low', 'unknown']).default('unknown'),
  notes: nullableText,
});

export const RouteSchema = z.strictObject({
  code: z.string().trim().min(1),
  name: z.string().trim().min(1),
  type: z.string().trim().min(1),
  description: nullableText,
  origin: nullableText,
  destination: nullableText,
  active: z.boolean().default(true),
  source: z.string().trim().min(1),
  verified: z.boolean().default(false),
  data_quality: z.enum(['complete', 'partial', 'minimal', 'unknown']).default('unknown'),
  notes: nullableText,
  last_verified_at: nullableText,
  /** Color de la ruta en formato #RRGGBB (opcional). */
  color: z
    .string()
    .regex(/^#[0-9A-Fa-f]{6}$/)
    .nullable()
    .default(null),
});

export const DirectionSchema = z.strictObject({
  route_code: z.string().trim().min(1),
  direction_key: z.string().trim().min(1),
  name: z.string().trim().min(1),
  origin: nullableText,
  destination: nullableText,
  description: nullableText,
  stop_count: z.number().int().nonnegative().nullable().default(null),
  verified: z.boolean().default(false),
  source: z.string().trim().min(1),
});

export const StopSchema = z
  .strictObject({
    external_id: nullableText,
    name: z.string().trim().min(1),
    aliases: z.array(z.string().trim().min(1)).default([]),
    latitude,
    longitude,
    address: nullableText,
    neighborhood: nullableText,
    active: z.boolean().default(true),
    source: z.string().trim().min(1),
    verified: z.boolean().default(false),
    notes: nullableText,
  })
  .refine((s) => (s.latitude === null) === (s.longitude === null), {
    message: 'latitude y longitude deben venir juntas o ambas en null',
  });

const RouteStopEntrySchema = z.strictObject({
  order: z.number().int().positive(),
  stop_external_id: nullableText,
  stop_name: z.string().trim().min(1),
});

export const RouteStopsFileSchema = z.record(
  z.string(),
  z.union([
    z.string(), // claves "_comment"
    z.strictObject({
      direction_key: z.string().trim().min(1),
      route_code: z.string().trim().min(1),
      name: z.string().trim().min(1),
      stops: z.array(RouteStopEntrySchema).min(2),
    }),
  ]),
);

export const ScheduleSchema = z.strictObject({
  route_code: z.string().trim().min(1),
  day_type: z.enum(['LV', 'LS', 'S', 'DF', 'LD']),
  day_type_label: nullableText,
  start_time: hhmm,
  end_time: hhmm,
  frequency_minutes: z.number().int().positive().nullable().default(null),
  source: z.string().trim().min(1),
  verified: z.boolean().default(false),
  notes: nullableText,
});

type Source = z.infer<typeof SourceSchema>;
type Route = z.infer<typeof RouteSchema>;
type Direction = z.infer<typeof DirectionSchema>;
type Stop = z.infer<typeof StopSchema>;
type Schedule = z.infer<typeof ScheduleSchema>;
type RouteStopsDirection = Exclude<z.infer<typeof RouteStopsFileSchema>[string], string>;

// ─── Reporte ───────────────────────────────────────────────────────────────

export interface ImportIssue {
  file: string;
  location: string;
  message: string;
}

export interface ImportReport {
  ok: boolean;
  counts: Record<
    'sources' | 'routes' | 'directions' | 'stops' | 'aliases' | 'routeStops' | 'schedules',
    number
  >;
  errors: ImportIssue[];
  warnings: ImportIssue[];
  summary: {
    stopsWithoutCoordinates: number;
    stopsWithoutExternalId: number;
    unverifiedSchedules: number;
  };
}

interface Dataset {
  sources: Source[];
  routes: Route[];
  directions: Direction[];
  stops: Stop[];
  routeStops: RouteStopsDirection[];
  schedules: Schedule[];
}

// ─── Lectura y validación ──────────────────────────────────────────────────

function listJson(dir: string): string[] {
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => path.join(dir, f));
}

function readValidated<T>(
  file: string,
  schema: z.ZodType<T>,
  errors: ImportIssue[],
  baseDir: string,
): T | undefined {
  const rel = path.relative(baseDir, file);
  let json: unknown;
  try {
    json = JSON.parse(fs.readFileSync(file, 'utf-8'));
  } catch (err) {
    errors.push({
      file: rel,
      location: '(archivo)',
      message: `JSON inválido: ${(err as Error).message}`,
    });
    return undefined;
  }
  const result = schema.safeParse(json);
  if (!result.success) {
    for (const issue of result.error.issues) {
      errors.push({
        file: rel,
        location: issue.path.join('.') || '(raíz)',
        message: issue.message,
      });
    }
    return undefined;
  }
  return result.data;
}

export function loadDataset(dataDir: string, errors: ImportIssue[]): Dataset {
  const arr = <T>(dir: string, schema: z.ZodType<T>) =>
    listJson(path.join(dataDir, dir)).flatMap(
      (f) => readValidated(f, z.array(schema), errors, dataDir) ?? [],
    );
  const routesDir = path.join(dataDir, 'routes');
  const one = <T>(file: string, schema: z.ZodType<T>) =>
    fs.existsSync(file) ? readValidated(file, schema, errors, dataDir) : undefined;

  const routeStopsFile = one(path.join(routesDir, 'route_stops.json'), RouteStopsFileSchema) ?? {};
  return {
    sources: arr('sources', SourceSchema),
    routes: one(path.join(routesDir, 'routes.json'), z.array(RouteSchema)) ?? [],
    directions: one(path.join(routesDir, 'route_directions.json'), z.array(DirectionSchema)) ?? [],
    stops: arr('stops', StopSchema),
    routeStops: Object.values(routeStopsFile).filter(
      (v): v is RouteStopsDirection => typeof v !== 'string',
    ),
    schedules: arr('schedules', ScheduleSchema),
  };
}

/** Comprobaciones de integridad entre archivos (referencias y duplicados). */
function crossValidate(data: Dataset, errors: ImportIssue[]): void {
  const sourceIds = new Set(data.sources.map((s) => s.id));
  const routeCodes = new Set(data.routes.map((r) => r.code));
  const directionKeys = new Set(data.directions.map((d) => d.direction_key));

  const checkSource = (file: string, location: string, source: string) => {
    if (!sourceIds.has(source))
      errors.push({ file, location, message: `fuente "${source}" no existe` });
  };
  const dupes = (values: string[]) => values.filter((v, i) => values.indexOf(v) !== i);

  for (const code of new Set(dupes(data.routes.map((r) => r.code))))
    errors.push({
      file: 'routes/routes.json',
      location: code,
      message: 'código de ruta duplicado',
    });
  for (const id of new Set(
    dupes(data.stops.flatMap((s) => (s.external_id ? [s.external_id] : []))),
  ))
    errors.push({ file: 'stops', location: id, message: 'código de parada duplicado' });
  for (const name of new Set(
    dupes(data.stops.filter((s) => !s.external_id).map((s) => s.name.toLowerCase())),
  ))
    errors.push({
      file: 'stops',
      location: name,
      message: 'dos paradas sin código con el mismo nombre',
    });

  data.routes.forEach((r) => {
    checkSource('routes/routes.json', r.code, r.source);
  });
  data.directions.forEach((d) => {
    checkSource('routes/route_directions.json', d.direction_key, d.source);
    if (!routeCodes.has(d.route_code))
      errors.push({
        file: 'routes/route_directions.json',
        location: d.direction_key,
        message: `ruta "${d.route_code}" no existe`,
      });
  });
  data.stops.forEach((s) => {
    checkSource('stops', s.external_id ?? s.name, s.source);
  });
  data.schedules.forEach((s) => {
    checkSource('schedules', `${s.route_code}/${s.day_type}`, s.source);
    if (!routeCodes.has(s.route_code))
      errors.push({
        file: 'schedules',
        location: s.route_code,
        message: `ruta "${s.route_code}" no existe`,
      });
  });
  data.routeStops.forEach((d) => {
    if (!directionKeys.has(d.direction_key))
      errors.push({
        file: 'routes/route_stops.json',
        location: d.direction_key,
        message: 'dirección no existe',
      });
    for (const order of new Set(dupes(d.stops.map((s) => String(s.order)))))
      errors.push({
        file: 'routes/route_stops.json',
        location: `${d.direction_key}.order=${order}`,
        message: 'orden repetido',
      });
  });
}

// ─── Escritura ─────────────────────────────────────────────────────────────

const bool = (v: boolean) => (v ? 1 : 0);

function writeDataset(db: Db, data: Dataset, report: ImportReport): void {
  const c = report.counts;

  const upsertSource = db.prepare(`
        INSERT INTO sources (id, name, url, source_type, last_checked, reliability, notes)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET name=excluded.name, url=excluded.url, source_type=excluded.source_type,
            last_checked=excluded.last_checked, reliability=excluded.reliability, notes=excluded.notes,
            updated_at=datetime('now')`);
  for (const s of data.sources) {
    upsertSource.run(s.id, s.name, s.url, s.source_type, s.last_checked, s.reliability, s.notes);
    c.sources++;
  }

  const upsertRoute = db.prepare(`
        INSERT INTO routes (code, name, type, description, origin, destination, active, source, verified, data_quality, notes, last_verified_at, color)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(code) DO UPDATE SET name=excluded.name, type=excluded.type, description=excluded.description,
            origin=excluded.origin, destination=excluded.destination, active=excluded.active, source=excluded.source,
            verified=excluded.verified, data_quality=excluded.data_quality, notes=excluded.notes,
            last_verified_at=excluded.last_verified_at, color=excluded.color, updated_at=datetime('now')`);
  for (const r of data.routes) {
    upsertRoute.run(
      r.code,
      r.name,
      r.type,
      r.description,
      r.origin,
      r.destination,
      bool(r.active),
      r.source,
      bool(r.verified),
      r.data_quality,
      r.notes,
      r.last_verified_at,
      r.color,
    );
    c.routes++;
  }
  const routeId = (code: string) =>
    (db.prepare('SELECT id FROM routes WHERE code = ?').get(code) as { id: number }).id;

  const upsertDirection = db.prepare(`
        INSERT INTO route_directions (route_id, direction_key, name, origin, destination, description, stop_count, verified, source)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(direction_key) DO UPDATE SET route_id=excluded.route_id, name=excluded.name, origin=excluded.origin,
            destination=excluded.destination, description=excluded.description, stop_count=excluded.stop_count,
            verified=excluded.verified, source=excluded.source, updated_at=datetime('now')`);
  for (const d of data.directions) {
    upsertDirection.run(
      routeId(d.route_code),
      d.direction_key,
      d.name,
      d.origin,
      d.destination,
      d.description,
      d.stop_count,
      bool(d.verified),
      d.source,
    );
    c.directions++;
  }

  // Paradas: clave natural = código externo; si no hay, nombre (entre las paradas sin código).
  const findByExternal = db.prepare('SELECT id FROM stops WHERE external_id = ?');
  const findUncoded = db.prepare(
    'SELECT id FROM stops WHERE external_id IS NULL AND lower(name) = lower(?)',
  );
  const insertStop = db.prepare(`
        INSERT INTO stops (external_id, name, latitude, longitude, address, neighborhood, active, source, verified, notes)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`);
  const updateStop = db.prepare(`
        UPDATE stops SET external_id=?, name=?, latitude=?, longitude=?, address=?, neighborhood=?, active=?, source=?,
            verified=?, notes=?, updated_at=datetime('now') WHERE id = ?`);
  const deleteAliases = db.prepare('DELETE FROM stop_aliases WHERE stop_id = ?');
  const insertAlias = db.prepare(
    'INSERT OR IGNORE INTO stop_aliases (stop_id, alias, source) VALUES (?, ?, ?)',
  );

  for (const s of data.stops) {
    const existing = (
      s.external_id ? findByExternal.get(s.external_id) : findUncoded.get(s.name)
    ) as { id: number } | undefined;
    const values = [
      s.external_id,
      s.name,
      s.latitude,
      s.longitude,
      s.address,
      s.neighborhood,
      bool(s.active),
      s.source,
      bool(s.verified),
      s.notes,
    ] as const;
    let id: number;
    if (existing) {
      updateStop.run(...values, existing.id);
      id = existing.id;
    } else {
      id = Number(insertStop.run(...values).lastInsertRowid);
    }
    c.stops++;
    deleteAliases.run(id);
    for (const alias of new Set(s.aliases)) {
      insertAlias.run(id, alias, s.source);
      c.aliases++;
    }
  }

  // Recorridos: se reemplazan completos por dirección para reflejar cambios de orden.
  const directionId = (key: string) =>
    (
      db.prepare('SELECT id FROM route_directions WHERE direction_key = ?').get(key) as {
        id: number;
      }
    ).id;
  const findAnyByName = db.prepare('SELECT id FROM stops WHERE lower(name) = lower(?)');
  const deleteRouteStops = db.prepare('DELETE FROM route_stops WHERE route_direction_id = ?');
  const insertRouteStop = db.prepare(
    'INSERT INTO route_stops (route_direction_id, stop_id, stop_order) VALUES (?, ?, ?)',
  );
  for (const d of data.routeStops) {
    const dirId = directionId(d.direction_key);
    deleteRouteStops.run(dirId);
    for (const entry of d.stops) {
      let stop = entry.stop_external_id
        ? (findByExternal.get(entry.stop_external_id) as { id: number } | undefined)
        : (findUncoded.get(entry.stop_name) as { id: number } | undefined);
      if (!stop && !entry.stop_external_id) {
        const matches = findAnyByName.all(entry.stop_name) as Array<{ id: number }>;
        if (matches.length === 1) stop = matches[0];
      }
      if (!stop) {
        report.errors.push({
          file: 'routes/route_stops.json',
          location: `${d.direction_key}.order=${entry.order}`,
          message: `parada "${entry.stop_name}" (código ${entry.stop_external_id ?? 'sin código'}) no existe o es ambigua`,
        });
        continue;
      }
      insertRouteStop.run(dirId, stop.id, entry.order);
      c.routeStops++;
    }
  }

  const upsertSchedule = db.prepare(`
        INSERT INTO schedules (route_id, day_type, day_type_label, start_time, end_time, frequency_minutes, source, verified, notes)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(route_id, day_type) DO UPDATE SET day_type_label=excluded.day_type_label, start_time=excluded.start_time,
            end_time=excluded.end_time, frequency_minutes=excluded.frequency_minutes, source=excluded.source,
            verified=excluded.verified, notes=excluded.notes, updated_at=datetime('now')`);
  for (const s of data.schedules) {
    upsertSchedule.run(
      routeId(s.route_code),
      s.day_type,
      s.day_type_label,
      s.start_time,
      s.end_time,
      s.frequency_minutes,
      s.source,
      bool(s.verified),
      s.notes,
    );
    c.schedules++;
  }

  if (report.errors.length > 0) {
    // Fuerza el rollback de la transacción: no se deja la BD a medias.
    throw new ImportAbortedError();
  }
}

class ImportAbortedError extends Error {}

function countOf(db: Db, sql: string): number {
  return (db.prepare(sql).get() as { c: number }).c;
}

/** Importa `dataDir` en `db`. No escribe nada si hay errores. */
export function importDataset(db: Db, dataDir: string): ImportReport {
  const report: ImportReport = {
    ok: false,
    counts: {
      sources: 0,
      routes: 0,
      directions: 0,
      stops: 0,
      aliases: 0,
      routeStops: 0,
      schedules: 0,
    },
    errors: [],
    warnings: [],
    summary: { stopsWithoutCoordinates: 0, stopsWithoutExternalId: 0, unverifiedSchedules: 0 },
  };

  const data = loadDataset(dataDir, report.errors);
  if (report.errors.length === 0) crossValidate(data, report.errors);
  if (report.errors.length > 0) return report;

  try {
    transaction(db, () => {
      writeDataset(db, data, report);
    });
  } catch (err) {
    if (!(err instanceof ImportAbortedError)) throw err;
    report.counts = {
      sources: 0,
      routes: 0,
      directions: 0,
      stops: 0,
      aliases: 0,
      routeStops: 0,
      schedules: 0,
    };
    return report;
  }

  report.summary = {
    stopsWithoutCoordinates: countOf(db, 'SELECT COUNT(*) AS c FROM stops WHERE latitude IS NULL'),
    stopsWithoutExternalId: countOf(
      db,
      'SELECT COUNT(*) AS c FROM stops WHERE external_id IS NULL',
    ),
    unverifiedSchedules: countOf(db, 'SELECT COUNT(*) AS c FROM schedules WHERE verified = 0'),
  };
  if (report.summary.stopsWithoutCoordinates > 0) {
    report.warnings.push({
      file: 'stops',
      location: '*',
      message: `${report.summary.stopsWithoutCoordinates} paradas sin coordenadas (no aparecen en búsquedas por cercanía)`,
    });
  }
  report.ok = true;
  return report;
}
