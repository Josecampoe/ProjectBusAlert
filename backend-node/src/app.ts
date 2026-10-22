/**
 * Construcción de la aplicación Express. Recibe sus dependencias para
 * que las pruebas puedan usar una BD en memoria y una IA simulada.
 */
import cors from 'cors';
import express, { type Express } from 'express';
import helmet from 'helmet';
import type { AppConfig } from './config/env';
import type { Logger } from './lib/logger';
import { createErrorHandler, notFoundHandler } from './middleware/errorHandler';
import { createRateLimiter, requireApiKey } from './middleware/security';
import type { TransitRepository } from './repositories/transitRepository';
import { createAssistantRouter } from './routes/assistantRoutes';
import { buildSchemas } from './routes/schemas';
import { createTransitRouter } from './routes/transitRoutes';
import type { AssistantService } from './services/assistantService';

export interface AppDependencies {
    config: AppConfig;
    logger: Logger;
    repo: TransitRepository;
    assistant: AssistantService;
}

export function createApp({ config, logger, repo, assistant }: AppDependencies): Express {
    const app = express();
    const schemas = buildSchemas(config);

    app.disable('x-powered-by');
    app.set('trust proxy', config.server.trustProxy);
    app.use(helmet());
    app.use(
        cors({
            // Sin orígenes configurados no se habilita CORS (el reloj no lo necesita).
            origin: config.server.corsOrigins.length > 0 ? config.server.corsOrigins : false,
        }),
    );
    app.use(express.json({ limit: config.server.bodyLimit }));

    // Pública: comprobación de vida para el balanceador / despliegue.
    app.get('/health', (_req, res) => {
        res.json({ status: 'ok', aiEnabled: assistant.aiEnabled });
    });

    const api = express.Router();
    api.use(createRateLimiter(config.security.rateLimitWindowMs, config.security.rateLimitMax));
    api.use(requireApiKey(config.security.apiKeys));
    api.use(createTransitRouter(repo, schemas, config));
    api.use(createAssistantRouter(assistant, schemas));
    app.use('/api', api);

    app.use(notFoundHandler);
    app.use(createErrorHandler(logger));
    return app;
}
