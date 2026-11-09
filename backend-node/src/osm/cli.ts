/**
 * Datos de OpenStreetMap para ubicar las paradas.
 *
 *   npm run osm:fetch     descarga los lugares de Pasto → data/raw/osm_pasto.json
 *   npm run osm:geocode   cruza las paradas con esos lugares → data/geo/osm_stop_coordinates.json
 *   npm run data:update   las dos cosas y luego `seed`
 *
 * Las coordenadas generadas solo se aplican a paradas que no tienen coordenadas
 * escritas en `data/stops/` (lo verificado a mano siempre gana).
 */
import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import { loadDataset, type ImportIssue } from '../db/importer';
import { geocodeStops, type StopToLocate } from './geocoder';
import { fetchOsmSnapshot, type OsmSnapshot } from './overpass';

const DATA_DIR = process.env.DATA_DIR ?? './data';
const SNAPSHOT_FILE = path.join(DATA_DIR, 'raw', 'osm_pasto.json');
const OUTPUT_FILE = path.join(DATA_DIR, 'geo', 'osm_stop_coordinates.json');

async function fetchCommand(): Promise<void> {
  console.log('[osm] Descargando lugares de Pasto desde OpenStreetMap…');
  const snapshot = await fetchOsmSnapshot({ log: console.log });
  fs.mkdirSync(path.dirname(SNAPSHOT_FILE), { recursive: true });
  fs.writeFileSync(SNAPSHOT_FILE, `${JSON.stringify(snapshot)}\n`);
  console.log(
    `[osm] ✔ ${String(snapshot.features.length)} lugares y ${String(snapshot.streets.length)} tramos de calle → ${SNAPSHOT_FILE}`,
  );
}

function geocodeCommand(): void {
  if (!fs.existsSync(SNAPSHOT_FILE)) {
    console.error(`[osm] Falta ${SNAPSHOT_FILE}. Ejecuta primero: npm run osm:fetch`);
    process.exit(1);
  }
  const snapshot = JSON.parse(fs.readFileSync(SNAPSHOT_FILE, 'utf-8')) as OsmSnapshot;
  const errors: ImportIssue[] = [];
  const { stops, routeStops } = loadDataset(DATA_DIR, errors);
  if (errors.length > 0) {
    console.error('[osm] Los datos tienen errores; corrígelos con `npm run data:validate`.');
    process.exit(1);
  }
  const pending: StopToLocate[] = stops
    .filter((s) => s.latitude === null)
    .map((s) => ({
      external_id: s.external_id,
      name: s.name,
      aliases: s.aliases,
      neighborhood: s.neighborhood,
    }));
  const routes = routeStops.map((d) => [...d.stops].sort((a, b) => a.order - b.order));
  const result = geocodeStops(pending, snapshot, routes);
  const output = {
    source: 'openstreetmap',
    attribution: snapshot.attribution,
    generated_at: new Date().toISOString(),
    osm_fetched_at: snapshot.fetched_at,
    note: 'Archivo generado por `npm run osm:geocode`. No lo edites a mano: corrige en data/stops/ (las coordenadas escritas ahí ganan).',
    coordinates: result.located,
    not_found: result.notFound,
  };
  fs.mkdirSync(path.dirname(OUTPUT_FILE), { recursive: true });
  fs.writeFileSync(OUTPUT_FILE, `${JSON.stringify(output, null, 2)}\n`);
  const byMethod = new Map<string, number>();
  for (const c of result.located) byMethod.set(c.method, (byMethod.get(c.method) ?? 0) + 1);
  console.log(
    `[osm] ✔ ${String(result.located.length)} de ${String(pending.length)} paradas ubicadas → ${OUTPUT_FILE}`,
  );
  for (const [method, count] of byMethod) console.log(`  ${method.padEnd(14)} ${String(count)}`);
  console.log(`  sin ubicar     ${String(result.notFound.length)} (lista en "not_found")`);
}

async function main(): Promise<void> {
  const command = process.argv[2];
  if (command === 'fetch') await fetchCommand();
  else if (command === 'geocode') geocodeCommand();
  else {
    console.error('Uso: tsx src/osm/cli.ts <fetch|geocode>');
    process.exit(1);
  }
}

main().catch((err: unknown) => {
  console.error(`[osm] ✖ ${(err as Error).message}`);
  process.exit(1);
});
