/**
 * Planificador de viajes sobre la red en memoria.
 * Busca recorridos directos entre cualquier parada candidata de origen y
 * cualquier parada candidata de destino, y los ordena por costo.
 */
import type { NetworkDirection, NetworkStop, TransitNetwork } from '../repositories/transitRepository';

export interface PlannerOptions {
    maxAlternatives: number;
    minutesPerStop: number;
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
    directionKey: string;
    directionName: string;
    board: StopRef;
    alight: StopRef;
    stopsCount: number;
    /** Paradas intermedias en orden, incluidas subida y bajada. */
    stops: StopRef[];
}

export interface PlanOption {
    legs: PlanLeg[];
    transfers: number;
    totalStops: number;
    /** Costo relativo en minutos para ordenar alternativas (no es un tiempo real). */
    cost: number;
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

function buildLeg(network: TransitNetwork, dir: NetworkDirection, fromIndex: number, toIndex: number): PlanLeg {
    const route = network.routes.get(dir.routeId)!;
    const segment = dir.stopIds.slice(fromIndex, toIndex + 1).map((id) => toStopRef(network.stops.get(id)!));
    return {
        routeCode: route.code,
        routeName: route.name,
        directionKey: dir.key,
        directionName: dir.name,
        board: segment[0],
        alight: segment[segment.length - 1],
        stopsCount: toIndex - fromIndex,
        stops: segment,
    };
}

function legIsDemo(network: TransitNetwork, dir: NetworkDirection, stopIds: number[]): boolean {
    return network.routes.get(dir.routeId)!.isDemo || stopIds.some((id) => network.stops.get(id)!.isDemo);
}

/** Todos los tramos directos posibles dentro de una dirección (sube en `from`, baja en `to`). */
function directLegs(
    network: TransitNetwork,
    fromIds: Set<number>,
    toIds: Set<number>,
): Array<{ dir: NetworkDirection; fromIndex: number; toIndex: number }> {
    const found: Array<{ dir: NetworkDirection; fromIndex: number; toIndex: number }> = [];
    const candidateDirs = new Set<NetworkDirection>();
    for (const id of fromIds) for (const d of network.directionsByStop.get(id) ?? []) candidateDirs.add(d);

    for (const dir of candidateDirs) {
        let best: { fromIndex: number; toIndex: number } | undefined;
        dir.stopIds.forEach((stopId, i) => {
            if (!fromIds.has(stopId)) return;
            for (let j = i + 1; j < dir.stopIds.length; j++) {
                if (toIds.has(dir.stopIds[j])) {
                    if (!best || j - i < best.toIndex - best.fromIndex) best = { fromIndex: i, toIndex: j };
                    break;
                }
            }
        });
        if (best) found.push({ dir, ...best });
    }
    return found;
}

export function planTrip(
    network: TransitNetwork,
    originIds: number[],
    destinationIds: number[],
    options: PlannerOptions,
): PlanResult {
    const origins = new Set(originIds);
    const destinations = new Set(destinationIds);
    if (originIds.some((id) => destinations.has(id))) return { status: 'same_place', options: [] };

    const plans: PlanOption[] = directLegs(network, origins, destinations).map(({ dir, fromIndex, toIndex }) => {
        const leg = buildLeg(network, dir, fromIndex, toIndex);
        return {
            legs: [leg],
            transfers: 0,
            totalStops: leg.stopsCount,
            cost: leg.stopsCount * options.minutesPerStop,
            isDemo: legIsDemo(network, dir, dir.stopIds.slice(fromIndex, toIndex + 1)),
        };
    });

    plans.sort((a, b) => a.cost - b.cost || a.legs[0].routeCode.localeCompare(b.legs[0].routeCode));
    const top = plans.slice(0, options.maxAlternatives);
    return { status: top.length > 0 ? 'ok' : 'no_route', options: top };
}
