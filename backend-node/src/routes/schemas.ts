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
  const latitude = z.coerce.number().min(-90).max(90);
  const longitude = z.coerce.number().min(-180).max(180);

  return {
    // Origen: texto (currentLocationName) o coordenadas GPS (latitude + longitude).
    voiceBody: z
      .strictObject({
        voiceText: text,
        currentLocationName: text.optional(),
        latitude: latitude.optional(),
        longitude: longitude.optional(),
        language,
      })
      .refine((b) => (b.latitude === undefined) === (b.longitude === undefined), {
        message: 'latitude y longitude van juntas',
        path: ['latitude'],
      })
      .refine((b) => b.currentLocationName !== undefined || b.latitude !== undefined, {
        message: 'falta el origen: currentLocationName o latitude/longitude',
        path: ['currentLocationName'],
      }),
    transportQueryBody: z.strictObject({
      question: text,
      language,
    }),
    searchQuery: z.object({ q: text }),
    plannerQuery: z.object({ origin: text, destination: text }),
    nearbyQuery: z.object({
      lat: latitude,
      lng: longitude,
      radius: z.coerce
        .number()
        .int()
        .positive()
        .max(config.nearby.maxRadiusM)
        .default(config.nearby.defaultRadiusM),
    }),
    routeParams: z.object({ code }),
    stopParams: z.object({ id: code }),
  };
}

export type Schemas = ReturnType<typeof buildSchemas>;
