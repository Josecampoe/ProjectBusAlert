/**
 * Planificador de viajes sobre la red en memoria.
 *
 * - Viajes directos: una dirección de ruta que pasa por origen y luego por destino.
 * - Viajes con un transbordo (si PLANNER_MAX_TRANSFERS ≥ 1): se baja de la
 *   ruta A en una parada y se sube a otra ruta B en la misma parada o en una
 *   parada cercana (a pie, dentro de PLANNER_TRANSFER_WALK_RADIUS_M; requiere
 *   coordenadas).
 *
 * Las alternativas se ordenan por costo = minutos estimados + penalización
 * por transbordo. Los minutos son ESTIMACIONES (ver arrivalTime.ts).
 */
import type {
  NetworkDirection,
  NetworkStop,
  TransitNetwork,
} from '../repositories/transitRepository';
import { calculateHaversineDistance } from '../utils/geoUtils';
import type { ArrivalEstimate, ArrivalTimeProvider } from './arrivalTime';

export interface PlannerOptions {
  maxAlternatives: number;
  maxTransfers: number;
  transferPenaltyMin: number;
  transferWalkRadiusM: number;
}

export interface StopRef {
  id: number;
  externalId: string | null;
  name: string;
  latitude: number | null;
  longitude: number | null;
}

export interface PlanLeg {
  routeCode: string;
  routeName: string;
  routeColor: string | null;
  directionKey: string;
  directionName: string;
  board: StopRef;
  alight: StopRef;
  stopsCount: number;
  /** Paradas del tramo en orden, incluidas subida y bajada. */
  stops: StopRef[];
  estimate: ArrivalEstimate;
}

export interface TransferStep {
  from: StopRef;
  to: StopRef;
  walkMeters: number;
  walkMinutes: number;
}

export interface PlanOption {
  legs: PlanLeg[];
  transfers: number;
  transferSteps: TransferStep[];
  totalStops: number;
  /** Suma estimada de espera + viaje + caminata, en minutos. */
  estimatedMinutes: number;
  /** Valor para ordenar (minutos + penalización por transbordo). */
  cost: number;
  isEstimate: true;
  isDemo: boolean;
}

export type PlanStatus = 'ok' | 'same_place' | 'no_route';

export interface PlanResult {
  status: PlanStatus;
  options: PlanOption[];
}

export function toStopRef(stop: NetworkStop): StopRef {
  return {
    id: stop.id,
    externalId: stop.externalId,
    name: stop.name,
    latitude: stop.latitude,
    longitude: stop.longitude,
  };
}

interface Segment {
  dir: NetworkDirection;
  fromIndex: number;
  toIndex: number;
}

class PlanBuilder {
  constructor(
    private readonly network: TransitNetwork,
    private readonly arrival: ArrivalTimeProvider,
    private readonly at: Date,
  ) {}

  leg({ dir, fromIndex, toIndex }: Segment): PlanLeg {
    const route = this.network.routes.get(dir.routeId)!;
    const stops = dir.stopIds
      .slice(fromIndex, toIndex + 1)
      .map((id) => toStopRef(this.network.stops.get(id)!));
    return {
      routeCode: route.code,
      routeName: route.name,
      routeColor: route.color,
      directionKey: dir.key,
      directionName: dir.name,
      board: stops[0],
      alight: stops[stops.length - 1],
      stopsCount: toIndex - fromIndex,
      stops,
      estimate: this.arrival.estimateLeg(route, stops, this.at),
    };
  }

  option(
    segments: Segment[],
    transferSteps: TransferStep[],
    penaltyPerTransfer: number,
  ): PlanOption {
    const legs = segments.map((s) => this.leg(s));
    const walk = transferSteps.reduce((sum, t) => sum + t.walkMinutes, 0);
    const minutes = legs.reduce((sum, l) => sum + l.estimate.totalMinutes, 0) + walk;
    const isDemo = segments.some(
      (s) =>
        this.network.routes.get(s.dir.routeId)!.isDemo ||
        s.dir.stopIds
          .slice(s.fromIndex, s.toIndex + 1)
          .some((id) => this.network.stops.get(id)!.isDemo),
    );
    return {
      legs,
      transfers: transferSteps.length,
      transferSteps,
      totalStops: legs.reduce((sum, l) => sum + l.stopsCount, 0),
      estimatedMinutes: minutes,
      cost: minutes + transferSteps.length * penaltyPerTransfer,
      isEstimate: true,
      isDemo,
    };
  }
}

/** Índice de la primera parada de `targets` después de `from` en la dirección. */
function firstIndexAfter(dir: NetworkDirection, from: number, targets: Set<number>): number {
  for (let j = from + 1; j < dir.stopIds.length; j++) {
    if (targets.has(dir.stopIds[j])) return j;
  }
  return -1;
}

/** Paradas a las que se puede caminar desde cada parada (incluida ella misma). */
function walkableStops(
  network: TransitNetwork,
  radiusM: number,
): Map<number, Array<{ id: number; meters: number }>> {
  const result = new Map<number, Array<{ id: number; meters: number }>>();
  for (const stop of network.stops.values()) result.set(stop.id, [{ id: stop.id, meters: 0 }]);
  if (radiusM <= 0) return result;
  const located = [...network.stops.values()].filter(
    (s) => s.latitude !== null && s.longitude !== null,
  );
  for (const a of located) {
    for (const b of located) {
      if (a.id === b.id) continue;
      const meters = calculateHaversineDistance(
        a.latitude!,
        a.longitude!,
        b.latitude!,
        b.longitude!,
      );
      if (meters <= radiusM) result.get(a.id)!.push({ id: b.id, meters });
    }
  }
  return result;
}

export function planTrip(
  network: TransitNetwork,
  originIds: number[],
  destinationIds: number[],
  options: PlannerOptions,
  arrival: ArrivalTimeProvider,
  at: Date = new Date(),
): PlanResult {
  const origins = new Set(originIds);
  const destinations = new Set(destinationIds);
  if (originIds.some((id) => destinations.has(id))) return { status: 'same_place', options: [] };

  const builder = new PlanBuilder(network, arrival, at);
  const plans: PlanOption[] = [];
  const directReach = new Set<number>();

  const originDirs = new Set<NetworkDirection>();
  for (const id of origins)
    for (const d of network.directionsByStop.get(id) ?? []) originDirs.add(d);

  // ── Directos: el tramo más corto por dirección ─────────────────────────
  for (const dir of originDirs) {
    let best: Segment | undefined;
    dir.stopIds.forEach((stopId, i) => {
      if (!origins.has(stopId)) return;
      const j = firstIndexAfter(dir, i, destinations);
      if (j >= 0 && (!best || j - i < best.toIndex - best.fromIndex)) {
        best = { dir, fromIndex: i, toIndex: j };
      }
    });
    if (best) {
      directReach.add(dir.id);
      plans.push(builder.option([best], [], options.transferPenaltyMin));
    }
  }

  // ── Un transbordo ──────────────────────────────────────────────────────
  if (options.maxTransfers >= 1) {
    const walkable = walkableStops(network, options.transferWalkRadiusM);
    const bestByPair = new Map<string, PlanOption>();

    for (const dirA of originDirs) {
      if (directReach.has(dirA.id)) continue; // ya llega sin transbordo
      dirA.stopIds.forEach((originId, i) => {
        if (!origins.has(originId)) return;
        for (let k = i + 1; k < dirA.stopIds.length; k++) {
          const alightId = dirA.stopIds[k];
          for (const { id: boardId, meters } of walkable.get(alightId) ?? []) {
            for (const dirB of network.directionsByStop.get(boardId) ?? []) {
              if (dirB.routeId === dirA.routeId) continue;
              const j = dirB.stopIds.indexOf(boardId);
              const m = firstIndexAfter(dirB, j, destinations);
              if (m < 0) continue;
              const step: TransferStep = {
                from: toStopRef(network.stops.get(alightId)!),
                to: toStopRef(network.stops.get(boardId)!),
                walkMeters: Math.round(meters),
                walkMinutes: meters > 0 ? arrival.walkMinutes(meters) : 0,
              };
              const candidate = builder.option(
                [
                  { dir: dirA, fromIndex: i, toIndex: k },
                  { dir: dirB, fromIndex: j, toIndex: m },
                ],
                [step],
                options.transferPenaltyMin,
              );
              const key = `${String(dirA.id)}>${String(dirB.id)}`;
              const current = bestByPair.get(key);
              if (!current || candidate.cost < current.cost) bestByPair.set(key, candidate);
            }
          }
        }
      });
    }
    plans.push(...bestByPair.values());
  }

  plans.sort(
    (a, b) =>
      a.cost - b.cost ||
      a.transfers - b.transfers ||
      a.legs[0].routeCode.localeCompare(b.legs[0].routeCode),
  );
  const top = plans.slice(0, options.maxAlternatives);
  return { status: top.length > 0 ? 'ok' : 'no_route', options: top };
}
