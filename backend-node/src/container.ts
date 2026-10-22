/**
 * Arma las dependencias reales a partir de la configuración.
 */
import type { AppConfig } from './config/env';
import { openDatabase, type Db } from './db/database';
import type { Logger } from './lib/logger';
import { TransitRepository } from './repositories/transitRepository';
import type { AiClient } from './services/ai/aiClient';
import { GeminiClient } from './services/ai/geminiClient';
import { AssistantService } from './services/assistantService';
import { MemoryCache } from './services/cacheService';

export interface Container {
    db: Db;
    repo: TransitRepository;
    assistant: AssistantService;
}

export function buildContainer(config: AppConfig, logger: Logger, aiOverride?: AiClient | null): Container {
    const db = openDatabase(config.data.dbPath);
    const repo = new TransitRepository(db, new MemoryCache(config.data.cacheTtlSeconds));
    const ai =
        aiOverride !== undefined
            ? aiOverride
            : config.ai.apiKey
              ? new GeminiClient(
                    {
                        apiKey: config.ai.apiKey,
                        models: [config.ai.primaryModel, config.ai.fallbackModel],
                        timeoutMs: config.ai.timeoutMs,
                        temperature: config.ai.temperature,
                    },
                    logger,
                )
              : null;
    return { db, repo, assistant: new AssistantService(repo, ai, config, logger) };
}
