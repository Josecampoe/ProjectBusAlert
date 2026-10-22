/**
 * Endpoints del asistente.
 *
 * POST /api/voice                → plan de viaje para el reloj
 * POST /api/ai/transport-query   → pregunta libre respondida con datos de la BD
 */
import { Router } from 'express';
import { AppError, ErrorCode } from '../errors/AppError';
import { validate } from '../middleware/validate';
import type { AssistantService } from '../services/assistantService';
import type { Language } from '../services/ai/prompts';
import { asyncHandler } from './asyncHandler';
import type { Schemas } from './schemas';

export function createAssistantRouter(assistant: AssistantService, schemas: Schemas): Router {
  const router = Router();

  router.post(
    '/voice',
    validate('body', schemas.voiceBody),
    asyncHandler(async (_req, res) => {
      const body = res.locals.body as {
        voiceText: string;
        currentLocationName: string;
        language: Language;
      };
      const result = await assistant.planTrip({
        destination: body.voiceText,
        originText: body.currentLocationName,
        language: body.language,
      });
      res.json(result);
    }),
  );

  router.post(
    '/ai/transport-query',
    validate('body', schemas.transportQueryBody),
    asyncHandler(async (_req, res) => {
      const body = res.locals.body as { question: string; language: Language };
      if (!assistant.aiEnabled) {
        throw new AppError(ErrorCode.AI_UNAVAILABLE, 'La IA no está configurada en este servidor');
      }
      let result;
      try {
        result = await assistant.answerQuestion(body.question, body.language);
      } catch {
        throw new AppError(ErrorCode.AI_UNAVAILABLE, 'El servicio de IA no respondió');
      }
      if (!result)
        throw new AppError(ErrorCode.AI_UNAVAILABLE, 'La IA no está configurada en este servidor');
      res.json(result);
    }),
  );

  return router;
}
