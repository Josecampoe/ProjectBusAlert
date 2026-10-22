import { beforeAll, describe, expect, it } from 'vitest';
import type { TransitNetwork } from '../repositories/transitRepository';
import { createTestContext } from '../test/helpers';
import { planTrip } from './planner';

const OPTIONS = { maxAlternatives: 5, minutesPerStop: 1.5 };
let network: TransitNetwork;
const id = (name: string) => [...network.stops.values()].find((s) => s.name === name)!.id;

beforeAll(() => {
  network = createTestContext().repo.getNetwork();
});

describe('planTrip (directo)', () => {
  it('encuentra una ruta directa con subida, bajada y paradas intermedias', () => {
    const result = planTrip(network, [id('Udenar Torobajo')], [id('Parque Nariño')], OPTIONS);
    expect(result.status).toBe('ok');
    const [leg] = result.options[0].legs;
    expect(leg.routeCode).toBe('FX1');
    expect(leg.board.name).toBe('Udenar Torobajo');
    expect(leg.alight.name).toBe('Parque Nariño');
    expect(leg.stopsCount).toBe(3);
    expect(leg.stops.map((s) => s.name)).toEqual([
      'Udenar Torobajo',
      'Universidad Cooperativa',
      'Parque Bolívar',
      'Parque Nariño',
    ]);
    expect(result.options[0].isDemo).toBe(true);
  });

  it('no ofrece el sentido contrario', () => {
    const result = planTrip(network, [id('Parque Nariño')], [id('Udenar Torobajo')], OPTIONS);
    expect(result.status).toBe('no_route');
    expect(result.options).toEqual([]);
  });

  it('detecta origen igual a destino', () => {
    const stop = id('Parque Bolívar');
    expect(planTrip(network, [stop], [stop], OPTIONS).status).toBe('same_place');
  });

  it('devuelve sin ruta cuando la parada no está en ningún recorrido', () => {
    expect(planTrip(network, [id('Barrio Sin Rutas')], [id('Parque Nariño')], OPTIONS).status).toBe(
      'no_route',
    );
  });
});
