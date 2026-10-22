/**
 * Punto de entrada del servidor.
 */
import 'dotenv/config';
import { createApp } from './app';
import { ConfigError, loadConfig } from './config/env';
import { buildContainer } from './container';
import { createLogger } from './lib/logger';

function main(): void {
    let config;
    try {
        config = loadConfig();
    } catch (err) {
        if (err instanceof ConfigError) {
            console.error(`${err.message}\nRevisa tu archivo .env (plantilla: .env.example).`);
            process.exit(1);
        }
        throw err;
    }

    const logger = createLogger(config.logLevel);
    const { repo, assistant } = buildContainer(config, logger);
    const app = createApp({ config, logger, repo, assistant });

    app.listen(config.server.port, config.server.host, () => {
        logger.info('server_started', {
            host: config.server.host,
            port: config.server.port,
            aiEnabled: assistant.aiEnabled,
            routes: repo.listRoutes().length,
        });
        if (repo.listRoutes().length === 0) {
            logger.warn('database_empty', { hint: 'Ejecuta "npm run seed" para cargar los datos.' });
        }
    });
}

main();
