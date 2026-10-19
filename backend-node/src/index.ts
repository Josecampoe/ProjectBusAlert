import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

// Controladores existentes
import { voiceRouter } from './controllers/voiceController';

// Controladores de transporte SETP
import { routesRouter }      from './controllers/transit/routesController';
import { stopsRouter }       from './controllers/transit/stopsController';
import { plannerRouter }     from './controllers/transit/plannerController';
import { aiTransportRouter } from './controllers/transit/aiTransportController';

// Inicializar base de datos al arrancar
import { getDb } from './db/db';

dotenv.config();

const app  = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// ─── API existente (smartwatch / voz) ─────────────────────────────────────────
app.use('/api/voice', voiceRouter);

// ─── API de transporte SETP ───────────────────────────────────────────────────
app.use('/api/routes',        routesRouter);
app.use('/api/stops',         stopsRouter);
app.use('/api/route-planner', plannerRouter);
app.use('/api/ai/transport-query', aiTransportRouter);

// ─── Health check ─────────────────────────────────────────────────────────────
app.get('/health', (_req, res) => {
    res.json({
        status:  'ok',
        message: 'BusAlert Backend is running!',
        version: '2.0.0',
        endpoints: [
            'POST /api/voice',
            'GET  /api/routes',
            'GET  /api/routes/:code',
            'GET  /api/routes/:code/stops',
            'GET  /api/routes/:code/directions',
            'GET  /api/routes/search?q=',
            'GET  /api/stops',
            'GET  /api/stops/search?q=',
            'GET  /api/stops/:id',
            'GET  /api/stops/:id/routes',
            'GET  /api/route-planner?origin=&destination=',
            'POST /api/ai/transport-query',
        ],
    });
});

// ─── Inicializar DB ────────────────────────────────────────────────────────────
try {
    getDb();
} catch (err) {
    console.error('[DB] Error al inicializar la base de datos:', err);
    process.exit(1);
}

app.listen(port, () => {
    console.log(`[Server]: BusAlert API v2.0 escuchando en http://localhost:${port}`);
    console.log(`[Server]: DB activa — ejecuta 'npx ts-node src/db/seed.ts' para cargar datos.`);
});
