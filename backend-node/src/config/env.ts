/**
 * Configuración central del backend.
 *
 * Todo valor ajustable (puertos, rutas, claves, límites, modelos, tiempos)
 * se lee aquí desde variables de entorno y se valida al arrancar.
 * Si falta una variable obligatoria el proceso termina con un mensaje claro.
 * Ningún otro módulo debe leer `process.env` directamente.
 */
import { z } from 'zod';

const csvList = z
  .string()
  .default('')
  .transform((value) =>
    value
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item.length > 0),
  );

const optionalSecret = z
  .string()
  .optional()
  .transform((value) => (value && value.trim().length > 0 ? value.trim() : undefined));

const positiveInt = z.coerce.number().int().positive();
const nonNegativeNumber = z.coerce.number().nonnegative();

/** Longitud mínima de cada clave de aplicación aceptada en API_KEYS. */
export const MIN_API_KEY_LENGTH = 16;

const EnvSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error', 'silent']).default('info'),

  // ── Servidor ──────────────────────────────────────────────────────────
  PORT: z.coerce.number().int().min(1).max(65535),
  HOST: z.string().min(1).default('0.0.0.0'),
  TRUST_PROXY: z.coerce.number().int().nonnegative().default(0),
  BODY_LIMIT: z.string().min(1).default('10kb'),
  CORS_ORIGINS: csvList,

  // ── Seguridad ─────────────────────────────────────────────────────────
  API_KEYS: csvList.pipe(
    z
      .array(
        z
          .string()
          .min(
            MIN_API_KEY_LENGTH,
            `cada clave debe tener al menos ${MIN_API_KEY_LENGTH} caracteres`,
          ),
      )
      .min(1, 'define al menos una clave de aplicación'),
  ),
  RATE_LIMIT_WINDOW_MS: positiveInt.default(60_000),
  RATE_LIMIT_MAX: positiveInt.default(60),

  // ── Datos ─────────────────────────────────────────────────────────────
  CITY_NAME: z.string().min(1).default('San Juan de Pasto, Colombia'),
  DB_PATH: z.string().min(1),
  DATA_DIR: z.string().min(1),
  CACHE_TTL_SECONDS: positiveInt.default(300),

  // ── Entrada de usuario ────────────────────────────────────────────────
  MAX_QUERY_LENGTH: positiveInt.default(300),
  SEARCH_MIN_TERM_LENGTH: positiveInt.default(4),
  SEARCH_MAX_RESULTS: positiveInt.default(20),

  // ── IA (opcional: sin clave se usa solo el planificador) ─────────────
  GEMINI_API_KEY: optionalSecret,
  GEMINI_MODEL_PRIMARY: z.string().min(1).default('gemini-3.8-flash'),
  GEMINI_MODEL_FALLBACK: z.string().min(1).default('gemini-3.5-flash-lite'),
  AI_TIMEOUT_MS: positiveInt.default(12_000),
  AI_TEMPERATURE: z.coerce.number().min(0).max(2).default(0.2),
  AI_MAX_PLAN_OPTIONS: positiveInt.default(3),

  // ── Planificador y estimaciones ──────────────────────────────────────
  PLANNER_MAX_ALTERNATIVES: positiveInt.default(5),
  PLANNER_MAX_TRANSFERS: z.coerce.number().int().min(0).max(1).default(1),
  PLANNER_TRANSFER_PENALTY_MIN: nonNegativeNumber.default(8),
  PLANNER_MINUTES_PER_STOP: z.coerce.number().positive().default(1.5),
  PLANNER_TRANSFER_WALK_RADIUS_M: nonNegativeNumber.default(150),
  ETA_AVERAGE_BUS_SPEED_KMH: z.coerce.number().positive().default(18),
  ETA_DEFAULT_HEADWAY_MIN: nonNegativeNumber.default(0),
  ETA_WALK_SPEED_KMH: z.coerce.number().positive().default(4.5),
  TRANSIT_TIMEZONE: z.string().min(1).default('America/Bogota'),

  // ── Búsqueda geográfica ──────────────────────────────────────────────
  NEARBY_DEFAULT_RADIUS_M: positiveInt.default(500),
  NEARBY_MAX_RADIUS_M: positiveInt.default(3000),
  NEARBY_MAX_RESULTS: positiveInt.default(10),
});

export type RawEnv = z.input<typeof EnvSchema>;

export interface AppConfig {
  env: 'development' | 'test' | 'production';
  logLevel: 'debug' | 'info' | 'warn' | 'error' | 'silent';
  server: {
    port: number;
    host: string;
    trustProxy: number;
    bodyLimit: string;
    corsOrigins: string[];
  };
  security: {
    apiKeys: string[];
    rateLimitWindowMs: number;
    rateLimitMax: number;
  };
  data: {
    cityName: string;
    dbPath: string;
    dataDir: string;
    cacheTtlSeconds: number;
  };
  input: {
    maxQueryLength: number;
    searchMinTermLength: number;
    searchMaxResults: number;
  };
  ai: {
    apiKey: string | undefined;
    enabled: boolean;
    primaryModel: string;
    fallbackModel: string;
    timeoutMs: number;
    temperature: number;
    maxPlanOptions: number;
  };
  planner: {
    maxAlternatives: number;
    maxTransfers: number;
    transferPenaltyMin: number;
    minutesPerStop: number;
    transferWalkRadiusM: number;
  };
  eta: {
    averageBusSpeedKmh: number;
    defaultHeadwayMin: number;
    walkSpeedKmh: number;
    timeZone: string;
  };
  nearby: {
    defaultRadiusM: number;
    maxRadiusM: number;
    maxResults: number;
  };
}

export class ConfigError extends Error {
  constructor(public readonly issues: string[]) {
    super(`Configuración inválida:\n${issues.map((issue) => `  - ${issue}`).join('\n')}`);
    this.name = 'ConfigError';
  }
}

/** Valida un conjunto de variables de entorno y devuelve la configuración tipada. */
export function loadConfig(source: Record<string, string | undefined> = process.env): AppConfig {
  const parsed = EnvSchema.safeParse(source);
  if (!parsed.success) {
    throw new ConfigError(
      parsed.error.issues.map((issue) => `${issue.path.join('.') || '(raíz)'}: ${issue.message}`),
    );
  }
  const e = parsed.data;

  try {
    new Intl.DateTimeFormat('en', { timeZone: e.TRANSIT_TIMEZONE });
  } catch {
    throw new ConfigError([`TRANSIT_TIMEZONE: zona horaria desconocida "${e.TRANSIT_TIMEZONE}"`]);
  }
  if (e.NEARBY_DEFAULT_RADIUS_M > e.NEARBY_MAX_RADIUS_M) {
    throw new ConfigError(['NEARBY_DEFAULT_RADIUS_M no puede ser mayor que NEARBY_MAX_RADIUS_M']);
  }

  return {
    env: e.NODE_ENV,
    logLevel: e.LOG_LEVEL,
    server: {
      port: e.PORT,
      host: e.HOST,
      trustProxy: e.TRUST_PROXY,
      bodyLimit: e.BODY_LIMIT,
      corsOrigins: e.CORS_ORIGINS,
    },
    security: {
      apiKeys: e.API_KEYS,
      rateLimitWindowMs: e.RATE_LIMIT_WINDOW_MS,
      rateLimitMax: e.RATE_LIMIT_MAX,
    },
    data: {
      cityName: e.CITY_NAME,
      dbPath: e.DB_PATH,
      dataDir: e.DATA_DIR,
      cacheTtlSeconds: e.CACHE_TTL_SECONDS,
    },
    input: {
      maxQueryLength: e.MAX_QUERY_LENGTH,
      searchMinTermLength: e.SEARCH_MIN_TERM_LENGTH,
      searchMaxResults: e.SEARCH_MAX_RESULTS,
    },
    ai: {
      apiKey: e.GEMINI_API_KEY,
      enabled: e.GEMINI_API_KEY !== undefined,
      primaryModel: e.GEMINI_MODEL_PRIMARY,
      fallbackModel: e.GEMINI_MODEL_FALLBACK,
      timeoutMs: e.AI_TIMEOUT_MS,
      temperature: e.AI_TEMPERATURE,
      maxPlanOptions: e.AI_MAX_PLAN_OPTIONS,
    },
    planner: {
      maxAlternatives: e.PLANNER_MAX_ALTERNATIVES,
      maxTransfers: e.PLANNER_MAX_TRANSFERS,
      transferPenaltyMin: e.PLANNER_TRANSFER_PENALTY_MIN,
      minutesPerStop: e.PLANNER_MINUTES_PER_STOP,
      transferWalkRadiusM: e.PLANNER_TRANSFER_WALK_RADIUS_M,
    },
    eta: {
      averageBusSpeedKmh: e.ETA_AVERAGE_BUS_SPEED_KMH,
      defaultHeadwayMin: e.ETA_DEFAULT_HEADWAY_MIN,
      walkSpeedKmh: e.ETA_WALK_SPEED_KMH,
      timeZone: e.TRANSIT_TIMEZONE,
    },
    nearby: {
      defaultRadiusM: e.NEARBY_DEFAULT_RADIUS_M,
      maxRadiusM: e.NEARBY_MAX_RADIUS_M,
      maxResults: e.NEARBY_MAX_RESULTS,
    },
  };
}
