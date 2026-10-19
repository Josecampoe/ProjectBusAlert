import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

import { voiceRouter } from './controllers/voiceController';
import { routesRouter } from './controllers/transit/routesController';
import { stopsRouter } from './controllers/transit/stopsController';
import { plannerRouter } from './controllers/transit/plannerController';

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

app.use('/api/voice', voiceRouter);
app.use('/api/routes', routesRouter);
app.use('/api/stops', stopsRouter);
app.use('/api/route-planner', plannerRouter);

app.listen(port, () => {
    console.log(`Server listening on port ${port}`);
});
