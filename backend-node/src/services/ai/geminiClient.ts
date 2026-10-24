/**
 * Implementación de AiClient con el SDK oficial `@google/genai`.
 * Prueba el modelo principal y, si falla por causas recuperables
 * (cuota, sobrecarga, modelo no disponible, timeout), el de respaldo.
 */
import { ApiError, GoogleGenAI } from '@google/genai';
import type { Logger } from '../../lib/logger';
import { AiError, type AiClient, type AiErrorKind, type JsonGenerationRequest } from './aiClient';

export interface GeminiClientOptions {
  apiKey: string;
  models: string[];
  timeoutMs: number;
  temperature: number;
}

const HTTP_UNAUTHORIZED = 401;
const HTTP_FORBIDDEN = 403;
const HTTP_NOT_FOUND = 404;
const HTTP_TOO_MANY_REQUESTS = 429;
const HTTP_SERVER_ERROR_MIN = 500;

export function classifyGeminiError(err: unknown): AiErrorKind {
  if (err instanceof AiError) return err.kind;
  if (err instanceof ApiError) {
    if (err.status === HTTP_UNAUTHORIZED || err.status === HTTP_FORBIDDEN) return 'auth';
    if (err.status === HTTP_TOO_MANY_REQUESTS) return 'quota';
    if (err.status === HTTP_NOT_FOUND || err.status >= HTTP_SERVER_ERROR_MIN) return 'unavailable';
    return 'unknown';
  }
  if (err instanceof Error && (err.name === 'AbortError' || err.name === 'TimeoutError'))
    return 'timeout';
  return 'network';
}

/** Errores para los que tiene sentido probar otro modelo. */
const RETRYABLE: ReadonlySet<AiErrorKind> = new Set([
  'quota',
  'unavailable',
  'timeout',
  'network',
  'invalid_output',
]);

export class GeminiClient implements AiClient {
  private readonly ai: GoogleGenAI;

  constructor(
    private readonly options: GeminiClientOptions,
    private readonly logger: Logger,
  ) {
    this.ai = new GoogleGenAI({
      apiKey: options.apiKey,
      httpOptions: { timeout: options.timeoutMs },
    });
  }

  async generateJson(request: JsonGenerationRequest): Promise<string> {
    let lastError: AiError | undefined;
    for (const model of [...new Set(this.options.models)]) {
      try {
        const response = await this.ai.models.generateContent({
          model,
          contents: request.userContent,
          config: {
            systemInstruction: request.systemInstruction,
            responseMimeType: 'application/json',
            responseJsonSchema: request.responseSchema,
            temperature: this.options.temperature,
            abortSignal: AbortSignal.timeout(this.options.timeoutMs),
          },
        });
        const text = (response.text ?? '').trim();
        if (text.length === 0) throw new AiError('invalid_output', 'Respuesta vacía del modelo');
        return text;
      } catch (err) {
        const kind = classifyGeminiError(err);
        lastError =
          err instanceof AiError ? err : new AiError(kind, `Fallo del modelo ${model}`, err);
        this.logger.warn('ai_model_failed', { model, kind });
        if (!RETRYABLE.has(kind)) break;
      }
    }
    throw lastError ?? new AiError('unknown', 'Sin modelos configurados');
  }
}
