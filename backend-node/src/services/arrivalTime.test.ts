import { describe, expect, it } from 'vitest';
import type { NetworkRoute, RouteSchedule } from '../repositories/transitRepository';
import { localDayAndTime, pathLengthMeters, ScheduleBasedArrivalEstimator } from './arrivalTime';
import type { StopRef } from './planner';

const OPTIONS = {
  averageBusSpeedKmh: 18, // 300 m/min
  minutesPerStop: 1.5,
  defaultHeadwayMin: 0,
  walkSpeedKmh: 4.5, // 75 m/min
  timeZone: 'America/Bogota',
};
const THURSDAY_10AM = new Date('2026-10-22T15:00:00Z');
const SUNDAY_10AM = new Date('2026-10-25T15:00:00Z');
const THURSDAY_11PM = new Date('2026-10-23T04:00:00Z');

const stop = (id: number, lat: number | null, lng: number | null): StopRef => ({
  id,
  externalId: null,
  name: `P${String(id)}`,
  latitude: lat,
  longitude: lng,
});
const route = (schedules: RouteSchedule[] = []): NetworkRoute => ({
  id: 1,
  code: 'FX1',
  name: 'FX1',
  color: null,
  isDemo: true,
  schedules,
});
const weekdaySchedule: RouteSchedule = {
  dayType: 'LV',
  startTime: '05:00',
  endTime: '22:00',
  frequencyMinutes: 10,
  verified: false,
};

describe('localDayAndTime', () => {
  it('usa la zona horaria del sistema de transporte', () => {
    expect(localDayAndTime(THURSDAY_10AM, 'America/Bogota')).toEqual({
      weekday: 4,
      time: '10:00',
    });
    expect(localDayAndTime(THURSDAY_11PM, 'America/Bogota')).toEqual({
      weekday: 4,
      time: '23:00',
    });
  });
});

describe('pathLengthMeters', () => {
  it('suma tramos o devuelve null si falta alguna coordenada', () => {
    expect(pathLengthMeters([stop(1, 0, 0), stop(2, 0, 0.01)])).toBeCloseTo(1112, -1);
    expect(pathLengthMeters([stop(1, 0, 0), stop(2, null, null)])).toBeNull();
  });
});

describe('ScheduleBasedArrivalEstimator', () => {
  const estimator = new ScheduleBasedArrivalEstimator(OPTIONS);

  it('estima por distancia cuando hay coordenadas y suma media frecuencia de espera', () => {
    // ~3 km a 300 m/min = 10 min de viaje; frecuencia 10 → espera 5.
    const stops = [stop(1, 0, 0), stop(2, 0, 0.027)];
    const estimate = estimator.estimateLeg(route([weekdaySchedule]), stops, THURSDAY_10AM);
    expect(estimate).toEqual({
      rideMinutes: 11,
      waitMinutes: 5,
      totalMinutes: 16,
      basis: 'distance',
      headwaySource: 'schedule',
      withinServiceHours: true,
      isEstimate: true,
    });
  });

  it('estima por número de paradas cuando faltan coordenadas', () => {
    const stops = [stop(1, null, null), stop(2, null, null), stop(3, null, null)];
    const estimate = estimator.estimateLeg(route(), stops, THURSDAY_10AM);
    expect(estimate).toMatchObject({
      rideMinutes: 3,
      waitMinutes: null,
      basis: 'stop_count',
      headwaySource: 'none',
      withinServiceHours: null,
    });
  });

  it('solo usa el horario del tipo de día que corresponde', () => {
    const estimate = estimator.estimateLeg(
      route([weekdaySchedule]),
      [stop(1, null, null), stop(2, null, null)],
      SUNDAY_10AM,
    );
    expect(estimate.headwaySource).toBe('none');
    expect(estimate.withinServiceHours).toBeNull();
  });

  it('marca cuando la consulta está fuera del horario de servicio', () => {
    const estimate = estimator.estimateLeg(
      route([weekdaySchedule]),
      [stop(1, null, null), stop(2, null, null)],
      THURSDAY_11PM,
    );
    expect(estimate.withinServiceHours).toBe(false);
  });

  it('usa la frecuencia por defecto configurada si la ruta no tiene una', () => {
    const withDefault = new ScheduleBasedArrivalEstimator({ ...OPTIONS, defaultHeadwayMin: 12 });
    const estimate = withDefault.estimateLeg(
      route(),
      [stop(1, null, null), stop(2, null, null)],
      THURSDAY_10AM,
    );
    expect(estimate).toMatchObject({ waitMinutes: 6, headwaySource: 'default' });
  });

  it('calcula minutos caminando', () => {
    expect(estimator.walkMinutes(150)).toBe(2);
  });
});
