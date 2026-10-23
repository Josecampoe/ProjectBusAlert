/**
 * Pruebas de contrato: las respuestas reales del backend (con fixtures)
 * deben coincidir con los JSON de `../contracts`, que también usan las
 * pruebas de Android. Si el contrato cambia a propósito, regenera con:
 *   UPDATE_CONTRACTS=1 npm test -- contracts
 */
import fs from 'node:fs';
import path from 'node:path';
import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { createTestContext, FakeAiClient, TEST_API_KEY } from './test/helpers';

const CONTRACTS_ROOT = path.join(__dirname, '..', '..', 'contracts');
const CONTRACTS_DIR = path.join(CONTRACTS_ROOT, 'voice');
const UPDATE = process.env.UPDATE_CONTRACTS === '1';

function checkContract(name: string, actual: unknown, dir: string = CONTRACTS_DIR): void {
  const file = path.join(dir, `${name}.json`);
  if (UPDATE) {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(file, `${JSON.stringify(actual, null, 2)}\n`);
  }
  expect(actual).toEqual(JSON.parse(fs.readFileSync(file, 'utf-8')));
}

const auth = { 'X-API-Key': TEST_API_KEY };
const aiAnswer = JSON.stringify({
  chosenOptionIndex: 0,
  spokenText: 'Toma la FX1 en Udenar Torobajo y bájate en Parque Bolívar.',
  displayTitle: 'FX1',
  displaySubtitle: 'Udenar Torobajo',
});

describe('contratos de POST /api/voice', () => {
  it('ok_ai', async () => {
    const { app } = createTestContext({ ai: new FakeAiClient(() => aiAnswer) });
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'parque bolivar', currentLocationName: 'udenar', language: 'es' });
    checkContract('ok_ai', res.body);
  });

  it('ok_planner', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'parque bolivar', currentLocationName: 'udenar' });
    checkContract('ok_planner', res.body);
  });

  it('ok_gps', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'udenar', latitude: 1.2143, longitude: -77.2787, language: 'en' });
    checkContract('ok_gps', res.body);
  });

  it('ok_transfer', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'terminal', currentLocationName: 'udenar' });
    checkContract('ok_transfer', res.body);
  });

  it('no_route', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'barrio sin rutas', currentLocationName: 'parque nariño' });
    checkContract('no_route', res.body);
  });

  it('error_unauthorized', async () => {
    const { app } = createTestContext();
    const res = await request(app).post('/api/voice').send({});
    checkContract('error_unauthorized', { status: res.status, body: res.body });
  });

  it('error_validation', async () => {
    const { app } = createTestContext();
    const res = await request(app).post('/api/voice').set(auth).send({ voiceText: '' });
    checkContract('error_validation', { status: res.status, body: res.body });
  });
});

describe('contratos de consulta de rutas y paradas', () => {
  const dir = path.join(CONTRACTS_ROOT, 'transit');

  it('routes', async () => {
    const { app } = createTestContext();
    const res = await request(app).get('/api/routes').set(auth);
    checkContract('routes', res.body, dir);
  });

  it('route_stops', async () => {
    const { app } = createTestContext();
    const res = await request(app).get('/api/routes/FX1/stops').set(auth);
    checkContract('route_stops', res.body, dir);
  });

  it('stops_search', async () => {
    const { app } = createTestContext();
    const res = await request(app).get('/api/stops/search').query({ q: 'parque' }).set(auth);
    checkContract('stops_search', res.body, dir);
  });

  it('stops_nearby', async () => {
    const { app } = createTestContext();
    const res = await request(app)
      .get('/api/stops/nearby')
      .query({ lat: 1.2143, lng: -77.2787, radius: 300 })
      .set(auth);
    checkContract('stops_nearby', res.body, dir);
  });

  it('stops_nearby_empty', async () => {
    const { app } = createTestContext();
    // Punto a varios kilómetros de cualquier parada de los fixtures.
    const res = await request(app)
      .get('/api/stops/nearby')
      .query({ lat: 1.25, lng: -77.32, radius: 300 })
      .set(auth);
    checkContract('stops_nearby_empty', res.body, dir);
  });

  it('error_route_not_found', async () => {
    const { app } = createTestContext();
    const res = await request(app).get('/api/routes/ZZ9/stops').set(auth);
    checkContract('error_route_not_found', { status: res.status, body: res.body }, dir);
  });
});
