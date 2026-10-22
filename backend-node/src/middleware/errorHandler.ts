/**
 * Manejo centralizado de errores. Nunca expone trazas ni mensajes
 * internos: responde siempre `{ error: { code, message, details? } }`.
 */
import type { ErrorRequestHandler, RequestHandler } from 'express';
import { AppError, ErrorCode, errorBody, HttpStatus } from '../errors/AppError';
import type { Logger } from '../lib/logger';

interface BodyParserError {
    type?: string;
    status?: number;
}

const BODY_PARSER_ERRORS: Record<string, ErrorCode> = {
    'entity.too.large': ErrorCode.PAYLOAD_TOO_LARGE,
    'entity.parse.failed': ErrorCode.INVALID_JSON,
};

export const notFoundHandler: RequestHandler = (_req, _res, next) => {
    next(new AppError(ErrorCode.NOT_FOUND, 'Recurso no encontrado'));
};

export function createErrorHandler(logger: Logger): ErrorRequestHandler {
    return (err: unknown, req, res, _next) => {
        if (err instanceof AppError) {
            res.status(err.status).json(errorBody(err.code, err.message, err.details));
            return;
        }
        const parserType = (err as BodyParserError | undefined)?.type;
        if (parserType && parserType in BODY_PARSER_ERRORS) {
            const appError = new AppError(BODY_PARSER_ERRORS[parserType], 'Cuerpo de la petición inválido');
            res.status(appError.status).json(errorBody(appError.code, appError.message));
            return;
        }
        logger.error('unhandled_error', {
            path: req.path,
            error: err instanceof Error ? `${err.name}: ${err.message}` : String(err),
        });
        res.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorBody(ErrorCode.INTERNAL_ERROR, 'Error interno'));
    };
}
