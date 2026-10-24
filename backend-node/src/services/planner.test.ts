import { beforeAll, describe, expect, it } from 'vitest';
import type {
  NetworkDirection,
  NetworkStop,
  TransitNetwork,
} from '../repositories/transitRepository';
import { createTestContext, FIXED_NOW } from '../test/helpers';
import { createArrivalEstimator } from './arrivalTime';
import { planTrip, type PlannerOptions } from './planner';

let network: TransitNetwork;
let arrival: ReturnType<typeof createArrivalEstimator>;
const id = (name: string) => [...network.stops.values()].find((s) => s.name === name)!.id;

const OPTIONS: PlannerOptions = {
  maxAlternatives: 5,
  maxTransfers: 1,
  transferPenaltyMin: 8,
  transferWalkRadiusM: 150,
  preferDirect: false,
};
const plan = (from: string, to: string, options: Partial<PlannerOptions> = {}) =>
  planTrip(network, [id(from)], [id(to)], { ...OPTIONS, ...options }, arrival, FIXED_NOW);

beforeAll(() => {
  const ctx = createTestContext();
  network = ctx.repo.getNetwork();
  arrival = createArrivalEstimator(ctx.config);
});

describe('planTrip — directo', () => {
  it('encuentra la ruta directa con subida, bajada y paradas intermedias', () => {
    const result = plan('Udenar Torobajo', 'Parque Nariño');
    expect(result.status).toBe('ok');
    const [option] = result.options;
    expect(option.transfers).toBe(0);
    const [leg] = option.legs;
    expect(leg).toMatchObject({
      routeCode: 'FX1',
      routeColor: '#1565C0',
      directionKey: 'FX1_D1',
      stopsCount: 3,
      board: { name: 'Udenar Torobajo' },
      alight: { name: 'Parque Nariño' },
    });
    expect(leg.stops.map((s) => s.name)).toEqual([
      'Udenar Torobajo',
      'Universidad Cooperativa',
      'Parque Bolívar',
      'Parque Nariño',
    ]);
    expect(option.isDemo).toBe(true);
    expect(option.isEstimate).toBe(true);
  });

  it('usa el sentido correcto de la ruta', () => {
    expect(plan('Parque Nariño', 'Udenar Torobajo').options[0].legs[0].directionKey).toBe('FX1_D2');
  });

  it('detecta origen igual a destino', () => {
    expect(plan('Parque Bolívar', 'Parque Bolívar').status).toBe('same_place');
  });

  it('devuelve sin ruta cuando la parada no está en ningún recorrido', () => {
    expect(plan('Barrio Sin Rutas', 'Parque Nariño').status).toBe('no_route');
  });
});

describe('planTrip — transbordos', () => {
  it('combina dos rutas en la misma parada', () => {
    const result = plan('Udenar Torobajo', 'Terminal de Transportes');
    expect(result.status).toBe('ok');
    const [option] = result.options;
    expect(option.transfers).toBe(1);
    expect(option.legs.map((l) => l.routeCode)).toEqual(['FX1', 'FX2']);
    expect(option.transferSteps[0]).toMatchObject({
      from: { name: 'Parque Nariño' },
      to: { name: 'Parque Nariño' },
      walkMeters: 0,
      walkMinutes: 0,
    });
    expect(option.totalStops).toBe(5);
  });

  it('permite caminar a una parada cercana dentro del radio', () => {
    const [option] = plan('Udenar Torobajo', 'Barrio Obrero').options;
    expect(option.legs.map((l) => l.routeCode)).toEqual(['FX1', 'FX3']);
    expect(option.transferSteps[0]).toMatchObject({
      from: { name: 'Parque Nariño' },
      to: { name: 'Plazuela San Andrés' },
    });
    expect(option.transferSteps[0].walkMeters).toBeGreaterThan(90);
    expect(option.transferSteps[0].walkMinutes).toBeGreaterThan(0);
  });

  it('no camina más allá del radio configurado', () => {
    expect(plan('Udenar Torobajo', 'Barrio Obrero', { transferWalkRadiusM: 50 }).status).toBe(
      'no_route',
    );
  });

  it('con PLANNER_MAX_TRANSFERS=0 solo devuelve directos', () => {
    expect(plan('Udenar Torobajo', 'Terminal de Transportes', { maxTransfers: 0 }).status).toBe(
      'no_route',
    );
  });

  it('ordena por costo e incluye la penalización por transbordo', () => {
    const [option] = plan('Udenar Torobajo', 'Terminal de Transportes').options;
    expect(option.cost).toBe(option.estimatedMinutes + OPTIONS.transferPenaltyMin);
    const direct = plan('Udenar Torobajo', 'Parque Nariño').options[0];
    expect(direct.cost).toBe(direct.estimatedMinutes);
  });

  it('respeta el máximo de alternativas', () => {
    expect(
      plan('Udenar Torobajo', 'Terminal de Transportes', { maxAlternatives: 1 }).options,
    ).toHaveLength(1);
  });
});

// ─── Redes mínimas sin coordenadas ──────────────────────────────────────────

const stop = (id: number, name: string): NetworkStop => ({
  id,
  externalId: null,
  name,
  neighborhood: null,
  address: null,
  latitude: null,
  longitude: null,
  verified: true,
  isDemo: false,
  searchNames: [name.toLowerCase()],
});

/** Crea una red con las paradas dadas y una dirección por ruta: [routeId, stopIds]. */
function buildNetwork(
  names: Record<number, string>,
  routes: Array<[number, number[]]>,
): TransitNetwork {
  const directions: NetworkDirection[] = routes.map(([routeId, stopIds]) => ({
    id: routeId,
    key: `R${String(routeId)}_D1`,
    name: `R${String(routeId)}`,
    routeId,
    stopIds,
  }));
  const directionsByStop = new Map<number, NetworkDirection[]>();
  for (const d of directions) {
    for (const stopId of d.stopIds) {
      directionsByStop.set(stopId, [...(directionsByStop.get(stopId) ?? []), d]);
    }
  }
  return {
    routes: new Map(
      routes.map(([routeId]) => [
        routeId,
        {
          id: routeId,
          code: `R${String(routeId)}`,
          name: `R${String(routeId)}`,
          color: null,
          isDemo: false,
          schedules: [],
        },
      ]),
    ),
    directions,
    stops: new Map(
      Object.entries(names).map(([key, name]) => [Number(key), stop(Number(key), name)]),
    ),
    directionsByStop,
  };
}

describe('planTrip — paradas con el mismo nombre', () => {
  // R1 llega a "Cruce" (id 2) y R2 sale de otra parada "Cruce" (id 3), al otro lado de la calle.
  const twinNetwork = () =>
    buildNetwork({ 1: 'Origen', 2: 'Cruce', 3: 'Cruce', 4: 'Destino' }, [
      [1, [1, 2]],
      [2, [3, 4]],
    ]);

  it('transborda entre paradas homónimas sin coordenadas y sin caminar', () => {
    const result = planTrip(twinNetwork(), [1], [4], OPTIONS, arrival, FIXED_NOW);
    expect(result.status).toBe('ok');
    const [option] = result.options;
    expect(option.legs.map((l) => l.routeCode)).toEqual(['R1', 'R2']);
    expect(option.transferSteps[0]).toMatchObject({
      from: { id: 2, name: 'Cruce' },
      to: { id: 3, name: 'Cruce' },
      walkMeters: 0,
    });
  });

  it('llega a la homónima del destino pedido (el otro lado de la calle)', () => {
    const result = planTrip(twinNetwork(), [1], [3], OPTIONS, arrival, FIXED_NOW);
    expect(result.status).toBe('ok');
    expect(result.options[0].legs[0]).toMatchObject({ routeCode: 'R1', alight: { id: 2 } });
  });

  it('origen y destino homónimos son el mismo lugar', () => {
    expect(planTrip(twinNetwork(), [2], [3], OPTIONS, arrival, FIXED_NOW).status).toBe(
      'same_place',
    );
  });

  it('con PLANNER_MAX_TRANSFERS=0 no las combina', () => {
    const result = planTrip(
      twinNetwork(),
      [1],
      [4],
      { ...OPTIONS, maxTransfers: 0 },
      arrival,
      FIXED_NOW,
    );
    expect(result.status).toBe('no_route');
  });
});

describe('planTrip — preferir directas', () => {
  // R1 va directo de 1 a 20 con muchas paradas; R2 + R3 llegan con menos paradas.
  const network2 = () => {
    const long = Array.from({ length: 20 }, (_, i) => i + 1); // 1 … 20
    const names = Object.fromEntries([...long, 99].map((id) => [id, `P${String(id)}`]));
    return buildNetwork(names, [
      [1, long],
      [2, [1, 99]],
      [3, [99, 20]],
    ]);
  };

  it('sin preferirlas, gana la de menor costo (con transbordo)', () => {
    const [first] = planTrip(network2(), [1], [20], OPTIONS, arrival, FIXED_NOW).options;
    expect(first.transfers).toBe(1);
  });

  it('con PLANNER_PREFER_DIRECT la directa va primero y el transbordo queda como alternativa', () => {
    const { options } = planTrip(
      network2(),
      [1],
      [20],
      { ...OPTIONS, preferDirect: true },
      arrival,
      FIXED_NOW,
    );
    expect(options.map((o) => o.transfers)).toEqual([0, 1]);
    expect(options[0].legs[0].routeCode).toBe('R1');
  });
});
