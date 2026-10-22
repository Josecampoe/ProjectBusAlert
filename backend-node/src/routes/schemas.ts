/**
 * Esquemas de validación de entrada de todos los endpoints.
 */
import { z } from 'zod';
import type { AppConfig } from '../config/env';

export const LANGUAGES = ['es', 'en'] as const;

/** Códigos de ruta y de parada: letras, números, guion y guion bajo. */
const CODE_PATTERN = /^[A-Za-z0-9_-]+$/;
const MAX_CODE_LENGTH = 32;

export function buildSchemas(config: AppConfig) {
  const text = z.string().trim().min(1).max(config.input.maxQueryLength);
  const language = z.enum(LANGUAGES).default('es');
  const code = z.string().regex(CODE_PATTERN).max(MAX_CODE_LENGTH);

  return {
    voiceBody: z.strictObject({
      voiceText: text,
      currentLocationName: text,
      language,
    }),
    transportQueryBody: z.strictObject({
      question: text,
      language,
    }),
    searchQuery: z.object({ q: text }),
    plannerQuery: z.object({ origin: text, destination: text }),
    routeParams: z.object({ code }),
    stopParams: z.object({ id: code }),
  };
}

export type Schemas = ReturnType<typeof buildSchemas>;
