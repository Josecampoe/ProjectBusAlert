/**
 * Descarga de lugares con nombre de Pasto desde OpenStreetMap (API Overpass).
 *
 * Se guarda una "foto" reducida en `data/raw/osm_pasto.json` para que la
 * importación no dependa de la red. Los datos de OpenStreetMap tienen licencia
 * ODbL: hay que citar "© colaboradores de OpenStreetMap".
 */

/** Área de Pasto (casco urbano y corregimientos cercanos): sur, oeste, norte, este. */
export const PASTO_BBOX = [1.1, -77.4, 1.32, -77.15] as const;

/** Servidores públicos de Overpass; si uno está saturado se prueba el siguiente. */
export const OVERPASS_ENDPOINTS = [
  'https://overpass-api.de/api/interpreter',
  'https://maps.mail.ru/osm/tools/overpass/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
];

export const OSM_ATTRIBUTION = '© colaboradores de OpenStreetMap (ODbL)';

/** Etiquetas cuyos elementos con nombre sirven para ubicar una parada. */
const FEATURE_KEYS = [
  'place',
  'amenity',
  'shop',
  'leisure',
  'tourism',
  'office',
  'healthcare',
  'building',
  'landuse',
  'historic',
  'public_transport',
];

/** Nombres alternos que también se indexan. */
const NAME_TAGS = ['name', 'alt_name', 'official_name', 'short_name', 'old_name', 'loc_name'];

export interface OsmFeature {
  /** "node/123", "way/456" o "relation/789". */
  id: string;
  names: string[];
  /** Tipo principal, p. ej. "place=neighbourhood" o "amenity=university". */
  kind: string;
  lat: number;
  lon: number;
}

export interface OsmStreet {
  id: string;
  name: string;
  /** Puntos [lat, lon] del trazado. */
  points: [number, number][];
}

export interface OsmSnapshot {
  source: 'openstreetmap';
  attribution: string;
  fetched_at: string;
  bbox: readonly number[];
  features: OsmFeature[];
  streets: OsmStreet[];
}

export function buildOverpassQuery(bbox: readonly number[] = PASTO_BBOX): string {
  const box = bbox.join(',');
  const features = FEATURE_KEYS.map((key) => `  nwr["name"]["${key}"](${box});`).join('\n');
  return `[out:json][timeout:180];
(
${features}
);
out tags center;
way["highway"]["name"](${box});
out tags geom;`;
}

interface OverpassElement {
  type: 'node' | 'way' | 'relation';
  id: number;
  lat?: number;
  lon?: number;
  center?: { lat: number; lon: number };
  geometry?: { lat: number; lon: number }[];
  tags?: Partial<Record<string, string>>;
}

const round = (n: number) => Math.round(n * 1e6) / 1e6;

/** Convierte la respuesta de Overpass en la foto reducida que usa el geocodificador. */
export function toSnapshot(
  elements: OverpassElement[],
  fetchedAt: Date,
  bbox: readonly number[] = PASTO_BBOX,
): OsmSnapshot {
  const features: OsmFeature[] = [];
  const streets: OsmStreet[] = [];
  const seen = new Set<string>();
  for (const el of elements) {
    const tags = el.tags ?? {};
    const id = `${el.type}/${String(el.id)}`;
    const name = tags.name;
    if (!name) continue;
    if (el.type === 'way' && tags.highway && el.geometry && el.geometry.length >= 2) {
      streets.push({ id, name, points: el.geometry.map((p) => [round(p.lat), round(p.lon)]) });
      continue;
    }
    if (seen.has(id)) continue;
    const lat = el.center?.lat ?? el.lat;
    const lon = el.center?.lon ?? el.lon;
    if (lat === undefined || lon === undefined) continue;
    const kindKey = FEATURE_KEYS.find((k) => tags[k] !== undefined);
    if (!kindKey) continue;
    seen.add(id);
    const names = [
      ...new Set(
        NAME_TAGS.flatMap((t) => (tags[t] ?? '').split(';'))
          .map((n) => n.trim())
          .filter(Boolean),
      ),
    ];
    features.push({
      id,
      names,
      kind: `${kindKey}=${tags[kindKey] ?? ''}`,
      lat: round(lat),
      lon: round(lon),
    });
  }
  const byId = (a: { id: string }, b: { id: string }) => a.id.localeCompare(b.id);
  return {
    source: 'openstreetmap',
    attribution: OSM_ATTRIBUTION,
    fetched_at: fetchedAt.toISOString(),
    bbox: [...bbox],
    features: features.sort(byId),
    streets: streets.sort(byId),
  };
}

export interface FetchOptions {
  endpoints?: string[];
  attempts?: number;
  initialDelayMs?: number;
  timeoutMs?: number;
  log?: (message: string) => void;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Descarga los lugares de Pasto. Reintenta con espera creciente y cambia de
 * servidor si uno responde con error (los servidores públicos se saturan).
 */
export async function fetchOsmSnapshot(options: FetchOptions = {}): Promise<OsmSnapshot> {
  const {
    endpoints = OVERPASS_ENDPOINTS,
    attempts = 5,
    initialDelayMs = 15_000,
    timeoutMs = 200_000,
    log = () => undefined,
  } = options;
  const body = new URLSearchParams({ data: buildOverpassQuery() });
  let delay = initialDelayMs;
  let lastError = 'sin intentos';
  for (let attempt = 1; attempt <= attempts; attempt++) {
    for (const url of endpoints) {
      try {
        const res = await fetch(url, {
          method: 'POST',
          body,
          headers: { 'User-Agent': 'BusAlert/1.0 (proyecto universitario, Pasto)' },
          signal: AbortSignal.timeout(timeoutMs),
        });
        if (!res.ok) {
          lastError = `${url} respondió ${String(res.status)}`;
          log(`  intento ${String(attempt)}: ${lastError}`);
          continue;
        }
        const json = (await res.json()) as { elements?: OverpassElement[] };
        if (!Array.isArray(json.elements)) throw new Error('respuesta sin "elements"');
        return toSnapshot(json.elements, new Date());
      } catch (err) {
        lastError = `${url}: ${(err as Error).message}`;
        log(`  intento ${String(attempt)}: ${lastError}`);
      }
    }
    if (attempt < attempts) {
      log(`  esperando ${String(Math.round(delay / 1000))} s antes de reintentar…`);
      await sleep(delay);
      delay *= 2;
    }
  }
  throw new Error(`No se pudo descargar OpenStreetMap: ${lastError}`);
}
