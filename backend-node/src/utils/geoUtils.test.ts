import { describe, expect, it } from 'vitest';
import { calculateHaversineDistance, isWithinRadius } from './geoUtils';

// Puntos de referencia aproximados en San Juan de Pasto (solo para probar el cálculo).
const PARQUE_NARINO = { lat: 1.2141, lng: -77.2786 };
const UDENAR_TOROBAJO = { lat: 1.2335, lng: -77.2925 };

describe('calculateHaversineDistance', () => {
  it('es cero para el mismo punto', () => {
    expect(
      calculateHaversineDistance(
        PARQUE_NARINO.lat,
        PARQUE_NARINO.lng,
        PARQUE_NARINO.lat,
        PARQUE_NARINO.lng,
      ),
    ).toBe(0);
  });

  it('calcula la distancia entre el centro y Udenar (~2,6 km)', () => {
    const d = calculateHaversineDistance(
      PARQUE_NARINO.lat,
      PARQUE_NARINO.lng,
      UDENAR_TOROBAJO.lat,
      UDENAR_TOROBAJO.lng,
    );
    expect(d).toBeGreaterThan(2_500);
    expect(d).toBeLessThan(2_800);
  });

  it('es simétrica', () => {
    const a = calculateHaversineDistance(
      PARQUE_NARINO.lat,
      PARQUE_NARINO.lng,
      UDENAR_TOROBAJO.lat,
      UDENAR_TOROBAJO.lng,
    );
    const b = calculateHaversineDistance(
      UDENAR_TOROBAJO.lat,
      UDENAR_TOROBAJO.lng,
      PARQUE_NARINO.lat,
      PARQUE_NARINO.lng,
    );
    expect(a).toBeCloseTo(b, 6);
  });
});

describe('isWithinRadius', () => {
  it('respeta el radio indicado', () => {
    const args = [
      PARQUE_NARINO.lat,
      PARQUE_NARINO.lng,
      UDENAR_TOROBAJO.lat,
      UDENAR_TOROBAJO.lng,
    ] as const;
    expect(isWithinRadius(...args, 3_000)).toBe(true);
    expect(isWithinRadius(...args, 1_000)).toBe(false);
  });
});
