/**
 * Paradas cercanas a una coordenada. Solo considera paradas que tienen
 * coordenadas en la base de datos.
 */
import type { NetworkStop, TransitNetwork } from '../repositories/transitRepository';
import { calculateHaversineDistance } from '../utils/geoUtils';

export interface GeoPoint {
  latitude: number;
  longitude: number;
}

export interface NearbyStop {
  stop: NetworkStop;
  distanceMeters: number;
}

export function findNearbyStops(
  network: TransitNetwork,
  point: GeoPoint,
  radiusMeters: number,
  maxResults: number,
): NearbyStop[] {
  const found: NearbyStop[] = [];
  for (const stop of network.stops.values()) {
    if (stop.latitude === null || stop.longitude === null) continue;
    const distanceMeters = calculateHaversineDistance(
      point.latitude,
      point.longitude,
      stop.latitude,
      stop.longitude,
    );
    if (distanceMeters <= radiusMeters) found.push({ stop, distanceMeters });
  }
  return found
    .sort((a, b) => a.distanceMeters - b.distanceMeters || a.stop.id - b.stop.id)
    .slice(0, maxResults);
}

/** Cuántas paradas no se pueden ubicar por falta de coordenadas. */
export function countStopsWithoutCoordinates(network: TransitNetwork): number {
  let count = 0;
  for (const stop of network.stops.values()) {
    if (stop.latitude === null || stop.longitude === null) count++;
  }
  return count;
}
