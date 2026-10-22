/**
 * Autenticación por clave de aplicación y límite de tasa.
 */
import { timingSafeEqual } from 'node:crypto';
import type { RequestHandler } from 'express';
import { rateLimit } from 'express-rate-limit';
import { AppError, ErrorCode, errorBody, HttpStatus } from '../errors/AppError';

export const API_KEY_HEADER = 'x-api-key';

function safeEquals(a: string, b: string): boolean {
    const left = Buffer.from(a);
    const right = Buffer.from(b);
    return left.length === right.length && timingSafeEqual(left, right);
}

/** Exige una de las claves configuradas en la cabecera `X-API-Key`. */
export function requireApiKey(validKeys: string[]): RequestHandler {
    return (req, _res, next) => {
        const provided = req.header(API_KEY_HEADER);
        if (!provided || !validKeys.some((key) => safeEquals(key, provided))) {
            next(new AppError(ErrorCode.UNAUTHORIZED, 'Falta la cabecera X-API-Key o no es válida'));
            return;
        }
        next();
    };
}

/** Límite de peticiones por IP en una ventana de tiempo. */
export function createRateLimiter(windowMs: number, max: number): RequestHandler {
    return rateLimit({
        windowMs,
        limit: max,
        standardHeaders: 'draft-8',
        legacyHeaders: false,
        handler: (_req, res) => {
            res.status(HttpStatus.TOO_MANY_REQUESTS).json(
                errorBody(ErrorCode.RATE_LIMITED, 'Demasiadas peticiones; intenta más tarde'),
            );
        },
    });
}
