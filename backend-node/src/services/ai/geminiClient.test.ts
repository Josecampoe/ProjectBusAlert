import { beforeEach, describe, expect, it, vi } from 'vitest';
import { silentLogger } from '../../lib/logger';
import { ApiError } from '@google/genai';
import { AiError } from './aiClient';
import { classifyGeminiError, GeminiClient } from './geminiClient';

// vi.mock se eleva antes de los imports. Se reemplaza el SDK y cada prueba define qué responde cada modelo.
const { generateContent } = vi.hoisted(() => ({ generateContent: vi.fn() }));
vi.mock('@google/genai', () => {
  class ApiError extends Error {
    constructor(readonly status: number) {
      super(`HTTP ${status}`);
    }
  }
  class GoogleGenAI {
    models = { generateContent };
  }
  return { ApiError, GoogleGenAI };
});

const request = { systemInstruction: 's', userContent: 'u', responseSchema: { type: 'object' } };
const client = () =>
  new GeminiClient(
    { apiKey: 'k', models: ['principal', 'respaldo'], timeoutMs: 1_000, temperature: 0.2 },
    silentLogger,
  );
const apiError = (status: number) => new (ApiError as unknown as new (s: number) => Error)(status);

beforeEach(() => {
  generateContent.mockReset();
});

describe('classifyGeminiError', () => {
  it.each([
    [401, 'auth'],
    [403, 'auth'],
    [429, 'quota'],
    [404, 'unavailable'],
    [503, 'unavailable'],
    [400, 'unknown'],
  ])('HTTP %i → %s', (status, kind) => {
    expect(classifyGeminiError(apiError(status))).toBe(kind);
  });

  it('distingue timeout y red', () => {
    const timeout = new Error('t');
    timeout.name = 'TimeoutError';
    expect(classifyGeminiError(timeout)).toBe('timeout');
    expect(classifyGeminiError(new TypeError('fetch failed'))).toBe('network');
  });
});

describe('GeminiClient', () => {
  it('pide JSON estructurado con instrucciones de sistema separadas', async () => {
    generateContent.mockResolvedValue({ text: '{"ok":true}' });
    await expect(client().generateJson(request)).resolves.toBe('{"ok":true}');
    const call = generateContent.mock.calls[0][0];
    expect(call.model).toBe('principal');
    expect(call.contents).toBe('u');
    expect(call.config).toMatchObject({
      systemInstruction: 's',
      responseMimeType: 'application/json',
      responseJsonSchema: { type: 'object' },
      temperature: 0.2,
    });
  });

  it('usa el modelo de respaldo si el principal está saturado', async () => {
    generateContent.mockRejectedValueOnce(apiError(503)).mockResolvedValueOnce({ text: '{}' });
    await expect(client().generateJson(request)).resolves.toBe('{}');
    expect(generateContent.mock.calls.map((c) => c[0].model)).toEqual(['principal', 'respaldo']);
  });

  it('no prueba otro modelo ante un error de autenticación', async () => {
    generateContent.mockRejectedValue(apiError(401));
    await expect(client().generateJson(request)).rejects.toMatchObject({ kind: 'auth' });
    expect(generateContent).toHaveBeenCalledTimes(1);
  });

  it('trata una respuesta vacía como salida inválida y reintenta', async () => {
    generateContent.mockResolvedValueOnce({ text: '  ' }).mockResolvedValueOnce({ text: '' });
    const error = await client()
      .generateJson(request)
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(AiError);
    expect((error as AiError).kind).toBe('invalid_output');
    expect(generateContent).toHaveBeenCalledTimes(2);
  });
});
