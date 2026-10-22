/**
 * Criterio de aceptación de la Fase 3:
 * "¿cómo voy a UDENAR?" desde una coordenada de Pasto usa la parada más
 * cercana y datos de la base de datos.
 */
import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { createTestContext, FakeAiClient, TEST_API_KEY } from './test/helpers';

const auth = { 'X-API-Key': TEST_API_KEY };
/** Punto a ~40 m del Parque Nariño (fixture). */
const NEAR_PARQUE_NARINO = { latitude: 1.2143, longitude: -77.2787 };

describe('consulta por GPS', () => {
  it('"¿cómo voy a UDENAR?" desde una coordenada usa la parada más cercana y la BD', async () => {
    const ai = new FakeAiClient(() =>
      JSON.stringify({
        chosenOptionIndex: 0,
        spokenText: 'Toma la FX1 en el Parque Nariño.',
        displayTitle: 'FX1',
        displaySubtitle: 'Parque Nariño',
      }),
    );
    const { app } = createTestContext({ ai });
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: '¿Cómo voy a UDENAR?', ...NEAR_PARQUE_NARINO });

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    // Parada más cercana primero, con su distancia.
    expect(res.body.originCandidates[0]).toMatchObject({ name: 'Parque Nariño' });
    expect(res.body.originCandidates[0].distanceMeters).toBeLessThan(60);
    // Plan calculado con los recorridos de la BD.
    const [leg] = res.body.recommended.legs;
    expect(leg).toMatchObject({
      routeCode: 'FX1',
      directionKey: 'FX1_D2',
      board: { name: 'Parque Nariño' },
      alight: { name: 'Udenar Torobajo' },
    });
    expect(res.body.recommended.isEstimate).toBe(true);
    expect(res.body.recommended.estimatedMinutes).toBeGreaterThan(0);
    // La IA no recibe las coordenadas del usuario, solo la parada.
    const sent = ai.requests[0].userContent;
    expect(sent).not.toContain(String(NEAR_PARQUE_NARINO.latitude));
    expect(sent).toContain('Parque Nariño');
  });

  it('responde no_nearby_stops si no hay paradas en el radio', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'udenar', latitude: 4.6097, longitude: -74.0817 });
    expect(res.body.status).toBe('no_nearby_stops');
  });

  it('las coordenadas tienen prioridad sobre el texto', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'udenar', currentLocationName: 'terminal', ...NEAR_PARQUE_NARINO });
    expect(res.body.originCandidates[0].name).toBe('Parque Nariño');
  });

  it('valida el par de coordenadas', async () => {
    const { app } = createTestContext();
    for (const body of [
      { voiceText: 'udenar', latitude: 1.2 },
      { voiceText: 'udenar', latitude: 91, longitude: 0 },
      { voiceText: 'udenar' },
    ]) {
      const res = await request(app).post('/api/voice').set(auth).send(body);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
    }
  });
});

describe('GET /api/stops/nearby', () => {
  const { app } = createTestContext();

  it('lista paradas por distancia con sus rutas', async () => {
    const res = await request(app)
      .get('/api/stops/nearby')
      .query({ lat: NEAR_PARQUE_NARINO.latitude, lng: NEAR_PARQUE_NARINO.longitude, radius: 200 })
      .set(auth);
    expect(res.status).toBe(200);
    expect(res.body.stops.map((s: { name: string }) => s.name)).toEqual([
      'Parque Nariño',
      'Plazuela San Andrés',
    ]);
    expect(res.body.stops[0].routes.map((r: { routeCode: string }) => r.routeCode)).toEqual(
      expect.arrayContaining(['FX1', 'FX2']),
    );
    expect(res.body.stopsWithoutCoordinates).toBe(1);
  });

  it('usa el radio por defecto y limita el máximo', async () => {
    const ok = await request(app)
      .get('/api/stops/nearby')
      .query({ lat: 1.2143, lng: -77.2787 })
      .set(auth);
    expect(ok.body.radiusMeters).toBe(500);
    const tooBig = await request(app)
      .get('/api/stops/nearby')
      .query({ lat: 1.2143, lng: -77.2787, radius: 99999 })
      .set(auth);
    expect(tooBig.body.error.code).toBe('VALIDATION_ERROR');
  });
});
