/**
 * Barrio en el que está el usuario, a partir de su ubicación GPS.
 *
 * Usa los barrios (place=neighbourhood, suburb, quarter…) de la foto de
 * OpenStreetMap en `data/raw/osm_pasto.json`. OSM guarda cada barrio como un
 * punto (su centro), así que se elige el centro más cercano dentro de
 * [NeighborhoodIndex.maxDistanceMeters]. Más lejos de eso no se nombra ningún
 * barrio: es mejor no decir nada que decir uno equivocado.
 */
import fs from 'node:fs';
import { calculateHaversineDistance } from '../utils/geoUtils';
import type { GeoPoint } from './nearby';

/** Tipos de OSM que cuentan como barrio o sector. */
const NEIGHBORHOOD_KINDS = new Set([
  'place=neighbourhood',
  'place=suburb',
  'place=quarter',
  'place=village',
  'place=hamlet',
]);

/** Distancia máxima por defecto al centro del barrio. */
export const DEFAULT_NEIGHBORHOOD_MAX_DISTANCE_M = 800;

export interface Neighborhood {
  name: string;
  latitude: number;
  longitude: number;
}

interface SnapshotFeature {
  names?: unknown;
  kind?: unknown;
  lat?: unknown;
  lon?: unknown;
}

export class NeighborhoodIndex {
  static readonly EMPTY = new NeighborhoodIndex([]);

  constructor(
    readonly neighborhoods: readonly Neighborhood[],
    readonly maxDistanceMeters: number = DEFAULT_NEIGHBORHOOD_MAX_DISTANCE_M,
  ) {}

  /** Nombre del barrio más cercano a [point], o null si ninguno está cerca. */
  nameAt(point: GeoPoint): string | null {
    let best: { name: string; distance: number } | null = null;
    for (const n of this.neighborhoods) {
      const distance = calculateHaversineDistance(
        point.latitude,
        point.longitude,
        n.latitude,
        n.longitude,
      );
      if (distance <= this.maxDistanceMeters && (best === null || distance < best.distance)) {
        best = { name: n.name, distance };
      }
    }
    return best?.name ?? null;
  }

  /** Lee los barrios de la foto de OSM. Si el archivo no existe o no es válido, queda vacío. */
  static fromSnapshotFile(file: string, maxDistanceMeters?: number): NeighborhoodIndex {
    let raw: string;
    try {
      raw = fs.readFileSync(file, 'utf8');
    } catch {
      return NeighborhoodIndex.EMPTY;
    }
    let features: unknown;
    try {
      features = (JSON.parse(raw) as { features?: unknown }).features;
    } catch {
      return NeighborhoodIndex.EMPTY;
    }
    return new NeighborhoodIndex(parseNeighborhoods(features), maxDistanceMeters);
  }
}

export function parseNeighborhoods(features: unknown): Neighborhood[] {
  if (!Array.isArray(features)) return [];
  const result: Neighborhood[] = [];
  for (const f of features as SnapshotFeature[]) {
    if (typeof f.kind !== 'string' || !NEIGHBORHOOD_KINDS.has(f.kind)) continue;
    if (typeof f.lat !== 'number' || typeof f.lon !== 'number') continue;
    const names: unknown[] = Array.isArray(f.names) ? (f.names as unknown[]) : [];
    const name = names.find((n): n is string => typeof n === 'string' && n.trim() !== '');
    if (name === undefined) continue;
    result.push({ name: name.trim(), latitude: f.lat, longitude: f.lon });
  }
  return result;
}
