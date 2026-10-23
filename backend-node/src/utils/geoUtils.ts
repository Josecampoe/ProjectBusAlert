/**
 * Utilidades geoespaciales para el cálculo de distancias entre paradas.
 */

/** Radio medio de la Tierra en metros (IUGG). */
export const EARTH_RADIUS_METERS = 6_371_000;
const DEGREES_IN_HALF_TURN = 180;

const toRadians = (degrees: number): number => (degrees * Math.PI) / DEGREES_IN_HALF_TURN;

/** Distancia en metros entre dos puntos (fórmula de Haversine). */
export function calculateHaversineDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const φ1 = toRadians(lat1);
  const φ2 = toRadians(lat2);
  const Δφ = toRadians(lat2 - lat1);
  const Δλ = toRadians(lon2 - lon1);

  const a = Math.sin(Δφ / 2) ** 2 + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return EARTH_RADIUS_METERS * c;
}
