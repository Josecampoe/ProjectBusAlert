import { describe, expect, it } from 'vitest';
import { ConfigError, loadConfig } from './env';
import { BASE_TEST_ENV } from '../test/helpers';

describe('loadConfig', () => {
    it('carga una configuración válida y separa listas por coma', () => {
        const config = loadConfig({ ...BASE_TEST_ENV, API_KEYS: 'aaaaaaaaaaaaaaaa, bbbbbbbbbbbbbbbb', CORS_ORIGINS: 'https://a.co,https://b.co' });
        expect(config.security.apiKeys).toEqual(['aaaaaaaaaaaaaaaa', 'bbbbbbbbbbbbbbbb']);
        expect(config.server.corsOrigins).toEqual(['https://a.co', 'https://b.co']);
        expect(config.ai.enabled).toBe(false);
    });

    it.each(['PORT', 'API_KEYS', 'DB_PATH', 'DATA_DIR'])('falla con mensaje claro si falta %s', (name) => {
        const env = { ...BASE_TEST_ENV };
        delete env[name];
        expect(() => loadConfig(env)).toThrow(ConfigError);
        try {
            loadConfig(env);
        } catch (err) {
            expect((err as ConfigError).issues.join('\n')).toContain(name);
        }
    });

    it('rechaza claves de aplicación cortas o vacías', () => {
        expect(() => loadConfig({ ...BASE_TEST_ENV, API_KEYS: 'corta' })).toThrow(ConfigError);
        expect(() => loadConfig({ ...BASE_TEST_ENV, API_KEYS: '' })).toThrow(ConfigError);
    });

    it('habilita la IA solo con una clave no vacía', () => {
        expect(loadConfig({ ...BASE_TEST_ENV, GEMINI_API_KEY: '   ' }).ai.enabled).toBe(false);
        expect(loadConfig({ ...BASE_TEST_ENV, GEMINI_API_KEY: 'k' }).ai.enabled).toBe(true);
    });

    it('rechaza un radio por defecto mayor que el máximo', () => {
        expect(() =>
            loadConfig({ ...BASE_TEST_ENV, NEARBY_DEFAULT_RADIUS_M: '5000', NEARBY_MAX_RADIUS_M: '1000' }),
        ).toThrow(ConfigError);
    });
});
