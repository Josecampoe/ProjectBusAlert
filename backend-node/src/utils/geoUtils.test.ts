import { calculateHaversineDistance, isWithinRadius } from './geoUtils';

describe('GeoUtils Haversine Tests', () => {
  it('debe calcular la distancia en metros entre dos puntos conocidos', () => {
    const dist = calculateHaversineDistance(7.119349, -73.122741, 7.1215, -73.124);
    expect(dist).toBeGreaterThan(0);
    expect(dist).toBeLessThan(1000);
  });

  it('debe validar si un punto se encuentra dentro del radio especificado', () => {
    const inside = isWithinRadius(7.119349, -73.122741, 7.1194, -73.1228, 500);
    expect(inside).toBe(true);
  });
});
