/**
 * Contrato del cliente de IA. El resto del backend depende solo de esta
 * interfaz, lo que permite sustituir Gemini por un doble en las pruebas.
 */
export interface JsonGenerationRequest {
  /** Instrucciones del sistema (nunca contienen texto del usuario). */
  systemInstruction: string;
  /** Contenido del turno de usuario: contexto de BD + datos del usuario delimitados. */
  userContent: string;
  /** JSON Schema que debe cumplir la respuesta. */
  responseSchema: Record<string, unknown>;
}

export interface AiClient {
  /** Devuelve el texto crudo (JSON) generado por el modelo. */
  generateJson(request: JsonGenerationRequest): Promise<string>;
}

export type AiErrorKind =
  'auth' | 'quota' | 'unavailable' | 'timeout' | 'network' | 'invalid_output' | 'unknown';

export class AiError extends Error {
  constructor(
    readonly kind: AiErrorKind,
    message: string,
    readonly cause?: unknown,
  ) {
    super(message);
    this.name = 'AiError';
  }
}
