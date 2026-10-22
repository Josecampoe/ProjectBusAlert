import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { createTestContext, TEST_API_KEY } from './test/helpers';

const auth = { 'X-API-Key': TEST_API_KEY };

describe('seguridad de la API', () => {
  const { app } = createTestContext();

  it('/health es público', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok', aiEnabled: false });
  });

  it('rechaza peticiones sin clave o con clave inválida', async () => {
    for (const headers of [{}, { 'X-API-Key': 'otra-clave-cualquiera-123' }]) {
      const res = await request(app).get('/api/routes').set(headers);
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('UNAUTHORIZED');
    }
  });

  it('acepta la clave configurada', async () => {
    const res = await request(app).get('/api/routes').set(auth);
    expect(res.status).toBe(200);
    expect(res.body.count).toBe(2);
  });

  it('aplica cabeceras de seguridad y no expone la tecnología', async () => {
    const res = await request(app).get('/health');
    expect(res.headers['x-content-type-options']).toBe('nosniff');
    expect(res.headers['x-powered-by']).toBeUndefined();
  });

  it('no habilita CORS si no hay orígenes configurados', async () => {
    const res = await request(app).get('/health').set('Origin', 'https://evil.example');
    expect(res.headers['access-control-allow-origin']).toBeUndefined();
  });

  it('habilita CORS solo para los orígenes configurados', async () => {
    const ctx = createTestContext({ env: { CORS_ORIGINS: 'https://busalert.example' } });
    const ok = await request(ctx.app).get('/health').set('Origin', 'https://busalert.example');
    expect(ok.headers['access-control-allow-origin']).toBe('https://busalert.example');
    const denied = await request(ctx.app).get('/health').set('Origin', 'https://evil.example');
    expect(denied.headers['access-control-allow-origin']).toBeUndefined();
  });

  it('limita la tasa de peticiones con un código estable', async () => {
    const ctx = createTestContext({ env: { RATE_LIMIT_MAX: '2' } });
    await request(ctx.app).get('/api/routes').set(auth);
    await request(ctx.app).get('/api/routes').set(auth);
    const res = await request(ctx.app).get('/api/routes').set(auth);
    expect(res.status).toBe(429);
    expect(res.body.error.code).toBe('RATE_LIMITED');
  });

  it('limita el tamaño del cuerpo', async () => {
    const ctx = createTestContext({ env: { BODY_LIMIT: '100b' } });
    const res = await request(ctx.app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'x'.repeat(200), currentLocationName: 'centro' });
    expect(res.status).toBe(413);
    expect(res.body.error.code).toBe('PAYLOAD_TOO_LARGE');
  });

  it('responde INVALID_JSON ante JSON mal formado', async () => {
    const res = await request(app)
      .post('/api/voice')
      .set({ ...auth, 'Content-Type': 'application/json' })
      .send('{"voiceText": ');
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('INVALID_JSON');
  });
});

describe('validación y errores', () => {
  const { app } = createTestContext();

  it('valida el cuerpo de /api/voice con detalle por campo', async () => {
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: '', extra: true });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    const fields = res.body.error.details.map((d: { field: string }) => d.field);
    expect(fields).toEqual(expect.arrayContaining(['voiceText', 'currentLocationName']));
  });

  it('rechaza textos más largos que el máximo configurado', async () => {
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'a'.repeat(301), currentLocationName: 'centro' });
    expect(res.status).toBe(400);
  });

  it('rechaza idiomas no soportados', async () => {
    const res = await request(app)
      .post('/api/voice')
      .set(auth)
      .send({ voiceText: 'terminal', currentLocationName: 'centro', language: 'fr' });
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('valida parámetros de ruta y consulta', async () => {
    expect((await request(app).get('/api/routes/E1;DROP').set(auth)).body.error.code).toBe(
      'VALIDATION_ERROR',
    );
    expect((await request(app).get('/api/stops/search').set(auth)).body.error.code).toBe(
      'VALIDATION_ERROR',
    );
    expect((await request(app).get('/api/route-planner?origin=x').set(auth)).body.error.code).toBe(
      'VALIDATION_ERROR',
    );
  });

  it('usa códigos específicos para recursos inexistentes', async () => {
    expect((await request(app).get('/api/routes/ZZ9').set(auth)).body.error.code).toBe(
      'ROUTE_NOT_FOUND',
    );
    expect((await request(app).get('/api/stops/T99').set(auth)).body.error.code).toBe(
      'STOP_NOT_FOUND',
    );
    expect((await request(app).get('/api/nada').set(auth)).body.error.code).toBe('NOT_FOUND');
  });

  it('no filtra detalles internos ante errores inesperados', async () => {
    const ctx = createTestContext();
    ctx.repo.listRoutes = () => {
      throw new Error('SQLITE_CORRUPT: detalle interno');
    };
    const res = await request(ctx.app).get('/api/routes').set(auth);
    expect(res.status).toBe(500);
    expect(res.body).toEqual({ error: { code: 'INTERNAL_ERROR', message: 'Error interno' } });
  });
});

describe('endpoints de transporte', () => {
  const { app } = createTestContext();

  it('lista las paradas de una ruta en orden', async () => {
    const res = await request(app).get('/api/routes/fx1/stops').set(auth);
    expect(res.status).toBe(200);
    expect(res.body.directions[0].stops.map((s: { name: string }) => s.name)).toEqual([
      'Udenar Torobajo',
      'Universidad Cooperativa',
      'Parque Bolívar',
      'Parque Nariño',
    ]);
  });

  it('busca paradas por alias y devuelve las rutas que pasan', async () => {
    const res = await request(app).get('/api/stops/search').query({ q: 'UCC' }).set(auth);
    expect(res.body.stops).toHaveLength(1);
    expect(res.body.stops[0].routes[0].routeCode).toBe('FX1');
  });

  it('obtiene una parada por código externo con sus alias', async () => {
    const res = await request(app).get('/api/stops/T04').set(auth);
    expect(res.body.stop.name).toBe('Parque Nariño');
    expect(res.body.aliases).toEqual(['Centro']);
  });

  it('planifica por nombres de parada', async () => {
    const res = await request(app)
      .get('/api/route-planner')
      .query({ origin: 'Udenar', destination: 'Parque Bolívar' })
      .set(auth);
    expect(res.body.status).toBe('ok');
    expect(res.body.plans[0].legs[0].routeCode).toBe('FX1');
  });
});
