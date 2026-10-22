/**
 * Estimación de tiempos de viaje.
 *
 * No hay una fuente de tiempo real disponible, así que el cálculo es
 * determinista y SIEMPRE se marca como estimación:
 *   - Viaje: distancia entre paradas / velocidad media (si todas tienen
 *     coordenadas) o número de paradas × minutos por parada.
 *   - Espera: la mitad de la frecuencia (llegada aleatoria del usuario),
 *     si la ruta tiene frecuencia cargada para el tipo de día actual.
 *
 * Para conectar tiempo real en el futuro basta con otra implementación
 * de `ArrivalTimeProvider` (por ejemplo, una que consulte la API del
 * operador) y pasarla al AssistantService.
 */
import type { AppConfig } from '../config/env';
import type { NetworkRoute } from '../repositories/transitRepository';
import { calculateHaversineDistance } from '../utils/geoUtils';
import type { StopRef } from './planner';

export type RideBasis = 'distance' | 'stop_count';
export type HeadwaySource = 'schedule' | 'default' | 'none';

export interface ArrivalEstimate {
  rideMinutes: number;
  /** Espera media en la parada; null si no hay frecuencia conocida. */
  waitMinutes: number | null;
  totalMinutes: number;
  basis: RideBasis;
  headwaySource: HeadwaySource;
  /** true/false si hay horario cargado para hoy; null si se desconoce. */
  withinServiceHours: boolean | null;
  isEstimate: true;
}

export interface ArrivalTimeProvider {
  estimateLeg(route: NetworkRoute, stops: StopRef[], at: Date): ArrivalEstimate;
  /** Minutos caminando entre dos paradas (para transbordos a pie). */
  walkMinutes(meters: number): number;
}

export interface ScheduleEstimatorOptions {
  averageBusSpeedKmh: number;
  minutesPerStop: number;
  defaultHeadwayMin: number;
  walkSpeedKmh: number;
  timeZone: string;
}

/** Espera media de quien llega a la parada sin mirar el horario. */
export const AVERAGE_WAIT_FRACTION = 0.5;
const METERS_PER_KM = 1000;
const MINUTES_PER_HOUR = 60;

/** Tipos de día del dataset que aplican a cada día de la semana (0 = domingo). */
const DAY_TYPES_BY_WEEKDAY: Record<number, readonly string[]> = {
  0: ['DF', 'LD'],
  1: ['LV', 'LS', 'LD'],
  2: ['LV', 'LS', 'LD'],
  3: ['LV', 'LS', 'LD'],
  4: ['LV', 'LS', 'LD'],
  5: ['LV', 'LS', 'LD'],
  6: ['S', 'LS', 'LD'],
};
const WEEKDAY_INDEX: Record<string, number> = {
  Sun: 0,
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
};

function metersPerMinute(kmh: number): number {
  return (kmh * METERS_PER_KM) / MINUTES_PER_HOUR;
}

/** Día de la semana y hora "HH:MM" en la zona horaria del sistema de transporte. */
export function localDayAndTime(at: Date, timeZone: string): { weekday: number; time: string } {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(at);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return { weekday: WEEKDAY_INDEX[get('weekday')] ?? 0, time: `${get('hour')}:${get('minute')}` };
}

export function pathLengthMeters(stops: StopRef[]): number | null {
  let total = 0;
  for (let i = 1; i < stops.length; i++) {
    const a = stops[i - 1];
    const b = stops[i];
    if (
      a.latitude === null ||
      a.longitude === null ||
      b.latitude === null ||
      b.longitude === null
    ) {
      return null;
    }
    total += calculateHaversineDistance(a.latitude, a.longitude, b.latitude, b.longitude);
  }
  return total;
}

export class ScheduleBasedArrivalEstimator implements ArrivalTimeProvider {
  constructor(private readonly options: ScheduleEstimatorOptions) {}

  estimateLeg(route: NetworkRoute, stops: StopRef[], at: Date): ArrivalEstimate {
    const meters = pathLengthMeters(stops);
    const stopsCount = Math.max(stops.length - 1, 0);
    const rideMinutes =
      meters !== null
        ? meters / metersPerMinute(this.options.averageBusSpeedKmh)
        : stopsCount * this.options.minutesPerStop;

    const { weekday, time } = localDayAndTime(at, this.options.timeZone);
    const todayTypes = DAY_TYPES_BY_WEEKDAY[weekday];
    const schedule = route.schedules.find((s) => todayTypes.includes(s.dayType));

    let waitMinutes: number | null = null;
    let headwaySource: HeadwaySource = 'none';
    if (schedule?.frequencyMinutes) {
      waitMinutes = schedule.frequencyMinutes * AVERAGE_WAIT_FRACTION;
      headwaySource = 'schedule';
    } else if (this.options.defaultHeadwayMin > 0) {
      waitMinutes = this.options.defaultHeadwayMin * AVERAGE_WAIT_FRACTION;
      headwaySource = 'default';
    }

    const withinServiceHours =
      schedule?.startTime && schedule.endTime
        ? time >= schedule.startTime && time <= schedule.endTime
        : null;

    const ride = Math.ceil(rideMinutes);
    const wait = waitMinutes === null ? null : Math.ceil(waitMinutes);
    return {
      rideMinutes: ride,
      waitMinutes: wait,
      totalMinutes: ride + (wait ?? 0),
      basis: meters !== null ? 'distance' : 'stop_count',
      headwaySource,
      withinServiceHours,
      isEstimate: true,
    };
  }

  walkMinutes(meters: number): number {
    return Math.ceil(meters / metersPerMinute(this.options.walkSpeedKmh));
  }
}

export function createArrivalEstimator(config: AppConfig): ArrivalTimeProvider {
  return new ScheduleBasedArrivalEstimator({
    averageBusSpeedKmh: config.eta.averageBusSpeedKmh,
    minutesPerStop: config.planner.minutesPerStop,
    defaultHeadwayMin: config.eta.defaultHeadwayMin,
    walkSpeedKmh: config.eta.walkSpeedKmh,
    timeZone: config.eta.timeZone,
  });
}
