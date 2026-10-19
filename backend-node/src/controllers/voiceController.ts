import { Router, Request, Response } from 'express';
import { processVoiceCommand } from '../services/aiService';

export const voiceRouter = Router();

voiceRouter.post('/', async (req: Request, res: Response) => {
    const { voiceText, currentLocationName, language } = req.body;

    // Validación de entrada
    if (!voiceText || typeof voiceText !== 'string' || voiceText.trim().length === 0) {
        return res.status(400).json({
            error: 'El campo voiceText es requerido y no puede estar vacío.'
        });
    }

    if (voiceText.trim().length > 500) {
        return res.status(400).json({
            error: 'El texto de voz es demasiado largo (máximo 500 caracteres).'
        });
    }

    const location = (currentLocationName && typeof currentLocationName === 'string')
        ? currentLocationName.trim()
        : 'Pasto, Nariño';

    const lang = (language === 'en') ? 'en' : 'es';

    try {
        console.log(`[BusAlert] 🎤 Procesando (${lang}): "${voiceText}" | Desde: "${location}"`);
        const instruction = await processVoiceCommand(voiceText.trim(), location, lang);
        res.json(instruction);
    } catch (error) {
        console.error('[BusAlert] ❌ Error crítico en el controlador:', error);
        res.status(500).json({
            spokenText: 'Ocurrió un error en el servidor. Por favor intenta de nuevo.',
            displayTitle: 'Error del servidor',
            displaySubtitle: 'Intenta de nuevo',
            estimatedMinutes: null,
            recommendedRouteId: null
        });
    }
});
