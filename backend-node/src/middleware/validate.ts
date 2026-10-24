/**
 * Validación de entrada con esquemas zod. El resultado tipado queda en
 * `res.locals.body`, `res.locals.query` o `res.locals.params`.
 */
import type { RequestHandler } from 'express';
import type { z } from 'zod';
import { AppError, ErrorCode } from '../errors/AppError';

type Part = 'body' | 'query' | 'params';

export function validate(part: Part, schema: z.ZodType): RequestHandler {
  return (req, res, next) => {
    const result = schema.safeParse(req[part]);
    if (!result.success) {
      next(
        new AppError(
          ErrorCode.VALIDATION_ERROR,
          'La petición no cumple el formato esperado',
          result.error.issues.map((issue) => ({
            field: issue.path.join('.') || part,
            issue: issue.code,
          })),
        ),
      );
      return;
    }
    res.locals[part] = result.data;
    next();
  };
}
