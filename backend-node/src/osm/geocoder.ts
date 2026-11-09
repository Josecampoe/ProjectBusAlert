/**
 * Ubica paradas cruzando su nombre con los lugares y calles de OpenStreetMap.
 *
 * - "Calle 18 Carrera 29", "Av. Panamericana Calle 18" → cruce de las dos calles.
 * - "Las Cuadras Calle 19" → el lugar, ajustado al punto más cercano de la calle.
 * - "Fátima", "Universidad Cooperativa", "Villa Flor II Manzana 12" → el lugar.
 *
 * Si el nombre es genérico ("Calle 17") o coincide con lugares lejanos entre sí,
 * la parada queda sin ubicar: es mejor no tener coordenadas que tenerlas mal.
 */
import { normalizeForSearch } from '../lib/text';
import { calculateHaversineDistance } from '../utils/geoUtils';
import type { OsmFeature, OsmSnapshot, OsmStreet } from './overpass';

export interface StopToLocate {
  external_id: string | null;
  name: string;
  aliases: string[];
  neighborhood: string | null;
}

export type LocateMethod = 'place' | 'intersection' | 'place_on_street';

export interface LocatedStop {
  external_id: string | null;
  name: string;
  latitude: number;
  longitude: number;
  method: LocateMethod;
  /** Qué se encontró en OpenStreetMap, para revisar a mano. */
  matched: string;
  osm_ids: string[];
}

export interface NotFoundStop {
  external_id: string | null;
  name: string;
  reason: string;
}

/** Parada de un recorrido, como en `route_stops.json`. */
export interface RouteStopRef {
  stop_external_id: string | null;
  stop_name: string;
}

export interface GeocodeResult {
  located: LocatedStop[];
  notFound: NotFoundStop[];
}

// ─── Normalización ─────────────────────────────────────────────────────────

/** Abreviaturas que se escriben distinto en la guía y en OpenStreetMap. */
const TOKEN_SYNONYMS: Record<string, string> = {
  av: 'avenida',
  avda: 'avenida',
  cra: 'carrera',
  kr: 'carrera',
  cr: 'carrera',
  cl: 'calle',
  cll: 'calle',
  mz: 'manzana',
  mzna: 'manzana',
  u: 'universidad',
  sto: 'santo',
  sta: 'santa',
  s: 'sur',
};

/** Palabras que no distinguen un lugar de otro. */
const ARTICLES = new Set(['de', 'del', 'la', 'las', 'los', 'el', 'y']);

/** Palabras genéricas al inicio de un nombre ("Barrio Fátima" ≈ "Fátima"). */
const GENERIC_PREFIXES = [
  'barrio',
  'urbanizacion',
  'conjunto residencial',
  'conjunto',
  'condominio',
  'parroquia',
  'iglesia',
  'templo',
  'parque',
  'centro comercial',
  'cc',
  'institucion educativa',
  'colegio',
  'escuela',
];

const STREET_TYPES = new Set(['calle', 'carrera', 'avenida', 'diagonal', 'transversal']);
const STREET_NUMBER = /^\d+[a-z]?$/;
const STREET_SUFFIX = new Set(['bis', 'sur', 'este', 'oeste', 'norte', 'a', 'b', 'c', 'd', 'e']);

function tokens(text: string): string[] {
  return normalizeForSearch(text)
    .split(' ')
    .filter(Boolean)
    .map((t) => TOKEN_SYNONYMS[t] ?? t);
}

/** Clave de comparación: sin tildes, artículos ni espacios ("Villa Flor II" = "Villaflor II"). */
function keyOf(words: string[]): string {
  return words.filter((w) => !ARTICLES.has(w)).join('');
}

function key(text: string): string {
  return keyOf(tokens(text));
}

function withoutGenericPrefix(text: string): string | null {
  const normalized = tokens(text).join(' ');
  for (const prefix of GENERIC_PREFIXES) {
    if (normalized.startsWith(`${prefix} `)) return normalized.slice(prefix.length + 1);
  }
  return null;
}

// ─── Lugares ───────────────────────────────────────────────────────────────

/** Prioridad del tipo de lugar cuando un nombre coincide con varios. */
function kindRank(kind: string): number {
  if (/^place=(neighbourhood|suburb|quarter|village|hamlet|town|locality)$/.test(kind)) return 0;
  if (kind === 'place=city_block') return 1;
  if (
    /^amenity=(university|college|hospital|clinic|bus_station|place_of_worship|school|townhall|police|fire_station|marketplace)$/.test(
      kind,
    )
  )
    return 2;
  if (/^(leisure|tourism|historic|landuse|public_transport)=/.test(kind)) return 3;
  return 4;
}

/** Distancia máxima entre coincidencias del mismo nombre para tratarlas como un solo lugar. */
const SAME_PLACE_METERS = 800;

function distance(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
  return calculateHaversineDistance(a.lat, a.lon, b.lat, b.lon);
}

interface PlaceMatch {
  lat: number;
  lon: number;
  matched: string;
  osmIds: string[];
}

type PlaceLookup = PlaceMatch | 'ambiguous' | null;

/** De varias coincidencias elige un lugar; null si están dispersas (nombre ambiguo). */
function pickPlace(features: OsmFeature[]): PlaceLookup {
  if (features.length === 0) return null;
  const bestRank = Math.min(...features.map((f) => kindRank(f.kind)));
  const best = features.filter((f) => kindRank(f.kind) === bestRank);
  const anchor = best[0];
  if (best.some((f) => distance(f, anchor) > SAME_PLACE_METERS)) return 'ambiguous';
  return {
    lat: round(best.reduce((sum, f) => sum + f.lat, 0) / best.length),
    lon: round(best.reduce((sum, f) => sum + f.lon, 0) / best.length),
    matched: `${anchor.names[0] ?? ''} (${anchor.kind})`,
    osmIds: best.map((f) => f.id),
  };
}

const round = (n: number) => Math.round(n * 1e6) / 1e6;

class PlaceIndex {
  private readonly exact = new Map<string, OsmFeature[]>();
  private readonly stripped = new Map<string, OsmFeature[]>();
  private readonly fullNames: { words: string[]; feature: OsmFeature }[] = [];

  constructor(features: OsmFeature[]) {
    for (const feature of features) {
      for (const name of feature.names) {
        add(this.exact, key(name), feature);
        const bare = withoutGenericPrefix(name);
        if (bare) add(this.stripped, key(bare), feature);
        this.fullNames.push({ words: tokens(name).filter((w) => !ARTICLES.has(w)), feature });
      }
    }
  }

  /**
   * Busca por nombre exacto, luego sin palabra genérica ("Parroquia Fátima" → "Fátima")
   * y, para nombres de 2+ palabras, por comienzo ("Universidad Cooperativa" →
   * "Universidad Cooperativa de Colombia").
   */
  find(text: string): PlaceLookup {
    const k = key(text);
    if (k.length < 3) return null;
    const bare = withoutGenericPrefix(text);
    const attempts: (OsmFeature[] | undefined)[] = [
      this.exact.get(k),
      this.stripped.get(k),
      bare ? this.exact.get(key(bare)) : undefined,
    ];
    for (const found of attempts) {
      if (found && found.length > 0) return pickPlace(unique(found));
    }
    const words = tokens(text).filter((w) => !ARTICLES.has(w));
    if (words.length >= 2) {
      const prefixed = this.fullNames
        .filter(({ words: w }) => w.length > words.length && words.every((x, i) => w[i] === x))
        .map(({ feature }) => feature);
      if (prefixed.length > 0) return pickPlace(unique(prefixed));
    }
    return null;
  }
}

function add<K, V>(map: Map<K, V[]>, k: K, v: V): void {
  const list = map.get(k);
  if (list) list.push(v);
  else map.set(k, [v]);
}

function unique(features: OsmFeature[]): OsmFeature[] {
  return [...new Map(features.map((f) => [f.id, f])).values()];
}

// ─── Calles ────────────────────────────────────────────────────────────────

/**
 * Extrae las calles mencionadas en un nombre: "Calle 18 Carrera 29" →
 * ["calle18", "carrera29"]; "Av. Las Américas Calle 16" → ["avenidaamericas", "calle16"].
 * Devuelve también el resto del texto ("Las Cuadras Calle 19" → "las cuadras").
 */
export function parseStreets(name: string): { streets: string[]; rest: string } {
  const words = tokens(name);
  const streets: string[] = [];
  const rest: string[] = [];
  let i = 0;
  while (i < words.length) {
    const w = words[i];
    if (!STREET_TYPES.has(w)) {
      rest.push(w);
      i++;
      continue;
    }
    const next = words.at(i + 1);
    if (w !== 'avenida' && next !== undefined && STREET_NUMBER.test(next)) {
      // "calle 18", "carrera 32b", "calle 6 sur", "carrera 21 a"
      const parts = [w, next];
      i += 2;
      while (i < words.length && STREET_SUFFIX.has(words[i]) && parts.length < 4) {
        parts.push(words[i]);
        i++;
      }
      streets.push(keyOf(parts));
      continue;
    }
    if (w === 'avenida' && next !== undefined) {
      // "avenida carrera 27" es la carrera 27; si no, el nombre va hasta la próxima calle.
      if (STREET_TYPES.has(next)) {
        i++;
        continue;
      }
      const parts = ['avenida'];
      i++;
      while (i < words.length && !STREET_TYPES.has(words[i])) {
        parts.push(words[i]);
        i++;
      }
      streets.push(keyOf(parts));
      continue;
    }
    rest.push(w);
    i++;
  }
  return { streets, rest: rest.join(' ') };
}

/** Claves con las que se reconoce una calle de OpenStreetMap. */
function streetKeys(osmName: string): string[] {
  const { streets } = parseStreets(osmName);
  const keys = new Set(streets);
  const words = tokens(osmName);
  // "Panamericana" sola se reconoce como "Avenida Panamericana".
  if (words[0] === 'avenida') keys.add(keyOf(words));
  return [...keys];
}

type Point = [number, number];

/** Proyección local en metros (suficiente para una ciudad cerca del ecuador). */
function project([lat, lon]: Point, lat0: number): Point {
  const R = 6_371_000;
  const rad = Math.PI / 180;
  return [lat * rad * R, lon * rad * R * Math.cos(lat0 * rad)];
}

function unproject([y, x]: Point, lat0: number): Point {
  const R = 6_371_000;
  const rad = Math.PI / 180;
  return [y / R / rad, x / (R * Math.cos(lat0 * rad)) / rad];
}

/** Punto más cercano de un segmento AB a P (en metros). */
function closestOnSegment(p: Point, a: Point, b: Point): Point {
  const dy = b[0] - a[0];
  const dx = b[1] - a[1];
  const len2 = dy * dy + dx * dx;
  const t =
    len2 === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dy + (p[1] - a[1]) * dx) / len2));
  return [a[0] + t * dy, a[1] + t * dx];
}

/** Punto donde se cruzan los segmentos AB y CD, si se cruzan. */
function segmentIntersection(a: Point, b: Point, c: Point, d: Point): Point | null {
  const r: Point = [b[0] - a[0], b[1] - a[1]];
  const s: Point = [d[0] - c[0], d[1] - c[1]];
  const denom = r[0] * s[1] - r[1] * s[0];
  if (denom === 0) return null; // paralelos
  const t = ((c[0] - a[0]) * s[1] - (c[1] - a[1]) * s[0]) / denom;
  const u = ((c[0] - a[0]) * r[1] - (c[1] - a[1]) * r[0]) / denom;
  if (t < 0 || t > 1 || u < 0 || u > 1) return null;
  return [a[0] + t * r[0], a[1] + t * r[1]];
}

const dist2 = (a: Point, b: Point) => (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2;

/** Distancia máxima entre dos calles para considerar que se cruzan. */
const INTERSECTION_METERS = 40;
/** Distancia máxima de un lugar a la calle mencionada junto a él. */
const PLACE_TO_STREET_METERS = 400;

class StreetIndex {
  private readonly byKey = new Map<string, OsmStreet[]>();

  constructor(streets: OsmStreet[]) {
    for (const street of streets) {
      for (const k of streetKeys(street.name)) add(this.byKey, k, street);
    }
  }

  get(k: string): OsmStreet[] {
    return this.byKey.get(k) ?? [];
  }

  /** Cruce de dos calles: punto medio del acercamiento mínimo entre sus trazados. */
  intersection(a: OsmStreet[], b: OsmStreet[]): { point: Point; ids: string[] } | null {
    const lat0 = a[0]?.points[0]?.[0] ?? 0;
    let best: { d2: number; point: Point; ids: string[] } | null = null;
    for (const sa of a) {
      const pa = sa.points.map((p) => project(p, lat0));
      for (const sb of b) {
        const pb = sb.points.map((p) => project(p, lat0));
        for (let i = 0; i + 1 < pa.length; i++) {
          for (let j = 0; j + 1 < pb.length; j++) {
            const crossing = segmentIntersection(pa[i], pa[i + 1], pb[j], pb[j + 1]);
            const candidates: [Point, Point][] = [
              ...(crossing ? [[crossing, crossing] as [Point, Point]] : []),
              [pa[i], closestOnSegment(pa[i], pb[j], pb[j + 1])],
              [pa[i + 1], closestOnSegment(pa[i + 1], pb[j], pb[j + 1])],
              [closestOnSegment(pb[j], pa[i], pa[i + 1]), pb[j]],
              [closestOnSegment(pb[j + 1], pa[i], pa[i + 1]), pb[j + 1]],
            ];
            for (const [p, q] of candidates) {
              const d2 = dist2(p, q);
              if (!best || d2 < best.d2) {
                best = { d2, point: [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2], ids: [sa.id, sb.id] };
              }
            }
          }
        }
      }
    }
    if (!best || best.d2 > INTERSECTION_METERS ** 2) return null;
    const [lat, lon] = unproject(best.point, lat0);
    return { point: [round(lat), round(lon)], ids: best.ids };
  }

  /** Punto de la calle más cercano a un lugar, si está a una distancia razonable. */
  snap(streets: OsmStreet[], place: Point): { point: Point; id: string } | null {
    const lat0 = place[0];
    const p = project(place, lat0);
    let best: { d2: number; point: Point; id: string } | null = null;
    for (const street of streets) {
      const pts = street.points.map((x) => project(x, lat0));
      for (let i = 0; i + 1 < pts.length; i++) {
        const q = closestOnSegment(p, pts[i], pts[i + 1]);
        const d2 = dist2(p, q);
        if (!best || d2 < best.d2) best = { d2, point: q, id: street.id };
      }
    }
    if (!best || best.d2 > PLACE_TO_STREET_METERS ** 2) return null;
    const [lat, lon] = unproject(best.point, lat0);
    return { point: [round(lat), round(lon)], id: best.id };
  }
}

// ─── Geocodificación ───────────────────────────────────────────────────────

/** Partes alternativas del nombre: "Udenar - Facultad de Veterinaria" → cada lado. */
function nameParts(name: string): string[] {
  const parts = name
    .split(/\s+-\s+|,/)
    .map((p) => p.trim())
    .filter(Boolean);
  return parts.length > 1 ? parts : [];
}

/**
 * Ubica las paradas. Con `routes` (el orden de paradas de cada sentido) descarta
 * las que quedan lejos de sus vecinas del recorrido: suelen ser homónimos
 * (otra "Villa María" en una vereda).
 */
export function geocodeStops(
  stops: StopToLocate[],
  snapshot: OsmSnapshot,
  routes: RouteStopRef[][] = [],
): GeocodeResult {
  const places = new PlaceIndex(snapshot.features);
  const streets = new StreetIndex(snapshot.streets);
  let located: LocatedStop[] = [];
  const notFound: NotFoundStop[] = [];

  for (const stop of stops) {
    const base = { external_id: stop.external_id, name: stop.name };
    const result = locate(stop, places, streets);
    if ('reason' in result) notFound.push({ ...base, reason: result.reason });
    else located.push({ ...base, ...result });
  }

  if (routes.length > 0) {
    const rejected = findRouteOutliers(located, routes);
    for (const stop of located) {
      const reason = rejected.get(stop);
      if (reason) notFound.push({ external_id: stop.external_id, name: stop.name, reason });
    }
    located = located.filter((s) => !rejected.has(s));
  }
  return { located, notFound };
}

/** Desvío máximo que puede causar una parada entre su anterior y su siguiente. */
const MAX_DETOUR_METERS = 2_000;
/** Distancia máxima a la única vecina ubicada (terminales), en ciudad y en zona rural. */
const MAX_SINGLE_NEIGHBOR_METERS = 3_000;
const MAX_SINGLE_NEIGHBOR_RURAL_METERS = 6_000;
/** Paradas sin ubicar que se saltan buscando una vecina. */
const MAX_NEIGHBOR_GAP = 3;

const isRural = (stop: LocatedStop) => /place=(village|hamlet)/.test(stop.matched);

const metersBetween = (a: LocatedStop, b: LocatedStop) =>
  calculateHaversineDistance(a.latitude, a.longitude, b.latitude, b.longitude);

/**
 * Paradas que no encajan en sus recorridos. En cada recorrido se mide cuánto
 * desvío causa la parada entre su vecina anterior y la siguiente (o, si solo hay
 * una, la distancia a ella). Es atípica si falla en la mayoría de sus recorridos.
 * Se descarta de a una, la peor primero, porque una parada mal ubicada también
 * hace ver mal a sus vecinas.
 */
function findRouteOutliers(
  located: LocatedStop[],
  routes: RouteStopRef[][],
): Map<LocatedStop, string> {
  const byId = new Map<string, LocatedStop>();
  const byName = new Map<string, LocatedStop>();
  for (const s of located) {
    if (s.external_id) byId.set(s.external_id, s);
    const k = normalizeForSearch(s.name);
    if (!byName.has(k) || !s.external_id) byName.set(k, s);
  }
  const resolve = (ref: RouteStopRef) =>
    (ref.stop_external_id ? byId.get(ref.stop_external_id) : undefined) ??
    byName.get(normalizeForSearch(ref.stop_name));
  const sequences = routes.map((route) => route.map(resolve));
  const removed = new Set<LocatedStop>();
  const rejected = new Map<LocatedStop, string>();

  // Sin recorrido no hay con qué verificarla, y el planificador no la usaría.
  const inRoutes = new Set(sequences.flat());
  for (const stop of located) {
    if (!inRoutes.has(stop))
      rejected.set(stop, 'no está en ningún recorrido; no se puede verificar');
  }

  const neighbor = (seq: (LocatedStop | undefined)[], i: number, dir: number) => {
    for (let step = 1; step <= MAX_NEIGHBOR_GAP + 1; step++) {
      const other = seq[i + dir * step];
      if (other === undefined || removed.has(other)) continue;
      return other === seq[i] ? undefined : other;
    }
    return undefined;
  };

  for (;;) {
    // Cuánto se pasa del límite cada parada en cada recorrido (> 0 = falla).
    const excess = new Map<LocatedStop, number[]>();
    for (const seq of sequences) {
      seq.forEach((stop, i) => {
        if (!stop || removed.has(stop)) return;
        const prev = neighbor(seq, i, -1);
        const next = neighbor(seq, i, 1);
        let value: number;
        if (prev && next) {
          value =
            metersBetween(prev, stop) +
            metersBetween(stop, next) -
            metersBetween(prev, next) -
            MAX_DETOUR_METERS;
        } else if (prev ?? next) {
          const limit = isRural(stop)
            ? MAX_SINGLE_NEIGHBOR_RURAL_METERS
            : MAX_SINGLE_NEIGHBOR_METERS;
          value = metersBetween(stop, (prev ?? next)!) - limit;
        } else {
          return; // sin vecinas ubicadas: no hay con qué comparar
        }
        const list = excess.get(stop);
        if (list) list.push(value);
        else excess.set(stop, [value]);
      });
    }
    let worst: { stop: LocatedStop; score: number } | null = null;
    for (const [stop, values] of excess) {
      const failures = values.filter((v) => v > 0).length;
      if (failures * 2 <= values.length) continue;
      const sorted = [...values].sort((a, b) => a - b);
      const score = sorted[Math.floor(sorted.length / 2)];
      if (!worst || score > worst.score) worst = { stop, score };
    }
    if (!worst) return rejected;
    removed.add(worst.stop);
    rejected.set(
      worst.stop,
      `${worst.stop.matched} queda lejos de las paradas vecinas del recorrido`,
    );
  }
}

type Located = Omit<LocatedStop, 'external_id' | 'name'>;

function locate(
  stop: StopToLocate,
  places: PlaceIndex,
  streets: StreetIndex,
): Located | { reason: string } {
  const { streets: streetKeys, rest } = parseStreets(stop.name);
  const found = streetKeys.map((k) => streets.get(k));

  // 1. Cruce de dos calles.
  if (streetKeys.length >= 2) {
    const [a, b] = [found[0], found[1]];
    if (a.length > 0 && b.length > 0) {
      const cross = streets.intersection(a, b);
      if (cross) {
        return {
          latitude: cross.point[0],
          longitude: cross.point[1],
          method: 'intersection',
          matched: `${a[0].name} con ${b[0].name}`,
          osm_ids: cross.ids,
        };
      }
    }
  }

  // "Vía La Laguna" es el camino hacia La Laguna, no el lugar.
  const firstWord = normalizeForSearch(stop.name).split(' ')[0];
  if (firstWord === 'via' || firstWord === 'variante') {
    return { reason: 'es un tramo de vía, no un lugar puntual' };
  }

  // 2. Lugar por nombre (nombre completo, alias, partes y nombre sin la calle).
  const candidates = [stop.name, ...stop.aliases, ...nameParts(stop.name)];
  if (streetKeys.length > 0 && rest.length > 0) candidates.push(rest);
  let ambiguous = false;
  for (const text of candidates) {
    const place = places.find(text);
    if (place === 'ambiguous') {
      ambiguous = true;
      continue;
    }
    if (!place) continue;
    // "Las Cuadras Calle 19": se ajusta el lugar a la calle mencionada.
    const street = found.find((s) => s.length > 0);
    const snapped =
      streetKeys.length === 1 && street ? streets.snap(street, [place.lat, place.lon]) : null;
    if (snapped) {
      return {
        latitude: snapped.point[0],
        longitude: snapped.point[1],
        method: 'place_on_street',
        matched: `${place.matched} sobre ${street![0].name}`,
        osm_ids: [...place.osmIds, snapped.id],
      };
    }
    return {
      latitude: place.lat,
      longitude: place.lon,
      method: 'place',
      matched: place.matched,
      osm_ids: place.osmIds,
    };
  }

  if (ambiguous) return { reason: 'el nombre coincide con varios lugares lejanos entre sí' };
  if (streetKeys.length >= 2)
    return { reason: 'no se encontró el cruce de esas calles en OpenStreetMap' };
  if (streetKeys.length === 1 && rest.length === 0) {
    return { reason: 'nombre genérico de una sola calle (no indica en qué punto)' };
  }
  return { reason: 'no aparece en OpenStreetMap' };
}
