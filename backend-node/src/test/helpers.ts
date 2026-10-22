/**
 * Utilidades compartidas por las pruebas: BD en memoria con fixtures,
 * configuración de prueba y un doble de la IA.
 */
import path from 'node:path';
import { createApp } from '../app';
import { loadConfig, type AppConfig } from '../config/env';
import { IN_MEMORY_DB, openDatabase } from '../db/database';
import { importDataset } from '../db/importer';
import { silentLogger } from '../lib/logger';
import { TransitRepository } from '../repositories/transitRepository';
import type { AiClient, JsonGenerationRequest } from '../services/ai/aiClient';
import { AssistantService } from '../services/assistantService';
import { MemoryCache } from '../services/cacheService';

export const FIXTURES_DIR = path.join(__dirname, 'fixtures', 'data');
export const TEST_API_KEY = 'test-api-key-0123456789';

export const BASE_TEST_ENV: Record<string, string> = {
  NODE_ENV: 'test',
  LOG_LEVEL: 'silent',
  PORT: '3000',
  API_KEYS: TEST_API_KEY,
  DB_PATH: IN_MEMORY_DB,
  DATA_DIR: FIXTURES_DIR,
  RATE_LIMIT_MAX: '1000',
};

export function testConfig(overrides: Record<string, string> = {}): AppConfig {
  return loadConfig({ ...BASE_TEST_ENV, ...overrides });
}

/** Doble de la IA: devuelve lo que diga `respond` y guarda las peticiones. */
export class FakeAiClient implements AiClient {
  readonly requests: JsonGenerationRequest[] = [];

  constructor(private readonly respond: (req: JsonGenerationRequest) => string | Promise<string>) {}

  async generateJson(request: JsonGenerationRequest): Promise<string> {
    this.requests.push(request);
    return this.respond(request);
  }
}

export function createTestContext(
  options: { ai?: AiClient | null; env?: Record<string, string> } = {},
) {
  const config = testConfig(options.env);
  const db = openDatabase(IN_MEMORY_DB);
  const report = importDataset(db, FIXTURES_DIR);
  if (!report.ok) throw new Error(`Fixtures inválidos: ${JSON.stringify(report.errors)}`);
  const repo = new TransitRepository(db, new MemoryCache(config.data.cacheTtlSeconds));
  const assistant = new AssistantService(repo, options.ai ?? null, config, silentLogger);
  const app = createApp({ config, logger: silentLogger, repo, assistant });
  return { config, db, repo, assistant, app };
}
